package com.hang2515.personalcalendar.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.hang2515.personalcalendar.BuildConfig
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object CloudConfig {
    val isConfigured: Boolean get() = BuildConfig.CLOUD_API_URL.startsWith("https://") &&
        BuildConfig.CLOUD_AUTH_DOMAIN.startsWith("https://") && BuildConfig.CLOUD_CLIENT_ID.isNotBlank()
}

class CloudAuth(context: Context) {
    private val preferences = context.getSharedPreferences("cloud-auth", Context.MODE_PRIVATE)
    private val service = AuthorizationService(context.applicationContext)
    private var cached: AuthState? = null
    private var generation = 0L

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("calendar-cloud-auth", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("calendar-cloud-auth", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized private fun state(): AuthState {
        cached?.let { return it }
        val saved = preferences.getString("state", null)
        return if (saved == null) AuthState().also { cached = it } else {
            val envelope = JSONObject(saved)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)))
            AuthState.jsonDeserialize(String(cipher.doFinal(Base64.decode(envelope.getString("value"), Base64.NO_WRAP)), Charsets.UTF_8))
                .also { cached = it }
        }
    }

    @Synchronized private fun save(state: AuthState) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val envelope = JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("value", Base64.encodeToString(cipher.doFinal(state.jsonSerializeString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
        check(preferences.edit().putString("state", envelope.toString()).commit()) { "无法保存登录状态" }
        cached = state
    }

    fun isSignedIn(): Boolean = state().isAuthorized

    fun accountEmail(): String? {
        val current = state()
        if (!current.isAuthorized) return null
        val idToken = current.idToken ?: return null
        // 只显示已完成授权流程的身份资料，不把解析出的声明用于服务端鉴权。
        val payload = idToken.split('.').getOrNull(1) ?: return null
        val claims = JSONObject(String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8))
        return claims.optString("email").trim().takeIf { it.isNotEmpty() }
    }

    fun loginIntent(): Intent {
        check(CloudConfig.isConfigured) { "此安装包尚未配置云服务" }
        val domain = BuildConfig.CLOUD_AUTH_DOMAIN.trimEnd('/')
        val configuration = AuthorizationServiceConfiguration(Uri.parse("$domain/oauth2/authorize"), Uri.parse("$domain/oauth2/token"))
        val request = AuthorizationRequest.Builder(configuration, BuildConfig.CLOUD_CLIENT_ID, ResponseTypeValues.CODE,
            Uri.parse("com.hang2515.personalcalendar://oauth/callback"))
            .setScopes("openid", "email", "personal-calendar/backup")
            .setAdditionalParameters(mapOf("prompt" to "login"))
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    suspend fun finishLogin(intent: Intent) {
        val expectedGeneration = synchronized(this) { ++generation }
        val response = AuthorizationResponse.fromIntent(intent)
        val error = AuthorizationException.fromIntent(intent)
        require(response != null && error == null) { "登录未完成，请重试" }
        val state = AuthState(response, null)
        suspendCancellableCoroutine<Unit> { continuation ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { token, exception ->
                if (exception != null || token == null) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("无法完成登录，请重试", exception))
                } else if (continuation.isActive) {
                    try {
                        synchronized(this) {
                            check(generation == expectedGeneration) { "登录状态已变化，请重试" }
                            state.update(token, null); save(state)
                        }
                        continuation.resume(Unit)
                    }
                    catch (failure: Exception) { continuation.resumeWithException(failure) }
                }
            }
        }
    }

    suspend fun token(): String = suspendCancellableCoroutine { continuation ->
        val state = state()
        val expectedGeneration = synchronized(this) { generation }
        if (!state.isAuthorized) {
            continuation.resumeWithException(IllegalStateException("请先登录云备份账号"))
        } else state.performActionWithFreshTokens(service) { access, _, exception ->
            try {
                if (exception != null || access == null) throw IllegalStateException("登录已失效，请重新登录", exception)
                synchronized(this) {
                    check(generation == expectedGeneration) { "登录状态已变化，请重新登录" }
                    // 刷新令牌已轮换时，即使调用协程取消，也要持久化新令牌。
                    save(state)
                }
                if (continuation.isActive) continuation.resume(access)
            } catch (failure: Exception) {
                if (continuation.isActive) continuation.resumeWithException(failure)
                else android.util.Log.w("CloudAuth", "已取消请求的会话刷新未保存：${failure.javaClass.simpleName}")
            }
        }
    }

    fun account(token: String): String {
        // 仅用于本机绑定；服务端独立校验令牌签名、有效期、受众和 scope。
        val claims = JSONObject(String(Base64.decode(token.split('.')[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8))
        return claims.getString("sub").also { require(Regex("[A-Za-z0-9_-]{1,128}").matches(it)) }
    }

    @Synchronized fun clear() {
        generation++
        check(preferences.edit().clear().commit()) { "无法清除登录状态" }
        cached = AuthState()
    }
}
