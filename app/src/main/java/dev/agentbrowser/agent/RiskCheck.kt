package dev.agentbrowser.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

private val RISKY_WORDS = listOf(
    "결제", "구매", "주문", "송금", "이체", "보내기", "전송", "게시", "등록", "삭제", "탈퇴", "해지", "취소하기",
    "pay", "buy", "purchase", "order", "checkout", "send", "post", "submit", "delete", "remove", "unsubscribe",
)

private fun JsonObject.flag(name: String): Boolean =
    runCatching { this[name]?.jsonPrimitive?.booleanOrNull }.getOrNull() == true

/**
 * True when the action must be approved by the user first: Claude marked it risky, or the element's
 * label names a payment, order, send, delete or similar action. Only clicks and submitting types qualify.
 */
fun needsConfirmation(toolName: String, input: JsonObject, elementLabel: String?): Boolean {
    val acts = toolName == "click" || (toolName == "type" && input.flag("submit"))
    if (!acts) return false
    if (input.flag("risky")) return true
    val label = elementLabel?.lowercase() ?: return false
    return RISKY_WORDS.any { label.contains(it) }
}
