package com.terminalforge.domain.sale
import com.terminalforge.domain.payment.*
import com.terminalforge.domain.transaction.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
class ExecuteSaleTest {
 private class FixedIds:SaleIdentityProvider { private var n=0; override fun newTransactionId()="txn-m2-${++n}"; override fun nowEpochMillis()=1_798_000_000_000L }
 private fun useCase(p:PaymentProcessor,r:TransactionRepository=InMemoryTransactionRepository())=ExecuteSale(p,r,FixedIds())
 private fun request(key:String="idem-m2")=SaleRequest(Money(2599,"KES"),key)
 @Test fun approvedSalePersisted() {
  val repo=InMemoryTransactionRepository(); val result=useCase(DeterministicMockPaymentProcessor(),repo).execute(request())
  assertTrue(result is SaleResult.Completed); val completed=result as SaleResult.Completed
  assertEquals(TransactionState.APPROVED,completed.transaction.state); assertEquals("MOCK-txn-m2-1",completed.processorReference)
  assertEquals(completed.transaction,repo.findById(completed.transaction.id))
 }
 @Test fun declinePersistsDeclined() {
  val repo=InMemoryTransactionRepository()
  val result=useCase(DeterministicMockPaymentProcessor(DeterministicMockPaymentProcessor.Scenario.DECLINED),repo).execute(request("decline"))
  assertTrue(result is SaleResult.Declined); assertEquals(TransactionState.DECLINED,repo.findById("txn-m2-1")?.state)
 }
 @Test fun processorFailureNeverApproves() {
  val repo=InMemoryTransactionRepository()
  val result=useCase(DeterministicMockPaymentProcessor(DeterministicMockPaymentProcessor.Scenario.FAILED),repo).execute(request("failed"))
  assertTrue(result is SaleResult.Failed); assertEquals(TransactionState.FAILED,repo.findById("txn-m2-1")?.state)
 }
 @Test fun adapterExceptionBecomesTypedFailure() {
  val repo=InMemoryTransactionRepository(); val p=PaymentProcessor { _:PaymentRequest -> error("simulated adapter error") }
  val result=useCase(p,repo).execute(request("exception"))
  assertTrue(result is SaleResult.Failed); assertEquals("PROCESSOR_ADAPTER_ERROR",(result as SaleResult.Failed).reasonCode)
  assertEquals(TransactionState.FAILED,repo.findById("txn-m2-1")?.state)
 }
 @Test fun zeroAmountRejected() { assertThrows(IllegalArgumentException::class.java) { SaleRequest(Money(0,"KES"),"zero") } }
 @Test fun duplicateIdDoesNotOverwrite() {
  val repo=InMemoryTransactionRepository(); repo.save(Transaction.create("txn-m2-1",Money(100,"KES"),OperationType.SALE,"existing",1L))
  assertThrows(IllegalArgumentException::class.java) { useCase(DeterministicMockPaymentProcessor(),repo).execute(request("new")) }
  assertEquals(TransactionState.CREATED,repo.findById("txn-m2-1")?.state)
 }
}
