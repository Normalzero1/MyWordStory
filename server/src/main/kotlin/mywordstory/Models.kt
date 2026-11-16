package mywordstory

// Records so that both Jackson and the Anthropic SDK's structured-output schema derivation can read them.

@JvmRecord
data class Synonym(val word: String, val nuance: String)

@JvmRecord
data class Example(val en: String, val ko: String, val context: String)

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
)

@JvmRecord
data class Word(
    val id: Long,
    val headword: String,
    val source: String,
    val content: WordContent,
    val createdAt: String,
)

@JvmRecord
data class AddWordRequest(val headword: String?)
