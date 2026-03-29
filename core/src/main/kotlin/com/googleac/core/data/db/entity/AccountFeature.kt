package com.googleac.core.data.db.entity

/**
 * Features that can be independently enabled for each Google account.
 *
 * Stored as a JSON array of [name] strings in [AccountEntity.enabledFeatures].
 * Displayed in [com.googleac.feature.auth.ui.FeatureEnablementScreen] immediately
 * after a new account authenticates so the user can opt in to only the services
 * they need.
 */
enum class AccountFeature(
    val displayName: String,
    val description: String
) {
    DRIVE(
        displayName = "Google Drive",
        description = "Browse, search and manage your Drive files"
    ),
    CALENDAR(
        displayName = "Google Calendar",
        description = "View and manage calendar events"
    ),
    TASKS(
        displayName = "Google Tasks",
        description = "Manage your task lists and to-dos"
    ),
    AI_SUMMARIZER(
        displayName = "AI Summarizer",
        description = "On-device AI summaries of your Drive documents"
    )
}
