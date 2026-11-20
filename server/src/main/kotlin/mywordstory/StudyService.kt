package mywordstory

import org.slf4j.LoggerFactory

/** Candidate word list, CEFR levels, level test and the buffer of ready-made new-word cards. */
class StudyService(
    private val repo: WordRepository,
    private val generator: WordGenerator,
    private val tagger: LevelTagger,
    /** How many generated, not yet studied word-list cards to keep ready. */
    private val bufferSize: Int = 40,
) {
    private val log = LoggerFactory.getLogger(StudyService::class.java)

    fun importWordListIfEmpty() {
        if (repo.candidateCount() > 0) return
        val rows = javaClass.getResourceAsStream("/wordlists/candidates.csv")!!.bufferedReader().readLines().drop(1)
            .filter { it.isNotBlank() }
            .map { line -> line.split(',').let { Candidate(it[0], it[1], it[2].toInt()) } }
        repo.importCandidates(rows)
        log.info("Imported {} candidate words", rows.size)
    }

    /** Tags untagged candidates in batches until none are left or the tagger stops placing words. */
    fun tagMissingLevels(batchSize: Int = 100): Int {
        var tagged = 0
        while (true) {
            val words = repo.candidatesWithoutLevel(batchSize)
            if (words.isEmpty()) break
            val levels = tagger.tag(words)
            if (levels.isEmpty()) break
            repo.setLevels(levels)
            tagged += levels.size
        }
        if (tagged > 0) log.info("Tagged CEFR levels for {} words", tagged)
        return tagged
    }

    /** Generates cards for upcoming word-list words. Does nothing before the level test. */
    fun refillBuffer(): Int {
        val startLevel = repo.getSetting(START_LEVEL) ?: return 0
        val need = bufferSize - repo.countNewWords("wordlist")
        if (need <= 0) return 0
        var made = 0
        for (headword in repo.nextCandidates(startLevel, need)) {
            try {
                if (repo.insertIfAbsent(headword, "wordlist", generator.generate(headword)) != null) made++
            } catch (e: GenerationException) {
                log.warn("Could not generate a card for {}: {}", headword, e.message)
            }
        }
        if (made > 0) log.info("Generated {} new-word cards", made)
        return made
    }

    fun levelTest(perLevel: Int = 10): Map<String, Any?> = mapOf(
        "startLevel" to repo.getSetting(START_LEVEL),
        "levels" to CEFR_LEVELS.associateWith { repo.sampleCandidates(it, perLevel) },
    )

    fun saveLevelTest(result: LevelTestResult) {
        val start = result.startLevel?.uppercase()
        require(start in CEFR_LEVELS) { "startLevel은 A1~C2 중 하나여야 합니다" }
        repo.markCandidates(result.known.orEmpty(), "known")
        repo.markCandidates(result.unknown.orEmpty(), "priority")
        repo.setSetting(START_LEVEL, start!!)
    }

    fun status(): Map<String, Any?> = mapOf(
        "startLevel" to repo.getSetting(START_LEVEL),
        "candidateLevels" to repo.levelCounts(),
        "readyNewWords" to repo.countNewWords("wordlist") + repo.countNewWords("manual"),
    )

    companion object {
        const val START_LEVEL = "startLevel"
    }
}
