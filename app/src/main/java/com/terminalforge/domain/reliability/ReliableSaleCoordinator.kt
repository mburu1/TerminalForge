package com.terminalforge.domain.reliability

import com.terminalforge.domain.payment.PaymentProcessor
import com.terminalforge.domain.payment.PaymentRequest
import com.terminalforge.domain.payment.ProcessorResult
import com.terminalforge.domain.transaction.Money
import com.terminalforge.domain.transaction.OperationType
import com.terminalforge.domain.transaction.Transaction
import com.terminalforge.domain.transaction.TransactionRepository
import com.terminalforge.domain.transaction.TransactionState
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

sealed interface SaleExecutionResult {
    data class Completed(val transaction: Transaction) : SaleExecutionResult
    data class IdempotentReplay(val transaction: Transaction) : SaleExecutionResult
    data class Ambiguous(val transaction: Transaction) : SaleExecutionResult
}

class IdempotencyConflict(message: String) : IllegalArgumentException(message)

/**
 * Reliability boundary for the simulator. Persist PROCESSING before calling the processor.
 * A timeout/adapter exception becomes UNKNOWN_OUTCOME, never DECLINED. No automatic retry is
 * attempted because interrupting a local thread cannot prove the processor stopped.
 */
class ReliableSaleCoordinator(
    private val processor: PaymentProcessor,
    private val repository: TransactionRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val processorTimeoutMillis: Long = 5_000L
) {
    init { require(processorTimeoutMillis > 0) { "Processor timeout must be positive." } }

    private val lock = Any()

    fun execute(transactionId: String, idempotencyKey: String, amount: Money): SaleExecutionResult =
        synchronized(lock) {
            require(transactionId.isNotBlank()) { "Transaction ID must not be blank." }
            require(idempotencyKey.isNotBlank()) { "Idempotency key must not be blank." }
            require(amount.minorUnits > 0) { "Sale amount must be greater than zero." }

            val existing = repository.findByIdempotencyKey(idempotencyKey)
            if (existing != null) {
                if (existing.amount != amount || existing.operationType != OperationType.SALE) {
                    throw IdempotencyConflict("Idempotency key was already used for a different request.")
                }
                return@synchronized SaleExecutionResult.IdempotentReplay(existing)
            }
            require(repository.findById(transactionId) == null) { "Transaction ID already exists." }

            var transaction = Transaction.create(
                id = transactionId,
                amount = amount,
                operationType = OperationType.SALE,
                idempotencyKey = idempotencyKey,
                createdAtEpochMillis = nowMillis()
            ).transitionTo(TransactionState.VALIDATING)
                .transitionTo(TransactionState.READY)
                .transitionTo(TransactionState.PROCESSING)

            repository.save(transaction) // write-ahead record before processor invocation
            val executor = Executors.newSingleThreadExecutor()
            try {
                val result = executor.submit(Callable {
                    processor.process(PaymentRequest(transaction.id, transaction.amount))
                }).get(processorTimeoutMillis, TimeUnit.MILLISECONDS)

                val previous = transaction
                transaction = when (result) {
                    is ProcessorResult.Approved -> transaction.transitionTo(TransactionState.APPROVED)
                    is ProcessorResult.Declined -> transaction.transitionTo(TransactionState.DECLINED)
                    is ProcessorResult.Failed -> transaction.transitionTo(TransactionState.FAILED)
                }
                repository.update(previous, transaction)
                SaleExecutionResult.Completed(transaction)
            } catch (_: TimeoutException) {
                val previous = transaction
                transaction = transaction.transitionTo(TransactionState.UNKNOWN_OUTCOME)
                repository.update(previous, transaction)
                SaleExecutionResult.Ambiguous(transaction)
            } catch (_: Exception) {
                // Unless the adapter explicitly returned a typed Failed result, delivery is ambiguous.
                val previous = transaction
                transaction = transaction.transitionTo(TransactionState.UNKNOWN_OUTCOME)
                repository.update(previous, transaction)
                SaleExecutionResult.Ambiguous(transaction)
            } finally {
                executor.shutdownNow()
            }
        }
}
