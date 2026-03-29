package com.googleac.feature.auth.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.googleac.core.data.db.entity.AccountEntity
import com.googleac.core.data.db.entity.AccountFeature
import com.googleac.feature.auth.data.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [FeatureEnablementScreen].
 *
 * Receives [accountId] and [email] from the Navigation [SavedStateHandle] and manages
 * the per-feature toggle state.  On [saveAndContinue] it persists the [AccountEntity]
 * (creating or updating as needed) with only the features the user has opted into.
 */
@HiltViewModel
class FeatureEnablementViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository
) : ViewModel() {

    val accountId: String = savedStateHandle.get<String>("accountId") ?: ""
    val email: String = savedStateHandle.get<String>("email") ?: ""

    private val _enabledFeatures = MutableStateFlow(emptySet<AccountFeature>())
    val enabledFeatures: StateFlow<Set<AccountFeature>> = _enabledFeatures

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    /** Toggle [feature] on (if currently off) or off (if currently on). */
    fun toggleFeature(feature: AccountFeature) {
        _enabledFeatures.update { current ->
            if (feature in current) current - feature else current + feature
        }
    }

    /** Enable every [AccountFeature] at once. */
    fun enableAllFeatures() {
        _enabledFeatures.value = AccountFeature.entries.toSet()
    }

    /**
     * Persists the account with the currently enabled features, then calls [onDone].
     *
     * - If the account already exists in the DB (e.g., re-running the flow after a crash),
     *   it is updated in place.
     * - Otherwise a fresh [AccountEntity] is created.
     */
    fun saveAndContinue(onDone: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val featureNames = _enabledFeatures.value.map { it.name }
                val existing = accountRepository.getAccount(accountId)
                if (existing != null) {
                    accountRepository.updateAccount(existing.copy(enabledFeatures = featureNames))
                } else {
                    accountRepository.addAccount(
                        AccountEntity(
                            accountId = accountId,
                            email = email,
                            displayName = email.substringBefore("@"),
                            enabledFeatures = featureNames
                        )
                    )
                }
                onDone()
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Skips feature selection — saves the account with no features enabled.
     * Features can be enabled later from the account settings screen.
     */
    fun skipAndContinue(onDone: () -> Unit) {
        _enabledFeatures.value = emptySet()
        saveAndContinue(onDone)
    }
}
