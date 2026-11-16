package mywordstory

import kotlin.test.Test
import kotlin.test.assertTrue

class WordGeneratorTest {
    @Test
    fun `derives an output schema with every card field`() {
        val request = ClaudeWordGenerator.buildParams("circle back").rawParams.toString()
        for (field in listOf("meaningsKo", "usageNote", "synonyms", "nuance", "collocations", "examples", "context", "tag")) {
            assertTrue(field in request, "schema is missing $field: $request")
        }
    }
}
