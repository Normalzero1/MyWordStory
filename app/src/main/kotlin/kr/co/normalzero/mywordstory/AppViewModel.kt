package kr.co.normalzero.mywordstory

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.launch
import kr.co.normalzero.mywordstory.study.Cefr
import kr.co.normalzero.mywordstory.study.DailyPlan
import kr.co.normalzero.mywordstory.study.NewWordSession
import kr.co.normalzero.mywordstory.study.ReviewSession

enum class Tab { STUDY, WORDS, SETTINGS }

/** A full-screen flow on top of the tabs. */
sealed interface Overlay {
    data object LevelTest : Overlay
    data class NewWords(val session: NewWordSession) : Overlay
    data class Review(val session: ReviewSession) : Overlay
    data object ReviewDone : Overlay
    data object AddWords : Overlay
    data class Card(val word: Word) : Overlay
}

/** One line of the add-words screen. */
data class AddResult(val headword: String, val state: String, val ok: Boolean?)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val store = StudyStore(StudyDb.open(app).dao())

    var serverUrl by mutableStateOf(prefs.getString("serverUrl", "").orEmpty()); private set
    var token by mutableStateOf(prefs.getString("token", "").orEmpty()); private set
    var dailyCap by mutableStateOf(prefs.getInt("dailyCap", 30)); private set
    var autoReduce by mutableStateOf(prefs.getBoolean("autoReduce", true)); private set
    val configured: Boolean get() = serverUrl.isNotBlank() && token.isNotBlank()

    var tab by mutableStateOf(Tab.STUDY)
    var overlay by mutableStateOf<Overlay?>(null)
    /** Bumped whenever a session object changes, so screens reading it recompose. */
    var tick by mutableStateOf(0); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)

    // Home
    var startLevel by mutableStateOf<String?>(null); private set
    var untaggedWords by mutableStateOf(0); private set
    var dueCount by mutableStateOf(0); private set
    var newToday by mutableStateOf(0); private set
    var reducedFrom by mutableStateOf<Int?>(null); private set
    var manualFirst by mutableStateOf<List<String>>(emptyList()); private set

    // Words tab
    var words by mutableStateOf<List<Word>>(emptyList()); private set
    var dueTimes by mutableStateOf<Map<Long, Instant>>(emptyMap()); private set

    // Level test
    var levelTest by mutableStateOf<LevelTest?>(null); private set
    var levelIndex by mutableStateOf(0); private set
    var knownInLevel by mutableStateOf<Set<String>>(emptySet())
    private val levelAnswers = mutableMapOf<String, Map<String, Boolean>>()

    // Add words
    var addInput by mutableStateOf("")
    var addResults by mutableStateOf<List<AddResult>>(emptyList()); private set

    private var api = WordApi(serverUrl, token)

    init {
        if (configured) refreshHome() else tab = Tab.SETTINGS
    }

    fun changed() { tick++ }

    // --- settings ---

    fun saveServer(url: String, newToken: String) {
        prefs.edit { putString("serverUrl", url.trim()).putString("token", newToken.trim()) }
        serverUrl = url.trim()
        token = newToken.trim()
        api = WordApi(serverUrl, token)
        tab = Tab.STUDY
        refreshHome()
    }

    fun changeDailyCap(delta: Int) {
        dailyCap = (dailyCap + delta).coerceIn(5, 100)
        prefs.edit { putInt("dailyCap", dailyCap) }
        refreshHome()
    }

    fun changeAutoReduce(on: Boolean) {
        autoReduce = on
        prefs.edit { putBoolean("autoReduce", on) }
        refreshHome()
    }

    // --- home ---

    private val today get() = LocalDate.now().toString()
    private fun studiedToday(): Int = if (prefs.getString("studiedDay", null) == today) prefs.getInt("studiedCount", 0) else 0
    private fun addStudiedToday(n: Int) = prefs.edit { putString("studiedDay", today).putInt("studiedCount", studiedToday() + n) }

    fun refreshHome() = run {
        dueCount = store.dueCount(Instant.now())
        val planned = DailyPlan.newWordCount(dailyCap, dueCount, autoReduce)
        reducedFrom = if (planned < dailyCap) dailyCap else null
        newToday = (planned - studiedToday()).coerceAtLeast(0)
        val status = api.status()
        startLevel = status.startLevel
        untaggedWords = status.candidateLevels["none"] ?: 0
        manualFirst = if (newToday > 0) api.newWords(newToday).filter { it.source == "manual" }.map { it.headword } else emptyList()
    }

    fun startStudy() = run {
        val now = Instant.now()
        val fresh = if (newToday > 0) api.newWords(newToday) else emptyList()
        if (fresh.isNotEmpty()) {
            val pool = fresh + store.studiedWords()
            overlay = Overlay.NewWords(NewWordSession(fresh, pool))
        } else {
            startReview(now)
        }
        changed()
    }

    private suspend fun startReview(now: Instant) {
        val due = store.dueWords(now)
        overlay = if (due.isEmpty()) { message = "오늘 할 학습을 모두 마쳤어요."; null } else Overlay.Review(ReviewSession(due))
    }

    /** Card screen: "이미 앎" or "필요 없음". */
    fun dropCard(session: NewWordSession, status: String) {
        val word = session.removeCurrentCard()
        changed()
        run(showBusy = false) { api.setStatus(word.id, status) }
    }

    /** Results screen: save ratings, tell the server, then go on to reviews. */
    fun finishNewWords(session: NewWordSession) = run {
        val now = Instant.now()
        val results = session.results()
        for (r in results) store.saveNewWord(r.word, r.rating, now)
        addStudiedToday(results.size)
        for (r in results) runCatching { api.setStatus(r.word.id, "studied") }
        startReview(Instant.now())
        changed()
        refreshDueCount()
    }

    fun finishReview(session: ReviewSession) = run {
        val now = Instant.now()
        val byId = session.ratings()
        val due = store.dueWords(now).associateBy { it.id }
        for ((id, rating) in byId) due[id]?.let { store.saveReview(it, rating, now) }
        overlay = Overlay.ReviewDone
        refreshDueCount()
    }

    private suspend fun refreshDueCount() {
        dueCount = store.dueCount(Instant.now())
    }

    fun closeOverlay() {
        overlay = null
        refreshHome()
    }

    // --- level test ---

    fun openLevelTest() = run {
        levelAnswers.clear()
        levelIndex = 0
        knownInLevel = emptySet()
        val test = api.levelTest()
        levelTest = test.copy(levels = test.levels.filterValues { it.isNotEmpty() })
        overlay = Overlay.LevelTest
    }

    val levelNames: List<String> get() = Cefr.entries.map { it.name }.filter { levelTest?.levels?.containsKey(it) == true }

    fun toggleKnown(word: String) {
        knownInLevel = if (word in knownInLevel) knownInLevel - word else knownInLevel + word
    }

    /** Next level, or finish at the first level where more than 30% were unknown. */
    fun submitLevel() {
        val test = levelTest ?: return
        val level = levelNames.getOrNull(levelIndex) ?: return
        val shown = test.levels.getValue(level)
        levelAnswers[level] = shown.associateWith { it in knownInLevel }
        val unknown = shown.count { it !in knownInLevel }
        val isLast = levelIndex == levelNames.lastIndex
        if (unknown * 10 > shown.size * 3 || isLast) {
            val start = if (unknown * 10 > shown.size * 3) level else Cefr.C2.name
            run {
                val all = levelAnswers.values.flatMap { it.entries }
                api.saveLevelTest(start, all.filter { it.value }.map { it.key }, all.filter { !it.value }.map { it.key })
                message = "${start}부터 시작해요. 서버가 새 단어 카드를 준비하는 데 몇 분 걸릴 수 있어요."
                overlay = null
                refreshHome()
            }
        } else {
            levelIndex++
            knownInLevel = emptySet()
        }
    }

    /** What the user would start at if they stopped now (shown under the words). */
    fun levelHint(): String {
        val shown = levelTest?.levels?.get(levelNames.getOrNull(levelIndex)) ?: return ""
        val unknown = shown.count { it !in knownInLevel }
        val pct = if (shown.isEmpty()) 0 else unknown * 100 / shown.size
        return "앎 ${shown.size - unknown} · 모름 $unknown ($pct%)"
    }

    // --- words ---

    fun refreshWords() = run {
        words = api.listWords()
        dueTimes = store.dueTimes()
    }

    fun openAddWords(prefill: String? = null) {
        if (prefill != null) addInput = prefill.trim()
        addResults = emptyList()
        overlay = Overlay.AddWords
    }

    fun addWords() {
        val lines = addInput.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (lines.isEmpty()) return
        addInput = ""
        addResults = lines.map { AddResult(it, "카드 만드는 중…", null) }
        viewModelScope.launch {
            for (line in lines) {
                val result = try {
                    val w = api.addWord(line)
                    AddResult(line, if (w.content.headword.equals(line, true)) "카드 완성" else "카드 완성 · ${w.content.headword}", true)
                } catch (e: ApiException) {
                    AddResult(line, e.message ?: "실패", false)
                }
                addResults = addResults.map { if (it.headword == line) result else it }
            }
        }
    }

    private fun run(showBusy: Boolean = true, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (showBusy) busy = true
            message = null
            try {
                block()
            } catch (e: ApiException) {
                message = e.message
            } finally {
                if (showBusy) busy = false
            }
        }
    }
}
