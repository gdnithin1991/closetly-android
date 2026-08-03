package com.smartwardrobe.app.domain

import java.time.LocalDate

/** A transparent, offline-first matcher. A hosted AI provider can be added behind this interface later. */
class RecommendationEngine {
    fun suggest(items: List<ClothingItem>, owner: Owner, occasion: Occasion): List<OutfitRecommendation> {
        return suggest(
            items = items,
            request = RecommendationRequest(
                owner = owner,
                occasion = occasion,
                weatherMood = WeatherMood.CLEAR
            )
        )
    }

    fun suggest(items: List<ClothingItem>, request: RecommendationRequest): List<OutfitRecommendation> {
        val usable = items.filter { it.owner == request.owner && it.availability == Availability.AVAILABLE }
        val tops = usable.filter { it.type == ItemType.TOP || it.type == ItemType.DRESS }
        val bottoms = usable.filter { it.type == ItemType.BOTTOM }
        val shoes = usable.filter { it.type == ItemType.SHOES }
        val outerwear = usable.filter { it.type == ItemType.OUTERWEAR }
        val extras = usable.filter { it.type in setOf(ItemType.WATCH, ItemType.BELT, ItemType.BAG, ItemType.ACCESSORY) }
        val today = LocalDate.now().toEpochDay()

        return tops.flatMap { top ->
            val bases = if (top.type == ItemType.DRESS) listOf(emptyList()) else bottoms.map { listOf(it) }
            bases.flatMap { bottom -> shoes.ifEmpty { listOf(null) }.map { shoe ->
                val weatherLayer = when (request.weatherMood) {
                    WeatherMood.RAIN, WeatherMood.COLD -> outerwear.firstOrNull()
                    else -> null
                }
                val outfit = listOf(top) + bottom + listOfNotNull(weatherLayer, shoe) + extras.take(2)
                OutfitRecommendation(outfit, score(outfit, request, today), explanation(outfit, request))
            } }
        }.sortedByDescending { it.score }.distinctBy { it.items.map(ClothingItem::id) }.take(5)
    }

    fun packingList(items: List<ClothingItem>, owner: Owner, outfitCount: Int): List<ClothingItem> =
        items.filter { it.owner == owner && it.availability == Availability.AVAILABLE }
            .sortedBy { it.lastWornEpochDay ?: Long.MIN_VALUE }
            .take((outfitCount * 3).coerceAtMost(items.size))

    fun weeklyOfficePlan(items: List<ClothingItem>, owner: Owner, weatherMood: WeatherMood): Map<String, OutfitRecommendation?> {
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
        val pool = items.toMutableList()
        return days.associateWith {
            val pick = suggest(
                pool,
                RecommendationRequest(owner = owner, occasion = Occasion.OFFICE, weatherMood = weatherMood)
            ).firstOrNull()
            pick?.items?.forEach { chosen ->
                val index = pool.indexOfFirst { it.id == chosen.id }
                if (index >= 0) pool[index] = pool[index].copy(availability = Availability.UNAVAILABLE)
            }
            pick
        }
    }

    private fun score(items: List<ClothingItem>, request: RecommendationRequest, today: Long): Int {
        val desired = when (request.occasion) {
            Occasion.OFFICE -> setOf(Formality.SMART_CASUAL, Formality.FORMAL)
            Occasion.FUNCTION -> setOf(Formality.TRADITIONAL, Formality.FESTIVE, Formality.FORMAL)
            Occasion.TRAVEL -> setOf(Formality.CASUAL, Formality.SMART_CASUAL)
        }
        val suitable = items.count { it.formality in desired } * 24
        val variety = items.count { (it.lastWornEpochDay ?: 0) < today - 3 } * 8
        val contrast = if (items.map { it.color.lowercase() }.distinct().size > 1) 10 else 2
        val weather = when (request.weatherMood) {
            WeatherMood.RAIN -> if (items.any { it.type == ItemType.OUTERWEAR || it.type == ItemType.SHOES }) 8 else 0
            WeatherMood.COLD -> if (items.any { it.type == ItemType.OUTERWEAR }) 10 else 0
            WeatherMood.HOT, WeatherMood.HUMID -> if (items.none { it.type == ItemType.OUTERWEAR }) 8 else 0
            WeatherMood.CLEAR -> 4
        }
        val eventText = request.placeOrEvent.lowercase()
        val eventFit = when {
            request.occasion == Occasion.FUNCTION && listOf("wedding", "pooja", "temple", "festival").any { it in eventText } ->
                items.count { it.formality in setOf(Formality.TRADITIONAL, Formality.FESTIVE) } * 8
            request.occasion == Occasion.FUNCTION && listOf("dinner", "resort", "party").any { it in eventText } ->
                items.count { it.formality in setOf(Formality.SMART_CASUAL, Formality.FORMAL, Formality.FESTIVE) } * 6
            request.occasion == Occasion.TRAVEL -> items.count { it.formality in setOf(Formality.CASUAL, Formality.SMART_CASUAL) } * 5
            else -> 0
        }
        return (suitable + variety + contrast + weather + eventFit).coerceAtMost(100)
    }

    private fun explanation(items: List<ClothingItem>, request: RecommendationRequest): String {
        val labels = items.joinToString(" + ") { it.name }
        val context = when {
            request.occasion == Occasion.FUNCTION && request.placeOrEvent.isNotBlank() -> " for ${request.placeOrEvent}"
            request.occasion == Occasion.TRAVEL && request.destination.isNotBlank() -> " for ${request.destination}"
            else -> ""
        }
        val weather = request.weatherMood.name.lowercase().replace('_', ' ')
        return "$labels is a balanced ${request.occasion.name.lowercase()} option$context using available items and $weather weather context."
    }
}
