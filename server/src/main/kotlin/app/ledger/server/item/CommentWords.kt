package app.ledger.server.item

import java.util.regex.Pattern

/** Mirrored in the browser's comment field, which counts the same way as you type. */
const val MAX_COMMENT_WORDS = 100

/**
 * `UNICODE_CHARACTER_CLASS` is what makes this agree with the browser. Java's bare `\s` is
 * ASCII-only, while JavaScript's is Unicode-aware, so without the flag an ideographic space
 * (U+3000 — what a Chinese keyboard produces) is not a word gap here and is one there. The counter
 * would then go red at 100 words on a comment this would have taken at 400, which is refusing text
 * the API accepts, in the very locale the two-sided limit exists for.
 */
private val WHITESPACE: Regex = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS).toRegex()

/**
 * How many words a comment is, for the limit the person writing it is shown.
 *
 * A deliberate port of `web/src/lib/words.ts`, and the reason it is a named function rather than
 * four lines inside the validator: the frontend counts the same text while it is being typed, so
 * the two have to reach the same number or the live counter contradicts the refusal. Both are
 * pinned to `server/src/test/resources/comment-word-vectors.json` — see `CommentWordVectorsTest`.
 *
 * The three steps are partly redundant on their own and stay that way on purpose: this matches the
 * TypeScript character for character, and the failure being guarded against is the two drifting
 * apart, not an inefficient count.
 */
fun countCommentWords(text: String): Int = text
    .trim()
    .split(WHITESPACE)
    .filter { it.isNotEmpty() }
    .size
