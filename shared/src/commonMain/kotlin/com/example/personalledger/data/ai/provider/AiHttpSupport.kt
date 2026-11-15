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
                    throw networkError(error)
                }
                attempt++
                delay(400L * attempt)
            }
        }
        throw networkError(lastError)
    }

    suspend fun readSafeErrorMessage(response: HttpResponse): String {
        val raw = runCatching { response.bodyAsText() }.getOrDefault("")
        val providerHint = extractProviderErrorHint(raw)
        return when (response.status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                "احراز هویت ناموفق بود. کلید یا دسترسی Jasper را بررسی کنید."
            HttpStatusCode.TooManyRequests ->
                "محدودیت نرخ درخواست. کمی بعد دوباره تلاش کنید."
            HttpStatusCode.NotFound ->
                "آدرس یا مدل Jasper پیدا نشد. مسیر chat و model id را بررسی کنید."
            HttpStatusCode.BadRequest ->
                providerHint ?: "درخواست نامعتبر بود. مدل انتخاب‌شده را بررسی کنید."
            HttpStatusCode.PaymentRequired ->
                "سهمیه یا اعتبار حساب Jasper کافی نیست."
            HttpStatusCode.ServiceUnavailable ->
                providerHint
                    ?: "سرویس Jasper موقتاً در دسترس نیست (کد 503). مدل یا کانال را بررسی کنید."
            else ->
                providerHint
                    ?: "پاسخ ناموفق از Jasper (کد ${response.status.value})."
        }
    }

    /**
     * Pull a short, secret-free hint from OpenAI-compatible error JSON.
     * Never returns Authorization material or raw API keys.
     */
    private fun extractProviderErrorHint(raw: String): String? {
        if (raw.isBlank()) return null
        val redacted = raw.replace(Regex("sk-[A-Za-z0-9_-]{6,}"), "[redacted]")
        val message = Regex("\"message\"\\s*:\\s*\"([^\"]{1,240})\"")
            .find(redacted)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\n", " ")
            ?.trim()
            .orEmpty()
        if (message.isBlank()) return null
        val lower = message.lowercase()
        return when {
            "no available channel" in lower || "channel" in lower && "model" in lower ->
                "مدل تنظیم‌شده برای این کلید کانال فعال ندارد. model id را از فهرست مدل‌های Jasper عوض کنید."
            "invalid" in lower && "model" in lower ->
                "شناسهٔ مدل Jasper نامعتبر یا برای این کلید غیرفعال است."
            else -> message.take(180)
        }
    }

    private fun networkError(cause: Throwable?): AiError.Network {
        val detail = cause?.let { err ->
            val name = err::class.simpleName.orEmpty()
            val msg = err.message.orEmpty()
                .replace(Regex("sk-[A-Za-z0-9_-]{6,}"), "[redacted]")
                .take(120)
            listOf(name, msg).filter { it.isNotBlank() }.joinToString(": ")
        }.orEmpty()
        val base = "اتصال شبکه به Jasper برقرار نشد. اینترنت دستگاه را بررسی کنید و اپ را Clean Build کنید."
        return if (detail.isBlank()) {
            AiError.Network(base, cause)
        } else {
            AiError.Network("$base ($detail)", cause)
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
    // Encode JSON ourselves so Darwin/OkHttp both send a raw JSON string body.
    // Passing JsonObject through ContentNegotiation has been flaky on iOS Ktor Darwin.
    when (body) {
        is kotlinx.serialization.json.JsonElement -> {
            setBody(
                com.example.personalledger.data.ai.aiJson.encodeToString(
                    kotlinx.serialization.json.JsonElement.serializer(),
                    body,
                ),
            )
        }
        is String -> setBody(body)
        else -> setBody(body)
    }
}
