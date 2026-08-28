package com.example.nvhspectro.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PDF comment layout [A4, plan 4.5].
 *
 * The operator's notes are the only free text on a customer deliverable, and the old wrapper
 * silently destroyed every line break in them. A fixed-width measure stands in for the Paint.
 */
class CommentWrapTest {
    /** One "pixel" per character — deterministic and easy to reason about. */
    private val measure: (String) -> Float = { it.length.toFloat() }

    private fun wrap(
        text: String,
        maxWidth: Float = 10f,
        maxLines: Int = 20,
    ) = PdfReportGenerator.wrapComment(text, maxWidth, maxLines, measure)

    @Test
    fun a4_userLineBreaks_arePreserved() {
        val lines = wrap("aa\nbb\ncc", maxWidth = 40f)
        assertEquals(listOf("aa", "bb", "cc"), lines)
    }

    @Test
    fun a4_blankLinesBetweenParagraphs_survive() {
        val lines = wrap("un\n\ndeux", maxWidth = 40f)
        assertEquals(listOf("un", "", "deux"), lines)
    }

    @Test
    fun a4_longParagraphs_stillWrapOnWidth() {
        // 10-wide: "aaa bbb" is 7, adding " ccc" would be 11 -> break.
        val lines = wrap("aaa bbb ccc ddd", maxWidth = 10f)
        assertEquals(listOf("aaa bbb", "ccc ddd"), lines)
    }

    @Test
    fun a4_wrappingAndExplicitBreaks_composeCorrectly() {
        val lines = wrap("aaa bbb ccc\nzzz", maxWidth = 10f)
        assertEquals(listOf("aaa bbb", "ccc", "zzz"), lines)
    }

    @Test
    fun a4_aWordWiderThanTheBox_isEmittedRatherThanLoopingForever() {
        val lines = wrap("supercalifragilistic ok", maxWidth = 5f)
        assertTrue(lines.isNotEmpty())
        assertEquals("supercalifragilistic", lines.first())
    }

    @Test
    fun a4_outputIsCappedToWhatFitsInTheBox() {
        val lines = wrap("a\nb\nc\nd\ne", maxWidth = 40f, maxLines = 3)
        assertEquals(3, lines.size)
        assertEquals(listOf("a", "b", "c"), lines)
    }

    @Test
    fun a4_emptyComment_producesOneEmptyLineNotACrash() {
        assertEquals(listOf(""), wrap(""))
    }
}
