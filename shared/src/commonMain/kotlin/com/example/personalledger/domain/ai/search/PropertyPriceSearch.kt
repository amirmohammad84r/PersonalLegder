package com.example.personalledger.domain.ai.search

/**
 * External property-price search contract.
 *
 * This app's Jasper gateway (`https://api.yjs.im` OpenAI-compatible chat) has **no documented
 * official web-search / retrieval tool** for our chat/completions path. DeepSeek's native
 * `deepseek_web_search` is documented on DeepSeek's own API, and Jasper.ai's `webSearch`
 * retrieval add-on is on `api.jasper.ai` `/v1/command` — neither is the same as this gateway.
 *
 * Until a documented Search API adapter is wired with its own secrets, [isAvailable] is false
 * and [search] returns [PropertyPriceSearchOutcome.Unavailable].
 */
interface PropertyPriceSearchProvider {
    val providerId: String
    fun isAvailable(): Boolean
    suspend fun search(request: PropertyPriceSearchRequest): PropertyPriceSearchOutcome
}

enum class PropertyListingCurrency {
    RIAL,
    TOMAN,
}

data class PropertyPriceSearchRequest(
    /** City/area the user stated or confirmed — never invented by the model path without args. */
    val locationQuery: String,
    val propertyType: String? = null,
    val areaSqmMin: Int? = null,
    val areaSqmMax: Int? = null,
    val maxResults: Int = 5,
)

data class PropertyPriceListing(
    val title: String,
    val url: String,
    val sourceName: String?,
    /** When the page/listing claims a publish/update time, if known. */
    val sourcePublishedAtEpochMs: Long?,
    /** When this app retrieved the listing. */
    val retrievedAtEpochMs: Long,
    val cityOrArea: String?,
    val propertyType: String?,
    val areaSqm: Int?,
    val priceAmount: Long?,
    val priceCurrency: PropertyListingCurrency?,
    /** Listings are asking prices unless the source proves a closed sale. */
    val priceKind: String = "ASKING_LISTING",
)

sealed class PropertyPriceSearchOutcome {
    data class Success(
        val queryUsed: String,
        val retrievedAtEpochMs: Long,
        val listings: List<PropertyPriceListing>,
        val notes: List<String> = emptyList(),
    ) : PropertyPriceSearchOutcome()

    data class ClarificationNeeded(val message: String) : PropertyPriceSearchOutcome()

    data class Unavailable(
        val reason: String,
        val activationHint: String,
    ) : PropertyPriceSearchOutcome()

    data class Failed(val message: String) : PropertyPriceSearchOutcome()
}

/**
 * Default provider: interface ready, no live search until a real API is configured.
 */
class UnavailablePropertyPriceSearchProvider : PropertyPriceSearchProvider {
    override val providerId: String = "none"

    override fun isAvailable(): Boolean = false

    override suspend fun search(request: PropertyPriceSearchRequest): PropertyPriceSearchOutcome =
        PropertyPriceSearchOutcome.Unavailable(
            reason = "No documented web-search API is configured for this Jasper gateway build.",
            activationHint = "Wire a dedicated PropertyPriceSearchProvider (e.g. a Search API with its own local secrets) and register it in AppContainer.",
        )
}
