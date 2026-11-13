package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConversationSummary
import com.example.model.DeliveryStatus
import com.example.ui.theme.Sim1Color
import com.example.ui.theme.Sim2Color
import com.example.ui.theme.StatusDelivered
import com.example.ui.theme.StatusFailed
import com.example.ui.theme.StatusPending
import com.example.ui.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    conversations: List<ConversationSummary>,
    uiState: MainUiState,
    onSelectConversation: (address: String, threadId: Long) -> Unit,
    onDeleteConversation: (address: String) -> Unit,
    onBlockNumber: (number: String) -> Unit,
    onOpenCompose: () -> Unit,
    onOpenSettings: () -> Unit,
    onSetDefaultSms: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onTabSelected: (Int) -> Unit,
    onSyncMessages: () -> Unit
) {
    val context = LocalContext.current
    var isSearchActive by remember { mutableStateOf(false) }
    var showDeveloperDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "TALAL SMS",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { isSearchActive = !isSearchActive },
                            modifier = Modifier.testTag("search_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "بحث"
                            )
                        }
                        IconButton(
                            onClick = onSyncMessages,
                            modifier = Modifier.testTag("sync_messages_button")
                        ) {
                            if (uiState.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "مزامنة الرسائل"
                                )
                            }
                        }
                        IconButton(
                            onClick = { showDeveloperDialog = true },
                            modifier = Modifier.testTag("developer_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "حقوق المبرمج والمطور"
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "الإعدادات"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Search Bar Input
                AnimatedVisibility(visible = isSearchActive) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("بحث في الرسائل وجهات الاتصال...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("search_text_field"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Default SMS App Warning Banner
                if (!uiState.isDefaultSmsApp) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("default_sms_banner"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تعيين TALAL SMS كافتراضي",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "فعّل التطبيق الافتراضي لتمكين الاستقبال التلقائي وتقارير التسليم",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = onSetDefaultSms,
                                modifier = Modifier.testTag("set_default_sms_button")
                            ) {
                                Text("تعيين", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Multi-SIM Operators Bar
                if (uiState.activeSimCards.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.activeSimCards, key = { it.subscriptionId }) { sim ->
                            val simColor = if (sim.simSlotIndex == 0) Sim1Color else Sim2Color
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = simColor.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, simColor.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SimCard,
                                        contentDescription = null,
                                        tint = simColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${sim.carrierName} (شريحة ${sim.simSlotIndex + 1})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (sim.isDefaultSms) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "• الافتراضية",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = simColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Filter Tabs
                TabRow(
                    selectedTabIndex = uiState.selectedFilterTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Tab(
                        selected = uiState.selectedFilterTab == 0,
                        onClick = { onTabSelected(0) },
                        text = { Text("الكل (${conversations.size})") }
                    )
                    Tab(
                        selected = uiState.selectedFilterTab == 1,
                        onClick = { onTabSelected(1) },
                        text = {
                            val unread = conversations.count { it.unreadCount > 0 }
                            Text("غير مقروءة ($unread)")
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenCompose,
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                text = { Text("رسالة جديدة", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("compose_fab")
            )
        }
    ) { innerPadding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.MarkChatUnread,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "لا توجد رسائل حالياً",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "اضغط على 'رسالة جديدة' لاختيار جهة اتصال وبدء المحادثة، أو اضغط على أيقونة المزامنة لاستيراد رسائل الهاتف",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("conversations_list")
            ) {
                items(
                    items = conversations,
                    key = { it.address }
                ) { conv ->
                    ConversationItem(
                        conversation = conv,
                        onClick = { onSelectConversation(conv.address, conv.threadId) },
                        onDelete = { onDeleteConversation(conv.address) },
                        onCall = {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${conv.address}"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        onBlock = { onBlockNumber(conv.address) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }

    if (showDeveloperDialog) {
        AlertDialog(
            onDismissRequest = { showDeveloperDialog = false },
            title = { Text("حقوق المبرمج والمطور", fontWeight = FontWeight.Bold) },
            text = { DeveloperRightsCard(context = context) },
            confirmButton = {
                TextButton(onClick = { showDeveloperDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

@Composable
fun ConversationItem(
    conversation: ConversationSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit,
    onBlock: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val displayName = conversation.contactName ?: conversation.address
    val initial = displayName.take(1).uppercase()
    val isUnread = conversation.unreadCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("conversation_item_${conversation.address}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isUnread) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = if (isUnread) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = DateUtils.formatMessageTime(conversation.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUnread) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DeliveryStatusIcon(status = DeliveryStatus.fromCode(conversation.lastDeliveryStatus))

                Text(
                    text = conversation.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUnread) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isUnread) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            text = conversation.unreadCount.toString(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "خيارات",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("اتصال") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onCall()
                    }
                )
                DropdownMenuItem(
                    text = { Text("حظر الرقم") },
                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onBlock()
                    }
                )
                DropdownMenuItem(
                    text = { Text("حذف المحادثة") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
fun DeliveryStatusIcon(status: DeliveryStatus) {
    when (status) {
        DeliveryStatus.DELIVERED -> {
            Row(modifier = Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = "تم التسليم",
                    tint = StatusDelivered,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DeliveryStatus.SENT -> {
            Row(modifier = Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = "تم الإرسال",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DeliveryStatus.PENDING -> {
            Row(modifier = Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "قيد الإرسال",
                    tint = StatusPending,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        DeliveryStatus.FAILED -> {
            Row(modifier = Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "فشل التسليم",
                    tint = StatusFailed,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DeliveryStatus.NONE -> {}
    }
}
