package mywordstory

import java.sql.DriverManager
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StudyServiceTest {
    private val url = System.getenv("TEST_DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/mywordstory_test"
    private val user = System.getenv("TEST_DATABASE_USER") ?: "postgres"
    private val password = System.getenv("TEST_DATABASE_PASSWORD") ?: "postgres"
    private val repo = WordRepository(url, user, password)

    private val levels = mapOf(
        "apple" to "A1", "budget" to "A2", "reimburse" to "B1",
        "leverage" to "B2", "streamline" to "B2", "tentative" to "C1",
    )
    private val generated = mutableListOf<String>()
    private val generator = WordGenerator { headword ->
        generated += headword
        WordContent(headword, "", "noun", listOf("뜻"), "", "", emptyList(), emptyList(), emptyList(), "daily", levels[headword], null)
    }
    private val taggerCalls = mutableListOf<List<String>>()
    private val tagger = LevelTagger { words -> taggerCalls += words; words.associateWith { levels.getValue(it) } }

    @BeforeTest
    fun reset() {
        repo.migrate()
        DriverManager.getConnection(url, user, password).use { it.createStatement().execute("TRUNCATE words, candidates, settings") }
    }

    private fun seed() = repo.importCandidates(levels.keys.mapIndexed { i, w -> Candidate(w, "bsl", i + 1) })

    @Test
    fun `imports the bundled word list once`() {
        val study = StudyService(repo, generator, tagger)
        study.importWordListIfEmpty()
        study.importWordListIfEmpty()
        assertEquals(3140, repo.candidateCount())
    }

    @Test
    fun `tags levels in batches until every candidate has one`() {
        seed()
        val tagged = StudyService(repo, generator, tagger).tagMissingLevels(batchSize = 4)
        assertEquals(6, tagged)
        assertEquals(listOf(4, 2), taggerCalls.map { it.size })
        assertEquals(mapOf("A1" to 1, "A2" to 1, "B1" to 1, "B2" to 2, "C1" to 1), repo.levelCounts())
    }

    @Test
    fun `stops when the tagger places no words`() {
        seed()
        assertEquals(0, StudyService(repo, generator, { emptyMap() }).tagMissingLevels())
    }

    @Test
    fun `level test, buffer and new words follow the design order`() {
        seed()
        val study = StudyService(repo, generator, tagger, bufferSize = 3)
        study.tagMissingLevels()

        @Suppress("UNCHECKED_CAST")
        val test = study.levelTest()["levels"] as Map<String, List<String>>
        assertEquals(setOf("leverage", "streamline"), test.getValue("B2").toSet())

        assertEquals(0, study.refillBuffer(), "no cards before the level test")

        // Start at B1, "streamline" is known, "budget" (A2) was unknown.
        study.saveLevelTest(LevelTestResult("B1", known = listOf("streamline"), unknown = listOf("budget")))
        assertEquals(3, study.refillBuffer())
        assertEquals(listOf("budget", "reimburse", "leverage"), generated)

        repo.insert("circle back", "manual", generator.generate("circle back"))
        val next = repo.newWords(10)
        assertEquals(listOf("circle back", "budget", "reimburse", "leverage"), next.map { it.headword })

        // Studying one word frees a buffer slot; the next candidate is "tentative" (C1).
        repo.setStatus(next[1].id, "studied")
        assertEquals(1, study.refillBuffer())
        assertEquals("tentative", generated.last())
        assertTrue(repo.newWords(10).none { it.headword == "budget" })
    }

    @Test
    fun `rejects an unknown start level`() {
        assertFailsWith<IllegalArgumentException> {
            StudyService(repo, generator, tagger).saveLevelTest(LevelTestResult("Z9", null, null))
        }
    }
}
