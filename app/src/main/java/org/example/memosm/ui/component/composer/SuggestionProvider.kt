package org.example.memosm.ui.component.composer

import androidx.compose.ui.text.TextRange

data class SuggestionResult(
    val suggestions: List<String>,
    val startIndex: Int,
    val replacementPrefix: String = "",
    val type: SuggestionType
)

enum class SuggestionType(val isAutoShown: Boolean) {
    HASHTAG(true),
    MARKDOWN(false),
    CODE_LANGUAGE(false)
}

object SuggestionProvider {
    private val MARKDOWN_SUGGESTIONS = listOf(
        "- ",
        "> ",
        "- [ ] "
    )

    private val CODE_LANGUAGES = listOf(
        "java",
        "kotlin",
        "js",
        "python",
        "bash",
        "go",
        "rust",
        "cpp",
        "c",
        "html",
        "css",
        "sql",
        "json",
        "yaml",
        "xml",
        "swift",
        "dart",
        "php",
        "ruby",
        "lua"
    )

    fun getSuggestions(
        text: String,
        selection: TextRange,
        availableTags: Set<String>
    ): SuggestionResult? {
        val cursorIndex = selection.start
        if (cursorIndex < 0 || cursorIndex > text.length) return null

        val textBeforeCursor = text.take(cursorIndex)

        // 1. Hashtag Check (Highest Priority if active matches)
        val lastHashIndex = textBeforeCursor.lastIndexOf('#')
        if (lastHashIndex != -1) {
            val potentialTag = textBeforeCursor.substring(lastHashIndex + 1)
            // Ensure no spaces or newlines in the tag being typed
            if (!potentialTag.contains(' ') && !potentialTag.contains('\n')) {
                val filteredTags = if (potentialTag.isEmpty()) {
                    availableTags.toList()
                } else {
                    availableTags.filter { it.contains(potentialTag, ignoreCase = true) }
                }
                
                if (filteredTags.isNotEmpty()) {
                    return SuggestionResult(
                        suggestions = filteredTags,
                        startIndex = lastHashIndex,
                        replacementPrefix = "#",
                        type = SuggestionType.HASHTAG
                    )
                }
            }
        }

        // 2. Start of Line / Markdown Check
        // Find the start of the current line
        val lastNewlineIndex = textBeforeCursor.lastIndexOf('\n')
        val lineStartIndex = lastNewlineIndex + 1
        val currentLinePrefix = textBeforeCursor.substring(lineStartIndex)

        // If the line is empty (cursor is at start of line), show markdown suggestions
        if (currentLinePrefix.isEmpty()) {
             return SuggestionResult(
                suggestions = MARKDOWN_SUGGESTIONS,
                startIndex = lineStartIndex,
                type = SuggestionType.MARKDOWN
            )
        }

        // 3. Code Block Language Check
        // Check if the line *starts* with ``` and cursor is right after it, OR if user is typing language
        // Case A: User typed ``` and nothing else yet -> Suggest languages
        if (currentLinePrefix == "```") {
             return SuggestionResult(
                suggestions = CODE_LANGUAGES,
                startIndex = lineStartIndex + 3, // Start replacing after ```
                type = SuggestionType.CODE_LANGUAGE
            )
        }

        // Case B: User typed ```la -> Suggest languages starting with 'la'
        if (currentLinePrefix.startsWith("```")) {
            val typedLang = currentLinePrefix.substring(3)
            // Ensure we don't suggest if there's a space (e.g. ``` java ) - though usually lang follows immediately
            if (!typedLang.contains(' ')) {
                 val filteredLangs = CODE_LANGUAGES.filter { it.startsWith(typedLang, ignoreCase = true) }
                 if (filteredLangs.isNotEmpty()) {
                     return SuggestionResult(
                        suggestions = filteredLangs,
                        startIndex = lineStartIndex + 3,
                        type = SuggestionType.CODE_LANGUAGE
                    )
                 }
            }
        }

        return null
    }
}
