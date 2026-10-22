package com.terminalforge.data.local

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64

/** Durable single-process callback inbox; identity is (transactionId, eventId). */
class FileCallbackDeduplicator(private val file: File) {
    private val seen = linkedSetOf<Pair<String, String>>()

    init {
        file.parentFile?.mkdirs()
        if (file.exists()) file.readLines(StandardCharsets.UTF_8).filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split('|')
            require(parts.size == 2) { "Callback inbox is corrupt." }
            seen += decode(parts[0]) to decode(parts[1])
        }
    }

    @Synchronized
    fun accept(transactionId: String, eventId: String): Boolean {
        require(transactionId.isNotBlank()) { "Transaction ID must not be blank." }
        require(eventId.isNotBlank()) { "Callback event ID must not be blank." }
        val key = transactionId to eventId
        if (key in seen) return false
        seen += key
        try {
            val temp = File(file.parentFile ?: File("."), file.name + ".tmp")
            temp.writeText(seen.joinToString("\n", postfix = "\n") { (transaction, event) ->
                "${encode(transaction)}|${encode(event)}"
            }, StandardCharsets.UTF_8)
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return true
        } catch (e: Exception) {
            seen.remove(key)
            throw IllegalStateException("Could not persist callback deduplication key.", e)
        }
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun decode(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}
