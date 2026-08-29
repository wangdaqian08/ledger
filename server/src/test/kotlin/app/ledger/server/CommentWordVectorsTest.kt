package app.ledger.server

import app.ledger.server.item.countCommentWords
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The contract between this server's word count and the browser's copy of it.
 *
 * The comment field shows "12/100 words" while you type, so the frontend has to reach the same
 * number this does before anything is sent. If the two disagree the counter goes red on text the
 * API would have taken, or green on text it refuses — one implementation of one rule, written
 * twice, which is the same shape of problem as the split and gets the same answer.
 *
 * `comment-word-vectors.json` is how they are kept honest: this test checks the Kotlin against it,
 * `web/tests/words.spec.ts` reads the same file and checks the TypeScript. Neither can drift
 * without a red test.
 *
 * The cases are hand-written, not generated, because the ones that matter are the characters the
 * two languages disagree about by default. U+3000 above all — the space a Chinese keyboard
 * produces, which Java's `\s` ignores unless asked and JavaScript's does not, in the very locale
 * the word limit's character backstop exists for. They are written as `\u` escapes so the file can
 * be read: a literal ideographic space is indistinguishable from a plain one in a diff.
 */
class CommentWordVectorsTest {
    @Test
    fun `the committed vectors are what this server counts`() {
        val vectors = ObjectMapper().readTree(File(VECTORS_PATH))

        // Guards the guard: an unreadable or truncated file would make every case pass vacuously.
        assertTrue(vectors.size() >= 12, "expected the full vector set, found ${vectors.size()}")
        for (vector in vectors) {
            val text = vector["text"].asText()
            assertEquals(
                vector["words"].asInt(),
                countCommentWords(text),
                "vector ${ObjectMapper().writeValueAsString(text)}",
            )
        }
    }

    private companion object {
        const val VECTORS_PATH = "src/test/resources/comment-word-vectors.json"
    }
}
