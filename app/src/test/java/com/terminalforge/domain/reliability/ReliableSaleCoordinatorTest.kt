package com.terminalforge.domain.reliability

import com.terminalforge.data.local.FileTransactionRepository
import com.terminalforge.data.local.FileCallbackDeduplicator
import com.terminalforge.domain.payment.PaymentProcessor
import com.terminalforge.domain.payment.ProcessorResult
import com.terminalforge.domain.transaction.InMemoryTransactionRepository
import com.terminalforge.domain.transaction.Money
import com.terminalforge.domain.transaction.OperationType
import com.terminalforge.domain.transaction.Transaction
import com.terminalforge.domain.transaction.TransactionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReliableSaleCoordinatorTest {
    private val amount = Money(1250, "KES")

    @Test
    fun replayWithSameKeyReturnsOriginalTransactionWithoutCallingProcessorAgain() {
        var calls = 0
        val repository = InMemoryTransactionRepository()
        val coordinator = ReliableSaleCoordinator(PaymentProcessor {
            calls++
            ProcessorResult.Approved("mock-ref-001")
        }, repository, { 100L })

        val first = coordinator.execute("txn-1", "order-1", amount) as SaleExecutionResult.Completed
        val replay = coordinator.execute("txn-2", "order-1", amount) as SaleExecutionResult.IdempotentReplay
        assertEquals(TransactionState.APPROVED, first.transaction.state)
        assertEquals(first.transaction.id, replay.transaction.id)
        assertEquals(1, calls)
    }

    @Test
    fun reusingKeyForDifferentAmountIsRejected() {
        val coordinator = ReliableSaleCoordinator(
            PaymentProcessor { ProcessorResult.Approved("mock-ref") },
            InMemoryTransactionRepository(), { 100L }
        )
        coordinator.execute("txn-1", "order-1", amount)
        assertThrows(IdempotencyConflict::class.java) {
            coordinator.execute("txn-2", "order-1", Money(2500, "KES"))
        }
    }

    @Test
    fun timeoutIsUnknownAndDoesNotRetryAutomatically() {
        var calls = 0
        val started = CountDownLatch(1)
        val processor = PaymentProcessor {
            calls++
            started.countDown()
            Thread.sleep(2_000)
            ProcessorResult.Approved("too-late")
        }
        val repository = InMemoryTransactionRepository()
        val coordinator = ReliableSaleCoordinator(processor, repository, { 100L }, 30L)
        val result = coordinator.execute("txn-timeout", "order-timeout", amount)
        assertTrue(started.await(1, TimeUnit.SECONDS))
        assertTrue(result is SaleExecutionResult.Ambiguous)
        assertEquals(TransactionState.UNKNOWN_OUTCOME, repository.findById("txn-timeout")!!.state)
        coordinator.execute("txn-replay", "order-timeout", amount)
        assertEquals(1, calls)
    }

    @Test
    fun duplicateCallbackIsRejectedAfterRestart() {
        val file = File.createTempFile("terminalforge-callbacks", ".db")
        try {
            file.delete()
            assertTrue(FileCallbackDeduplicator(file).accept("txn-1", "event-1"))
            assertFalse(FileCallbackDeduplicator(file).accept("txn-1", "event-1"))
            assertTrue(FileCallbackDeduplicator(file).accept("txn-1", "event-2"))
        } finally { file.delete() }
    }

    @Test
    fun restartRecoveryMovesInFlightTransactionToReconciliationRequired() {
        val file = File.createTempFile("terminalforge-transactions", ".db")
        try {
            file.delete()
            val repository = FileTransactionRepository(file)
            val transaction = Transaction.create(
                "txn-recovery", amount, OperationType.SALE, "idem-recovery", 10L
            ).transitionTo(TransactionState.VALIDATING)
                .transitionTo(TransactionState.READY)
                .transitionTo(TransactionState.PROCESSING)
            repository.save(transaction)

            val restarted = FileTransactionRepository(file)
            val recovered = StartupRecovery(restarted).recover().single()
            assertEquals(TransactionState.RECONCILIATION_REQUIRED, recovered.state)
            assertEquals(TransactionState.RECONCILIATION_REQUIRED,
                FileTransactionRepository(file).findById("txn-recovery")!!.state)
        } finally { file.delete() }
    }
}
