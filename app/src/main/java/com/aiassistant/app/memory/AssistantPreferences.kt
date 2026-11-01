package com.aiassistant.app.memory

import java.util.Calendar

/** Validates the user-chosen assistant name. The name is injected into the AI system prompt, so the
 *  allowed character set is deliberately narrow (no newlines, quotes, braces or markup). */
object AssistantNameValidator {
    const val MAX_LENGTH = 20
    private val ALLOWED = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N} '\\-]*$")

    sealed interface Result {
        data class Valid(val name: String) : Result
        data class Invalid(val reason: String) : Result
    }

    fun validate(raw: String): Result {
        val n = raw.trim().replace(Regex("\\s+"), " ")
        return when {
            n.isEmpty() -> Result.Invalid("Please enter a name.")
            n.length > MAX_LENGTH -> Result.Invalid("Use $MAX_LENGTH characters or fewer.")
            !ALLOWED.matches(n) -> Result.Invalid("Use letters, numbers, spaces, hyphens or apostrophes only.")
            else -> Result.Valid(n)
        }
    }
}

object Greeting {
    fun forNow(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }
}
