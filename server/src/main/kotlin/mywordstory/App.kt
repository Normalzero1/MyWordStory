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

fun Application.module(repo: WordRepository, generator: WordGenerator, apiToken: String) {
    install(ContentNegotiation) { jackson() }
    install(StatusPages) {
        exception<BadRequestException> { call, e ->
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
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw BadRequestException("id는 숫자여야 합니다")
                    val word = repo.find(id)
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
            }
        }
    }
}
