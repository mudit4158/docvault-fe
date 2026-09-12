package com.docvault.app.data

import com.docvault.app.data.net.ApiError
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/**
 * Outcome of a network call, with a message fit to show a user.
 */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val message: String, val status: Int? = null) : ApiResult<Nothing>
}

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * Runs a call and normalises every failure mode into [ApiResult.Err].
 *
 * The backend puts a human-readable reason in `detail` and leans on status
 * codes that carry product meaning — 409 for "you are the only admin", 403 for
 * "members cannot do that". Surfacing `detail` verbatim means the rules stay
 * defined in one place (the server) rather than being re-implemented here.
 */
suspend fun <T> apiCall(block: suspend () -> Response<T>): ApiResult<T> = try {
    val response = block()
    if (response.isSuccessful) {
        @Suppress("UNCHECKED_CAST")
        ApiResult.Ok((response.body() ?: Unit) as T)
    } else {
        ApiResult.Err(response.errorMessage(), response.code())
    }
} catch (e: IOException) {
    // No route to host, connection refused, DNS failure, timeout.
    ApiResult.Err(
        "Cannot reach the server. Check it is running and that the address on " +
            "the sign-in screen is correct.",
    )
} catch (e: Exception) {
    ApiResult.Err(e.message ?: "Something went wrong")
}

private fun <T> Response<T>.errorMessage(): String {
    val raw = runCatching { errorBody()?.string() }.getOrNull()

    val detail = raw?.let {
        runCatching { errorJson.decodeFromString<ApiError>(it).detail }.getOrNull()
    }
    if (detail != null) return detail

    // 422 bodies are FastAPI's validation envelope, whose `detail` is a list
    // rather than a string, so the decode above misses it.
    if (code() == 422) return "Please check the details you entered."

    return when (code()) {
        401 -> "Your session has expired. Please sign in again."
        403 -> "You do not have permission to do that."
        404 -> "Not found."
        409 -> "That conflicts with the current state."
        else -> "Request failed (${code()})."
    }
}
