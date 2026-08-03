    package com.smartwardrobe.app.ui

enum class AppPage { HOME, WARDROBE, PLAN, SETTINGS }

fun AppPage.label() = when (this) {
    AppPage.HOME -> "Home"
    AppPage.WARDROBE -> "Wardrobe"
    AppPage.PLAN -> "Plan"
    AppPage.SETTINGS -> "Settings"
}
