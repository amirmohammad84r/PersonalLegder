package com.example.personalledger.data.ai.provider

import com.example.personalledger.data.ai.aiJson
import com.example.personalledger.domain.ai.AiChatRequest
import com.example.personalledger.domain.ai.AiChatResult
import com.example.personalledger.domain.ai.AiChatRole
import com.example.personalledger.domain.ai.AiCompletionMessage
import com.example.personalledger.domain.ai.AiCompletionRequest
import com.example.personalledger.domain.ai.AiCompletionResult
import com.example.personalledger.domain.ai.AiError
import com.example.personalledger.domain.ai.AiModelInfo
import com.example.personalledger.domain.ai.AiProposedToolCall
import com.example.personalledger.domain.ai.AiProviderId
import com.example.personalledger.domain.ai.AiToolCallingMode
import com.example.personalledger.domain.ai.AiToolDefinition
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Shared client for official OpenAI-compatible HTTPS APIs:
 * GET {base}/models and POST {base}/chat/completions with Bearer auth.
 * Native tools use the documented OpenAI/DeepSeek tools + tool_calls shape.
 */
class OpenAiCompatibleAdapter(
    private val client: HttpClient,
    override val providerId: AiProviderId,
    private val modelsUrl: String,
    private val chatUrl: String,
    private val modelPredicate: (CompatibleModelItem) -> Boolean,
    private val emptyAnswerMessage: String,
    override val toolCallingMode: AiToolCallingMode = AiToolCallingMode.OPENAI_COMPATIBLE_TOOLS,
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
        val result = complete(
            AiCompletionRequest(
                providerId = request.providerId,
                modelId = request.modelId,
                apiKey = request.apiKey,
                messages = buildList {
                    add(AiCompletionMessage.System(request.systemPrompt))
                    request.history.takeLast(8).forEach { msg ->
                        when (msg.role) {
                            AiChatRole.USER -> add(AiCompletionMessage.User(msg.content))
                            AiChatRole.ASSISTANT ->
                                add(AiCompletionMessage.Assistant(content = msg.content))
                        }
                    }
                    add(AiCompletionMessage.User(request.userPrompt))
                },
                enableTools = false,
            ),
        )
        val text = result.text?.trim().orEmpty()
        if (text.isBlank()) throw AiError.Provider(emptyAnswerMessage)
        return AiChatResult(text = text)
    }

    override suspend fun complete(request: AiCompletionRequest): AiCompletionResult {
        requireKey(request.apiKey)
        if (request.modelId.isBlank()) {
            throw AiError.InvalidModel("مدل انتخاب نشده است.")
        }
        val body = buildChatBody(request)
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
        return parseCompletion(response.bodyText())
    }

    private fun buildChatBody(request: AiCompletionRequest): JsonObject = buildJsonObject {
        put("model", request.modelId)
        put("temperature", 0.2)
        // Jasper/DeepSeek reasoning can consume a small budget; leave room for visible content.
        put("max_tokens", 1024)
        put("messages", messagesToJson(request.messages))
        val deepSeekLike = request.modelId.contains("deepseek", ignoreCase = true)
        val disableThinking =
            request.disableThinkingForTools ||
                (providerId == AiProviderId.JASPER && deepSeekLike)
        if (disableThinking) {
            // Live Jasper probe: low max_tokens with thinking can return HTTP 200 + empty choices.
            putJsonObject("thinking") { put("type", "disabled") }
        }
        if (request.enableTools && !request.tools.isNullOrEmpty()) {
            put("tools", toolsToJson(request.tools))
            put("tool_choice", "auto")
        }
    }

    private fun messagesToJson(messages: List<AiCompletionMessage>): JsonArray = buildJsonArray {
        messages.forEach { message ->
            add(
                when (message) {
                    is AiCompletionMessage.System -> buildJsonObject {
                        put("role", "system")
                        put("content", message.content)
                    }
                    is AiCompletionMessage.User -> buildJsonObject {
                        put("role", "user")
                        put("content", message.content)
                    }
                    is AiCompletionMessage.Assistant -> buildJsonObject {
                        put("role", "assistant")
                        if (message.content != null) put("content", message.content)
                        else put("content", JsonNull)
                        if (message.toolCalls.isNotEmpty()) {
                            putJsonArray("tool_calls") {
                                message.toolCalls.forEach { call ->
                                    add(
                                        buildJsonObject {
                                            put("id", call.id)
                                            put("type", "function")
                                            putJsonObject("function") {
                                                put("name", call.name)
                                                put("arguments", call.argumentsJson)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    is AiCompletionMessage.ToolResult -> buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", message.toolCallId)
                        put("content", message.content)
                    }
                },
            )
        }
    }

    private fun toolsToJson(tools: List<AiToolDefinition>): JsonArray = buildJsonArray {
        tools.forEach { tool ->
            add(
                buildJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.parametersSchema)
                    }
                },
            )
        }
    }

    private fun parseCompletion(raw: String): AiCompletionResult {
        val root = aiJson.parseToJsonElement(raw).jsonObject
        val choices = root["choices"]?.jsonArray.orEmpty()
        if (choices.isEmpty()) {
            // Live Jasper probe: thinking models can return 200 with empty choices when
            // the token budget is consumed by reasoning. Treat as provider empty answer.
            return AiCompletionResult(text = null, rawAssistantContent = null)
        }
        val first = choices.first().jsonObject
        val message = first["message"]?.jsonObject
            ?: return AiCompletionResult(text = null)
        val contentElement = message["content"]
        val content = contentElement
            ?.takeIf { it !is kotlinx.serialization.json.JsonNull }
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
        val toolCalls = message["tool_calls"]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val function = obj["function"]?.jsonObject ?: return@mapNotNull null
            val name = function["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val arguments = function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
            if (name.isBlank()) return@mapNotNull null
            AiProposedToolCall(
                id = id.ifBlank { "call_$name" },
                name = name,
                argumentsJson = arguments,
            )
        }
        return AiCompletionResult(
            text = content,
            toolCalls = toolCalls,
            rawAssistantContent = content,
        )
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

private suspend fun HttpResponse.bodyText(): String = bodyAsText()
