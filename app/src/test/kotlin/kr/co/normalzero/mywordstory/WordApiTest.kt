package kr.co.normalzero.mywordstory

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WordApiTest {
    // Shape of the server's response (see server AppTest).
    private val wordJson = """
        {"id":7,"headword":"circle back","source":"manual","createdAt":"2026-11-17T09:00:00Z",
         "content":{"headword":"circle back","ipa":"/ˈsɜrkəl bæk/","partOfSpeech":"phrasal verb",
           "meaningsKo":["나중에 다시 논의하다"],"definitionEn":"to return to a topic later",
           "usageNote":"회의에서 자주 씁니다.","synonyms":[{"word":"follow up","nuance":"더 일반적입니다."}],
           "collocations":["circle back on"],
           "examples":[{"en":"Let's circle back.","ko":"나중에 다시 얘기해요.","context":"business"}],
           "tag":"business"}}
    """.trimIndent()

    private fun api(status: HttpStatusCode, body: String, seen: MutableList<String> = mutableListOf()) =
        WordApi("https://example.test/", "secret", MockEngine { request ->
            seen += "${request.method.value} ${request.url} ${request.headers[HttpHeaders.Authorization]}"
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        })

    @Test
    fun addWordSendsTokenAndParsesCard() = runBlocking {
        val seen = mutableListOf<String>()
        val word = api(HttpStatusCode.Created, wordJson, seen).addWord("circle back")
        assertEquals(listOf("POST https://example.test/words Bearer secret"), seen)
        assertEquals("phrasal verb", word.content.partOfSpeech)
        assertEquals("follow up", word.content.synonyms.single().word)
    }

    @Test
    fun listWordsParsesList() = runBlocking {
        assertEquals(7L, api(HttpStatusCode.OK, "[$wordJson]").listWords().single().id)
    }

    @Test
    fun serverErrorMessageIsShown() {
        val e = assertThrows(ApiException::class.java) {
            runBlocking { api(HttpStatusCode.BadGateway, """{"error":"Claude API 호출에 실패했습니다"}""").addWord("x") }
        }
        assertEquals("Claude API 호출에 실패했습니다", e.message)
    }

    @Test
    fun wrongTokenHasClearMessage() {
        val e = assertThrows(ApiException::class.java) {
            runBlocking { api(HttpStatusCode.Unauthorized, "").listWords() }
        }
        assertEquals("토큰이 맞지 않습니다. 설정에서 토큰을 확인해 주세요.", e.message)
    }
}
