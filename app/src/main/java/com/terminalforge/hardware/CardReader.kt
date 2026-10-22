package com.terminalforge.hardware

/** Android-free boundary for a certified or simulated card-present reader. */
interface CardReader {
    suspend fun readCard(): CardReadResult
}

sealed interface CardReadResult {
    data class CardDetected(val method: CardEntryMethod) : CardReadResult
}

enum class CardEntryMethod { CONTACT, CONTACTLESS, MAGSTRIPE }
