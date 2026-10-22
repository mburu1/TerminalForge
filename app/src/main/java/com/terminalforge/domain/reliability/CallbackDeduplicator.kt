package com.terminalforge.domain.reliability

/** Port for deduplicating callback events by (transactionId, eventId). */
interface CallbackEventStore {
    fun accept(transactionId: String, eventId: String): Boolean
}

/** In-memory implementation for fast deterministic unit tests. */
class CallbackDeduplicator : CallbackEventStore {
    private val seen = mutableSetOf<Pair<String, String>>()

    @Synchronized
    override fun accept(transactionId: String, eventId: String): Boolean {
        require(transactionId.isNotBlank()) { "Transaction ID must not be blank." }
        require(eventId.isNotBlank()) { "Callback event ID must not be blank." }
        return seen.add(transactionId to eventId)
    }
}
