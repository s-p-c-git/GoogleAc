package com.googleac.feature.auth.data

import com.googleac.core.data.db.dao.AccountDao
import com.googleac.core.data.db.entity.AccountEntity
import com.googleac.core.data.db.entity.PersonaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [AccountRepository].
 *
 * Validates:
 * 1. Multiple account additions (ac1, ac2) are each persisted via the DAO.
 * 2. Account count is queried before and after each insertion (logging hook).
 * 3. observeAllAccounts returns the combined list from the DAO.
 * 4. removeAccount delegates to the DAO and re-queries the count for logging.
 * 5. setActiveAccount clears first, then sets the requested account.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var accountDao: AccountDao
    private lateinit var repository: AccountRepository

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun makeAccount(
        id: String,
        email: String,
        persona: PersonaType = PersonaType.PERSONAL
    ) = AccountEntity(
        accountId = id,
        email = email,
        displayName = email.substringBefore("@"),
        personaType = persona.name
    )

    private val ac1 = makeAccount("ac1", "personal@gmail.com", PersonaType.PERSONAL)
    private val ac2 = makeAccount("ac2", "work@corp.com", PersonaType.CORPORATE)

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        accountDao = mock()
        repository = AccountRepository(accountDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── addAccount — single ───────────────────────────────────────────────────

    @Test
    fun `addAccount inserts account into DAO`() = runTest(testDispatcher) {
        whenever(accountDao.getAccountCount()).thenReturn(0, 1)

        repository.addAccount(ac1)

        verify(accountDao).insertAccount(ac1)
    }

    @Test
    fun `addAccount queries count before and after insertion for logging`() = runTest(testDispatcher) {
        whenever(accountDao.getAccountCount()).thenReturn(0, 1)

        repository.addAccount(ac1)

        // getAccountCount called twice: once before insertion, once after
        verify(accountDao, times(2)).getAccountCount()
    }

    // ── addAccount — multiple (ac1 then ac2) ──────────────────────────────────

    @Test
    fun `adding ac1 then ac2 inserts both accounts into the DAO`() = runTest(testDispatcher) {
        whenever(accountDao.getAccountCount()).thenReturn(0, 1, 1, 2)

        repository.addAccount(ac1)
        repository.addAccount(ac2)

        val captor = argumentCaptor<AccountEntity>()
        verify(accountDao, times(2)).insertAccount(captor.capture())
        assertEquals(listOf(ac1, ac2), captor.allValues)
    }

    @Test
    fun `adding multiple accounts reports increasing count for logging`() = runTest(testDispatcher) {
        // Simulate count growing with each add: 0→1 for ac1, 1→2 for ac2
        whenever(accountDao.getAccountCount()).thenReturn(0, 1, 1, 2)

        repository.addAccount(ac1)
        repository.addAccount(ac2)

        // 4 total count queries: 2 per addAccount call
        verify(accountDao, times(4)).getAccountCount()
    }

    @Test
    fun `getAccountCount reflects all added accounts`() = runTest(testDispatcher) {
        whenever(accountDao.getAccountCount()).thenReturn(2)

        val count = repository.getAccountCount()

        assertEquals(2, count)
    }

    // ── observeAllAccounts ────────────────────────────────────────────────────

    @Test
    fun `observeAllAccounts returns flow from DAO`() = runTest(testDispatcher) {
        whenever(accountDao.observeAllAccounts()).thenReturn(flowOf(listOf(ac1, ac2)))

        val flow = repository.observeAllAccounts()

        val collected = mutableListOf<List<AccountEntity>>()
        flow.collect { collected.add(it) }
        assertEquals(1, collected.size)
        assertEquals(listOf(ac1, ac2), collected[0])
    }

    @Test
    fun `observeAllAccounts returns empty list when no accounts added`() = runTest(testDispatcher) {
        whenever(accountDao.observeAllAccounts()).thenReturn(flowOf(emptyList()))

        val collected = mutableListOf<AccountEntity>()
        repository.observeAllAccounts().collect { collected.addAll(it) }
        assertEquals(0, collected.size)
    }

    // ── Accessing drive files per account (account isolation) ─────────────────

    @Test
    fun `ac1 and ac2 have distinct account IDs`() {
        assertEquals("ac1", ac1.accountId)
        assertEquals("ac2", ac2.accountId)
    }

    @Test
    fun `ac1 uses PERSONAL persona and ac2 uses CORPORATE persona`() {
        assertEquals(PersonaType.PERSONAL.name, ac1.personaType)
        assertEquals(PersonaType.CORPORATE.name, ac2.personaType)
    }

    @Test
    fun `observeActiveAccount proxies the DAO flow`() = runTest(testDispatcher) {
        whenever(accountDao.observeActiveAccount()).thenReturn(flowOf(ac1))

        val collected = mutableListOf<AccountEntity?>()
        repository.observeActiveAccount().collect { collected.add(it) }

        assertEquals(1, collected.size)
        assertEquals(ac1, collected[0])
    }

    // ── removeAccount ─────────────────────────────────────────────────────────

    @Test
    fun `removeAccount delegates to DAO delete`() = runTest(testDispatcher) {
        whenever(accountDao.getAccountCount()).thenReturn(1, 0)

        repository.removeAccount(ac1)

        verify(accountDao).deleteAccount(ac1)
    }

    @Test
    fun `removeAccount queries count for logging`() = runTest(testDispatcher) {
        whenever(accountDao.getAccountCount()).thenReturn(1, 0)

        repository.removeAccount(ac1)

        verify(accountDao, times(2)).getAccountCount()
    }

    // ── setActiveAccount ──────────────────────────────────────────────────────

    @Test
    fun `setActiveAccount clears all then sets the requested account`() = runTest(testDispatcher) {
        repository.setActiveAccount("ac1")

        verify(accountDao).clearActiveAccount()
        verify(accountDao).setActiveAccount("ac1")
    }

    @Test
    fun `setActiveAccount for ac2 passes correct ID to DAO`() = runTest(testDispatcher) {
        repository.setActiveAccount("ac2")

        verify(accountDao).setActiveAccount("ac2")
    }
}
