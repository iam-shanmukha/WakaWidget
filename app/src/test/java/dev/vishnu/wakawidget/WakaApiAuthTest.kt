package dev.vishnu.wakawidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakaApiAuthTest {

    @Test
    fun `api key uses basic auth with base64 of the raw key`() {
        // base64("waka_abc") = d2FrYV9hYmM=
        assertEquals("Basic d2FrYV9hYmM=", WakaApi.authorizationHeader("waka_abc"))
    }

    @Test
    fun `oauth token uses bearer auth`() {
        assertEquals("Bearer waka_tok_123", WakaApi.authorizationHeader("waka_tok_123"))
    }

    @Test
    fun `only waka_tok_ prefix counts as an oauth token`() {
        assertTrue(WakaApi.isOAuthToken("waka_tok_123"))
        assertFalse(WakaApi.isOAuthToken("waka_123"))
        assertFalse(WakaApi.isOAuthToken("ca415f6d-5f3e-4e3d-b3fa-c665d6fd5e1b"))
    }

    @Test
    fun `formatDuration shows hours and minutes`() {
        assertEquals("0m", formatDuration(59.0))
        assertEquals("45m", formatDuration(45 * 60.0))
        assertEquals("3h 12m", formatDuration(3 * 3600.0 + 12 * 60))
    }
}
