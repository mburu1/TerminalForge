package com.terminalforge.domain.reliability

import com.terminalforge.domain.transaction.Transaction
import com.terminalforge.domain.transaction.TransactionRepository
import com.terminalforge.domain.transaction.TransactionState

/** Recover interrupted processor calls without retrying potentially completed operations. */
class StartupRecovery(private val repository: TransactionRepository) {
    @Synchronized
    fun recover(): List<Transaction> {
        val recovered = mutableListOf<Transaction>()
        repository.findAll().forEach { stored ->
            var current = stored
            if (current.state == TransactionState.PROCESSING) {
                val unknown = current.transitionTo(TransactionState.UNKNOWN_OUTCOME)
                repository.update(current, unknown)
                current = unknown
            }
            if (current.state == TransactionState.UNKNOWN_OUTCOME) {
                val reconciliation = current.transitionTo(TransactionState.RECONCILIATION_REQUIRED)
                repository.update(current, reconciliation)
                current = reconciliation
            }
            if (current != stored) recovered += current
        }
        return recovered
    }
}
