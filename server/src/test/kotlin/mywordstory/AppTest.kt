package mywordstory

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.sql.DriverManager
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Needs a PostgreSQL database: TEST_DATABASE_URL, TEST_DATABASE_USER, TEST_DATABASE_PASSWORD.
class AppTest {
    private val url = System.getenv("TEST_DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/mywordstory_test"
    private val user = System.getenv("TEST_DATABASE_USER") ?: "postgres"
    private val password = System.getenv("TEST_DATABASE_PASSWORD") ?: "postgres"
    private val repo = WordRepository(url, user, password)
    private val mapper = ObjectMapper()
    private val token = "test-token"
    private val generated = mutableListOf<String>()

    private val fakeGenerator = WordGenerator { headword ->
        generated += headword
        if (headword == "fail") throw GenerationException("모델이 이 단어의 생성을 거절했습니다: fail")
        WordContent(
            headword = headword, ipa = "/ˈsɜrkəl bæk/", partOfSpeech = "phrasal verb",
            meaningsKo = listOf("나중에 다시 논의하다"), definitionEn = "to return to a topic later",
            usageNote = "회의에서 자주 씁니다.", synonyms = listOf(Synonym("follow up", "더 일반적입니다.")),
            collocations = listOf("circle back on"),
            examples = listOf(Example("Let's circle back.", "나중에 다시 얘기해요.", "business")),
            tag = "business",
            cefr = "B2",
            cloze = Cloze("Let's ___ on this tomorrow.", "circle back"),
        )
    }

    @BeforeTest
    fun resetDatabase() {
        repo.migrate()
        DriverManager.getConnection(url, user, password).use { it.createStatement().execute("TRUNCATE words, candidates, settings") }
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(repo, fakeGenerator, token, StudyService(repo, fakeGenerator, { emptyMap() })) }
        block()
    }

    private suspend fun ApplicationTestBuilder.addWord(headword: String, auth: String? = token) =
        client.post("/words") {
            if (auth != null) header(HttpHeaders.Authorization, "Bearer $auth")
            contentType(ContentType.Application.Json)
            setBody("""{"headword": ${mapper.writeValueAsString(headword)}}""")
        }

    @Test
    fun `rejects requests without the right token`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, addWord("circle back", auth = null).status)
        assertEquals(HttpStatusCode.Unauthorized, addWord("circle back", auth = "wrong").status)
        assertEquals(emptyList(), generated)
    }

    @Test
    fun `adds a word, then returns the stored one without generating again`() = withApp {
        val created = addWord("  Circle   back ")
        assertEquals(HttpStatusCode.Created, created.status)
        val body = mapper.readTree(created.bodyAsText())
        assertEquals("Circle back", body["headword"].asText())
        assertEquals("phrasal verb", body["content"]["partOfSpeech"].asText())

        val again = addWord("circle back")
        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals(body["id"], mapper.readTree(again.bodyAsText())["id"])
        assertEquals(listOf("Circle back"), generated)

        val list = client.get("/words") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(1, mapper.readTree(list.bodyAsText()).size())
        val one = client.get("/words/${body["id"].asLong()}") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.OK, one.status)
    }

    @Test
    fun `study endpoints for new words, status and level test`() = withApp {
        val auth: io.ktor.client.request.HttpRequestBuilder.() -> Unit = { header(HttpHeaders.Authorization, "Bearer $token") }
        val id = mapper.readTree(addWord("circle back").bodyAsText())["id"].asLong()

        val next = mapper.readTree(client.get("/study/new?count=5", auth).bodyAsText())
        assertEquals(listOf("circle back"), next.map { it["headword"].asText() })
        assertEquals("B2", next[0]["content"]["cefr"].asText())

        val studied = client.post("/words/$id/status") { auth(); contentType(ContentType.Application.Json); setBody("""{"status":"studied"}""") }
        assertEquals("studied", mapper.readTree(studied.bodyAsText())["status"].asText())
        assertEquals(0, mapper.readTree(client.get("/study/new", auth).bodyAsText()).size())

        val badStatus = client.post("/words/$id/status") { auth(); contentType(ContentType.Application.Json); setBody("""{"status":"done"}""") }
        assertEquals(HttpStatusCode.BadRequest, badStatus.status)

        val badLevel = client.post("/level-test") { auth(); contentType(ContentType.Application.Json); setBody("""{"startLevel":"Z1"}""") }
        assertEquals(HttpStatusCode.BadRequest, badLevel.status)
        val ok = client.post("/level-test") { auth(); contentType(ContentType.Application.Json); setBody("""{"startLevel":"b1","known":[],"unknown":[]}""") }
        assertEquals(HttpStatusCode.NoContent, ok.status)
        assertEquals("B1", mapper.readTree(client.get("/level-test", auth).bodyAsText())["startLevel"].asText())
    }

    @Test
    fun `rejects an empty headword`() = withApp {
        assertEquals(HttpStatusCode.BadRequest, addWord("   ").status)
        assertEquals(emptyList(), generated)
    }

    @Test
    fun `reports a generation failure as 502 and stores nothing`() = withApp {
        val res = addWord("fail")
        assertEquals(HttpStatusCode.BadGateway, res.status)
        assertTrue(res.bodyAsText().contains("거절"))
        assertEquals(0, repo.list().size)
    }
}
