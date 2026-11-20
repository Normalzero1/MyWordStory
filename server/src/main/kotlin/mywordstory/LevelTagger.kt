package mywordstory

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicException
import com.anthropic.models.messages.MessageCreateParams

/** Gives each word a CEFR level (A1..C2). Words it can't place are left out of the result. */
fun interface LevelTagger {
    fun tag(words: List<String>): Map<String, String>
}

@JvmRecord
data class WordLevel(val word: String, val cefr: String)

@JvmRecord
data class WordLevels(val levels: List<WordLevel>)

class ClaudeLevelTagger(
    private val client: AnthropicClient = AnthropicOkHttpClient.fromEnv(),
) : LevelTagger {

    override fun tag(words: List<String>): Map<String, String> {
        val response = try {
            client.messages().create(buildParams(words))
        } catch (e: AnthropicException) {
            throw GenerationException("Claude API 호출에 실패했습니다: ${e.message}")
        }
        val levels = response.content().stream().flatMap { it.text().stream() }.findFirst()
            .orElseThrow { GenerationException("모델 응답에 레벨이 없습니다") }.text().levels
        val asked = words.associateBy { it.lowercase() }
        return levels.mapNotNull { l ->
            val word = asked[l.word.lowercase()] ?: return@mapNotNull null
            val level = l.cefr.uppercase()
            if (level in CEFR_LEVELS) word to level else null
        }.toMap()
    }

    companion object {
        fun buildParams(words: List<String>) = MessageCreateParams.builder()
            .model(ClaudeWordGenerator.MODEL)
            .maxTokens(8000L)
            .system(
                "For each English word in the user message, give the CEFR level (A1, A2, B1, B2, C1 or C2) " +
                    "at which a learner usually meets it, judged by its most common meaning. " +
                    "Return every word exactly as written, once."
            )
            .addUserMessage(words.joinToString("\n"))
            .outputConfig(WordLevels::class.java)
            .build()
    }
}
