package kr.co.normalzero.mywordstory

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import io.github.openspacedrepetition.Card
import io.github.openspacedrepetition.Rating
import java.time.Instant
import kotlinx.serialization.json.Json
import kr.co.normalzero.mywordstory.study.Srs

/** A word the user has studied: its card content and FSRS state. */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val wordId: Long,
    val wordJson: String,
    val srsJson: String,
    /** Next review, epoch millis. */
    val due: Long,
)

/** Every rating given, kept for syncing between devices later (stage 3). */
@Entity(tableName = "review_logs")
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    val rating: Int,
    val reviewedAt: Long,
    /** "new" or "review". */
    val kind: String,
)

@Dao
interface StudyDao {
    @Upsert
    suspend fun upsert(card: CardEntity)

    @Insert
    suspend fun log(entry: ReviewLogEntity)

    @Query("SELECT * FROM cards WHERE due <= :now ORDER BY due")
    suspend fun due(now: Long): List<CardEntity>

    @Query("SELECT count(*) FROM cards WHERE due <= :now")
    suspend fun dueCount(now: Long): Int

    @Query("SELECT * FROM cards")
    suspend fun all(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE wordId = :wordId")
    suspend fun find(wordId: Long): CardEntity?
}

@Database(entities = [CardEntity::class, ReviewLogEntity::class], version = 1, exportSchema = false)
abstract class StudyDb : RoomDatabase() {
    abstract fun dao(): StudyDao

    companion object {
        fun open(context: Context): StudyDb = Room.databaseBuilder(context, StudyDb::class.java, "study.db").build()
    }
}

class StudyStore(private val dao: StudyDao, private val srs: Srs = Srs()) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun dueCount(now: Instant): Int = dao.dueCount(now.toEpochMilli())

    suspend fun dueWords(now: Instant): List<Word> = dao.due(now.toEpochMilli()).map { decode(it) }

    suspend fun studiedWords(): List<Word> = dao.all().map { decode(it) }

    /** Next review time for every studied word. */
    suspend fun dueTimes(): Map<Long, Instant> = dao.all().associate { it.wordId to Instant.ofEpochMilli(it.due) }

    suspend fun saveNewWord(word: Word, rating: Rating, now: Instant) = save(word, srs.newCard(), rating, now, "new")

    suspend fun saveReview(word: Word, rating: Rating, now: Instant) {
        val existing = dao.find(word.id) ?: return
        save(word, Card.fromJson(existing.srsJson), rating, now, "review")
    }

    private suspend fun save(word: Word, card: Card, rating: Rating, now: Instant, kind: String) {
        val next = srs.review(card, rating, now)
        dao.upsert(CardEntity(word.id, json.encodeToString(Word.serializer(), word), next.toJson(), next.due.toEpochMilli()))
        dao.log(ReviewLogEntity(wordId = word.id, rating = rating.value, reviewedAt = now.toEpochMilli(), kind = kind))
    }

    private fun decode(entity: CardEntity): Word = json.decodeFromString(Word.serializer(), entity.wordJson)
}
