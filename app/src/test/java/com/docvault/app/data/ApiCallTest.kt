package com.docvault.app.data

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

/**
 * The 422 error-body parsing specifically — this is where a weak password's
 * real reason ("needs an uppercase letter") used to get lost, replaced by a
 * generic "please check the details you entered" that told the user nothing.
 */
class ApiCallTest {

    private fun errorResponse(code: Int, body: String): Response<Unit> =
        Response.error(code, body.toResponseBody("application/json".toMediaType()))

    @Test
    fun `surfaces the real reason from a 422 validation error`() = runBlocking {
        val body = """
            {"detail":[{"type":"value_error","loc":["body","password"],
            "msg":"Value error, Password must contain at least one uppercase letter, at least one special character",
            "input":"weakweak","ctx":{"error":{}}}]}
        """.trimIndent()

        val result = apiCall { errorResponse(422, body) }

        assertEquals(
            ApiResult.Err(
                "Password must contain at least one uppercase letter, at least one special character",
                422,
            ),
            result,
        )
    }

    @Test
    fun `joins multiple field errors on separate lines`() = runBlocking {
        val body = """
            {"detail":[
                {"msg":"Value error, Password must contain at least one number"},
                {"msg":"String should have at least 1 character"}
            ]}
        """.trimIndent()

        val result = apiCall { errorResponse(422, body) } as ApiResult.Err

        assertEquals(
            "Password must contain at least one number\nString should have at least 1 character",
            result.message,
        )
    }

    @Test
    fun `falls back to a generic message when a 422 body cannot be parsed`() = runBlocking {
        val result = apiCall { errorResponse(422, "not json at all") } as ApiResult.Err
        assertEquals("Please check the details you entered.", result.message)
    }

    @Test
    fun `surfaces a plain string detail unchanged for non-422 errors`() = runBlocking {
        val result = apiCall { errorResponse(409, """{"detail":"You are the only admin"}""") }
        assertEquals(ApiResult.Err("You are the only admin", 409), result)
    }
}
