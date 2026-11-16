package mywordstory

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

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
                """.trimIndent()
            )
        }
    }

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

    fun insert(headword: String, source: String, content: WordContent): Word = connect { conn ->
        conn.prepareStatement(
            "INSERT INTO words (headword, source, content) VALUES (?, ?, ?::jsonb) RETURNING id"
        ).use { st ->
            st.setString(1, headword)
            st.setString(2, source)
            st.setString(3, mapper.writeValueAsString(content))
            st.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
        }
    }.let { find(it)!! }

    private fun ResultSet.toWord() = Word(
        id = getLong("id"),
        headword = getString("headword"),
        source = getString("source"),
        content = mapper.readValue(getString("content"), WordContent::class.java),
        createdAt = getString("created_at"),
    )

    private fun <T> connect(block: (Connection) -> T): T =
        DriverManager.getConnection(url, user, password).use(block)

    private companion object {
        const val SELECT = "SELECT id, headword, source, content::text AS content, " +
            "to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') AS created_at FROM words"
    }
}
