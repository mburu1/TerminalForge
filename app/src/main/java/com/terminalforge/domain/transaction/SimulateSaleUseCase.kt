package com.terminalforge.domain.transaction

import com.terminalforge.domain.payment.PaymentProcessor
import com.terminalforge.domain.payment.PaymentRequest
import com.terminalforge.domain.payment.PaymentResult
import java.util.UUID

fun interface TransactionIdProvider { fun newId(): String }
fun interface SaleClock { fun nowEpochMillis(): Long }

sealed interface SaleResult {
    data class Completed(val transaction: Transaction, val processorReference: String) : SaleResult
    data class Declined(val transaction: Transaction, val reasonCode: String) : SaleResult
    data class Failed(val transaction: Transaction, val reasonCode: String) : SaleResult
    data class UnknownOutcome(val transaction: Transaction, val reasonCode: String) : SaleResult
}

/** Coordinates one simulated sale using the existing M1 state-transition graph. */
class SimulateSaleUseCase(
    private val paymentProcessor: PaymentProcessor,
    private val transactionRepository: TransactionRepository,
    private val idProvider: TransactionIdProvider = TransactionIdProvider { UUID.randomUUID().toString() },
    private val clock: SaleClock = SaleClock { System.currentTimeMillis() }
) {
    fun execute(amount: Money, idempotencyKey: String): SaleResult {
        require(amount.minorUnits > 0) { "Sale amount must be greater than zero." }
        require(idempotencyKey.isNotBlank()) { "Idempotency key must not be blank." }

        val existing = transactionRepository.findAll().firstOrNull { it.idempotencyKey == idempotencyKey }
        if (existing != null) return existing.toSaleResult()

        var transaction = Transaction.create(
            id = idProvider.newId(),
            amount = amount,
            operationType = OperationType.SALE,
            idempotencyKey = idempotencyKey,
            createdAtEpochMillis = clock.nowEpochMillis()
        )
        transactionRepository.save(transaction)
        transaction = transaction.transitionTo(TransactionState.VALIDATING)
        transactionRepository.save(transaction)
        transaction = transaction.transitionTo(TransactionState.READY)
        transactionRepository.save(transaction)
        transaction = transaction.transitionTo(TransactionState.PROCESSING)
        transactionRepository.save(transaction)

        val paymentResult = try {
            paymentProcessor.process(PaymentRequest(transaction.id, transaction.idempotencyKey, transaction.amount))
        } catch (_: Exception) {
            // The request may have reached the processor before the exception.
            PaymentResult.UnknownOutcome("MOCK_PROCESSOR_EXCEPTION")
        }

        val result = when (paymentResult) {
            is PaymentResult.Approved -> {
                transaction = transaction.transitionTo(TransactionState.APPROVED)
                SaleResult.Completed(transaction, paymentResult.processorReference)
            }
            is PaymentResult.Declined -> {
                transaction = transaction.transitionTo(TransactionState.DECLINED)
                SaleResult.Declined(transaction, paymentResult.reasonCode)
            }
            is PaymentResult.Failed -> {
                transaction = transaction.transitionTo(TransactionState.FAILED)
                SaleResult.Failed(transaction, paymentResult.reasonCode)
            }
            is PaymentResult.UnknownOutcome -> {
                transaction = transaction.transitionTo(TransactionState.UNKNOWN_OUTCOME)
                SaleResult.UnknownOutcome(transaction, paymentResult.reasonCode)
            }
        }
        transactionRepository.save(transaction)
        return result
    }

    private fun Transaction.toSaleResult(): SaleResult = when (state) {
        TransactionState.APPROVED -> SaleResult.Completed(this, "MOCK-$id")
        TransactionState.DECLINED -> SaleResult.Declined(this, "ALREADY_RECORDED")
        TransactionState.FAILED -> SaleResult.Failed(this, "ALREADY_RECORDED")
        TransactionState.UNKNOWN_OUTCOME, TransactionState.RECONCILIATION_REQUIRED ->
            SaleResult.UnknownOutcome(this, "RECONCILIATION_REQUIRED")
        else -> SaleResult.Failed(this, "TRANSACTION_IN_PROGRESS")
    }
}
