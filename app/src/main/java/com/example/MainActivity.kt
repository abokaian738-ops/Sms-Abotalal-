package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.telephony.TelephonyManagerHelper
import com.example.ui.*
import com.example.ui.theme.TalalSmsTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val conversations by viewModel.conversations.collectAsState()
            val threadMessages by viewModel.currentThreadMessages.collectAsState()
            val blockedNumbers by viewModel.blockedNumbers.collectAsState()

            // Dynamic Dark/Light Theme based on user setting
            val isDark = when (uiState.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            // Permissions Launcher
            val permissionsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                viewModel.refreshTelephonyState()
                viewModel.syncSystemMessages()
            }

            // Default SMS App Launcher
            val defaultSmsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                viewModel.refreshTelephonyState()
            }

            LaunchedEffect(Unit) {
                // Request runtime permissions on first launch
                val requiredPermissions = mutableListOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CONTACTS
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }

                val missing = requiredPermissions.filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isNotEmpty()) {
                    permissionsLauncher.launch(missing.toTypedArray())
                }
            }

            TalalSmsTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        uiState.isSettingsOpen -> {
                            SettingsScreen(
                                uiState = uiState,
                                blockedNumbers = blockedNumbers,
                                onBack = { viewModel.openSettings(false) },
                                onSetDefaultSms = {
                                    val intent = TelephonyManagerHelper.createDefaultSmsIntent(this@MainActivity)
                                    defaultSmsLauncher.launch(intent)
                                },
                                onToggleDeliveryReports = { viewModel.setDeliveryReportsEnabled(it) },
                                onSaveCustomSmsc = { viewModel.setCustomSmsc(it) },
                                onSaveSignature = { enabled, text -> viewModel.setSignature(enabled, text) },
                                onSetThemeMode = { viewModel.setThemeMode(it) },
                                onBlockNumber = { viewModel.blockNumber(it) },
                                onUnblockNumber = { viewModel.unblockNumber(it) }
                            )
                        }

                        uiState.selectedAddress != null -> {
                            MessageThreadScreen(
                                address = uiState.selectedAddress!!,
                                threadId = uiState.selectedThreadId ?: 0L,
                                messages = threadMessages,
                                activeSims = uiState.activeSimCards,
                                selectedSimSlotIndex = uiState.selectedSimSlotIndex,
                                deliveryReportsEnabled = uiState.deliveryReportsEnabled,
                                onSelectSimSlot = { viewModel.setSelectedSimSlot(it) },
                                onSendMessage = { recipient, text ->
                                    viewModel.sendMessage(recipient, text)
                                },
                                onBack = { viewModel.clearSelectedConversation() },
                                onDeleteThread = {
                                    viewModel.deleteConversation(
                                        uiState.selectedAddress!!,
                                        uiState.selectedThreadId ?: 0L
                                    )
                                },
                                onBlockNumber = { viewModel.blockNumber(it) }
                            )
                        }

                        else -> {
                            ConversationListScreen(
                                conversations = conversations,
                                uiState = uiState,
                                onSelectConversation = { address, threadId ->
                                    viewModel.selectConversation(address, threadId)
                                },
                                onDeleteConversation = { address, threadId ->
                                    viewModel.deleteConversation(address, threadId)
                                },
                                onBlockNumber = { viewModel.blockNumber(it) },
                                onOpenCompose = { viewModel.openComposeDialog(true) },
                                onOpenSettings = { viewModel.openSettings(true) },
                                onSetDefaultSms = {
                                    val intent = TelephonyManagerHelper.createDefaultSmsIntent(this@MainActivity)
                                    defaultSmsLauncher.launch(intent)
                                },
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                onTabSelected = { viewModel.setFilterTab(it) },
                                onSyncMessages = { viewModel.syncSystemMessages() }
                            )
                        }
                    }

                    // Compose New Message Dialog
                    if (uiState.isComposeDialogOpen) {
                        ComposeMessageDialog(
                            activeSims = uiState.activeSimCards,
                            selectedSimSlotIndex = uiState.selectedSimSlotIndex,
                            onSelectSimSlot = { viewModel.setSelectedSimSlot(it) },
                            onSend = { recipient, body ->
                                viewModel.sendMessage(recipient, body)
                            },
                            onDismiss = { viewModel.openComposeDialog(false) }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshTelephonyState()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action

        // Check if opened from notification
        val threadId = intent.getLongExtra("EXTRA_THREAD_ID", -1L)
        val address = intent.getStringExtra("EXTRA_ADDRESS")
        if (!address.isNullOrBlank()) {
            viewModel.selectConversation(address, if (threadId != -1L) threadId else kotlin.math.abs(address.hashCode().toLong()))
            return
        }

        // Check if opened via SEND or SENDTO (e.g. sms:0501234567 or smsto:0501234567)
        if (action == Intent.ACTION_SENDTO || action == Intent.ACTION_VIEW) {
            val uri: Uri? = intent.data
            if (uri != null) {
                val scheme = uri.scheme
                if (scheme == "sms" || scheme == "smsto" || scheme == "mms" || scheme == "mmsto") {
                    val recipient = uri.schemeSpecificPart?.substringBefore('?') ?: ""
                    if (recipient.isNotBlank()) {
                        val newThreadId = kotlin.math.abs(recipient.hashCode().toLong())
                        viewModel.selectConversation(recipient, newThreadId)
                    }
                }
            }
        } else if (action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.openComposeDialog(true)
            }
        }
    }
}
