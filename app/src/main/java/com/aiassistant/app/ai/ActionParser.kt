package com.aiassistant.app.ai

import org.json.JSONObject

/** Strict parser/validator. The model PROPOSES; this decides whether the proposal is even well-formed. */
object ActionParser {
    sealed interface ParseResult {
        data class Valid(val action: PhoneAction) : ParseResult
        data class Invalid(val reason: String) : ParseResult
    }

    private val ALLOWED_KEYS = setOf("action", "target", "text", "direction", "durationMs", "requiresConfirmation")
    private val CONTROL_CHARS = Regex("[\\p{Cntrl}&&[^\\n\\t]]")
    private const val MAX_TARGET = 60
    private const val MAX_TEXT = 500

    fun parse(toolName: String, args: JSONObject): ParseResult {
        if (toolName != ActionSchema.TOOL_NAME) return ParseResult.Invalid("Unknown tool '$toolName'.")
        val keys = args.keys().asSequence().toList()
        keys.firstOrNull { it !in ALLOWED_KEYS }?.let { return ParseResult.Invalid("Unexpected argument '$it'.") }

        val type = args.optString("action").trim().uppercase()
        if (type !in ActionSchema.ACTIONS) {
            return ParseResult.Invalid("Unsupported action '$type'. Allowed: ${ActionSchema.ACTIONS.joinToString()}.")
        }
        val target = args.optString("target").trim()
        val text = args.optString("text")

        if (CONTROL_CHARS.containsMatchIn(target) || CONTROL_CHARS.containsMatchIn(text)) {
            return ParseResult.Invalid("Arguments contain control characters.")
        }
        if (target.length > MAX_TARGET) return ParseResult.Invalid("Target is too long.")
        if (text.length > MAX_TEXT) return ParseResult.Invalid("Text is too long.")

        return when (type) {
            "OPEN_APP" -> if (target.isEmpty()) ParseResult.Invalid("OPEN_APP needs a target app name.")
            else ParseResult.Valid(PhoneAction.OpenApp(target))

            "CLICK_ELEMENT" -> if (target.isEmpty()) ParseResult.Invalid("CLICK_ELEMENT needs the element's visible text.")
            else ParseResult.Valid(PhoneAction.ClickElement(target))

            "TYPE_TEXT" -> if (text.isEmpty()) ParseResult.Invalid("TYPE_TEXT needs text.")
            else ParseResult.Valid(PhoneAction.TypeText(text, target.ifEmpty { null }))

            "SCROLL" -> {
                val dir = Direction.entries.firstOrNull { it.name == args.optString("direction").trim().uppercase() }
                    ?: return ParseResult.Invalid("SCROLL needs direction UP, DOWN, LEFT or RIGHT.")
                val dur = args.optLong("durationMs", 500).coerceIn(200, 2000)
                ParseResult.Valid(PhoneAction.Scroll(dir, dur))
            }

            "BACK" -> ParseResult.Valid(PhoneAction.Back)
            "HOME" -> ParseResult.Valid(PhoneAction.Home)
            "RECENTS" -> ParseResult.Valid(PhoneAction.Recents)
            "STOP_SPEAKING" -> ParseResult.Valid(PhoneAction.StopSpeaking)
            "STOP_CONTROL" -> ParseResult.Valid(PhoneAction.StopControl)
            else -> ParseResult.Invalid("Unsupported action.")
        }
    }
}
