package com.example.nvhspectro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Format-specifier contract for the strings the code formats at runtime.
 *
 * Why this exists: `notice_processing_spectrogram` declared `%3$d` while
 * `AnalyzerViewModel` passed a `String` for that slot, so **loading any WAV or
 * video of 60 s or more crashed the process** with
 * `IllegalFormatConversionException: d != java.lang.String`. It shipped to
 * master and survived 202 unit tests and five device gates, because:
 *
 *  - the `>= 60.0` guard means only long files reach the call, and every gate
 *    to date used 8–9 s recordings;
 *  - lint's `StringFormatMatches` **is** armed as an error, but every string is
 *    read through `AnalyzerViewModel.res(id, vararg args: Any)`, and that
 *    indirection hides the argument types from the check.
 *
 * So the armed gate could not see the defect. This test formats the real
 * strings.xml entries with the argument types their real call sites pass.
 */
class StringFormatContractTest {
    private val strings: Map<String, String> by lazy {
        val xml = File("src/main/res/values/strings.xml").readText()
        Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }
    }

    private fun format(
        name: String,
        vararg args: Any,
    ): String {
        val template = strings[name] ?: throw AssertionError("missing string: $name")
        return String.format(Locale.ROOT, template, *args)
    }

    @Test
    fun processingSpectrogram_acceptsTheArgumentsTheViewModelPasses() {
        // AnalyzerViewModel.processFullWavSpectrogram: Int, Int, then a
        // String from String.format("%.1f", …) — not an Int.
        val msg = format("notice_processing_spectrogram", 5, 0, "0.6")
        assertTrue("the estimate must survive formatting", msg.contains("0.6"))
        assertTrue(msg.contains("5"))
    }

    @Test
    fun recalculatingOrders_takesNoArguments() {
        assertEquals(strings["notice_recalculating_orders"], format("notice_recalculating_orders"))
    }

    @Test
    fun extractingVideoProgress_takesAnInt() {
        assertTrue(format("notice_extracting_video_progress", 42).contains("42"))
    }

    /**
     * Every positional specifier must start at 1 and be contiguous — a gap
     * (`%1$s` … `%3$s`) throws `MissingFormatArgumentException` at runtime for
     * a caller that passes the number of arguments the string appears to want.
     */
    @Test
    fun everyStringUsesContiguousPositionalArguments() {
        val offenders =
            strings.filter { (_, template) ->
                val positions =
                    Regex("""%(\d+)\$""")
                        .findAll(template)
                        .map { it.groupValues[1].toInt() }
                        .toSortedSet()
                positions.isNotEmpty() && positions.toList() != (1..positions.size).toList()
            }
        assertEquals("strings with non-contiguous positional arguments", emptyMap<String, String>(), offenders)
    }
}
