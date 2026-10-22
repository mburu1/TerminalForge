package com.terminalforge.hardware

/** Prints an already-rendered receipt; payment/domain rules stay outside this port. */
interface ReceiptPrinter {
    suspend fun print(receipt: Receipt): PrintResult
}

data class Receipt(val transactionId: String, val lines: List<String>) {
    init {
        require(transactionId.isNotBlank()) { "Transaction ID must not be blank." }
        require(lines.isNotEmpty()) { "Receipt must contain at least one line." }
        require(lines.none { it.contains('\u0000') }) { "Receipt lines cannot contain NUL characters." }
    }
}

sealed interface PrintResult {
    data object Printed : PrintResult
}
