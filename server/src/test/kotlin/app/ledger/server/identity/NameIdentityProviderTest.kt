package app.ledger.server.identity

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** No Spring, no database — like the mock's test, a pure function with a class around it. */
class NameIdentityProviderTest {
    private val provider = NameIdentityProvider()

    @Test
    fun `the same name is always the same person`() {
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
        assertEquals("mary.anne@name.invalid", provider.verify("Mary Anne").email)
    }

    @Test
    fun `an empty name is not an identity`() {
        assertFailsWith<InvalidIdentityToken> { provider.verify("   ") }
    }

    @Test
    fun `the provider string is its own, never the mock's`() {
        // `users` is unique on (provider, subject): "name" keeps these identities distinct from
        // dev's mock rows and from the Google ones step 10 will mint.
        assertEquals("name", provider.verify("Bob").provider)
    }
}
