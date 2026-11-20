package kr.co.normalzero.mywordstory.study

import io.github.openspacedrepetition.Rating
import kotlin.random.Random
import kr.co.normalzero.mywordstory.Word

/** Which side of a practice card faces up. Picked at random each time a card is shown (Q40). */
enum class Direction { WORD_TO_MEANING, MEANING_TO_WORD }

/** Feedback shown after a multiple-choice answer until the user moves on. */
data class Answered(val word: Word, val choices: Choices, val picked: Int) {
    val correct: Boolean get() = picked == choices.answerIndex
}

data class WordResult(val word: Word, val practice: Int, val quiz: Int, val cloze: Int) {
    val total: Int get() = practice + quiz + cloze
    val rating: Rating get() = Grading.newWordRating(total)
}

/**
 * Today's new words (docs/DESIGN.md, 학습): cards → practice → (again or on) → multiple choice → cloze → results.
 * [pool] supplies wrong options; it should include these words and may include older ones.
 */
class NewWordSession(words: List<Word>, private val pool: List<Word>, private val random: Random = Random.Default) {
    enum class Stage { CARDS, PRACTICE, PRACTICE_DONE, QUIZ, CLOZE, SUMMARY }

    var stage = if (words.isEmpty()) Stage.SUMMARY else Stage.CARDS
        private set
    private val active = words.toMutableList()
    val words: List<Word> get() = active

    var cardIndex = 0
        private set
    val currentCard: Word? get() = active.getOrNull(cardIndex)

    var practice: LoopSession<Word>? = null
        private set
    var direction = Direction.WORD_TO_MEANING
        private set
    private var practiceScores: Map<Long, Int>? = null

    var quiz: LoopSession<Word>? = null
        private set
    var cloze: LoopSession<Word>? = null
        private set
    var choices: Choices? = null
        private set
    var answered: Answered? = null
        private set

    /** "이미 앎" or "필요 없음" on a card: the word leaves today's session. */
    fun removeCurrentCard(): Word {
        val word = active.removeAt(cardIndex)
        if (cardIndex >= active.size) finishCards()
        return word
    }

    fun nextCard() {
        cardIndex++
        if (cardIndex >= active.size) finishCards()
    }

    private fun finishCards() {
        if (active.isEmpty()) stage = Stage.SUMMARY else startPractice()
    }

    private fun startPractice() {
        practice = LoopSession(active, random)
        stage = Stage.PRACTICE
        flip()
    }

    private fun flip() {
        direction = if (random.nextBoolean()) Direction.WORD_TO_MEANING else Direction.MEANING_TO_WORD
    }

    fun answerPractice(remembered: Boolean) {
        val p = practice ?: return
        p.answer(remembered)
        if (p.isDone) endPractice() else flip()
    }

    /** "연습 통과": stop practising now; misses so far still count. */
    fun passPractice() = endPractice()

    private fun endPractice() {
        val p = practice ?: return
        // Only the first practice run is scored; "연습 한 번 더" is extra.
        if (practiceScores == null) practiceScores = active.associate { it.id to p.score(it) }
        stage = Stage.PRACTICE_DONE
    }

    fun practiceAgain() = startPractice()

    fun startQuiz() {
        quiz = LoopSession(active, random)
        stage = Stage.QUIZ
        prepareChoices()
    }

    /** Index into [choices]. Records the answer and shows feedback until [next]. */
    fun answer(picked: Int) {
        if (answered != null) return
        val word = currentQuestion() ?: return
        val c = choices ?: return
        answered = Answered(word, c, picked)
        loop()?.answer(picked == c.answerIndex)
    }

    fun next() {
        answered = null
        when (stage) {
            Stage.QUIZ -> if (quiz!!.isDone) startCloze() else prepareChoices()
            Stage.CLOZE -> if (cloze!!.isDone) stage = Stage.SUMMARY else prepareChoices()
            else -> {}
        }
    }

    private fun startCloze() {
        val withCloze = active.filter { it.content.cloze != null }
        if (withCloze.isEmpty()) {
            stage = Stage.SUMMARY
            return
        }
        cloze = LoopSession(withCloze, random)
        stage = Stage.CLOZE
        prepareChoices()
    }

    private fun loop(): LoopSession<Word>? = when (stage) {
        Stage.QUIZ -> quiz
        Stage.CLOZE -> cloze
        else -> null
    }

    fun currentQuestion(): Word? = loop()?.current

    private fun prepareChoices() {
        val word = currentQuestion() ?: return
        val others = pool.filter { it.id != word.id }
        choices = if (stage == Stage.QUIZ) {
            ChoiceMaker.make(
                word.content.meaningsKo.first(), word.content.partOfSpeech,
                others.map { it.content.meaningsKo.first() to it.content.partOfSpeech }, random,
            )
        } else {
            ChoiceMaker.make(
                word.content.cloze!!.answer, word.content.partOfSpeech,
                others.map { it.headword to it.content.partOfSpeech }, random,
            )
        }
    }

    fun results(): List<WordResult> = active.map {
        WordResult(it, practiceScores?.get(it.id) ?: 0, quiz?.score(it) ?: 0, cloze?.score(it) ?: 0)
    }
}

/** Due reviews: practice cards only; the first try decides the rating (Q44). */
class ReviewSession(words: List<Word>, private val random: Random = Random.Default) {
    val loop = LoopSession(words, random)
    val total = words.size
    var flipped = false
        private set
    var direction = Direction.WORD_TO_MEANING
        private set
    private val firstTry = mutableMapOf<Long, Boolean>()

    init { flipNew() }

    fun reveal() { flipped = true }

    fun answer(remembered: Boolean) {
        val word = loop.current ?: return
        firstTry.putIfAbsent(word.id, remembered)
        loop.answer(remembered)
        flipNew()
    }

    private fun flipNew() {
        flipped = false
        direction = if (random.nextBoolean()) Direction.WORD_TO_MEANING else Direction.MEANING_TO_WORD
    }

    /** Words whose first try is known, with their rating. */
    fun ratings(): Map<Long, Rating> = firstTry.mapValues { Grading.reviewRating(it.value) }
}
