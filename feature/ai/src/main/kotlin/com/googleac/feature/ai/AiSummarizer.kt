package com.googleac.feature.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device AI summarization and semantic search using Google AICore / Gemini Nano.
 *
 * This implementation provides the interface and logic for:
 * 1. Summarizing text from local PDF files (returns bulleted list)
 * 2. Semantic search over indexed file content
 *
 * Note: The actual Gemini Nano inference engine requires the Google AICore service
 * (available on Pixel 8+ devices). On unsupported devices, falls back to
 * extractive summarization.
 */
@Singleton
class AiSummarizer @Inject constructor() {

    /**
     * Summarizes the provided text content and returns a bulleted list.
     *
     * On devices with Google AICore (Pixel 8+):
     * - Uses Gemini Nano on-device model for abstractive summarization
     *
     * On other devices:
     * - Falls back to extractive summarization (key sentence extraction)
     *
     * @param text Text content extracted from a local PDF or document
     * @param maxBullets Maximum number of bullet points to return
     * @return List of bullet-point strings
     */
    suspend fun summarizeText(text: String, maxBullets: Int = 5): List<String> =
        withContext(Dispatchers.Default) {
            if (text.isBlank()) return@withContext emptyList()

            // Truncate to model context window (Gemini Nano supports ~4096 tokens ~= 16KB chars)
            val truncated = text.take(16_000)

            // Check if Gemini Nano / AICore is enabled on this device
            return@withContext if (isGeminiNanoEnabled()) {
                summarizeWithGeminiNano(truncated, maxBullets)
            } else {
                extractiveSummarize(truncated, maxBullets)
            }
        }

    /**
     * Performs semantic similarity search over a list of indexed text snippets.
     *
     * @param query User's search query
     * @param corpus Map of fileId to indexed text
     * @return List of fileIds sorted by relevance
     */
    suspend fun semanticSearch(query: String, corpus: Map<String, String>): List<String> =
        withContext(Dispatchers.Default) {
            if (query.isBlank() || corpus.isEmpty()) return@withContext emptyList()

            // Simple TF-IDF style ranking as fallback when Gemini Nano unavailable
            val queryTokens = tokenize(query)
            corpus.entries
                .map { (fileId, text) ->
                    val docTokenSet = tokenize(text).toSet()
                    val score = queryTokens.count { it in docTokenSet }.toDouble() / (queryTokens.size + 1)
                    fileId to score
                }
                .filter { it.second > 0.0 }
                .sortedByDescending { it.second }
                .map { it.first }
        }

    /**
     * Detects meeting times, dates, or event references in document text.
     * Used for the Semantic Proactivity feature - suggesting Calendar entries.
     *
     * @param text Document text to analyze
     * @return List of detected temporal expressions
     */
    suspend fun detectMeetingTimes(text: String): List<String> =
        withContext(Dispatchers.Default) {
            val patterns = listOf(
                Regex("""(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\s+\d{1,2}(?::\d{2})?\s*(?:AM|PM|am|pm)"""),
                Regex("""\d{1,2}/\d{1,2}/\d{2,4}\s+(?:at\s+)?\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)?"""),
                Regex("""(?:meeting|call|sync|standup|review)\s+(?:at|on)\s+\d{1,2}(?::\d{2})?\s*(?:AM|PM|am|pm)?""", RegexOption.IGNORE_CASE),
                Regex("""\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)""")
            )
            patterns.flatMap { it.findAll(text).map { match -> match.value.trim() } }
                .distinct()
        }

    /**
     * Feature flag for Gemini Nano / AICore on-device inference.
     * Returns true only when the AICore SDK is publicly available and integrated.
     * Currently disabled — falls back to extractive summarization on all devices.
     */
    private fun isGeminiNanoEnabled(): Boolean = false

    private suspend fun summarizeWithGeminiNano(text: String, maxBullets: Int): List<String> {
        // Gemini Nano via AICore SDK (com.google.android.gms:play-services-tasks)
        // Implementation when AICore becomes publicly available
        return extractiveSummarize(text, maxBullets)
    }

    /**
     * Extractive summarization: selects the most representative sentences.
     */
    private fun extractiveSummarize(text: String, maxBullets: Int): List<String> {
        val sentences = text.split(Regex("[.!?]+"))
            .map { it.trim() }
            .filter { it.length > 20 }

        if (sentences.isEmpty()) return emptyList()

        // Score sentences by term frequency
        val allTokens = tokenize(text)
        val tokenFreq = allTokens.groupingBy { it }.eachCount()

        return sentences
            .map { sentence ->
                val sentTokens = tokenize(sentence)
                val score = sentTokens.sumOf { tokenFreq[it] ?: 0 }.toDouble() / (sentTokens.size + 1)
                sentence to score
            }
            .sortedByDescending { it.second }
            .take(maxBullets)
            .sortedBy { sentences.indexOf(it.first) }
            .map { "- ${it.first}" }
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.length > 3 }
}
