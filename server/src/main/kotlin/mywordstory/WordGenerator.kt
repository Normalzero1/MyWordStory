package mywordstory

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.StopReason

fun interface WordGenerator {
    fun generate(headword: String): WordContent
}

class GenerationException(message: String) : RuntimeException(message)

/** Generates a word card with Claude. Reads ANTHROPIC_API_KEY from the environment. */
class ClaudeWordGenerator(
    private val client: AnthropicClient = AnthropicOkHttpClient.fromEnv(),
) : WordGenerator {

    override fun generate(headword: String): WordContent {
        val response = try {
            client.messages().create(buildParams(headword))
        } catch (e: AnthropicException) {
            throw GenerationException("Claude API 호출에 실패했습니다: ${e.message}")
        }
        when (response.stopReason().orElse(null)) {
            StopReason.REFUSAL -> throw GenerationException("모델이 이 단어의 생성을 거절했습니다: $headword")
            StopReason.MAX_TOKENS -> throw GenerationException("생성 결과가 너무 길어 잘렸습니다: $headword")
            else -> {}
        }
        return response.content().stream()
            .flatMap { it.text().stream() }
            .findFirst()
            .orElseThrow { GenerationException("모델 응답에 단어 내용이 없습니다: $headword") }
            .text()
    }

    companion object {
        const val MODEL = "claude-haiku-5-5"

        fun buildParams(headword: String) = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(4000L)
            .system(SYSTEM_PROMPT)
            .addUserMessage(headword)
            .outputConfig(WordContent::class.java)
            .build()

        val SYSTEM_PROMPT = """
            You write vocabulary cards for a Korean adult learning English for everyday conversation and IT/business work.
            The user message is one English word or phrase (it may be a phrasal verb or idiom). Fill every field:
            - headword: the word or phrase in its common dictionary form. If the input is misspelled, use the intended word.
            - ipa: American English IPA.
            - partOfSpeech: in English, e.g. "noun", "verb", "phrasal verb", "idiom".
            - meaningsKo: 1 to 3 Korean meanings. Put the meaning most used in conversation or IT/business first.
            - definitionEn: a one-line English definition.
            - usageNote: 2 or 3 Korean sentences on how native speakers actually use it: formality, US/UK difference, typical situations, and mistakes Korean speakers often make.
            - synonyms: 2 to 4 English synonyms. For each, one Korean sentence on how its nuance differs from the headword.
            - collocations: 3 to 5 common collocations.
            - examples: exactly 2. One with context "daily" (everyday conversation) and one with context "business" (IT/business workplace). Each with a natural Korean translation in "ko".
            - tag: "business" if the word is mainly used in IT/business settings, otherwise "daily".
            - cefr: the CEFR level a learner usually meets this word at: one of A1, A2, B1, B2, C1, C2.
            - cloze: a natural English sentence using the word, different from the examples. In "sentence", replace the word (in the exact form used) with "___"; put the removed text in "answer".
        """.trimIndent()
    }
}
