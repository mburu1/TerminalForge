package com.terminalforge.data.repository

import com.terminalforge.domain.transaction.Transaction
import com.terminalforge.domain.transaction.TransactionRepository
import java.util.concurrent.ConcurrentHashMap

/** Process-local M2 adapter; records are lost when the app process exits. */
class InMemoryTransactionRepository : TransactionRepository {
    private val transactions = ConcurrentHashMap<String, Transaction>()

    override fun save(transaction: Transaction) {
        transactions[transaction.id] = transaction
    }

    override fun findById(transactionId: String): Transaction? = transactions[transactionId]

    override fun findAll(): List<Transaction> = transactions.values.sortedWith(
        compareBy<Transaction> { it.createdAtEpochMillis }.thenBy { it.id }
    )
}
