package com.aiassistant.app.automation

enum class ActionExecutionStatus(val userText: String) {
    COMPLETED("Action completed."),
    COMPLETED_UNVERIFIED("Requested, but I could not verify it."),
    FAILED("Action failed."),
    TARGET_NOT_FOUND("Target not found."),
    AMBIGUOUS("Several matches. I need a clearer target."),
    SERVICE_DISABLED("Accessibility service disabled."),
    PERMISSION_REQUIRED("Permission required."),
    UNSUPPORTED("Action unsupported."),
    CONFIRMATION_DENIED("Not confirmed. Action cancelled."),
    BLOCKED("Blocked for safety."),
    INVALID("Invalid action request."),
    APP_NOT_INSTALLED("App not installed."),
}

data class ActionResult(val status: ActionExecutionStatus, val message: String) {
    val success: Boolean get() = status == ActionExecutionStatus.COMPLETED
}
