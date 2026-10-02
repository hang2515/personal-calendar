package com.hang2515.personalcalendar.backup

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudAuthTest {
    @Test fun loginRequestOpensAuthorizationWithPromptScopesAndPkce() {
        val request = CloudAuth(RuntimeEnvironment.getApplication())
            .loginRequest("https://example.auth.example.com/", "test-mobile-client")
        val uri = request.toUri()
        assertEquals("https://example.auth.example.com/oauth2/authorize", uri.buildUpon().clearQuery().build().toString())
        assertEquals("login", uri.getQueryParameter("prompt"))
        assertEquals("code", uri.getQueryParameter("response_type"))
        assertEquals("com.hang2515.personalcalendar://oauth/callback", uri.getQueryParameter("redirect_uri"))
        assertEquals(setOf("openid", "email", "personal-calendar/backup"), uri.getQueryParameter("scope")!!.split(" ").toSet())
        assertEquals("S256", uri.getQueryParameter("code_challenge_method"))
        assertFalse(uri.getQueryParameter("code_challenge").isNullOrBlank())
        assertFalse(uri.getQueryParameter("state").isNullOrBlank())
        assertFalse(uri.getQueryParameter("nonce").isNullOrBlank())
        assertNull(uri.getQueryParameter("client_secret"))
    }
}
