package com.hang2515.personalcalendar.backup

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import android.content.Intent
import android.content.IntentFilter
import android.content.ActivityNotFoundException
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.content.pm.Signature
import android.net.Uri
import net.openid.appauth.AuthorizationManagementActivity
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudAuthTest {
    @Test fun installedStandardBrowserReceivesAuthorizationRequestWithoutCustomTabs() {
        val context = RuntimeEnvironment.getApplication()
        val browserPackage = "com.example.testbrowser"
        val application = ApplicationInfo().apply { packageName = browserPackage; enabled = true }
        val activityInfo = ActivityInfo().apply {
            packageName = browserPackage; name = "$browserPackage.BrowserActivity"
            applicationInfo = application; exported = true; enabled = true
        }
        val info = PackageInfo().apply {
            packageName = browserPackage; versionName = "1.0"; applicationInfo = application
            activities = arrayOf(activityInfo); signatures = arrayOf(Signature(byteArrayOf(1, 2, 3)))
        }
        val packages = shadowOf(context.packageManager)
        packages.installPackage(info)
        val query = Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_BROWSABLE).setData(Uri.fromParts("http", "", null))
        packages.addResolveInfoForIntent(query, ResolveInfo().apply {
            this.activityInfo = activityInfo
            filter = IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_BROWSABLE); addDataScheme("http"); addDataScheme("https")
            }
        })
        val intent = CloudAuth(context).loginIntent("https://auth.example.com", "test-client")
        Robolectric.buildActivity(AuthorizationManagementActivity::class.java, intent).use { controller ->
            val activity = controller.setup().get()
            val browserIntent = shadowOf(activity).nextStartedActivity
            assertEquals(browserPackage, browserIntent.`package`)
            assertEquals(Intent.ACTION_VIEW, browserIntent.action)
            assertEquals("login", browserIntent.data!!.getQueryParameter("prompt"))
            assertEquals("S256", browserIntent.data!!.getQueryParameter("code_challenge_method"))
        }
    }

    @Test fun missingBrowserReportsActivityNotFoundInsteadOfInvalidAuthorizationParameters() {
        assertThrows(ActivityNotFoundException::class.java) {
            CloudAuth(RuntimeEnvironment.getApplication()).loginIntent("https://auth.example.com", "test-client")
        }
    }

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
