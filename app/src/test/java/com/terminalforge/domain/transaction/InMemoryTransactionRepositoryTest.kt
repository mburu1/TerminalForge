package com.terminalforge.domain.transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
class InMemoryTransactionRepositoryTest {
 private fun txn(id:String="txn-repo-001")=Transaction.create(id,Money(1250,"KES"),OperationType.SALE,"idem-$id",1L)
 @Test fun savesAndRetrievesById() {
  val repo=InMemoryTransactionRepository(); val saved=txn().transitionTo(TransactionState.FAILED); repo.save(saved)
  assertEquals(saved,repo.findById(saved.id)); assertEquals(listOf(saved),repo.findAll())
 }
 @Test fun missingIdReturnsNull() { assertNull(InMemoryTransactionRepository().findById("missing")) }
 @Test fun duplicateIdRejectedWithoutOverwrite() {
  val repo=InMemoryTransactionRepository(); val original=txn(); repo.save(original)
  assertThrows(IllegalArgumentException::class.java) { repo.save(original.transitionTo(TransactionState.VALIDATING)) }
  assertEquals(TransactionState.CREATED,repo.findById(original.id)?.state); assertEquals(1,repo.findAll().size)
 }
}
