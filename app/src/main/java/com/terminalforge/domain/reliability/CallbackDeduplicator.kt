package com.terminalforge.domain.reliability

/**
 * Synchronized event deduplicator. Use a durable unique constraint on (transactionId, eventId)
 * in a database-backed adapter before accepting external callbacks in a deployed system.
 */
class CallbackDeduplicator {
    private val seen = mutableSetOf<Pair<String, String>>()

    @Synchronized
    fun accept(transactionId: String, eventId: String): Boolean {
        require(transactionId.isNotBlank()) { "Transaction ID must not be blank." }
        require(eventId.isNotBlank()) { "Callback event ID must not be blank." }
        return seen.add(transactionId to eventId)
    }
}
