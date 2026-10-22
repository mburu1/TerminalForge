package com.terminalforge.domain.transaction

/** Persistence port. Duplicate transaction IDs and logical requests are never overwritten. */
interface TransactionRepository {
    fun save(transaction: Transaction)
    fun findById(id: String): Transaction?
    fun findAll(): List<Transaction>

    fun findByIdempotencyKey(key: String): Transaction? =
        findAll().firstOrNull { it.idempotencyKey == key }

    /** Persist a legal state-transition snapshot for an already inserted transaction. */
    fun update(previous: Transaction, replacement: Transaction) {
        throw UnsupportedOperationException("This repository does not support transaction updates.")
    }
}

/** Thread-safe, non-durable M2 adapter; use FileTransactionRepository for restart tests. */
class InMemoryTransactionRepository : TransactionRepository {
    private val transactions = linkedMapOf<String, Transaction>()

    @Synchronized
    override fun save(transaction: Transaction) {
        require(!transactions.containsKey(transaction.id)) { "Transaction ID already exists." }
        require(transactions.values.none { it.idempotencyKey == transaction.idempotencyKey }) {
            "Idempotency key already exists."
        }
        transactions[transaction.id] = transaction
    }

    @Synchronized
    override fun update(previous: Transaction, replacement: Transaction) {
        require(previous.id == replacement.id) { "Cannot change transaction identity." }
        require(transactions[previous.id] == previous) { "Transaction changed concurrently." }
        require(replacement.idempotencyKey == previous.idempotencyKey) { "Cannot change idempotency key." }
        transactions[replacement.id] = replacement
    }

    @Synchronized
    override fun findById(id: String): Transaction? = transactions[id]

    @Synchronized
    override fun findAll(): List<Transaction> = transactions.values.toList()

    @Synchronized
    override fun findByIdempotencyKey(key: String): Transaction? =
        transactions.values.firstOrNull { it.idempotencyKey == key }
}
