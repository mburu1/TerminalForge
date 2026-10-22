package com.terminalforge.domain.sale

import com.terminalforge.data.local.FileCallbackDeduplicator
import com.terminalforge.data.local.FileTransactionRepository
import com.terminalforge.domain.payment.PaymentProcessor
import com.terminalforge.domain.payment.ProcessorResult
import com.terminalforge.domain.transaction.InMemoryTransactionRepository
import com.terminalforge.domain.transaction.Money
import com.terminalforge.domain.transaction.OperationType
import com.terminalforge.domain.transaction.Transaction
import com.terminalforge.domain.transaction.TransactionState
import com.terminalforge.domain.reliability.CallbackHandlingResult
import com.terminalforge.domain.reliability.ProcessorCallbackHandler
import com.terminalforge.domain.reliability.StartupRecovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch

class ExecuteSaleReliabilityTest {
    private class Ids : SaleIdentityProvider {
        private var next = 0
        override fun newTransactionId() = "txn-m3-${++next}"
        override fun nowEpochMillis() = 100L
    }
    private fun request(key: String = "idem-m3", amount: Money = Money(500, "KES")) = SaleRequest(amount, key)

    @Test fun sameLogicalRequestReturnsOriginalAndDoesNotInvokeProcessorAgain() {
        var calls = 0
        val repository = InMemoryTransactionRepository()
        val sale = ExecuteSale(PaymentProcessor { calls++; ProcessorResult.Approved("mock-ref") }, repository, Ids())
        val first = sale.execute(request()) as SaleResult.Completed
        val replay = sale.execute(request()) as SaleResult.IdempotentReplay
        assertEquals(first.transaction.id, replay.transaction.id)
        assertEquals(1, calls)
    }

    @Test fun idempotencyKeyWithDifferentAmountIsRejected() {
        val sale = ExecuteSale(PaymentProcessor { ProcessorResult.Approved("mock-ref") },
            InMemoryTransactionRepository(), Ids())
        sale.execute(request())
        assertThrows(IdempotencyConflict::class.java) { sale.execute(request(amount = Money(501, "KES"))) }
    }

    @Test fun timeoutIsUnknownAndNotAutomaticallyRetried() {
        var calls = 0
        val started = CountDownLatch(1)
        val sale = ExecuteSale(PaymentProcessor {
            calls++
            started.countDown()
            Thread.sleep(1_000)
            ProcessorResult.Approved("late-mock-ref")
        }, InMemoryTransactionRepository(), Ids(), 25L)
        val result = sale.execute(request("timeout"))
        assertTrue(started.await(1, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(result is SaleResult.Ambiguous)
        assertEquals(TransactionState.UNKNOWN_OUTCOME, (result as SaleResult.Ambiguous).transaction.state)
        assertTrue(sale.execute(request("timeout")) is SaleResult.IdempotentReplay)
        assertEquals(1, calls)
    }

    @Test fun restartRecoveryRoutesProcessingTransactionToReconciliation() {
        val file = File.createTempFile("terminalforge-m3", ".db")
        try {
            file.delete()
            val repository = FileTransactionRepository(file)
            val processing = Transaction.create("txn-recovery", Money(500, "KES"), OperationType.SALE, "idem-recovery", 1L)
                .transitionTo(TransactionState.VALIDATING)
                .transitionTo(TransactionState.READY)
                .transitionTo(TransactionState.PROCESSING)
            repository.save(processing)
            val restarted = FileTransactionRepository(file)
            assertEquals(TransactionState.RECONCILIATION_REQUIRED, StartupRecovery(restarted).recover().single().state)
            assertEquals(TransactionState.RECONCILIATION_REQUIRED, FileTransactionRepository(file).findById("txn-recovery")!!.state)
        } finally { file.delete() }
    }

    @Test fun callbackEventIsDurableAndLateOutcomeRequiresReconciliation() {
        val eventFile = File.createTempFile("terminalforge-events", ".db")
        val transactionFile = File.createTempFile("terminalforge-txn", ".db")
        try {
            eventFile.delete()
            transactionFile.delete()
            val repository = FileTransactionRepository(transactionFile)
            val unknown = Transaction.create("txn-late", Money(500, "KES"), OperationType.SALE, "idem-late", 1L)
                .transitionTo(TransactionState.VALIDATING)
                .transitionTo(TransactionState.READY)
                .transitionTo(TransactionState.PROCESSING)
                .transitionTo(TransactionState.UNKNOWN_OUTCOME)
            repository.save(unknown)
            val handler = ProcessorCallbackHandler(repository, FileCallbackDeduplicator(eventFile))
            assertTrue(handler.handle("txn-late", "evt-1", ProcessorResult.Approved("mock-ref"))
                is CallbackHandlingResult.ReconciliationRequired)
            assertFalse(FileCallbackDeduplicator(eventFile).accept("txn-late", "evt-1"))
            assertEquals(TransactionState.RECONCILIATION_REQUIRED, FileTransactionRepository(transactionFile).findById("txn-late")!!.state)
        } finally { eventFile.delete(); transactionFile.delete() }
    }
}
