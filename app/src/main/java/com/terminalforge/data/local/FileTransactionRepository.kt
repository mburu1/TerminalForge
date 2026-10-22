package com.terminalforge.data.local

import com.terminalforge.domain.transaction.Money
import com.terminalforge.domain.transaction.OperationType
import com.terminalforge.domain.transaction.Transaction
import com.terminalforge.domain.transaction.TransactionRepository
import com.terminalforge.domain.transaction.TransactionState
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64

/**
 * Atomic snapshot repository for the educational simulator. On Android, pass a file inside the
 * app-private filesDir. This deliberately avoids introducing Room in M3; use Room/SQL constraints
 * for a production-grade multi-process store.
 */
class FileTransactionRepository(private val file: File) : TransactionRepository {
    private val transactions = linkedMapOf<String, Transaction>()

    init {
        file.parentFile?.mkdirs()
        if (file.exists()) load()
    }

    @Synchronized
    override fun save(transaction: Transaction) {
        require(!transactions.containsKey(transaction.id)) { "Transaction ID already exists." }
        require(transactions.values.none { it.idempotencyKey == transaction.idempotencyKey }) {
            "Idempotency key already exists."
        }
        transactions[transaction.id] = transaction
        persistOrRollback(transaction.id)
    }

    @Synchronized
    override fun update(previous: Transaction, replacement: Transaction) {
        require(previous.id == replacement.id) { "Cannot change transaction identity." }
        require(previous.amount == replacement.amount) { "Cannot change transaction amount." }
        require(previous.operationType == replacement.operationType) { "Cannot change operation type." }
        require(previous.createdAtEpochMillis == replacement.createdAtEpochMillis) { "Cannot change creation time." }
        require(transactions[previous.id] == previous) { "Transaction changed concurrently." }
        require(replacement.idempotencyKey == previous.idempotencyKey) { "Cannot change idempotency key." }
        previous.transitionTo(replacement.state) // validate the existing domain transition graph
        transactions[replacement.id] = replacement
        persistOrRollback(replacement.id, previous)
    }

    @Synchronized
    override fun findById(id: String): Transaction? = transactions[id]

    @Synchronized
    override fun findAll(): List<Transaction> = transactions.values.toList()

    @Synchronized
    override fun findByIdempotencyKey(key: String): Transaction? =
        transactions.values.firstOrNull { it.idempotencyKey == key }

    private fun persistOrRollback(changedId: String, previous: Transaction? = null) {
        try {
            val temp = File(file.parentFile ?: File("."), file.name + ".tmp")
            temp.writeText(
                transactions.values.joinToString("\n", postfix = if (transactions.isEmpty()) "" else "\n") { encode(it) },
                StandardCharsets.UTF_8
            )
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            if (previous == null) transactions.remove(changedId) else transactions[changedId] = previous
            throw IllegalStateException("Could not persist transaction snapshot.", e)
        }
    }

    private fun load() {
        file.readLines(StandardCharsets.UTF_8).filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split('|')
            require(parts.size == 6) { "Transaction snapshot is corrupt." }
            val id = decode(parts[0])
            val amount = Money(parts[1].toLong(), decode(parts[2]))
            val operation = OperationType.valueOf(parts[3])
            val key = decode(parts[4])
            val timestampAndState = parts[5].split(':')
            require(timestampAndState.size == 2) { "Transaction snapshot is corrupt." }
            val createdAt = timestampAndState[0].toLong()
            val state = TransactionState.valueOf(timestampAndState[1])
            val transaction = restore(id, amount, operation, key, createdAt, state)
            require(transactions.putIfAbsent(id, transaction) == null) { "Duplicate transaction ID in snapshot." }
            require(transactions.values.count { it.idempotencyKey == key } == 1) { "Duplicate idempotency key in snapshot." }
        }
    }

    private fun encode(t: Transaction): String = listOf(
        encode(t.id), t.amount.minorUnits.toString(), encode(t.amount.currency), t.operationType.name,
        encode(t.idempotencyKey), "${t.createdAtEpochMillis}:${t.state.name}"
    ).joinToString("|")

    private fun restore(
        id: String, amount: Money, operation: OperationType, key: String, createdAt: Long, target: TransactionState
    ): Transaction {
        var t = Transaction.create(id, amount, operation, key, createdAt)
        val paths = mapOf(
            TransactionState.CREATED to emptyList(),
            TransactionState.VALIDATING to listOf(TransactionState.VALIDATING),
            TransactionState.READY to listOf(TransactionState.VALIDATING, TransactionState.READY),
            TransactionState.PROCESSING to listOf(TransactionState.VALIDATING, TransactionState.READY, TransactionState.PROCESSING),
            TransactionState.APPROVED to listOf(TransactionState.VALIDATING, TransactionState.READY, TransactionState.PROCESSING, TransactionState.APPROVED),
            TransactionState.DECLINED to listOf(TransactionState.VALIDATING, TransactionState.READY, TransactionState.PROCESSING, TransactionState.DECLINED),
            TransactionState.FAILED to listOf(TransactionState.VALIDATING, TransactionState.READY, TransactionState.PROCESSING, TransactionState.FAILED),
            TransactionState.UNKNOWN_OUTCOME to listOf(TransactionState.VALIDATING, TransactionState.READY, TransactionState.PROCESSING, TransactionState.UNKNOWN_OUTCOME),
            TransactionState.RECONCILIATION_REQUIRED to listOf(TransactionState.VALIDATING, TransactionState.READY, TransactionState.PROCESSING, TransactionState.UNKNOWN_OUTCOME, TransactionState.RECONCILIATION_REQUIRED),
            TransactionState.RESOLVED to listOf(TransactionState.VALIDATING, TransactionState.READY, TransactionState.PROCESSING, TransactionState.UNKNOWN_OUTCOME, TransactionState.RECONCILIATION_REQUIRED, TransactionState.RESOLVED),
            TransactionState.REFUND_PENDING to listOf(TransactionState.VALIDATING, TransactionState.READY, TransactionState.PROCESSING, TransactionState.APPROVED, TransactionState.REFUND_PENDING),
            TransactionState.REFUNDED to listOf(TransactionState.VALIDATING, TransactionState.READY, TransactionState.PROCESSING, TransactionState.APPROVED, TransactionState.REFUND_PENDING, TransactionState.REFUNDED),
            TransactionState.REVERSAL_PENDING to listOf(TransactionState.VALIDATING, TransactionState.READY, TransactionState.PROCESSING, TransactionState.APPROVED, TransactionState.REVERSAL_PENDING),
            TransactionState.REVERSED to listOf(TransactionState.VALIDATING, TransactionState.READY, TransactionState.PROCESSING, TransactionState.APPROVED, TransactionState.REVERSAL_PENDING, TransactionState.REVERSED)
        )
        paths[target]?.forEach { t = t.transitionTo(it) }
            ?: throw IllegalArgumentException("Unsupported transaction state in snapshot: $target")
        return t
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}
