package com.example.personalledger.data.ai.jasper

/**
 * Jasper gateway base URL as shown in the account panel.
 * Chat/model paths come from local secrets (build-time) so they stay configurable
 * without inventing undocumented routes in source.
 */
object JasperEndpoints {
    const val BASE_URL = "https://api.yjs.im"

    fun chatUrl(chatPath: String): String = join(BASE_URL, chatPath)

    fun modelsUrl(modelsPath: String): String = join(BASE_URL, modelsPath)

    private fun join(base: String, path: String): String {
        val b = base.trimEnd('/')
        val p = path.trim().let { if (it.startsWith("/")) it else "/$it" }
        return b + p
    }
}
