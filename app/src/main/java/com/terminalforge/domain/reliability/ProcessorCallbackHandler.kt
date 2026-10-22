package com.terminalforge.domain.reliability

import com.terminalforge.domain.payment.ProcessorResult
import com.terminalforge.domain.transaction.TransactionRepository
import com.terminalforge.domain.transaction.TransactionState

sealed interface CallbackHandlingResult {
    data object Duplicate : CallbackHandlingResult
    data object UnknownTransaction : CallbackHandlingResult
    data class Applied(val state: TransactionState) : CallbackHandlingResult
    data class ReconciliationRequired(val lateResult: ProcessorResult) : CallbackHandlingResult
    data class IgnoredTerminal(val state: TransactionState) : CallbackHandlingResult
}

/**
 * Applies a processor callback at most once per (transactionId,eventId).
 * Late callbacks after timeout are recorded for reconciliation; they do not silently overwrite
 * UNKNOWN_OUTCOME with a guessed financial result.
 */
class ProcessorCallbackHandler(
    private val repository: TransactionRepository,
    private val deduplicator: CallbackEventStore
) {
    @Synchronized
    fun handle(transactionId: String, eventId: String, result: ProcessorResult): CallbackHandlingResult {
        if (!deduplicator.accept(transactionId, eventId)) return CallbackHandlingResult.Duplicate
        val current = repository.findById(transactionId) ?: return CallbackHandlingResult.UnknownTransaction

        if (current.state == TransactionState.UNKNOWN_OUTCOME) {
            val reconciliation = current.transitionTo(TransactionState.RECONCILIATION_REQUIRED)
            repository.update(current, reconciliation)
            return CallbackHandlingResult.ReconciliationRequired(result)
        }
        if (current.state != TransactionState.PROCESSING) {
            return CallbackHandlingResult.IgnoredTerminal(current.state)
        }

        val next = when (result) {
            is ProcessorResult.Approved -> current.transitionTo(TransactionState.APPROVED)
            is ProcessorResult.Declined -> current.transitionTo(TransactionState.DECLINED)
            is ProcessorResult.Failed -> current.transitionTo(TransactionState.FAILED)
        }
        repository.update(current, next)
        return CallbackHandlingResult.Applied(next.state)
    }
}
