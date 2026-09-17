package com.docvault.app.data.net

import com.docvault.app.data.TokenStore
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Attaches the bearer token to every request that needs one.
 *
 * Register and login are skipped — the backend rejects a request carrying a
 * token it did not issue, and there is no token to send at that point anyway.
 */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        val isPublic = path.endsWith("/auth/login") ||
            path.endsWith("/auth/register") ||
            path.endsWith("/auth/password/forgot")
        val token = tokenStore.token

        if (isPublic || token == null) return chain.proceed(request)

        val response = chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        )

        // The token we sent was rejected: expired (they last 60 minutes) or
        // issued against a database that has since been reset. Clear it and
        // signal the UI, otherwise the user is stranded on a screen whose
        // every request fails. Only for authenticated calls — a 401 from
        // login means wrong credentials, not a dead session.
        if (response.code == 401) {
            tokenStore.onUnauthorized()
        }

        return response
    }
}

/**
 * Builds [DocVaultApi] instances for the currently configured backend URL.
 *
 * Retrofit bakes its base URL in at construction, but the URL is editable at
 * runtime on the sign-in screen. So the client is cached against the URL it
 * was built for and rebuilt when that changes — cheap, and it keeps the
 * "one APK for emulator and phone" behaviour.
 */
class ApiProvider(private val tokenStore: TokenStore) {

    private var cachedUrl: String? = null
    private var cachedApi: DocVaultApi? = null

    private val json = Json {
        ignoreUnknownKeys = true   // backend may add fields; do not crash on them
        explicitNulls = false
    }

    @Synchronized
    fun api(): DocVaultApi {
        val url = tokenStore.baseUrl
        cachedApi?.let { if (cachedUrl == url) return it }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    // Headers only: the body carries passwords and tokens, and
                    // logcat is not a safe place for either.
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            // Short timeouts: a wrong LAN address should fail fast and show the
            // user an error, not hang for 30 seconds looking like a freeze.
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val api = Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DocVaultApi::class.java)

        cachedUrl = url
        cachedApi = api
        return api
    }
}
