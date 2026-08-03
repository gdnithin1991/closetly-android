package com.smartwardrobe.app.data

import android.content.Context
import com.smartwardrobe.app.domain.AppSettings
import com.smartwardrobe.app.domain.Availability
import com.smartwardrobe.app.domain.ClothingItem
import com.smartwardrobe.app.domain.Formality
import com.smartwardrobe.app.domain.ItemType
import com.smartwardrobe.app.domain.Owner
import com.smartwardrobe.app.domain.WardrobeSection
import org.json.JSONArray
import org.json.JSONObject

class LocalCacheRepository(private val context: Context) {
    private val file get() = context.filesDir.resolve("closetly-cache.json")

    fun save(items: List<ClothingItem>, sections: List<WardrobeSection>, settings: AppSettings) {
        val root = JSONObject()
            .put(
                "settings",
                JSONObject()
                    .put("homeCity", settings.homeCity)
                    .put("secondProfileName", settings.secondProfileName)
                    .put("defaultFunctionContext", settings.defaultFunctionContext)
                    .put("defaultTravelDestination", settings.defaultTravelDestination)
                    .put("aiSuggestionsUsedToday", settings.aiSuggestionsUsedToday)
                    .put("aiSuggestionEpochDay", settings.aiSuggestionEpochDay)
                    .put("hostedAiEndpoint", settings.hostedAiEndpoint)
                    .put("hostedAiApiKey", settings.hostedAiApiKey)
                    .put("lastWeatherSummary", settings.lastWeatherSummary)
            )
            .put("sections", JSONArray(sections.map { it.toJson() }))
            .put("items", JSONArray(items.map { it.toJson() }))
        file.writeText(root.toString())
    }

    fun load(): CacheSnapshot? {
        if (!file.exists()) return null
        val root = JSONObject(file.readText())
        return CacheSnapshot(
            items = root.optJSONArray("items").toItems(),
            sections = root.optJSONArray("sections").toSections(),
            settings = root.optJSONObject("settings").toAppSettings()
        )
    }

    private fun JSONObject?.toAppSettings(): AppSettings {
        if (this == null) return AppSettings()
        return AppSettings(
            homeCity = optString("homeCity", "Set home city"),
            secondProfileName = optString("secondProfileName", "Household member"),
            defaultFunctionContext = optString("defaultFunctionContext", ""),
            defaultTravelDestination = optString("defaultTravelDestination", ""),
            aiSuggestionsUsedToday = optInt("aiSuggestionsUsedToday", 0),
            aiSuggestionEpochDay = optLong("aiSuggestionEpochDay", 0),
            hostedAiEndpoint = optString("hostedAiEndpoint", ""),
            hostedAiApiKey = optString("hostedAiApiKey", ""),
            lastWeatherSummary = optString("lastWeatherSummary", "Weather not fetched yet")
        )
    }

    private fun ClothingItem.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("owner", owner.name)
        .put("type", type.name)
        .put("color", color)
        .put("formality", formality.name)
        .put("availability", availability.name)
        .put("lastWornEpochDay", lastWornEpochDay)
        .put("privateToOwner", privateToOwner)
        .put("wardrobeSection", wardrobeSection)
        .put("photoUri", photoUri)
        .put("photoData", photoData)
        .put("photoByteSize", photoByteSize)
        .put("notes", notes)

    private fun WardrobeSection.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("capacity", capacity)
        .put("layoutPhotoUri", layoutPhotoUri)

    private fun JSONArray?.toItems(): List<ClothingItem> {
        if (this == null) return emptyList()
        return (0 until length()).map { getJSONObject(it) }.map { data ->
            ClothingItem(
                id = data.optString("id"),
                name = data.optString("name", "Unnamed item"),
                owner = data.optEnum("owner", Owner.ME),
                type = data.optEnum("type", ItemType.TOP),
                color = data.optString("color", "Unknown"),
                formality = data.optEnum("formality", Formality.SMART_CASUAL),
                availability = data.optEnum("availability", Availability.AVAILABLE),
                lastWornEpochDay = data.optLongOrNull("lastWornEpochDay"),
                privateToOwner = data.optBoolean("privateToOwner", false),
                wardrobeSection = data.optString("wardrobeSection", "Unassigned"),
                photoUri = data.optString("photoUri").ifBlank { null },
                photoData = data.optString("photoData").ifBlank { null },
                photoByteSize = data.optLong("photoByteSize", 0),
                notes = data.optString("notes", "")
            )
        }
    }

    private fun JSONArray?.toSections(): List<WardrobeSection> {
        if (this == null) return emptyList()
        return (0 until length()).map { getJSONObject(it) }.map { data ->
            WardrobeSection(
                id = data.optString("id"),
                name = data.optString("name", "Section"),
                capacity = data.optInt("capacity", 10),
                layoutPhotoUri = data.optString("layoutPhotoUri").ifBlank { null }
            )
        }
    }

    private inline fun <reified T : Enum<T>> JSONObject.optEnum(name: String, fallback: T): T =
        optString(name).let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (has(name) && !isNull(name)) optLong(name) else null
}

data class CacheSnapshot(
    val items: List<ClothingItem>,
    val sections: List<WardrobeSection>,
    val settings: AppSettings
)
