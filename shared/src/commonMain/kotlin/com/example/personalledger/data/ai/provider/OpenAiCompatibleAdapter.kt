package com.example.personalledger.data.ai.provider

import com.example.personalledger.data.ai.aiJson
import com.example.personalledger.domain.ai.AiChatRequest
import com.example.personalledger.domain.ai.AiChatResult
import com.example.personalledger.domain.ai.AiChatRole
import com.example.personalledger.domain.ai.AiError
import com.example.personalledger.domain.ai.AiModelInfo
import com.example.personalledger.domain.ai.AiProviderId
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Shared client for official OpenAI-compatible HTTPS APIs:
 * GET {base}/models and POST {base}/chat/completions with Bearer auth.
 * API key is sent only in the Authorization header.
 */
class OpenAiCompatibleAdapter(
    private val client: HttpClient,
    override val providerId: AiProviderId,
    private val modelsUrl: String,
    private val chatUrl: String,
    private val modelPredicate: (CompatibleModelItem) -> Boolean,
    private val emptyAnswerMessage: String,
) : AiProviderAdapter {
    override suspend fun listModels(apiKey: String): List<AiModelInfo> {
        requireKey(apiKey)
        val response = client.getJson(modelsUrl) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
        }
        if (!response.status.isSuccess()) {
            val msg = AiHttpSupport.readSafeErrorMessage(response)
            throw AiHttpSupport.mapHttpFailure(response, msg)
        }
        val payload = aiJson.decodeFromString(
            CompatibleModelsResponse.serializer(),
            response.bodyText(),
        )
        return payload.data
            .map { item ->
                item.copy(
                    id = item.id.trim(),
                    name = item.name?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            .filter { it.id.isNotEmpty() && modelPredicate(it) }
            .distinctBy { it.id }
            .sortedBy { (it.name ?: it.id).lowercase() }
            .map { item ->
                AiModelInfo(
                    id = item.id,
                    displayName = item.name ?: item.id,
                    description = providerId.displayName,
                    fromLiveCatalog = true,
                )
            }
    }

    override suspend fun chat(request: AiChatRequest): AiChatResult {
        requireKey(request.apiKey)
        if (request.modelId.isBlank()) {
            throw AiError.InvalidModel("مدل انتخاب نشده است.")
        }
        val messages = buildList {
            add(CompatibleMessage(role = "system", content = request.systemPrompt))
            request.history.takeLast(8).forEach { msg ->
                add(
                    CompatibleMessage(
                        role = when (msg.role) {
                            AiChatRole.USER -> "user"
                            AiChatRole.ASSISTANT -> "assistant"
                        },
                        content = msg.content,
                    ),
                )
            }
            add(CompatibleMessage(role = "user", content = request.userPrompt))
        }
        val body = CompatibleChatRequest(
            model = request.modelId,
            messages = messages,
            temperature = 0.2,
        )
        val response = client.postJson(chatUrl) {
            header(HttpHeaders.Authorization, "Bearer ${request.apiKey}")
            contentType(ContentType.Application.Json)
            jsonBody(body)
        }
        if (!response.status.isSuccess()) {
            val msg = AiHttpSupport.readSafeErrorMessage(response)
            throw when {
                response.status.value == 404 ->
                    AiError.InvalidModel("مدل انتخاب‌شده در این حساب در دسترس نیست.")
                else -> AiHttpSupport.mapHttpFailure(response, msg)
            }
        }
        val text = parseChatText(response.bodyText())
        if (text.isBlank()) throw AiError.Provider(emptyAnswerMessage)
        return AiChatResult(text = text)
    }

    private fun parseChatText(raw: String): String {
        val root = aiJson.parseToJsonElement(raw).jsonObject
        val choices = root["choices"]?.jsonArray ?: return ""
        val first = choices.firstOrNull()?.jsonObject ?: return ""
        val message = first["message"]?.jsonObject ?: return ""
        return message["content"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
    }

    private fun requireKey(apiKey: String) {
        if (apiKey.isBlank()) throw AiError.Authentication("کلید API وارد نشده است.")
    }
}

object OpenAiCompatibleModelFilters {
    fun openAiChat(item: CompatibleModelItem): Boolean = isOpenAiChatCapable(item.id)

    fun deepSeekChat(item: CompatibleModelItem): Boolean {
        val id = item.id.lowercase()
        return id.startsWith("deepseek-") && !isNonChatNoise(id)
    }

    fun gapGptChat(item: CompatibleModelItem): Boolean = !isNonChatNoise(item.id.lowercase())

    fun isOpenAiChatCapable(id: String): Boolean {
        val lower = id.lowercase()
        if (isNonChatNoise(lower)) return false
        return lower.startsWith("gpt-") ||
            lower.startsWith("o1") ||
            lower.startsWith("o3") ||
            lower.startsWith("o4") ||
            lower.startsWith("chatgpt-")
    }

    private fun isNonChatNoise(lower: String): Boolean =
        lower.contains("embedding") ||
            lower.contains("whisper") ||
            lower.contains("tts") ||
            lower.contains("dall-e") ||
            lower.contains("moderation") ||
            lower.contains("realtime") ||
            lower.contains("transcribe") ||
            lower.contains("speech") ||
            lower.contains("audio") ||
            lower.contains("image") ||
            lower.contains("video") ||
            lower.contains("davinci") ||
            lower.contains("babbage") ||
            lower.contains("search") ||
            lower.contains("similarity")
}

@Serializable
data class CompatibleModelItem(
    val id: String,
    val name: String? = null,
)

@Serializable
private data class CompatibleModelsResponse(
    val data: List<CompatibleModelItem> = emptyList(),
)

@Serializable
private data class CompatibleChatRequest(
    val model: String,
    val messages: List<CompatibleMessage>,
    val temperature: Double = 0.2,
)

@Serializable
private data class CompatibleMessage(
    val role: String,
    val content: String,
)

private suspend fun HttpResponse.bodyText(): String = bodyAsText()
