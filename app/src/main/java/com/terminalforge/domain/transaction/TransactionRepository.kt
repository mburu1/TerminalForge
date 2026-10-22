package com.terminalforge.domain.transaction

/**
 * Persistence port. Implementations may be in-memory, Room-backed, or remote.
 */
interface TransactionRepository {
    fun save(transaction: Transaction)
    fun findById(transactionId: String): Transaction?
    fun findAll(): List<Transaction>
}
