package mywordstory

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/** One row of the candidate word list (resources/wordlists/candidates.csv). */
data class Candidate(val headword: String, val sources: String, val rank: Int)

class WordRepository(
    private val url: String,
    private val user: String,
    private val password: String,
) {
    private val mapper = ObjectMapper()

    fun migrate() = connect { conn ->
        conn.createStatement().use {
            it.execute(
                """
                CREATE TABLE IF NOT EXISTS words (
                    id BIGSERIAL PRIMARY KEY,
                    headword TEXT NOT NULL,
                    source TEXT NOT NULL,
                    content JSONB NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
                );
                CREATE UNIQUE INDEX IF NOT EXISTS words_headword_key ON words (lower(headword));
                ALTER TABLE words ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'new';
                CREATE TABLE IF NOT EXISTS candidates (
                    key TEXT PRIMARY KEY,
                    headword TEXT NOT NULL,
                    sources TEXT NOT NULL,
                    rank INT NOT NULL,
                    cefr TEXT,
                    status TEXT NOT NULL DEFAULT 'new'
                );
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
                """.trimIndent()
            )
        }
    }

    // --- words ---

    fun findByHeadword(headword: String): Word? = connect { conn ->
        conn.prepareStatement("$SELECT WHERE lower(headword) = lower(?)").use { st ->
            st.setString(1, headword)
            st.executeQuery().use { rs -> if (rs.next()) rs.toWord() else null }
        }
    }

    fun find(id: Long): Word? = connect { conn ->
        conn.prepareStatement("$SELECT WHERE id = ?").use { st ->
            st.setLong(1, id)
            st.executeQuery().use { rs -> if (rs.next()) rs.toWord() else null }
        }
    }

    fun list(): List<Word> = connect { conn ->
        conn.prepareStatement("$SELECT ORDER BY id DESC").use { st ->
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toWord()) } }
        }
    }

    /** Words not studied yet: the user's own first, then oldest first. */
    fun newWords(limit: Int): List<Word> = connect { conn ->
        conn.prepareStatement("$SELECT WHERE status = 'new' ORDER BY (source = 'manual') DESC, id LIMIT ?").use { st ->
            st.setInt(1, limit)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toWord()) } }
        }
    }

    fun countNewWords(source: String): Int = connect { conn ->
        conn.prepareStatement("SELECT count(*) FROM words WHERE status = 'new' AND source = ?").use { st ->
            st.setString(1, source)
            st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
    }

    fun setStatus(id: Long, status: String): Word? {
        connect { conn ->
            conn.prepareStatement("UPDATE words SET status = ? WHERE id = ?").use { st ->
                st.setString(1, status)
                st.setLong(2, id)
                st.executeUpdate()
            }
        }
        return find(id)
    }

    fun insert(headword: String, source: String, content: WordContent): Word =
        insertIfAbsent(headword, source, content) ?: error("단어가 이미 있습니다: $headword")

    /** Returns null when a word with the same headword already exists. */
    fun insertIfAbsent(headword: String, source: String, content: WordContent): Word? = connect { conn ->
        conn.prepareStatement(
            "INSERT INTO words (headword, source, content) VALUES (?, ?, ?::jsonb) " +
                "ON CONFLICT (lower(headword)) DO NOTHING RETURNING id"
        ).use { st ->
            st.setString(1, headword)
            st.setString(2, source)
            st.setString(3, mapper.writeValueAsString(content))
            st.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
        }
    }?.let { find(it) }

    // --- candidates ---

    fun candidateCount(): Int = connect { conn ->
        conn.createStatement().use { it.executeQuery("SELECT count(*) FROM candidates").use { rs -> rs.next(); rs.getInt(1) } }
    }

    fun importCandidates(rows: List<Candidate>) = connect { conn ->
        conn.prepareStatement(
            "INSERT INTO candidates (key, headword, sources, rank) VALUES (lower(?), ?, ?, ?) ON CONFLICT (key) DO NOTHING"
        ).use { st ->
            for (c in rows) {
                st.setString(1, c.headword)
                st.setString(2, c.headword)
                st.setString(3, c.sources)
                st.setInt(4, c.rank)
                st.addBatch()
            }
            st.executeBatch()
        }
    }

    fun candidatesWithoutLevel(limit: Int): List<String> = connect { conn ->
        conn.prepareStatement("SELECT headword FROM candidates WHERE cefr IS NULL ORDER BY rank, key LIMIT ?").use { st ->
            st.setInt(1, limit)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    fun setLevels(levels: Map<String, String>) = connect { conn ->
        conn.prepareStatement("UPDATE candidates SET cefr = ? WHERE key = lower(?)").use { st ->
            for ((word, level) in levels) {
                st.setString(1, level)
                st.setString(2, word)
                st.addBatch()
            }
            st.executeBatch()
        }
    }

    fun levelCounts(): Map<String, Int> = connect { conn ->
        conn.createStatement().use {
            it.executeQuery("SELECT coalesce(cefr, 'none'), count(*) FROM candidates GROUP BY 1").use { rs ->
                buildMap { while (rs.next()) put(rs.getString(1), rs.getInt(2)) }
            }
        }
    }

    /** Random candidates of one level that the user has not met yet. */
    fun sampleCandidates(level: String, limit: Int): List<String> = connect { conn ->
        conn.prepareStatement(
            "SELECT c.headword FROM candidates c WHERE c.cefr = ? AND c.status = 'new' " +
                "AND NOT EXISTS (SELECT 1 FROM words w WHERE lower(w.headword) = c.key) ORDER BY random() LIMIT ?"
        ).use { st ->
            st.setString(1, level)
            st.setInt(2, limit)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    fun markCandidates(words: List<String>, status: String) = connect { conn ->
        conn.prepareStatement("UPDATE candidates SET status = ? WHERE key = lower(?)").use { st ->
            for (w in words) {
                st.setString(1, status)
                st.setString(2, w)
                st.addBatch()
            }
            st.executeBatch()
        }
    }

    /**
     * Candidates to turn into cards next: ones marked unknown in the level test first, then
     * candidates at or above [startLevel], easier levels first. Skips known words and words
     * that already have a card.
     */
    fun nextCandidates(startLevel: String, limit: Int): List<String> = connect { conn ->
        conn.prepareStatement(
            """
            SELECT c.headword FROM candidates c
            WHERE c.status <> 'known' AND c.cefr IS NOT NULL
              AND (c.status = 'priority' OR array_position(?::text[], c.cefr) >= array_position(?::text[], ?))
              AND NOT EXISTS (SELECT 1 FROM words w WHERE lower(w.headword) = c.key)
            ORDER BY (c.status = 'priority') DESC, array_position(?::text[], c.cefr), c.rank
            LIMIT ?
            """.trimIndent()
        ).use { st ->
            val levels = conn.createArrayOf("text", CEFR_LEVELS.toTypedArray())
            st.setArray(1, levels)
            st.setArray(2, levels)
            st.setString(3, startLevel)
            st.setArray(4, levels)
            st.setInt(5, limit)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    // --- settings ---

    fun getSetting(key: String): String? = connect { conn ->
        conn.prepareStatement("SELECT value FROM settings WHERE key = ?").use { st ->
            st.setString(1, key)
            st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    fun setSetting(key: String, value: String) = connect { conn ->
        conn.prepareStatement(
            "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value"
        ).use { st ->
            st.setString(1, key)
            st.setString(2, value)
            st.executeUpdate()
        }
    }

    private fun ResultSet.toWord() = Word(
        id = getLong("id"),
        headword = getString("headword"),
        source = getString("source"),
        content = mapper.readValue(getString("content"), WordContent::class.java),
        createdAt = getString("created_at"),
        status = getString("status"),
    )

    private fun <T> connect(block: (Connection) -> T): T =
        DriverManager.getConnection(url, user, password).use(block)

    private companion object {
        const val SELECT = "SELECT id, headword, source, status, content::text AS content, " +
            "to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') AS created_at FROM words"
    }
}
