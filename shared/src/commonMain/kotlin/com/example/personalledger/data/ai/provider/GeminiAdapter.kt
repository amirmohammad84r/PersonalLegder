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
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Official Google Gemini Generative Language API:
 * - GET /v1beta/models with header x-goog-api-key
 * - POST /v1beta/models/{model}:generateContent with header x-goog-api-key
 * API key only in the auth header (never query/body).
 */
class GeminiAdapter(
    private val client: HttpClient,
) : AiProviderAdapter {
    override val providerId: AiProviderId = AiProviderId.GEMINI

    override suspend fun listModels(apiKey: String): List<AiModelInfo> {
        requireKey(apiKey)
        val collected = mutableListOf<AiModelInfo>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append(AiEndpoints.GEMINI_MODELS)
                append("?pageSize=100")
                if (!pageToken.isNullOrBlank()) {
                    append("&pageToken=")
                    append(pageToken)
                }
            }
            val response = client.getJson(url) {
                header(API_KEY_HEADER, apiKey)
            }
            if (!response.status.isSuccess()) {
                val msg = AiHttpSupport.readSafeErrorMessage(response)
                throw AiHttpSupport.mapHttpFailure(response, msg)
            }
            val payload = aiJson.decodeFromString(
                GeminiModelsResponse.serializer(),
                response.bodyText(),
            )
            payload.models
                .filter { model ->
                    model.supportedGenerationMethods.any {
                        it.equals("generateContent", ignoreCase = true)
                    }
                }
                .forEach { model ->
                    val id = AiEndpoints.normalizeGeminiModelId(model.name)
                    if (id.isNotBlank()) {
                        collected += AiModelInfo(
                            id = id,
                            displayName = model.displayName?.takeIf { it.isNotBlank() } ?: id,
                            description = model.description,
                            fromLiveCatalog = true,
                        )
                    }
                }
            pageToken = payload.nextPageToken
        } while (!pageToken.isNullOrBlank())

        return collected
            .distinctBy { it.id }
            .sortedBy { it.displayName.lowercase() }
    }

    override suspend fun chat(request: AiChatRequest): AiChatResult {
        requireKey(request.apiKey)
        val modelId = AiEndpoints.normalizeGeminiModelId(request.modelId)
        if (modelId.isBlank()) throw AiError.InvalidModel("مدل انتخاب نشده است.")

        val contents = buildList {
            request.history.takeLast(8).forEach { msg ->
                add(
                    GeminiContent(
                        role = when (msg.role) {
                            AiChatRole.USER -> "user"
                            AiChatRole.ASSISTANT -> "model"
                        },
                        parts = listOf(GeminiPart(text = msg.content)),
                    ),
                )
            }
            add(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = request.userPrompt)),
                ),
            )
        }
        val body = GeminiGenerateRequest(
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = request.systemPrompt)),
            ),
            contents = contents,
            generationConfig = GeminiGenerationConfig(temperature = 0.2),
        )
        val response = client.postJson(AiEndpoints.geminiGenerateContent(modelId)) {
            header(API_KEY_HEADER, request.apiKey)
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
        val text = parseGenerateText(response.bodyText())
        if (text.isBlank()) throw AiError.Provider("پاسخ خالی از Gemini دریافت شد.")
        return AiChatResult(text = text)
    }

    private fun parseGenerateText(raw: String): String {
        val root = aiJson.parseToJsonElement(raw).jsonObject
        val candidates = root["candidates"]?.jsonArray ?: return ""
        val first = candidates.firstOrNull()?.jsonObject ?: return ""
        val content = first["content"]?.jsonObject ?: return ""
        val parts = content["parts"]?.jsonArray ?: return ""
        return parts.mapNotNull { part ->
            part.jsonObject["text"]?.jsonPrimitive?.contentOrNull
        }.joinToString("").trim()
    }

    private fun requireKey(apiKey: String) {
        if (apiKey.isBlank()) throw AiError.Authentication("کلید API وارد نشده است.")
    }

    private companion object {
        const val API_KEY_HEADER = "x-goog-api-key"
    }
}

@Serializable
private data class GeminiModelsResponse(
    val models: List<GeminiModelItem> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
private data class GeminiModelItem(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val supportedGenerationMethods: List<String> = emptyList(),
)

@Serializable
private data class GeminiGenerateRequest(
    val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
private data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
private data class GeminiPart(
    val text: String,
)

@Serializable
private data class GeminiGenerationConfig(
    val temperature: Double = 0.2,
)

private suspend fun HttpResponse.bodyText(): String = bodyAsText()
