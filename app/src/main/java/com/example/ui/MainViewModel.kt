package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BlockedNumberEntity
import com.example.data.SmsRepository
import com.example.model.ConversationSummary
import com.example.model.SimInfo
import com.example.model.SmsMessage
import com.example.telephony.TelephonyManagerHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val selectedAddress: String? = null,
    val selectedThreadId: Long? = null,
    val searchQuery: String = "",
    val selectedFilterTab: Int = 0, // 0 = All, 1 = Unread, 2 = Blocked
    val isDefaultSmsApp: Boolean = false,
    val activeSimCards: List<SimInfo> = emptyList(),
    val selectedSimSlotIndex: Int = 0,
    val deliveryReportsEnabled: Boolean = true,
    val customSmsc: String = "",
    val signatureEnabled: Boolean = false,
    val signatureText: String = "",
    val themeMode: Int = 0, // 0 = System, 1 = Light, 2 = Dark
    val isComposeDialogOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isSyncing: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmsRepository(application)
    private val prefs = repository.preferences

    private val _uiState = MutableStateFlow(
        MainUiState(
            isDefaultSmsApp = TelephonyManagerHelper.isDefaultSmsApp(application),
            activeSimCards = TelephonyManagerHelper.getActiveSimCards(application),
            deliveryReportsEnabled = prefs.deliveryReportsEnabled,
            customSmsc = prefs.customSmsc,
            signatureEnabled = prefs.signatureEnabled,
            signatureText = prefs.signatureText,
            themeMode = prefs.themeMode
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val conversations: StateFlow<List<ConversationSummary>> = combine(
        repository.getConversationsFlow(),
        _uiState
    ) { list, state ->
        var filtered = list
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.trim().lowercase()
            filtered = filtered.filter {
                it.address.lowercase().contains(q) ||
                (it.contactName?.lowercase()?.contains(q) == true) ||
                it.snippet.lowercase().contains(q)
            }
        }
        when (state.selectedFilterTab) {
            1 -> filtered.filter { it.unreadCount > 0 }
            else -> filtered
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentThreadMessages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val currentThreadMessages: StateFlow<List<SmsMessage>> = _currentThreadMessages.asStateFlow()

    val blockedNumbers: StateFlow<List<BlockedNumberEntity>> =
        repository.getBlockedNumbersFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshTelephonyState()
        syncSystemMessages()
    }

    fun refreshTelephonyState() {
        val app = getApplication<Application>()
        val isDefault = TelephonyManagerHelper.isDefaultSmsApp(app)
        val sims = TelephonyManagerHelper.getActiveSimCards(app)
        _uiState.value = _uiState.value.copy(
            isDefaultSmsApp = isDefault,
            activeSimCards = sims
        )
    }

    fun syncSystemMessages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            try {
                repository.syncWithSystemSms()
            } catch (e: Exception) {
                e.printStackTrace()
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
            repository.getMessagesForAddress(address).collect { messages ->
                _currentThreadMessages.value = messages
            }
        }
    }

    fun clearSelectedConversation() {
        _uiState.value = _uiState.value.copy(
            selectedAddress = null,
            selectedThreadId = null
        )
        _currentThreadMessages.value = emptyList()
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
            repository.sendMessage(
                recipientAddress = recipient.trim(),
                body = body.trim(),
                simInfo = chosenSim
            )
            // If starting a fresh message not in current thread, open thread
            if (_uiState.value.selectedAddress == null) {
                val threadId = kotlin.math.abs(recipient.trim().hashCode().toLong())
                selectConversation(recipient.trim(), threadId)
            }
        }
    }

    fun deleteConversation(address: String, threadId: Long) {
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

    fun openComposeDialog(open: Boolean) {
        _uiState.value = _uiState.value.copy(isComposeDialogOpen = open)
    }

    fun openSettings(open: Boolean) {
        _uiState.value = _uiState.value.copy(isSettingsOpen = open)
    }
}
