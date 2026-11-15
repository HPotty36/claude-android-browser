package dev.agentbrowser.chat

data class Segment(val text: String, val bold: Boolean)

/** Splits text on `**bold**` pairs and drops the markers; an unpaired `**` stays as typed. */
fun boldSegments(text: String): List<Segment> {
    val segments = mutableListOf<Segment>()
    var rest = text
    while (rest.isNotEmpty()) {
        val open = rest.indexOf("**")
        val close = if (open >= 0) rest.indexOf("**", open + 2) else -1
        if (open < 0 || close < 0 || close == open + 2) {
            segments += Segment(rest, false)
            break
        }
        if (open > 0) segments += Segment(rest.substring(0, open), false)
        segments += Segment(rest.substring(open + 2, close), true)
        rest = rest.substring(close + 2)
    }
    return segments
}
