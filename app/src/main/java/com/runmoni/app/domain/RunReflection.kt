package com.runmoni.app.domain

enum class RunEffort(internal val wireValue: String, val label: String) {
    EASY("easy", "쉬웠어요"),
    MODERATE("moderate", "보통이에요"),
    HARD("hard", "힘들었어요");

    companion object {
        internal fun fromStorage(value: String?) = entries.firstOrNull { it.wireValue == value }
    }
}

/** A runner's optional, local description of a finished session. */
data class RunReflection(val effort: RunEffort?, val note: String?) {
    companion object {
        internal const val MaxNoteLength = 120
        private val lineBreak = Regex("[\r\n\u000B\u000C\u0085\u2028\u2029]+")

        internal fun singleLine(text: String): String = text.replace(lineBreak, " ")

        internal fun noteFits(text: String): Boolean = text.codePointCount(0, text.length) <= MaxNoteLength

        internal fun fromInput(effort: RunEffort?, text: String?): RunReflection? {
            val note = text?.let(::singleLine)?.trim()?.takeIf { it.isNotEmpty() }
            require(note == null || noteFits(note))
            return if (effort == null && note == null) null else RunReflection(effort, note)
        }

        internal fun fromStorage(effort: String?, text: String?): RunReflection? =
            fromInput(RunEffort.fromStorage(effort), text?.let(::singleLine)?.trim()?.takeIf(::noteFits))
    }
}
