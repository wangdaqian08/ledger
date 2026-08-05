package app.ledger.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The contract between this engine and the browser's copy of the same arithmetic.
 *
 * The SplitBar shows each person's amount live as it is dragged, which means the frontend has to
 * predict largest remainder — including which member absorbs each spare cent — before anything is
 * saved. That is a second implementation of the one rule this application cannot get wrong.
 *
 * `split-vectors.json` is how the two are kept honest. This test regenerates the expected output
 * from the engine and fails if the committed file disagrees; `web/tests/split.spec.ts` reads the
 * same file and fails if the TypeScript port disagrees. Neither side can drift without a red test,
 * and the file is readable enough to diff by eye when one does.
 *
 * Regenerate deliberately with `./gradlew :engine:test -Dledger.vectors.write=true`, and read the
 * diff before committing it — a change here means every existing item's rounding has moved.
 */
class SplitVectorsTest {
    @Test
    fun `the committed vectors are what this engine produces`() {
        val produced = CASES.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]") { case ->
            val members = List(case.weights.size) { MemberId("m$it") }
            val rule =
                if (case.equal) {
                    SplitRule.Equal
                } else {
                    SplitRule.Weighted(members.zip(case.weights).toMap())
                }
            val shares = shares(case.totalMinor, members, rule, case.salt)

            """  {"totalMinor": ${case.totalMinor}, "weights": ${case.weights.toJson()}, """ +
                """"salt": ${case.salt}, "equal": ${case.equal}, """ +
                """"shares": ${members.map { shares.getValue(it) }.toJson()}}"""
        } + "\n"

        val file = File(VECTORS_PATH)
        if (System.getProperty("ledger.vectors.write") == "true") {
            file.writeText(produced)
        }

        assertEquals(
            produced,
            file.readText(),
            "the committed split vectors no longer match this engine — if that is intended, " +
                "regenerate with -Dledger.vectors.write=true and check what moved",
        )
    }

    @Test
    fun `every vector still adds up to its own total`() {
        // The file is only worth trusting as a contract if it is itself correct.
        CASES.forEach { case ->
            val members = List(case.weights.size) { MemberId("m$it") }
            val rule =
                if (case.equal) SplitRule.Equal else SplitRule.Weighted(members.zip(case.weights).toMap())

            assertEquals(case.totalMinor, shares(case.totalMinor, members, rule, case.salt).values.sum())
        }
    }

    private data class Case(
        val totalMinor: Long,
        val weights: List<Int>,
        val salt: Long,
        val equal: Boolean = false,
    )

    private companion object {
        const val VECTORS_PATH = "src/test/resources/split-vectors.json"

        fun List<Number>.toJson() = joinToString(", ", "[", "]")

        /**
         * Chosen for the awkward cases, not for coverage of the easy ones: totals that do not
         * divide, salts that rotate the tie-break onto different people, a salt far larger than the
         * member count, a negative salt, and lopsided weights where one person's fraction dominates.
         */
        val CASES = buildList {
            // Equal splits that do not divide cleanly.
            add(Case(10_000, List(3) { 1 }, salt = 0, equal = true))
            add(Case(10_000, List(3) { 1 }, salt = 1, equal = true))
            add(Case(10_000, List(3) { 1 }, salt = 2, equal = true))
            add(Case(100_000, List(13) { 1 }, salt = 0, equal = true))
            add(Case(100_000, List(14) { 1 }, salt = 7, equal = true))
            add(Case(200_000, List(14) { 1 }, salt = 12_345_678_901L, equal = true))
            add(Case(100_000, List(13) { 1 }, salt = -5, equal = true))
            // The salt is a UUID's two halves XORed together, which can land anywhere in a Long —
            // including its minimum, the one value naive negation cannot survive.
            add(Case(100_000, List(7) { 1 }, salt = Long.MIN_VALUE, equal = true))
            add(Case(1, List(4) { 1 }, salt = 0, equal = true))
            add(Case(0, List(3) { 1 }, salt = 0, equal = true))

            // Weighted, including a zero weight who must never be handed a spare cent.
            add(Case(10_001, listOf(2, 1), salt = 0))
            add(Case(10_000, listOf(1, 1, 2), salt = 3))
            add(Case(9_999, listOf(5, 3, 2), salt = 1))
            add(Case(100, listOf(1, 0, 1), salt = 0))
            add(Case(7, listOf(1, 1, 1, 1, 1, 1, 1, 1), salt = 5))
            add(Case(123_456_789, listOf(7, 11, 13, 17), salt = 99))
        }
    }
}
