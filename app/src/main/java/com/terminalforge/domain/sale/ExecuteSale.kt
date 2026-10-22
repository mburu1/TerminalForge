package com.terminalforge.domain.sale
import com.terminalforge.domain.payment.PaymentProcessor
import com.terminalforge.domain.payment.PaymentRequest
import com.terminalforge.domain.payment.ProcessorResult
import com.terminalforge.domain.transaction.*
data class SaleRequest(val amount:Money,val idempotencyKey:String) {
 init { require(amount.minorUnits>0) { "Sale amount must be positive." }; require(idempotencyKey.isNotBlank()) }
}
interface SaleIdentityProvider { fun newTransactionId():String; fun nowEpochMillis():Long }
sealed interface SaleResult {
 data class Completed(val transaction:Transaction,val processorReference:String):SaleResult
 data class Declined(val transaction:Transaction,val reasonCode:String):SaleResult
 data class Failed(val transaction:Transaction,val reasonCode:String):SaleResult
}
/** Executes one sale; only explicit processor approval can approve the transaction. */
class ExecuteSale(private val paymentProcessor:PaymentProcessor,private val transactionRepository:TransactionRepository,private val identityProvider:SaleIdentityProvider) {
 fun execute(request:SaleRequest):SaleResult {
  var transaction=Transaction.create(identityProvider.newTransactionId(),request.amount,OperationType.SALE,request.idempotencyKey,identityProvider.nowEpochMillis())
   .transitionTo(TransactionState.VALIDATING).transitionTo(TransactionState.READY).transitionTo(TransactionState.PROCESSING)
  val processorResult=try { paymentProcessor.process(PaymentRequest(transaction.id,request.amount)) }
   catch (_:Exception) { ProcessorResult.Failed("PROCESSOR_ADAPTER_ERROR") }
  val result=when(processorResult) {
   is ProcessorResult.Approved -> { transaction=transaction.transitionTo(TransactionState.APPROVED); SaleResult.Completed(transaction,processorResult.processorReference) }
   is ProcessorResult.Declined -> { transaction=transaction.transitionTo(TransactionState.DECLINED); SaleResult.Declined(transaction,processorResult.reasonCode) }
   is ProcessorResult.Failed -> { transaction=transaction.transitionTo(TransactionState.FAILED); SaleResult.Failed(transaction,processorResult.reasonCode) }
  }
  transactionRepository.save(transaction)
  return result
 }
}
