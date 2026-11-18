package kr.co.normalzero.mywordstory.study

import io.github.openspacedrepetition.Card
import io.github.openspacedrepetition.Rating
import io.github.openspacedrepetition.Scheduler
import java.time.Duration
import java.time.Instant
import kotlin.math.max

/** Turns study results into FSRS ratings (docs/DESIGN.md, Q43 and Q44). */
object Grading {
    /** Sum of the practice, multiple-choice and cloze scores. New words never get EASY. */
    fun newWordRating(totalScore: Int): Rating = when {
        totalScore <= 1 -> Rating.GOOD
        totalScore <= 3 -> Rating.HARD
        else -> Rating.AGAIN
    }

    /** Only the first try of a review counts; retries after a miss are not graded. */
    fun reviewRating(rightFirstTime: Boolean): Rating = if (rightFirstTime) Rating.GOOD else Rating.AGAIN
}

/** FSRS without minute-level steps: after a day's study the next review is whole days away. */
class Srs(
    private val scheduler: Scheduler = Scheduler.builder()
        .learningSteps(arrayOf<Duration>())
        .relearningSteps(arrayOf<Duration>())
        .build(),
) {
    fun newCard(): Card = Card.builder().build()

    fun review(card: Card, rating: Rating, now: Instant): Card = scheduler.reviewCard(card, rating, now).card()
}

object DailyPlan {
    /**
     * New words for today. With [autoReduce], each 2 due reviews above [cap] take one new word away
     * (cap 30 and 42 due reviews gives 24).
     */
    fun newWordCount(cap: Int, dueReviews: Int, autoReduce: Boolean): Int =
        if (!autoReduce) cap else max(0, cap - max(0, dueReviews - cap) / 2)
}
