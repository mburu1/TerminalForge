package com.terminalforge.domain.reliability

import com.terminalforge.domain.payment.ProcessorResult
import com.terminalforge.domain.transaction.InMemoryTransactionRepository
import com.terminalforge.domain.transaction.Money
import com.terminalforge.domain.transaction.OperationType
import com.terminalforge.domain.transaction.Transaction
import com.terminalforge.domain.transaction.TransactionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessorCallbackHandlerTest {
    @Test
    fun callbackAppliesOnceAndDuplicateEventIsIgnored() {
        val repository = InMemoryTransactionRepository()
        val processing = Transaction.create("txn-callback", Money(100, "KES"), OperationType.SALE, "idem-callback", 1L)
            .transitionTo(TransactionState.VALIDATING)
            .transitionTo(TransactionState.READY)
            .transitionTo(TransactionState.PROCESSING)
        repository.save(processing)
        val handler = ProcessorCallbackHandler(repository, CallbackDeduplicator())

        assertTrue(handler.handle("txn-callback", "evt-1", ProcessorResult.Approved("mock-ref"))
            is CallbackHandlingResult.Applied)
        assertEquals(CallbackHandlingResult.Duplicate,
            handler.handle("txn-callback", "evt-1", ProcessorResult.Approved("mock-ref")))
        assertEquals(TransactionState.APPROVED, repository.findById("txn-callback")!!.state)
    }

    @Test
    fun lateCallbackRequiresReconciliationInsteadOfChangingUnknownToApproved() {
        val repository = InMemoryTransactionRepository()
        val unknown = Transaction.create("txn-late", Money(100, "KES"), OperationType.SALE, "idem-late", 1L)
            .transitionTo(TransactionState.VALIDATING)
            .transitionTo(TransactionState.READY)
            .transitionTo(TransactionState.PROCESSING)
            .transitionTo(TransactionState.UNKNOWN_OUTCOME)
        repository.save(unknown)
        val handler = ProcessorCallbackHandler(repository, CallbackDeduplicator())

        assertTrue(handler.handle("txn-late", "evt-late", ProcessorResult.Approved("mock-ref"))
            is CallbackHandlingResult.ReconciliationRequired)
        assertEquals(TransactionState.RECONCILIATION_REQUIRED, repository.findById("txn-late")!!.state)
    }
}
