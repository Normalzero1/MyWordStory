package mywordstory

// Records so that both Jackson and the Anthropic SDK's structured-output schema derivation can read them.

@JvmRecord
data class Synonym(val word: String, val nuance: String)

@JvmRecord
data class Example(val en: String, val ko: String, val context: String)

/** A sentence with the word blanked out as "___", and the exact text that was removed. */
@JvmRecord
data class Cloze(val sentence: String, val answer: String)

/** What the LLM generates for one word card (docs/DESIGN.md "단어 내용"). */
@JvmRecord
data class WordContent(
    val headword: String,
    val ipa: String,
    val partOfSpeech: String,
    val meaningsKo: List<String>,
    val definitionEn: String,
    val usageNote: String,
    val synonyms: List<Synonym>,
    val collocations: List<String>,
    val examples: List<Example>,
    val tag: String,
    // Added in stage 2; cards made before that have null here.
    val cefr: String?,
    val cloze: Cloze?,
)

@JvmRecord
data class Word(
    val id: Long,
    val headword: String,
    /** "manual" (added by the user) or "wordlist" (picked from the candidate list). */
    val source: String,
    val content: WordContent,
    val createdAt: String,
    /** "new" (not studied yet), "studied", "known" or "excluded". */
    val status: String,
)

@JvmRecord
data class StatusRequest(val status: String?)

/** Level test result: candidates the user marked as known, and the level to start new words from. */
@JvmRecord
data class LevelTestResult(val startLevel: String?, val known: List<String>?, val unknown: List<String>?)

val CEFR_LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

@JvmRecord
data class AddWordRequest(val headword: String?)
