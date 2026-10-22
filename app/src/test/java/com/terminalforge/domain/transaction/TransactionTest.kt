package com.terminalforge.domain.transaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransactionTest {
    private fun transaction() = Transaction.create(
        id = "txn-test-001",
        amount = Money(1250, "KES"),
        operationType = OperationType.SALE,
        idempotencyKey = "idem-test-001",
        createdAtEpochMillis = 1L
    )

    @Test
    fun validSaleTransitionsReachApproved() {
        val approved = transaction()
            .transitionTo(TransactionState.VALIDATING)
            .transitionTo(TransactionState.READY)
            .transitionTo(TransactionState.PROCESSING)
            .transitionTo(TransactionState.APPROVED)

        assertEquals(TransactionState.APPROVED, approved.state)
    }

    @Test
    fun timeoutCanBecomeUnknownOutcomeButNotDefinitiveDecline() {
        val processing = transaction()
            .transitionTo(TransactionState.VALIDATING)
            .transitionTo(TransactionState.READY)
            .transitionTo(TransactionState.PROCESSING)

        assertEquals(
            TransactionState.UNKNOWN_OUTCOME,
            processing.transitionTo(TransactionState.UNKNOWN_OUTCOME).state
        )
        assertThrows(IllegalArgumentException::class.java) {
            processing.transitionTo(TransactionState.DECLINED)
        }
    }

    @Test
    fun terminalStateCannotTransitionAgain() {
        val declined = transaction()
            .transitionTo(TransactionState.VALIDATING)
            .transitionTo(TransactionState.READY)
            .transitionTo(TransactionState.PROCESSING)
            .transitionTo(TransactionState.DECLINED)

        assertThrows(IllegalArgumentException::class.java) {
            declined.transitionTo(TransactionState.PROCESSING)
        }
    }

    @Test
    fun transactionRequiresPositiveAmountAndIdempotencyKey() {
        assertThrows(IllegalArgumentException::class.java) {
            Transaction.create("txn-zero", Money(0, "KES"), OperationType.SALE, "idem-zero", 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Transaction.create("txn-blank-key", Money(1, "KES"), OperationType.SALE, " ", 1L)
        }
    }
}
