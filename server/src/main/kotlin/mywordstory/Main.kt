package mywordstory

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val repo = WordRepository(env("DATABASE_URL"), env("DATABASE_USER"), env("DATABASE_PASSWORD"))
    repo.migrate()
    val port = System.getenv("PORT")?.toInt() ?: 8080
    embeddedServer(Netty, port = port) {
        module(repo, ClaudeWordGenerator(), env("API_TOKEN"))
    }.start(wait = true)
}

private fun env(name: String): String =
    System.getenv(name) ?: error("환경 변수 $name 이(가) 필요합니다")
