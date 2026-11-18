package kr.co.normalzero.mywordstory.study

import kotlin.random.Random

/** Four shuffled options: the answer plus three distinct wrong ones, preferring the same part of speech. */
data class Choices(val options: List<String>, val answerIndex: Int)

object ChoiceMaker {
    /**
     * [answer] is the right option; [pool] holds (option, partOfSpeech) pairs taken from other words.
     * Returns fewer than four options only when the pool has too few distinct entries.
     */
    fun make(answer: String, partOfSpeech: String, pool: List<Pair<String, String>>, random: Random = Random.Default): Choices {
        val candidates = pool.filter { !it.first.equals(answer, ignoreCase = true) }.distinctBy { it.first.lowercase() }
        val (samePos, otherPos) = candidates.partition { it.second == partOfSpeech }
        val wrong = (samePos.shuffled(random) + otherPos.shuffled(random)).take(3).map { it.first }
        val options = (wrong + answer).shuffled(random)
        return Choices(options, options.indexOf(answer))
    }
}

enum class Cefr { A1, A2, B1, B2, C1, C2 }

object LevelTest {
    /** First level, from A1 up, where more than 30% of the tested words were unknown; C2 if none. */
    fun startLevel(answers: Map<Cefr, List<Boolean>>): Cefr =
        Cefr.entries.firstOrNull { level ->
            val known = answers[level].orEmpty()
            known.isNotEmpty() && known.count { !it } * 10 > known.size * 3
        } ?: Cefr.C2
}
