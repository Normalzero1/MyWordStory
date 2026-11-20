package kr.co.normalzero.mywordstory.study

import io.github.openspacedrepetition.Rating
import kotlin.random.Random
import kr.co.normalzero.mywordstory.Cloze
import kr.co.normalzero.mywordstory.Word
import kr.co.normalzero.mywordstory.WordContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

fun word(id: Long, headword: String, meaning: String, cloze: Boolean = true) = Word(
    id, headword, "wordlist",
    WordContent(
        headword, "", "noun", listOf(meaning), "", "", emptyList(), emptyList(), emptyList(), "daily",
        "B1", if (cloze) Cloze("I need ___ now.", headword) else null,
    ),
    "2026-11-20T00:00:00Z",
)

class NewWordSessionTest {
    private val apple = word(1, "apple", "사과")
    private val banana = word(2, "banana", "바나나")
    private val car = word(3, "car", "자동차", cloze = false)
    private val known = word(4, "deadline", "마감")
    private val pool = listOf(apple, banana, car, known, word(5, "budget", "예산"), word(6, "memo", "메모"))

    private fun NewWordSession.answerQuestion(correct: Boolean) {
        val c = choices!!
        answer(if (correct) c.answerIndex else (c.answerIndex + 1) % c.options.size)
        next()
    }

    @Test
    fun runsTheWholeFlowAndScoresEachStage() {
        val s = NewWordSession(listOf(apple, known, banana, car), pool, Random(7))
        assertEquals(NewWordSession.Stage.CARDS, s.stage)
        s.nextCard() // apple
        assertEquals(known, s.removeCurrentCard()) // "이미 앎"
        s.nextCard() // banana
        s.nextCard() // car -> practice
        assertEquals(NewWordSession.Stage.PRACTICE, s.stage)
        assertEquals(listOf(apple, banana, car), s.words)

        // Practice: banana wrong once.
        var bananaMissed = false
        while (s.stage == NewWordSession.Stage.PRACTICE) {
            val w = s.practice!!.current!!
            val right = w != banana || bananaMissed
            if (w == banana) bananaMissed = true
            s.answerPractice(right)
        }
        assertEquals(NewWordSession.Stage.PRACTICE_DONE, s.stage)

        // Extra practice with everything wrong once does not change the score.
        s.practiceAgain()
        val missedAgain = mutableSetOf<Long>()
        while (s.stage == NewWordSession.Stage.PRACTICE) {
            val w = s.practice!!.current!!
            s.answerPractice(!missedAgain.add(w.id))
        }

        s.startQuiz()
        assertEquals(4, s.choices!!.options.size)
        // Quiz: car wrong twice.
        var carMisses = 0
        while (s.stage == NewWordSession.Stage.QUIZ) {
            val w = s.currentQuestion()!!
            val right = w != car || carMisses == 2
            if (!right) carMisses++
            s.answerQuestion(right)
        }
        // Cloze only for words that have one: apple and banana. Apple wrong once.
        assertEquals(NewWordSession.Stage.CLOZE, s.stage)
        assertEquals(2, s.cloze!!.remainingThisRound)
        var appleMissed = false
        while (s.stage == NewWordSession.Stage.CLOZE) {
            val w = s.currentQuestion()!!
            val right = w != apple || appleMissed
            if (w == apple) appleMissed = true
            s.answerQuestion(right)
        }
        assertEquals(NewWordSession.Stage.SUMMARY, s.stage)

        val r = s.results().associateBy { it.word.headword }
        assertEquals(listOf(0, 0, 1), r.getValue("apple").let { listOf(it.practice, it.quiz, it.cloze) })
        assertEquals(1, r.getValue("banana").total)
        assertEquals(2, r.getValue("car").total)
        assertEquals(Rating.GOOD, r.getValue("apple").rating)
        assertEquals(Rating.HARD, r.getValue("car").rating)
    }

    @Test
    fun clozeOptionsAreWordsAndIncludeTheAnswer() {
        val s = NewWordSession(listOf(apple), pool, Random(1))
        s.nextCard()
        s.passPractice()
        s.startQuiz()
        s.answerQuestion(true)
        assertEquals(NewWordSession.Stage.CLOZE, s.stage)
        val c = s.choices!!
        assertEquals("apple", c.options[c.answerIndex])
        assertTrue(c.options.all { o -> pool.any { it.headword == o } })
    }

    @Test
    fun feedbackStaysUntilNextAndIgnoresSecondTap() {
        val s = NewWordSession(listOf(apple, banana), pool, Random(2))
        s.nextCard(); s.nextCard(); s.passPractice(); s.startQuiz()
        val c = s.choices!!
        s.answer(c.answerIndex)
        s.answer((c.answerIndex + 1) % 4) // ignored
        assertTrue(s.answered!!.correct)
        s.next()
        assertNull(s.answered)
    }

    @Test
    fun removingEveryCardEndsTheSession() {
        val s = NewWordSession(listOf(apple), pool)
        s.removeCurrentCard()
        assertEquals(NewWordSession.Stage.SUMMARY, s.stage)
        assertTrue(s.results().isEmpty())
    }
}

class ReviewSessionTest {
    @Test
    fun onlyTheFirstTryIsRated() {
        val a = word(1, "apple", "사과")
        val b = word(2, "banana", "바나나")
        val s = ReviewSession(listOf(a, b), Random(3))
        var bMissed = false
        while (!s.loop.isDone) {
            val w = s.loop.current!!
            s.reveal()
            val right = w != b || bMissed
            if (w == b) bMissed = true
            s.answer(right)
        }
        assertEquals(mapOf(1L to Rating.GOOD, 2L to Rating.AGAIN), s.ratings())
    }
}
