package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SmsEntity
import com.example.model.DeliveryStatus
import com.example.model.SimInfo
import com.example.model.SmsMessage
import com.example.ui.theme.Sim1Color
import com.example.ui.theme.Sim2Color
import com.example.ui.theme.StatusDelivered
import com.example.ui.theme.StatusFailed
import com.example.ui.theme.StatusPending
import com.example.ui.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageThreadScreen(
    address: String,
    messages: List<SmsMessage>,
    activeSims: List<SimInfo>,
    selectedSimSlotIndex: Int,
    deliveryReportsEnabled: Boolean,
    onSelectSimSlot: (Int) -> Unit,
    onSendMessage: (recipient: String, text: String) -> Unit,
    onBack: () -> Unit,
    onDeleteThread: () -> Unit,
    onBlockNumber: (String) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val listState = rememberLazyListState()

    var textInput by remember { mutableStateOf("") }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showSimPickerSheet by remember { mutableStateOf(false) }
    var selectedMessageForDetails by remember { mutableStateOf<SmsMessage?>(null) }

    val contactName = messages.firstOrNull()?.contactName
    val displayName = contactName ?: address

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Character counter logic
    val isAscii = textInput.all { it.code < 128 }
    val maxSinglePart = if (isAscii) 160 else 70
    val maxMultiPart = if (isAscii) 153 else 67
    val charCount = textInput.length
    val smsParts = if (charCount <= maxSinglePart) 1 else ((charCount - 1) / maxMultiPart) + 1

    val currentSim = activeSims.getOrNull(selectedSimSlotIndex) ?: activeSims.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (contactName != null) {
                                Text(
                                    text = address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$address"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.testTag("call_contact_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "اتصال"
                        )
                    }
                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "خيارات المحادثة")
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("حظر هذا الرقم") },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                onClick = {
                                    showOptionsMenu = false
                                    onBlockNumber(address)
                                    Toast.makeText(context, "تمت إضافة الرقم لقائمة الحظر", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("حذف هذه المحادثة") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showOptionsMenu = false
                                    onDeleteThread()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // SIM Card Selector & Character Counter Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentSim != null) {
                        val simColor = if (currentSim.simSlotIndex == 0) Sim1Color else Sim2Color
                        Surface(
                            onClick = { if (activeSims.size > 1) showSimPickerSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            color = simColor.copy(alpha = 0.15f),
                            modifier = Modifier.testTag("sim_selector_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = null,
                                    tint = simColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "شريحة ${currentSim.simSlotIndex + 1}: ${currentSim.carrierName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = simColor
                                )
                                if (activeSims.size > 1) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = simColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "الشريحة الافتراضية للنظام",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "$charCount/$maxSinglePart • $smsParts SMS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Input Field and Send Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("اكتب رسالتك هنا...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                onSendMessage(address, textInput)
                                textInput = ""
                            }
                        },
                        enabled = textInput.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "إرسال الرسالة"
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (deliveryReportsEnabled) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusDelivered,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تقارير التسليم مفعلة عبر مشغل شبكة الجوال",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .testTag("thread_messages_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        message = msg,
                        onClick = { selectedMessageForDetails = msg },
                        onRetry = { onSendMessage(msg.address, msg.body) }
                    )
                }
            }
        }
    }

    // SIM Selection Bottom Sheet
    if (showSimPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSimPickerSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "اختيار شريحة الإرسال",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                activeSims.forEachIndexed { index, sim ->
                    val simColor = if (sim.simSlotIndex == 0) Sim1Color else Sim2Color
                    Surface(
                        onClick = {
                            onSelectSimSlot(index)
                            showSimPickerSheet = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedSimSlotIndex == index) simColor.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (selectedSimSlotIndex == index) {
                            androidx.compose.foundation.BorderStroke(2.dp, simColor)
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("sim_option_$index")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SimCard,
                                contentDescription = null,
                                tint = simColor,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${sim.carrierName} (شريحة ${sim.simSlotIndex + 1})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "مركز الرسائل SMSC: ${sim.smscNumber ?: "تلقائي من الشريحة"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selectedSimSlotIndex == index) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = simColor
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Message Details Dialog
    selectedMessageForDetails?.let { msg ->
        AlertDialog(
            onDismissRequest = { selectedMessageForDetails = null },
            title = {
                Text("تفاصيل الرسالة", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المرسل/المستقبل: ${msg.address}", style = MaterialTheme.typography.bodyMedium)
                    Text("التاريخ والوقت: ${DateUtils.formatDetailedDate(msg.date)}", style = MaterialTheme.typography.bodyMedium)
                    Text("الشريحة المستخدمة: ${msg.carrierName}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "حالة التسليم: " + when (msg.deliveryStatus) {
                            DeliveryStatus.DELIVERED -> "تم التسليم بنجاح للمستقبل ✓✓"
                            DeliveryStatus.SENT -> "تم الإرسال لمركز الرسائل ✓"
                            DeliveryStatus.PENDING -> "قيد التسليم إلى هاتف المستقبل ⏳"
                            DeliveryStatus.FAILED -> "فشل التسليم ⚠️"
                            DeliveryStatus.NONE -> "لم يُطلب تقرير تسليم"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!msg.smsc.isNullOrBlank()) {
                        Text("مركز الرسائل (SMSC): ${msg.smsc}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SMS Text", msg.body))
                        Toast.makeText(context, "تم نسخ النص", Toast.LENGTH_SHORT).show()
                        selectedMessageForDetails = null
                    }
                ) {
                    Text("نسخ النص")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedMessageForDetails = null }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

@Composable
fun MessageBubble(
    message: SmsMessage,
    onClick: () -> Unit,
    onRetry: () -> Unit
) {
    val isOutgoing = message.type != SmsEntity.TYPE_INBOX

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isOutgoing) 16.dp else 4.dp,
                    bottomEnd = if (isOutgoing) 4.dp else 16.dp
                ),
                color = if (isOutgoing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .testTag("message_bubble_${message.id}")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isOutgoing) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = DateUtils.formatMessageTime(message.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOutgoing) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )

                        if (isOutgoing) {
                            Spacer(modifier = Modifier.width(4.dp))
                            when (message.deliveryStatus) {
                                DeliveryStatus.DELIVERED -> {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = "تم التسليم",
                                        tint = StatusDelivered,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                DeliveryStatus.SENT -> {
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = "تم الإرسال",
                                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                DeliveryStatus.PENDING -> {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "قيد الإرسال",
                                        tint = StatusPending,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                DeliveryStatus.FAILED -> {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "فشل التسليم - اضغط لإعادة الإرسال",
                                        tint = StatusFailed,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onRetry() }
                                    )
                                }
                                DeliveryStatus.NONE -> {}
                            }
                        }
                    }
                }
            }

            if (isOutgoing) {
                Row(
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.carrierName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                    if (message.deliveryStatus == DeliveryStatus.DELIVERED) {
                        Text(
                            text = " • تم التسليم بنجاح ✓✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusDelivered,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (message.deliveryStatus == DeliveryStatus.FAILED) {
                        Text(
                            text = " • فشل الإرسال (اضغط لإعادة المحاولة)",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusFailed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onRetry() }
                        )
                    }
                }
            }
        }
    }
}
