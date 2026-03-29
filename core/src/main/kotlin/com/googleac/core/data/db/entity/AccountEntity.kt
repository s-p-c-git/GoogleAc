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
    val createdAt: Long = System.currentTimeMillis()
)

enum class PersonaType {
    PERSONAL, CORPORATE, CLIENT, VAULT
}
