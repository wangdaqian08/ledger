package app.ledger.server.identity

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** No Spring, no database — this is a pure function with a class around it. */
class MockIdentityProviderTest {
    private val provider = MockIdentityProvider()

    @Test
    fun `the same name is always the same person`() {
        // If this ever stops holding, dev sprouts a fresh Bob on every sign-in and no trip survives
        // a restart of the browser.
        assertEquals(provider.verify("Bob").subject, provider.verify("Bob").subject)
    }

    @Test
    fun `name matching ignores case and surrounding space`() {
        assertEquals(provider.verify("Bob").subject, provider.verify("  bob ").subject)
    }

    @Test
    fun `the display name keeps the capitalisation that was typed`() {
        assertEquals("Bob", provider.verify("  Bob ").displayName)
    }

    @Test
    fun `a name with spaces produces a usable email`() {
        assertEquals("mary.anne@ledger.test", provider.verify("Mary Anne").email)
    }

    @Test
    fun `an empty name is not an identity`() {
        assertFailsWith<InvalidIdentityToken> { provider.verify("   ") }
    }
}
