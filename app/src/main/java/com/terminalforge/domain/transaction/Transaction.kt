package com.terminalforge.domain.transaction

enum class OperationType {
    SALE,
    REFUND,
    REVERSAL
}

enum class TransactionState {
    CREATED,
    VALIDATING,
    READY,
    PROCESSING,
    APPROVED,
    DECLINED,
    FAILED,
    UNKNOWN_OUTCOME,
    RECONCILIATION_REQUIRED,
    RESOLVED,
    REFUND_PENDING,
    REFUNDED,
    REVERSAL_PENDING,
    REVERSED
}

/**
 * Immutable transaction snapshot. Instances begin in CREATED; later state changes
 * must go through transitionTo so callers cannot bypass the transition graph.
 */
class Transaction private constructor(
    val id: String,
    val amount: Money,
    val operationType: OperationType,
    val state: TransactionState,
    val idempotencyKey: String,
    val createdAtEpochMillis: Long
) {
    init {
        require(id.isNotBlank()) { "Transaction ID must not be blank." }
        require(amount.minorUnits > 0) { "Transaction amount must be greater than zero." }
        require(idempotencyKey.isNotBlank()) { "Idempotency key must not be blank." }
        require(createdAtEpochMillis >= 0) { "Creation timestamp must not be negative." }
    }

    fun transitionTo(next: TransactionState): Transaction {
        require(next in ALLOWED_TRANSITIONS.getValue(state)) {
            "Illegal transaction state transition: $state -> $next"
        }
        return Transaction(
            id = id,
            amount = amount,
            operationType = operationType,
            state = next,
            idempotencyKey = idempotencyKey,
            createdAtEpochMillis = createdAtEpochMillis
        )
    }

    companion object {
        fun create(
            id: String,
            amount: Money,
            operationType: OperationType,
            idempotencyKey: String,
            createdAtEpochMillis: Long
        ): Transaction = Transaction(
            id = id,
            amount = amount,
            operationType = operationType,
            state = TransactionState.CREATED,
            idempotencyKey = idempotencyKey,
            createdAtEpochMillis = createdAtEpochMillis
        )

        private val ALLOWED_TRANSITIONS = mapOf(
            TransactionState.CREATED to setOf(TransactionState.VALIDATING, TransactionState.FAILED),
            TransactionState.VALIDATING to setOf(TransactionState.READY, TransactionState.FAILED),
            TransactionState.READY to setOf(TransactionState.PROCESSING, TransactionState.FAILED),
            TransactionState.PROCESSING to setOf(
                TransactionState.APPROVED,
                TransactionState.DECLINED,
                TransactionState.FAILED,
                TransactionState.UNKNOWN_OUTCOME
            ),
            TransactionState.UNKNOWN_OUTCOME to setOf(TransactionState.RECONCILIATION_REQUIRED),
            TransactionState.RECONCILIATION_REQUIRED to setOf(TransactionState.RESOLVED),
            TransactionState.APPROVED to setOf(
                TransactionState.REFUND_PENDING,
                TransactionState.REVERSAL_PENDING
            ),
            TransactionState.REFUND_PENDING to setOf(
                TransactionState.REFUNDED,
                TransactionState.FAILED,
                TransactionState.UNKNOWN_OUTCOME
            ),
            TransactionState.REVERSAL_PENDING to setOf(
                TransactionState.REVERSED,
                TransactionState.FAILED,
                TransactionState.UNKNOWN_OUTCOME
            ),
            TransactionState.DECLINED to emptySet(),
            TransactionState.FAILED to emptySet(),
            TransactionState.RESOLVED to emptySet(),
            TransactionState.REFUNDED to emptySet(),
            TransactionState.REVERSED to emptySet()
        )
    }
}
