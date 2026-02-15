package org.example.memosm.ui.component.composer

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestionProviderTest {

    private val availableTags = setOf("android", "kotlin", "java")

    @Test
    fun `test hashtag suggestion`() {
        val text = "Hello #andr"
        val selection = TextRange(text.length)
        val result = SuggestionProvider.getSuggestions(text, selection, availableTags)

        assertNotNull(result)
        println("Result for 'Hello #andr': $result")
        assertEquals(SuggestionType.HASHTAG, result?.type)
        assertEquals("Expected 1 suggestion but got: ${result?.suggestions}", 1, result?.suggestions?.size)
        assertEquals("android", result?.suggestions?.first())
        assertEquals(text.indexOf("#") + 1, result?.startIndex)
    }

    @Test
    fun `test hashtag suggestion no match`() {
        val text = "Hello #xyz"
        val selection = TextRange(text.length)
        val result = SuggestionProvider.getSuggestions(text, selection, availableTags)

        assertNull(result)
    }

    @Test
    fun `test markdown start of line suggestions`() {
        val text = ""
        val selection = TextRange(0)
        val result = SuggestionProvider.getSuggestions(text, selection, emptySet())

        assertNotNull(result)
        assertEquals(SuggestionType.MARKDOWN, result?.type)
        assertEquals(3, result?.suggestions?.size) // -, >, [ ]
    }

    @Test
    fun `test markdown suggestion after newline`() {
        val text = "Some text\n"
        val selection = TextRange(text.length)
        val result = SuggestionProvider.getSuggestions(text, selection, emptySet())

        assertNotNull(result)
        assertEquals(SuggestionType.MARKDOWN, result?.type)
    }

    @Test
    fun `test code block suggestion`() {
        val text = "Some text\n```"
        val selection = TextRange(text.length)
        val result = SuggestionProvider.getSuggestions(text, selection, emptySet())

        assertNotNull(result)
        assertEquals(SuggestionType.CODE_LANGUAGE, result?.type)
        assertEquals(text.length, result?.startIndex)
    }

    @Test
    fun `test code block language filter`() {
        val text = "Some text\n```kot"
        val selection = TextRange(text.length)
        val result = SuggestionProvider.getSuggestions(text, selection, emptySet())

        assertNotNull(result)
        assertEquals(SuggestionType.CODE_LANGUAGE, result?.type)
        assertEquals(1, result?.suggestions?.size)
        assertEquals("kotlin", result?.suggestions?.first())
    }

    @Test
    fun `test no suggestion when typing normal text`() {
        val text = "Some text"
        val selection = TextRange(text.length)
        val result = SuggestionProvider.getSuggestions(text, selection, emptySet())

        assertNull(result)
    }
}
