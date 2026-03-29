package com.googleac.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a Google account managed by the app.
 * Each account has an isolated sandbox enforced via accountId partitioning.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "email")
    val email: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "photo_url")
    val photoUrl: String? = null,

    /** Persona type: PERSONAL, CORPORATE, CLIENT, VAULT */
    @ColumnInfo(name = "persona_type")
    val personaType: String = PersonaType.PERSONAL.name,

    /** Material 3 tonal palette seed color as ARGB int */
    @ColumnInfo(name = "tonal_seed_color")
    val tonalSeedColor: Int? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Features explicitly enabled for this account.
     *
     * Stored as a JSON array of [AccountFeature] names (e.g. `["DRIVE","TASKS"]`).
     * An empty list means no features are enabled yet — the user either skipped
     * the [com.googleac.feature.auth.ui.FeatureEnablementScreen] or has not yet
     * selected any features.
     */
    @ColumnInfo(name = "enabled_features")
    val enabledFeatures: List<String> = emptyList()
)

enum class PersonaType {
    PERSONAL, CORPORATE, CLIENT, VAULT
}
