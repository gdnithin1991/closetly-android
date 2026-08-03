package com.smartwardrobe.app.domain

enum class Owner { ME, WIFE }
enum class ItemType { TOP, BOTTOM, DRESS, OUTERWEAR, SHOES, WATCH, BELT, BAG, ACCESSORY }
enum class Availability { AVAILABLE, LAUNDRY, DRY_CLEANING, PACKED, REPAIR, UNAVAILABLE }
enum class Occasion { OFFICE, FUNCTION, TRAVEL }
enum class Formality { CASUAL, SMART_CASUAL, FORMAL, TRADITIONAL, FESTIVE }
enum class WeatherMood { CLEAR, RAIN, COLD, HOT, HUMID }

data class ClothingItem(
    val id: String,
    val name: String,
    val owner: Owner,
    val type: ItemType,
    val color: String,
    val formality: Formality,
    val availability: Availability = Availability.AVAILABLE,
    val lastWornEpochDay: Long? = null,
    val privateToOwner: Boolean = false,
    val wardrobeSection: String = "Unassigned",
    val photoUri: String? = null,
    val photoData: String? = null,
    val photoByteSize: Long = 0,
    val notes: String = ""
)

data class WardrobeSection(
    val id: String,
    val name: String,
    val capacity: Int,
    val layoutPhotoUri: String? = null
)

data class RecommendationRequest(
    val owner: Owner,
    val occasion: Occasion,
    val weatherMood: WeatherMood,
    val placeOrEvent: String = "",
    val destination: String = "",
    val outfitCount: Int = 5
)

data class TravelPlan(
    val destination: String,
    val startDate: String,
    val endDate: String,
    val outfitCount: Int,
    val packedItemIds: Set<String> = emptySet()
)

data class NotificationPreferences(
    val dailyOutfit: Boolean = true,
    val weeklyPlan: Boolean = true,
    val laundryFollowUp: Boolean = true,
    val purchaseReminder: Boolean = true,
    val travelPacking: Boolean = true
)

data class AppSettings(
    val homeCity: String = "Set home city",
    val secondProfileName: String = "Household member",
    val defaultFunctionContext: String = "",
    val defaultTravelDestination: String = "",
    val aiSuggestionsUsedToday: Int = 0,
    val aiSuggestionEpochDay: Long = 0,
    val hostedAiEndpoint: String = "",
    val hostedAiApiKey: String = "",
    val lastWeatherSummary: String = "Weather not fetched yet"
)

data class PurchaseReminder(
    val id: String,
    val merchant: String,
    val amount: String,
    val source: String,
    val handled: Boolean = false
)

data class OutfitRecommendation(
    val items: List<ClothingItem>,
    val score: Int,
    val reason: String
)
