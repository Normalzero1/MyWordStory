package kr.co.normalzero.mywordstory.study

import io.github.openspacedrepetition.Rating
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopSessionTest {
    @Test
    fun wrongItemsComeBackUntilAllAreRight() {
        val s = LoopSession(listOf("apple", "banana", "car"), Random(1))
        // Round 1: banana and car wrong.
        repeat(3) { s.answer(correct = s.current == "apple") }
        assertEquals(2, s.round)
        assertEquals(setOf("banana", "car"), List(s.remainingThisRound) { s.current!!.also { s.answer(it == "car") } }.toSet())
        // Round 3: only banana is left; answer it right.
        assertEquals(3, s.round)
        assertEquals("banana", s.current)
        s.answer(true)
        assertTrue(s.isDone)
        assertEquals(0, s.score("apple"))
        assertEquals(1, s.score("car"))
        assertEquals(2, s.score("banana"))
        assertTrue(s.wasRightFirstTime("apple"))
        assertFalse(s.wasRightFirstTime("car"))
    }

    @Test
    fun comingBackCountsWrongAnswersOfTheCurrentRound() {
        val s = LoopSession(listOf(1, 2, 3), Random(2))
        s.answer(false)
        assertEquals(1, s.comingBackCount)
        assertEquals(2, s.remainingThisRound)
    }
}

class GradingTest {
    @Test
    fun scoreBandsFollowTheDesign() {
        // Example from Q43: apple 1, banana 5, car 3.
        assertEquals(Rating.GOOD, Grading.newWordRating(0))
        assertEquals(Rating.GOOD, Grading.newWordRating(1))
        assertEquals(Rating.HARD, Grading.newWordRating(3))
        assertEquals(Rating.AGAIN, Grading.newWordRating(5))
        assertEquals(Rating.GOOD, Grading.reviewRating(true))
        assertEquals(Rating.AGAIN, Grading.reviewRating(false))
    }

    @Test
    fun newCardsAreScheduledDaysAheadNotMinutes() {
        val srs = Srs()
        val now = Instant.parse("2026-11-18T12:00:00Z")
        fun daysUntilDue(rating: Rating) = Duration.between(now, srs.review(srs.newCard(), rating, now).due).toDays()
        val again = daysUntilDue(Rating.AGAIN)
        val hard = daysUntilDue(Rating.HARD)
        val good = daysUntilDue(Rating.GOOD)
        assertTrue("again=$again", again >= 1)
        assertTrue("hard=$hard again=$again", hard >= again)
        assertTrue("good=$good hard=$hard", good > hard)
    }

    @Test
    fun cardStateSurvivesJson() {
        val srs = Srs()
        val card = srs.review(srs.newCard(), Rating.GOOD, Instant.parse("2026-11-18T12:00:00Z"))
        assertEquals(card.due, io.github.openspacedrepetition.Card.fromJson(card.toJson()).due)
    }

    @Test
    fun backlogReducesNewWords() {
        assertEquals(24, DailyPlan.newWordCount(cap = 30, dueReviews = 42, autoReduce = true))
        assertEquals(30, DailyPlan.newWordCount(cap = 30, dueReviews = 20, autoReduce = true))
        assertEquals(30, DailyPlan.newWordCount(cap = 30, dueReviews = 42, autoReduce = false))
        assertEquals(0, DailyPlan.newWordCount(cap = 30, dueReviews = 200, autoReduce = true))
    }
}

class ChoicesTest {
    @Test
    fun makesFourDistinctOptionsWithTheAnswer() {
        val pool = listOf("여유" to "noun", "마감" to "noun", "승인하다" to "verb", "마감" to "noun", "대략적인" to "adjective", "결과물" to "noun")
        val c = ChoiceMaker.make("결과물", "noun", pool, Random(3))
        assertEquals(4, c.options.size)
        assertEquals(4, c.options.toSet().size)
        assertEquals("결과물", c.options[c.answerIndex])
        // Same part of speech first: the three wrong options are the other nouns.
        assertTrue(c.options.containsAll(listOf("여유", "마감")))
    }

    @Test
    fun startLevelIsFirstLevelOverThirtyPercentUnknown() {
        val known = { n: Int -> List(10) { it >= n } } // n unknown out of 10
        assertEquals(Cefr.B1, LevelTest.startLevel(mapOf(Cefr.A1 to known(0), Cefr.A2 to known(3), Cefr.B1 to known(4), Cefr.B2 to known(8))))
        assertEquals(Cefr.C2, LevelTest.startLevel(mapOf(Cefr.A1 to known(1))))
    }
}
