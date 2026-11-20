package mywordstory

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

fun main() {
    val log = LoggerFactory.getLogger("mywordstory")
    val repo = WordRepository(env("DATABASE_URL"), env("DATABASE_USER"), env("DATABASE_PASSWORD"))
    repo.migrate()
    val generator = ClaudeWordGenerator()
    val study = StudyService(repo, generator, ClaudeLevelTagger())
    study.importWordListIfEmpty()

    // One background thread: tag CEFR levels, then keep new-word cards ready.
    val jobs = Executors.newSingleThreadScheduledExecutor()
    val backgroundWork = Runnable {
        runCatching { study.tagMissingLevels() }.onFailure { log.warn("CEFR tagging failed: {}", it.message) }
        runCatching { study.refillBuffer() }.onFailure { log.warn("Card generation failed: {}", it.message) }
    }
    jobs.scheduleWithFixedDelay(backgroundWork, 0, 10, TimeUnit.MINUTES)

    val port = System.getenv("PORT")?.toInt() ?: 8080
    embeddedServer(Netty, port = port) {
        module(repo, generator, env("API_TOKEN"), study) { jobs.execute(backgroundWork) }
    }.start(wait = true)
}

private fun env(name: String): String =
    System.getenv(name) ?: error("환경 변수 $name 이(가) 필요합니다")
