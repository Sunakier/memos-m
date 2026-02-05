package org.example.memosm.ui.component.item.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.CodeHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes

object CodeHighlighter {

    fun highlightCode(code: String, language: String, isDarkMode: Boolean = true): AnnotatedString {
        val syntaxLanguage = getSyntaxLanguage(language)
        val theme = SyntaxThemes.atom(isDarkMode)
        
        val highlights = Highlights.Builder()
            .code(code)
            .theme(theme)
            .language(syntaxLanguage)
            .build()

        val structure = highlights.getHighlights()

        return buildAnnotatedString {
            append(code)
            structure.forEach { highlight ->
                val start = highlight.location.start
                val end = highlight.location.end
                
                // Ensure range is valid
                if (start in code.indices && end <= code.length) {
                    when (highlight) {
                        is ColorHighlight -> {
                            addStyle(
                                style = SpanStyle(color = Color(highlight.rgb).copy(alpha = 1f)),
                                start = start,
                                end = end
                            )
                        }
                        is BoldHighlight -> {
                            addStyle(
                                style = SpanStyle(fontWeight = FontWeight.Bold),
                                start = start,
                                end = end
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getSyntaxLanguage(alias: String): SyntaxLanguage {
        return when (alias.lowercase()) {
            "c" -> SyntaxLanguage.C
            "cpp", "c++" -> SyntaxLanguage.CPP
            "csharp", "c#" -> SyntaxLanguage.CSHARP
            "dart" -> SyntaxLanguage.DART
            "go", "golang" -> SyntaxLanguage.GO
            "java" -> SyntaxLanguage.JAVA
            "js", "javascript" -> SyntaxLanguage.JAVASCRIPT
            "json" -> SyntaxLanguage.JAVASCRIPT
            "kotlin", "kt" -> SyntaxLanguage.KOTLIN
            "php" -> SyntaxLanguage.PHP
            "perl" -> SyntaxLanguage.PERL
            "python", "py" -> SyntaxLanguage.PYTHON
            "ruby", "rb" -> SyntaxLanguage.RUBY
            "rust", "rs" -> SyntaxLanguage.RUST
            "swift" -> SyntaxLanguage.SWIFT
            "ts", "typescript" -> SyntaxLanguage.TYPESCRIPT
            "coffee", "coffeescript" -> SyntaxLanguage.COFFEESCRIPT
            "xml", "html" -> SyntaxLanguage.DEFAULT
            "yaml", "yml" -> SyntaxLanguage.DEFAULT
            "shell", "sh", "bash" -> SyntaxLanguage.SHELL
            else -> SyntaxLanguage.DEFAULT
        }
    }
}
