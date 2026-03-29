package com.googleac.feature.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiSummarizerTest {

    private lateinit var summarizer: AiSummarizer

    @Before
    fun setUp() {
        summarizer = AiSummarizer()
    }

    // ── summarizeText ─────────────────────────────────────────────────────────

    @Test
    fun `summarizeText returns empty list for empty string`() = runTest {
        assertTrue(summarizer.summarizeText("").isEmpty())
    }

    @Test
    fun `summarizeText returns empty list for blank whitespace string`() = runTest {
        assertTrue(summarizer.summarizeText("   \t\n  ").isEmpty())
    }

    @Test
    fun `summarizeText returns bullets prefixed with dash`() = runTest {
        val text = "The quarterly review will discuss the annual budget. " +
                "Revenue increased substantially compared to last year. " +
                "Customer satisfaction surveys showed strong improvements. " +
                "The new product launch exceeded all revenue expectations. " +
                "Team performance metrics are trending positively upward."
        val bullets = summarizer.summarizeText(text, maxBullets = 3)
        assertTrue("Expected at least one bullet", bullets.isNotEmpty())
        assertTrue(
            "All bullets must start with '- '",
            bullets.all { it.startsWith("- ") }
        )
    }

    @Test
    fun `summarizeText respects maxBullets limit`() = runTest {
        val text = (1..20).joinToString(". ") {
            "Sentence number $it contains important information about the annual topic"
        }
        val bullets = summarizer.summarizeText(text, maxBullets = 3)
        assertTrue("Should return at most 3 bullets, got ${bullets.size}", bullets.size <= 3)
    }

    @Test
    fun `summarizeText with maxBullets=1 returns at most one bullet`() = runTest {
        val text = "First important sentence about finance. " +
                "Second important sentence about revenue. " +
                "Third important sentence about forecasting."
        val bullets = summarizer.summarizeText(text, maxBullets = 1)
        assertTrue(bullets.size <= 1)
    }

    @Test
    fun `summarizeText handles text with fewer sentences than maxBullets`() = runTest {
        val text = "Only one long sentence that is long enough to pass the filter."
        val bullets = summarizer.summarizeText(text, maxBullets = 5)
        assertTrue("Should return 5 or fewer bullets", bullets.size <= 5)
    }

    @Test
    fun `summarizeText handles very long text without throwing`() = runTest {
        val longText = "This sentence contains crucial project information for review. ".repeat(500)
        val bullets = summarizer.summarizeText(longText, maxBullets = 5)
        assertTrue("Should produce bullets from long text", bullets.isNotEmpty())
    }

    @Test
    fun `summarizeText strips sentences shorter than 21 characters`() = runTest {
        // All sentences below 21 chars → nothing passes the length filter → empty list
        val text = "Short. Brief. Tiny."
        val bullets = summarizer.summarizeText(text)
        assertTrue("Short sentences should yield no bullets", bullets.isEmpty())
    }

    // ── semanticSearch ────────────────────────────────────────────────────────

    @Test
    fun `semanticSearch returns empty list for blank query`() = runTest {
        val corpus = mapOf("file1" to "quarterly financial budget report forecast")
        assertTrue(summarizer.semanticSearch("", corpus).isEmpty())
        assertTrue(summarizer.semanticSearch("   ", corpus).isEmpty())
    }

    @Test
    fun `semanticSearch returns empty list for empty corpus`() = runTest {
        assertTrue(summarizer.semanticSearch("budget report", emptyMap()).isEmpty())
    }

    @Test
    fun `semanticSearch excludes files with no token overlap`() = runTest {
        val corpus = mapOf(
            "tech_doc" to "android kotlin compose mobile application development",
            "recipe_book" to "pasta ingredients cooking kitchen oven timer"
        )
        val results = summarizer.semanticSearch("android kotlin mobile", corpus)
        assertTrue("Relevant file should be in results", "tech_doc" in results)
        assertFalse("Unrelated file should be excluded", "recipe_book" in results)
    }

    @Test
    fun `semanticSearch ranks more-relevant file first`() = runTest {
        val corpus = mapOf(
            "highly_relevant" to "quarterly budget finance report quarterly revenue finance quarterly",
            "less_relevant" to "quarterly report brief"
        )
        val results = summarizer.semanticSearch("quarterly budget finance", corpus)
        assertEquals(2, results.size)
        assertEquals("highly_relevant should rank first", "highly_relevant", results.first())
    }

    @Test
    fun `semanticSearch returns all files that share at least one token`() = runTest {
        val corpus = mapOf(
            "file_a" to "android development testing",
            "file_b" to "testing quality assurance",
            "file_c" to "cooking and baking"
        )
        val results = summarizer.semanticSearch("android testing", corpus)
        assertTrue("file_a" in results)
        assertTrue("file_b" in results)
        assertFalse("file_c should not match", "file_c" in results)
    }

    @Test
    fun `semanticSearch tokens shorter than 4 characters are ignored`() = runTest {
        // "the", "and", "or" are all <= 3 chars — they are stripped by tokenize()
        val corpus = mapOf("doc" to "large important document with content")
        val results = summarizer.semanticSearch("the and or", corpus)
        // Query tokens are all stripped → query token list is empty → nothing matched
        assertTrue(results.isEmpty())
    }

    // ── detectMeetingTimes ────────────────────────────────────────────────────

    @Test
    fun `detectMeetingTimes returns empty list for blank input`() = runTest {
        assertTrue(summarizer.detectMeetingTimes("").isEmpty())
        assertTrue(summarizer.detectMeetingTimes("   ").isEmpty())
    }

    @Test
    fun `detectMeetingTimes detects HH colon MM AM-PM time pattern`() = runTest {
        val text = "The standup is scheduled for 9:30 AM every weekday."
        val times = summarizer.detectMeetingTimes(text)
        assertTrue("Should detect 9:30 AM", times.any { it.contains("9:30") })
    }

    @Test
    fun `detectMeetingTimes detects meeting keyword pattern`() = runTest {
        val text = "Meeting at 2:00 PM with the product team."
        val times = summarizer.detectMeetingTimes(text)
        assertTrue("Should detect meeting time pattern", times.isNotEmpty())
    }

    @Test
    fun `detectMeetingTimes detects date-slash-date-slash-year time pattern`() = runTest {
        val text = "Deadline submission: 6/15/2026 at 17:00"
        val times = summarizer.detectMeetingTimes(text)
        assertTrue("Should detect date-time pattern", times.isNotEmpty())
    }

    @Test
    fun `detectMeetingTimes returns distinct results without duplicates`() = runTest {
        val text = "Meeting at 3:00 PM. Reminder: meeting at 3:00 PM again."
        val times = summarizer.detectMeetingTimes(text)
        assertEquals(
            "Results should be deduplicated",
            times.size,
            times.distinct().size
        )
    }

    @Test
    fun `detectMeetingTimes detects day-name time pattern`() = runTest {
        val text = "Please join us on Friday 3:00 PM for the retrospective."
        val times = summarizer.detectMeetingTimes(text)
        assertTrue("Should detect Friday time pattern", times.isNotEmpty())
    }

    @Test
    fun `detectMeetingTimes is case-insensitive for keywords`() = runTest {
        val text = "MEETING at 11:00 AM — do not miss it."
        val times = summarizer.detectMeetingTimes(text)
        assertTrue("Should detect case-insensitive meeting keyword", times.isNotEmpty())
    }
}
