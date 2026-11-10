package com.example.personalledger.data.ai.provider

import com.example.personalledger.domain.ai.AiError
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal object AiHttpSupport {
    private const val MAX_TRANSIENT_RETRIES = 2

    suspend fun executeWithLimitedRetry(
        client: HttpClient,
        method: HttpMethod,
        url: String,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt <= MAX_TRANSIENT_RETRIES) {
            try {
                val response = client.request(url) {
                    this.method = method
                    block()
                }
                if (shouldRetryStatus(response.status) && attempt < MAX_TRANSIENT_RETRIES) {
                    attempt++
                    delay(400L * attempt)
                    continue
                }
                return response
            } catch (cancelled: CancellationException) {
                throw AiError.Cancelled()
            } catch (_: HttpRequestTimeoutException) {
                throw AiError.Timeout()
            } catch (_: ConnectTimeoutException) {
                throw AiError.Timeout()
            } catch (error: Throwable) {
                if (error is AiError) throw error
                lastError = error
                if (attempt >= MAX_TRANSIENT_RETRIES) {
                    throw AiError.Network(cause = error)
                }
                attempt++
                delay(400L * attempt)
            }
        }
        throw AiError.Network(cause = lastError)
    }

    suspend fun readSafeErrorMessage(response: HttpResponse): String {
        // Consume body without exposing it to UI/logs.
        runCatching { response.bodyAsText() }
        return when (response.status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                "احراز هویت ناموفق بود. کلید API را بررسی کنید."
            HttpStatusCode.TooManyRequests ->
                "محدودیت نرخ درخواست. کمی بعد دوباره تلاش کنید."
            HttpStatusCode.NotFound ->
                "مدل یا سرویس پیدا نشد. تنظیمات را بررسی کنید."
            HttpStatusCode.BadRequest ->
                "درخواست نامعتبر بود. مدل انتخاب‌شده را بررسی کنید."
            HttpStatusCode.PaymentRequired ->
                "سهمیه یا اعتبار حساب ارائه‌دهنده کافی نیست."
            else ->
                "پاسخ ناموفق از ارائه‌دهنده (کد ${response.status.value})."
        }
    }

    fun mapHttpFailure(response: HttpResponse, safeMessage: String): AiError =
        when (response.status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                AiError.Authentication(safeMessage)
            HttpStatusCode.TooManyRequests ->
                AiError.RateLimited(safeMessage)
            HttpStatusCode.PaymentRequired ->
                AiError.QuotaExceeded(safeMessage)
            HttpStatusCode.NotFound ->
                AiError.InvalidModel(safeMessage)
            else -> AiError.Provider(safeMessage)
        }

    private fun shouldRetryStatus(status: HttpStatusCode): Boolean =
        status == HttpStatusCode.TooManyRequests ||
            status == HttpStatusCode.BadGateway ||
            status == HttpStatusCode.ServiceUnavailable ||
            status == HttpStatusCode.GatewayTimeout
}

internal suspend fun HttpClient.postJson(
    url: String,
    configure: HttpRequestBuilder.() -> Unit,
): HttpResponse =
    AiHttpSupport.executeWithLimitedRetry(this, HttpMethod.Post, url, configure)

internal suspend fun HttpClient.getJson(
    url: String,
    configure: HttpRequestBuilder.() -> Unit,
): HttpResponse =
    AiHttpSupport.executeWithLimitedRetry(this, HttpMethod.Get, url, configure)

internal fun HttpRequestBuilder.jsonBody(body: Any) {
    setBody(body)
}
