package com.terminalforge.domain.transaction
/** Persistence port. Duplicate transaction IDs are rejected, never overwritten. */
interface TransactionRepository {
 fun save(transaction:Transaction)
 fun findById(id:String):Transaction?
 fun findAll():List<Transaction>
}
/** Thread-safe, non-durable M2 adapter. */
class InMemoryTransactionRepository:TransactionRepository {
 private val transactions=linkedMapOf<String,Transaction>()
 @Synchronized override fun save(transaction:Transaction) {
  require(!transactions.containsKey(transaction.id)) { "Transaction ID already exists." }
  transactions[transaction.id]=transaction
 }
 @Synchronized override fun findById(id:String):Transaction?=transactions[id]
 @Synchronized override fun findAll():List<Transaction> = transactions.values.toList()
}
