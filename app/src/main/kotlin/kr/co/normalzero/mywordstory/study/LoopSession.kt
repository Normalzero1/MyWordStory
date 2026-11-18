package kr.co.normalzero.mywordstory.study

import kotlin.random.Random

/**
 * One stage of study (practice, multiple choice or cloze) over a set of items.
 * Items answered wrong come back, reshuffled, after the current round, until every item
 * has been answered right once. An item's score is how many times it was answered wrong:
 * right on the first try is 0, right on the second try is 1, and so on (docs/DESIGN.md, Q43).
 */
class LoopSession<T>(items: List<T>, private val random: Random = Random.Default) {
    private var queue = ArrayDeque(items.shuffled(random))
    private val comingBack = mutableListOf<T>()
    private val misses = mutableMapOf<T, Int>()

    var round = 1
        private set

    val current: T? get() = queue.firstOrNull()
    val remainingThisRound: Int get() = queue.size
    val comingBackCount: Int get() = comingBack.size
    val isDone: Boolean get() = queue.isEmpty() && comingBack.isEmpty()

    fun answer(correct: Boolean) {
        val item = queue.removeFirstOrNull() ?: error("No item to answer: the session is done")
        if (!correct) {
            misses[item] = (misses[item] ?: 0) + 1
            comingBack += item
        }
        if (queue.isEmpty() && comingBack.isNotEmpty()) {
            queue = ArrayDeque(comingBack.shuffled(random))
            comingBack.clear()
            round++
        }
    }

    fun score(item: T): Int = misses[item] ?: 0

    fun wasRightFirstTime(item: T): Boolean = score(item) == 0
}
