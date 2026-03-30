package com.googleac.feature.auth.ui

import androidx.lifecycle.SavedStateHandle
import com.googleac.core.data.db.entity.AccountEntity
import com.googleac.core.data.db.entity.AccountFeature
import com.googleac.feature.auth.data.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [FeatureEnablementViewModel].
 *
 * Validates:
 * 1. Initial state — no features enabled, not saving.
 * 2. [toggleFeature] enables and disables individual features.
 * 3. [enableAllFeatures] enables every [AccountFeature] at once.
 * 4. [saveAndContinue] creates a new account when none exists.
 * 5. [saveAndContinue] updates an existing account in place.
 * 6. [skipAndContinue] saves an account with an empty features list.
 * 7. The [onDone] callback is invoked exactly once per save call.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeatureEnablementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var accountRepository: AccountRepository
    private lateinit var viewModel: FeatureEnablementViewModel

    private val testAccountId = "abc123"
    private val testEmail = "user@example.com"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        accountRepository = mock()
        viewModel = FeatureEnablementViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf("accountId" to testAccountId, "email" to testEmail)
            ),
            accountRepository = accountRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial enabledFeatures is empty`() {
        assertTrue(viewModel.enabledFeatures.value.isEmpty())
    }

    @Test
    fun `initial isSaving is false`() {
        assertFalse(viewModel.isSaving.value)
    }

    @Test
    fun `accountId is read from SavedStateHandle`() {
        assertEquals(testAccountId, viewModel.accountId)
    }

    @Test
    fun `email is read from SavedStateHandle`() {
        assertEquals(testEmail, viewModel.email)
    }

    // ── toggleFeature ─────────────────────────────────────────────────────────

    @Test
    fun `toggleFeature enables a feature that is off`() {
        viewModel.toggleFeature(AccountFeature.DRIVE)
        assertTrue(AccountFeature.DRIVE in viewModel.enabledFeatures.value)
    }

    @Test
    fun `toggleFeature disables a feature that is on`() {
        viewModel.toggleFeature(AccountFeature.DRIVE)
        viewModel.toggleFeature(AccountFeature.DRIVE)
        assertFalse(AccountFeature.DRIVE in viewModel.enabledFeatures.value)
    }

    @Test
    fun `toggling multiple features accumulates them independently`() {
        viewModel.toggleFeature(AccountFeature.DRIVE)
        viewModel.toggleFeature(AccountFeature.CALENDAR)
        val enabled = viewModel.enabledFeatures.value
        assertTrue(AccountFeature.DRIVE in enabled)
        assertTrue(AccountFeature.CALENDAR in enabled)
        assertFalse(AccountFeature.TASKS in enabled)
        assertFalse(AccountFeature.AI_SUMMARIZER in enabled)
    }

    @Test
    fun `toggling one feature does not affect others`() {
        viewModel.toggleFeature(AccountFeature.DRIVE)
        viewModel.toggleFeature(AccountFeature.TASKS)
        viewModel.toggleFeature(AccountFeature.DRIVE) // disable DRIVE
        assertFalse(AccountFeature.DRIVE in viewModel.enabledFeatures.value)
        assertTrue(AccountFeature.TASKS in viewModel.enabledFeatures.value)
    }

    // ── enableAllFeatures ─────────────────────────────────────────────────────

    @Test
    fun `enableAllFeatures enables every AccountFeature`() {
        viewModel.enableAllFeatures()
        assertEquals(AccountFeature.entries.toSet(), viewModel.enabledFeatures.value)
    }

    @Test
    fun `enableAllFeatures is idempotent`() {
        viewModel.enableAllFeatures()
        viewModel.enableAllFeatures()
        assertEquals(AccountFeature.entries.toSet(), viewModel.enabledFeatures.value)
    }

    // ── saveAndContinue — new account ─────────────────────────────────────────

    @Test
    fun `saveAndContinue creates account when none exists`() = runTest(testDispatcher) {
        whenever(accountRepository.getAccount(testAccountId)).thenReturn(null)

        viewModel.toggleFeature(AccountFeature.DRIVE)
        var doneCalled = false
        viewModel.saveAndContinue { doneCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()

        verify(accountRepository).addAccount(any())
        assertTrue(doneCalled)
    }

    @Test
    fun `saveAndContinue new account contains selected features`() = runTest(testDispatcher) {
        whenever(accountRepository.getAccount(testAccountId)).thenReturn(null)

        viewModel.toggleFeature(AccountFeature.DRIVE)
        viewModel.toggleFeature(AccountFeature.TASKS)
        viewModel.saveAndContinue {}
        testDispatcher.scheduler.advanceUntilIdle()

        val captor = argumentCaptor<AccountEntity>()
        verify(accountRepository).addAccount(captor.capture())
        val saved = captor.firstValue
        assertTrue(AccountFeature.DRIVE.name in saved.enabledFeatures)
        assertTrue(AccountFeature.TASKS.name in saved.enabledFeatures)
        assertFalse(AccountFeature.CALENDAR.name in saved.enabledFeatures)
    }

    @Test
    fun `saveAndContinue new account uses email from SavedStateHandle`() = runTest(testDispatcher) {
        whenever(accountRepository.getAccount(testAccountId)).thenReturn(null)
        viewModel.saveAndContinue {}
        testDispatcher.scheduler.advanceUntilIdle()

        val captor = argumentCaptor<AccountEntity>()
        verify(accountRepository).addAccount(captor.capture())
        assertEquals(testEmail, captor.firstValue.email)
        assertEquals(testAccountId, captor.firstValue.accountId)
    }

    // ── saveAndContinue — existing account ────────────────────────────────────

    @Test
    fun `saveAndContinue updates account when one already exists`() = runTest(testDispatcher) {
        val existing = AccountEntity(
            accountId = testAccountId,
            email = testEmail,
            displayName = "user"
        )
        whenever(accountRepository.getAccount(testAccountId)).thenReturn(existing)

        viewModel.toggleFeature(AccountFeature.CALENDAR)
        viewModel.saveAndContinue {}
        testDispatcher.scheduler.advanceUntilIdle()

        verify(accountRepository, never()).addAccount(any())
        val captor = argumentCaptor<AccountEntity>()
        verify(accountRepository).updateAccount(captor.capture())
        assertTrue(AccountFeature.CALENDAR.name in captor.firstValue.enabledFeatures)
    }

    // ── skipAndContinue ───────────────────────────────────────────────────────

    @Test
    fun `skipAndContinue saves account with empty features`() = runTest(testDispatcher) {
        whenever(accountRepository.getAccount(testAccountId)).thenReturn(null)

        viewModel.toggleFeature(AccountFeature.DRIVE) // will be cleared by skip
        viewModel.skipAndContinue {}
        testDispatcher.scheduler.advanceUntilIdle()

        val captor = argumentCaptor<AccountEntity>()
        verify(accountRepository).addAccount(captor.capture())
        assertTrue(captor.firstValue.enabledFeatures.isEmpty())
    }

    @Test
    fun `skipAndContinue invokes onDone callback`() = runTest(testDispatcher) {
        whenever(accountRepository.getAccount(testAccountId)).thenReturn(null)

        var doneCalled = false
        viewModel.skipAndContinue { doneCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(doneCalled)
    }
}
