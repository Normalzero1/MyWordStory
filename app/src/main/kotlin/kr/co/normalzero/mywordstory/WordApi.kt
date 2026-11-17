package kr.co.normalzero.mywordstory

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Mirrors server/src/main/kotlin/mywordstory/Models.kt.

@Serializable
data class Synonym(val word: String, val nuance: String)

@Serializable
data class Example(val en: String, val ko: String, val context: String)

@Serializable
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

@Serializable
data class Word(
    val id: Long,
    val headword: String,
    val source: String,
    val content: WordContent,
    val createdAt: String,
)

@Serializable
private data class AddWordRequest(val headword: String)

@Serializable
private data class ErrorBody(val error: String? = null)

class ApiException(message: String) : Exception(message)

class WordApi(baseUrl: String, private val token: String, engine: HttpClientEngine = OkHttp.create()) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
        // Generating a card with the LLM can take a while.
        install(HttpTimeout) { requestTimeoutMillis = 90_000 }
    }

    suspend fun listWords(): List<Word> = send { client.get("$base/words") { bearerAuth(token) } }.body()

    suspend fun addWord(headword: String): Word = send {
        client.post("$base/words") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(AddWordRequest(headword))
        }
    }.body()

    private suspend fun send(request: suspend () -> HttpResponse): HttpResponse {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("서버에 연결할 수 없습니다. 서버 주소를 확인해 주세요. (${e.message})")
        }
        if (response.status.isSuccess()) return response
        if (response.status.value == 401) throw ApiException("토큰이 맞지 않습니다. 설정에서 토큰을 확인해 주세요.")
        val serverMessage = runCatching { json.decodeFromString<ErrorBody>(response.bodyAsText()).error }.getOrNull()
        throw ApiException(serverMessage ?: "서버 오류가 났습니다. (${response.status.value})")
    }
}
