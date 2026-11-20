package mywordstory

import kotlin.test.Test
import kotlin.test.assertTrue

class WordGeneratorTest {
    @Test
    fun `derives an output schema with every card field`() {
        val request = ClaudeWordGenerator.buildParams("circle back").rawParams.toString()
        for (field in listOf("meaningsKo", "usageNote", "synonyms", "nuance", "collocations", "examples", "context", "tag", "cefr", "cloze", "sentence", "answer")) {
            assertTrue(field in request, "schema is missing $field: $request")
        }
    }

    @Test
    fun `derives the level tagger schema`() {
        val request = ClaudeLevelTagger.buildParams(listOf("leverage", "budget")).rawParams.toString()
        assertTrue("levels" in request && "cefr" in request && "leverage\\nbudget" in request.replace("\n", "\\n"), request)
    }
}
