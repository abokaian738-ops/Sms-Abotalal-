package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BlockedNumberEntity
import com.example.data.SmsRepository
import com.example.model.ContactInfo
import com.example.model.ConversationSummary
import com.example.model.SimInfo
import com.example.model.SmsMessage
import com.example.telephony.ContactsHelper
import com.example.telephony.TelephonyManagerHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val selectedAddress: String? = null,
    val selectedThreadId: Long? = null,
    val searchQuery: String = "",
    val selectedFilterTab: Int = 0, // 0 = All, 1 = Unread
    val isDefaultSmsApp: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val activeSimCards: List<SimInfo> = emptyList(),
    val selectedSimSlotIndex: Int = 0,
    val deliveryReportsEnabled: Boolean = true,
    val customSmsc: String = "",
    val signatureEnabled: Boolean = false,
    val signatureText: String = "",
    val themeMode: Int = 0, // 0 = System, 1 = Light, 2 = Dark
    val isComposeDialogOpen: Boolean = false,
    val prefillComposeText: String = "",
    val isSettingsOpen: Boolean = false,
    val isSyncing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmsRepository(application)
    private val prefs = repository.preferences

    private val _uiState = MutableStateFlow(
        MainUiState(
            isDefaultSmsApp = TelephonyManagerHelper.isDefaultSmsApp(application),
            hasContactsPermission = ContactsHelper.hasContactsPermission(application),
            activeSimCards = TelephonyManagerHelper.getActiveSimCards(application),
            deliveryReportsEnabled = prefs.deliveryReportsEnabled,
            customSmsc = prefs.customSmsc,
            signatureEnabled = prefs.signatureEnabled,
            signatureText = prefs.signatureText,
            themeMode = prefs.themeMode
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _deviceContacts = MutableStateFlow<List<ContactInfo>>(emptyList())
    val deviceContacts: StateFlow<List<ContactInfo>> = _deviceContacts.asStateFlow()

    val conversations: StateFlow<List<ConversationSummary>> = combine(
        repository.getConversationsFlow(),
        _uiState.map { it.searchQuery to it.selectedFilterTab }.distinctUntilChanged()
    ) { list, (query, filterTab) ->
        var filtered = list
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            filtered = filtered.filter {
                it.address.lowercase().contains(q) ||
                    (it.contactName?.lowercase()?.contains(q) == true) ||
                    it.snippet.lowercase().contains(q)
            }
        }
        when (filterTab) {
            1 -> filtered.filter { it.unreadCount > 0 }
            else -> filtered
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentThreadMessages: StateFlow<List<SmsMessage>> = _uiState
        .map { it.selectedAddress }
        .distinctUntilChanged()
        .flatMapLatest { address ->
            if (address.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.getMessagesForAddress(address)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedNumbers: StateFlow<List<BlockedNumberEntity>> =
        repository.getBlockedNumbersFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshTelephonyState()
        refreshContacts()
        syncSystemMessages()
    }

    fun refreshTelephonyState() {
        val app = getApplication<Application>()
        val isDefault = TelephonyManagerHelper.isDefaultSmsApp(app)
        val hasContacts = ContactsHelper.hasContactsPermission(app)
        val sims = TelephonyManagerHelper.getActiveSimCards(app)
        val currentSlot = _uiState.value.selectedSimSlotIndex
        val safeSlot = if (sims.isNotEmpty() && currentSlot >= sims.size) 0 else currentSlot

        _uiState.value = _uiState.value.copy(
            isDefaultSmsApp = isDefault,
            hasContactsPermission = hasContacts,
            activeSimCards = sims,
            selectedSimSlotIndex = safeSlot
        )
    }

    fun refreshContacts() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val hasPerm = ContactsHelper.hasContactsPermission(app)
            _uiState.value = _uiState.value.copy(hasContactsPermission = hasPerm)
            if (hasPerm) {
                _deviceContacts.value = repository.loadDeviceContacts()
            } else {
                _deviceContacts.value = emptyList()
            }
        }
    }

    suspend fun resolvePickedContact(uri: Uri): ContactInfo? {
        return repository.resolvePickedContact(uri)
    }

    fun syncSystemMessages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            try {
                repository.syncWithSystemSms()
            } catch (_: Exception) {
            } finally {
                _uiState.value = _uiState.value.copy(isSyncing = false)
            }
        }
    }

    fun selectConversation(address: String, threadId: Long) {
        _uiState.value = _uiState.value.copy(
            selectedAddress = address,
            selectedThreadId = threadId
        )
        viewModelScope.launch {
            repository.markAddressAsRead(address)
        }
    }

    fun clearSelectedConversation() {
        _uiState.value = _uiState.value.copy(
            selectedAddress = null,
            selectedThreadId = null
        )
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setFilterTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedFilterTab = tab)
    }

    fun setSelectedSimSlot(index: Int) {
        _uiState.value = _uiState.value.copy(selectedSimSlotIndex = index)
    }

    fun sendMessage(recipient: String, body: String) {
        if (recipient.isBlank() || body.isBlank()) return
        val currentSims = _uiState.value.activeSimCards
        val selectedSlot = _uiState.value.selectedSimSlotIndex
        val chosenSim = currentSims.getOrNull(selectedSlot) ?: currentSims.firstOrNull()

        viewModelScope.launch {
            val cleanRecipient = recipient.trim()
            repository.sendMessage(
                recipientAddress = cleanRecipient,
                body = body.trim(),
                simInfo = chosenSim
            )
            if (_uiState.value.selectedAddress == null) {
                val threadId = kotlin.math.abs(cleanRecipient.hashCode().toLong())
                selectConversation(cleanRecipient, threadId)
            }
        }
    }

    fun deleteConversation(address: String) {
        viewModelScope.launch {
            repository.deleteAddress(address)
            if (_uiState.value.selectedAddress == address) {
                clearSelectedConversation()
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun blockNumber(number: String) {
        viewModelScope.launch {
            repository.blockNumber(number)
        }
    }

    fun unblockNumber(number: String) {
        viewModelScope.launch {
            repository.unblockNumber(number)
        }
    }

    fun setDeliveryReportsEnabled(enabled: Boolean) {
        prefs.deliveryReportsEnabled = enabled
        _uiState.value = _uiState.value.copy(deliveryReportsEnabled = enabled)
    }

    fun setCustomSmsc(smsc: String) {
        prefs.customSmsc = smsc.trim()
        _uiState.value = _uiState.value.copy(customSmsc = smsc.trim())
    }

    fun setSignature(enabled: Boolean, text: String) {
        prefs.signatureEnabled = enabled
        prefs.signatureText = text
        _uiState.value = _uiState.value.copy(signatureEnabled = enabled, signatureText = text)
    }

    fun setThemeMode(mode: Int) {
        prefs.themeMode = mode
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun openComposeDialog(open: Boolean, prefillText: String = "") {
        _uiState.value = _uiState.value.copy(
            isComposeDialogOpen = open,
            prefillComposeText = if (open) prefillText else ""
        )
        if (open) {
            refreshContacts()
        }
    }

    fun openSettings(open: Boolean) {
        _uiState.value = _uiState.value.copy(isSettingsOpen = open)
    }
}
