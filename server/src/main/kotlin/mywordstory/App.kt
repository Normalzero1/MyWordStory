package mywordstory

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val WORD_STATUSES = setOf("new", "studied", "known", "excluded")

/** [onStudyChange] runs after anything that may need more cards generated (it should not block). */
fun Application.module(
    repo: WordRepository,
    generator: WordGenerator,
    apiToken: String,
    study: StudyService,
    onStudyChange: () -> Unit = {},
) {
    install(ContentNegotiation) { jackson() }
    install(StatusPages) {
        exception<BadRequestException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "잘못된 요청입니다")))
        }
        exception<IllegalArgumentException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "잘못된 요청입니다")))
        }
        exception<GenerationException> { call, e ->
            call.respond(HttpStatusCode.BadGateway, mapOf("error" to e.message))
        }
    }
    install(Authentication) {
        bearer("token") {
            authenticate { if (it.token == apiToken) UserIdPrincipal("me") else null }
        }
    }

    routing {
        get("/health") { call.respondText("ok") }

        authenticate("token") {
            route("/words") {
                get { call.respond(repo.list()) }

                get("/{id}") {
                    val word = repo.find(call.wordId())
                    if (word == null) call.respond(HttpStatusCode.NotFound, mapOf("error" to "단어가 없습니다"))
                    else call.respond(word)
                }

                post {
                    val headword = call.receive<AddWordRequest>().headword
                        ?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
                    if (headword.isEmpty() || headword.length > 100) {
                        throw BadRequestException("headword는 1~100자여야 합니다")
                    }
                    val existing = repo.findByHeadword(headword)
                    if (existing != null) return@post call.respond(HttpStatusCode.OK, existing)

                    val content = withContext(Dispatchers.IO) { generator.generate(headword) }
                    call.respond(HttpStatusCode.Created, repo.insert(headword, "manual", content))
                }

                post("/{id}/status") {
                    val id = call.wordId()
                    val status = call.receive<StatusRequest>().status
                    require(status in WORD_STATUSES) { "status는 ${WORD_STATUSES.joinToString()} 중 하나여야 합니다" }
                    val word = repo.setStatus(id, status!!)
                    if (word == null) call.respond(HttpStatusCode.NotFound, mapOf("error" to "단어가 없습니다"))
                    else call.respond(word).also { onStudyChange() }
                }
            }

            // Cards ready for today's new words; the user's own words come first.
            get("/study/new") {
                val count = call.request.queryParameters["count"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30
                call.respond(repo.newWords(count))
                onStudyChange()
            }

            get("/study/status") { call.respond(study.status()) }

            route("/level-test") {
                get { call.respond(study.levelTest()) }
                post {
                    study.saveLevelTest(call.receive<LevelTestResult>())
                    call.respond(HttpStatusCode.NoContent)
                    onStudyChange()
                }
            }

            // For checking how the CEFR levels came out.
            get("/candidates/sample") {
                val level = call.request.queryParameters["level"]?.uppercase()
                require(level in CEFR_LEVELS) { "level은 A1~C2 중 하나여야 합니다" }
                val count = call.request.queryParameters["count"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
                call.respond(repo.sampleCandidates(level!!, count))
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.wordId(): Long =
    parameters["id"]?.toLongOrNull() ?: throw BadRequestException("id는 숫자여야 합니다")
