package com.smartwardrobe.app.data

import com.smartwardrobe.app.domain.ClothingItem
import com.smartwardrobe.app.domain.Formality
import com.smartwardrobe.app.domain.ItemType
import com.smartwardrobe.app.domain.Owner
import com.smartwardrobe.app.domain.WardrobeSection

fun defaultSections() = listOf(
    WardrobeSection("s1", "Office shelf", 18),
    WardrobeSection("s2", "Function wear", 12),
    WardrobeSection("s3", "Shoes rack", 10),
    WardrobeSection("s4", "Accessories drawer", 20)
)

fun demoWardrobe() = listOf(
    ClothingItem("1", "Navy Oxford Shirt", Owner.ME, ItemType.TOP, "Navy", Formality.SMART_CASUAL, wardrobeSection = "Office shelf"),
    ClothingItem("2", "White Formal Shirt", Owner.ME, ItemType.TOP, "White", Formality.FORMAL, wardrobeSection = "Office shelf"),
    ClothingItem("3", "Green Polo", Owner.ME, ItemType.TOP, "Green", Formality.CASUAL, wardrobeSection = "Office shelf"),
    ClothingItem("4", "Beige Chinos", Owner.ME, ItemType.BOTTOM, "Beige", Formality.SMART_CASUAL, wardrobeSection = "Office shelf"),
    ClothingItem("5", "Charcoal Trousers", Owner.ME, ItemType.BOTTOM, "Charcoal", Formality.FORMAL, wardrobeSection = "Office shelf"),
    ClothingItem("6", "Brown Loafers", Owner.ME, ItemType.SHOES, "Brown", Formality.SMART_CASUAL, wardrobeSection = "Shoes rack"),
    ClothingItem("7", "Black Formal Shoes", Owner.ME, ItemType.SHOES, "Black", Formality.FORMAL, wardrobeSection = "Shoes rack"),
    ClothingItem("8", "Classic Watch", Owner.ME, ItemType.WATCH, "Silver", Formality.FORMAL, wardrobeSection = "Accessories drawer"),
    ClothingItem("9", "Rain Jacket", Owner.ME, ItemType.OUTERWEAR, "Navy", Formality.CASUAL, wardrobeSection = "Office shelf"),
    ClothingItem("10", "Festival Kurta", Owner.ME, ItemType.TOP, "Teal", Formality.TRADITIONAL, privateToOwner = true, wardrobeSection = "Function wear"),
    ClothingItem("11", "Maroon Saree", Owner.WIFE, ItemType.DRESS, "Maroon", Formality.FESTIVE, privateToOwner = true, wardrobeSection = "Function wear"),
    ClothingItem("12", "Cream Handbag", Owner.WIFE, ItemType.BAG, "Beige", Formality.SMART_CASUAL, wardrobeSection = "Accessories drawer")
)
