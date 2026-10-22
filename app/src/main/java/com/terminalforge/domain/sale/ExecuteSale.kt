package com.terminalforge.domain.sale

import com.terminalforge.domain.payment.PaymentProcessor
import com.terminalforge.domain.payment.PaymentRequest
import com.terminalforge.domain.payment.ProcessorResult
import com.terminalforge.domain.transaction.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class SaleRequest(val amount: Money, val idempotencyKey: String) {
    init {
        require(amount.minorUnits > 0) { "Sale amount must be positive." }
        require(idempotencyKey.isNotBlank()) { "Idempotency key must not be blank." }
    }
}

interface SaleIdentityProvider {
    fun newTransactionId(): String
    fun nowEpochMillis(): Long
}

sealed interface SaleResult {
    data class Completed(val transaction: Transaction, val processorReference: String) : SaleResult
    data class Declined(val transaction: Transaction, val reasonCode: String) : SaleResult
    data class Failed(val transaction: Transaction, val reasonCode: String) : SaleResult
    data class Ambiguous(val transaction: Transaction) : SaleResult
    data class IdempotentReplay(val transaction: Transaction) : SaleResult
}

class IdempotencyConflict(message: String) : IllegalArgumentException(message)

/**
 * Executes the M2 sale flow with M3 reliability controls, reusing the existing domain model.
 * A timeout or thrown adapter exception is ambiguous; it is never rewritten as a decline/failure.
 */
class ExecuteSale(
    private val paymentProcessor: PaymentProcessor,
    private val transactionRepository: TransactionRepository,
    private val identityProvider: SaleIdentityProvider,
    private val processorTimeoutMillis: Long = 5_000L
) {
    init { require(processorTimeoutMillis > 0) { "Processor timeout must be positive." } }

    @Synchronized
    fun execute(request: SaleRequest): SaleResult {
        val existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey)
        if (existing != null) {
            if (existing.amount != request.amount || existing.operationType != OperationType.SALE) {
                throw IdempotencyConflict("Idempotency key was already used for a different request.")
            }
            return SaleResult.IdempotentReplay(existing)
        }

        var transaction = Transaction.create(
            id = identityProvider.newTransactionId(),
            amount = request.amount,
            operationType = OperationType.SALE,
            idempotencyKey = request.idempotencyKey,
            createdAtEpochMillis = identityProvider.nowEpochMillis()
        ).transitionTo(TransactionState.VALIDATING)
            .transitionTo(TransactionState.READY)
            .transitionTo(TransactionState.PROCESSING)

        require(transactionRepository.findById(transaction.id) == null) {
            "Transaction ID already exists."
        }
        transactionRepository.save(transaction) // write-ahead PROCESSING record

        val executor = Executors.newSingleThreadExecutor()
        try {
            val processorResult = executor.submit(Callable {
                paymentProcessor.process(PaymentRequest(transaction.id, request.amount))
            }).get(processorTimeoutMillis, TimeUnit.MILLISECONDS)

            val previous = transaction
            val result = when (processorResult) {
                is ProcessorResult.Approved -> {
                    transaction = transaction.transitionTo(TransactionState.APPROVED)
                    transactionRepository.update(previous, transaction)
                    SaleResult.Completed(transaction, processorResult.processorReference)
                }
                is ProcessorResult.Declined -> {
                    transaction = transaction.transitionTo(TransactionState.DECLINED)
                    transactionRepository.update(previous, transaction)
                    SaleResult.Declined(transaction, processorResult.reasonCode)
                }
                is ProcessorResult.Failed -> {
                    transaction = transaction.transitionTo(TransactionState.FAILED)
                    transactionRepository.update(previous, transaction)
                    SaleResult.Failed(transaction, processorResult.reasonCode)
                }
            }
            return result
        } catch (_: TimeoutException) {
            val previous = transaction
            transaction = transaction.transitionTo(TransactionState.UNKNOWN_OUTCOME)
            transactionRepository.update(previous, transaction)
            return SaleResult.Ambiguous(transaction)
        } catch (_: Exception) {
            val previous = transaction
            transaction = transaction.transitionTo(TransactionState.UNKNOWN_OUTCOME)
            transactionRepository.update(previous, transaction)
            return SaleResult.Ambiguous(transaction)
        } finally {
            executor.shutdownNow()
        }
    }
}
