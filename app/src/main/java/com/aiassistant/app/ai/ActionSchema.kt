package com.aiassistant.app.ai

import org.json.JSONArray
import org.json.JSONObject

enum class Direction { UP, DOWN, LEFT, RIGHT }

/** The complete allowlist of things the assistant can ask the phone to do. Nothing else can be executed. */
sealed interface PhoneAction {
    data class OpenApp(val appName: String) : PhoneAction
    data class ClickElement(val targetText: String) : PhoneAction
    data class TypeText(val text: String, val fieldLabel: String?) : PhoneAction
    data class Scroll(val direction: Direction, val durationMs: Long) : PhoneAction
    data object Back : PhoneAction
    data object Home : PhoneAction
    data object Recents : PhoneAction
    data object StopSpeaking : PhoneAction
    data object StopControl : PhoneAction
}

/** Privacy-safe description for logs: never includes typed text. */
fun PhoneAction.describe(): String = when (this) {
    is PhoneAction.OpenApp -> "OPEN_APP($appName)"
    is PhoneAction.ClickElement -> "CLICK_ELEMENT($targetText)"
    is PhoneAction.TypeText -> "TYPE_TEXT(${text.length} chars)"
    is PhoneAction.Scroll -> "SCROLL($direction)"
    PhoneAction.Back -> "BACK"
    PhoneAction.Home -> "HOME"
    PhoneAction.Recents -> "RECENTS"
    PhoneAction.StopSpeaking -> "STOP_SPEAKING"
    PhoneAction.StopControl -> "STOP_CONTROL"
}

object ActionSchema {
    const val TOOL_NAME = "execute_phone_action"

    val ACTIONS = listOf(
        "OPEN_APP", "CLICK_ELEMENT", "TYPE_TEXT", "SCROLL",
        "BACK", "HOME", "RECENTS", "STOP_SPEAKING", "STOP_CONTROL",
    )

    private fun str(desc: String) = JSONObject().put("type", "STRING").put("description", desc)

    /** Gemini function declaration(s) sent in the Live API setup message. */
    fun functionDeclarations(): JSONArray {
        val props = JSONObject()
            .put("action", str("Which phone action to perform.").put("enum", JSONArray(ACTIONS)))
            .put("target", str("OPEN_APP: the app name. CLICK_ELEMENT: the visible text or label of the element. TYPE_TEXT: optional label of the field."))
            .put("text", str("TYPE_TEXT only: the text to type."))
            .put("direction", str("SCROLL only.").put("enum", JSONArray(Direction.entries.map { it.name })))
            .put("durationMs", JSONObject().put("type", "INTEGER").put("description", "SCROLL only: gesture duration, 200-2000. Longer is slower."))
        val params = JSONObject().put("type", "OBJECT").put("properties", props).put("required", JSONArray().put("action"))
        return JSONArray().put(
            JSONObject()
                .put("name", TOOL_NAME)
                .put("description", "Operate the user's Android phone on their explicit request. Returns the real result.")
                .put("parameters", params)
        )
    }
}
