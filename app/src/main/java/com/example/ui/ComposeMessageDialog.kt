package com.example.ui

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ContactInfo
import com.example.model.SimInfo
import com.example.telephony.ContactsHelper
import com.example.ui.theme.Sim1Color
import com.example.ui.theme.Sim2Color
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeMessageDialog(
    activeSims: List<SimInfo>,
    selectedSimSlotIndex: Int,
    contacts: List<ContactInfo>,
    hasContactsPermission: Boolean,
    prefillText: String = "",
    onRequestContactsPermission: () -> Unit,
    onResolvePickedContact: suspend (Uri) -> ContactInfo?,
    onSelectSimSlot: (Int) -> Unit,
    onSend: (recipient: String, message: String) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var recipientInput by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<ContactInfo?>(null) }
    var messageText by remember(prefillText) { mutableStateOf(prefillText) }
    var showContactsDrawer by remember { mutableStateOf(false) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val dataUri = result.data?.data
        if (dataUri != null) {
            coroutineScope.launch {
                val picked = onResolvePickedContact(dataUri)
                if (picked != null) {
                    selectedContact = picked
                    recipientInput = picked.phoneNumber
                    showContactsDrawer = false
                }
            }
        }
    }

    // Fast filtered contacts matching typed name or phone number
    val filteredContacts by remember(recipientInput, contacts, showContactsDrawer) {
        derivedStateOf {
            val query = recipientInput.trim()
            if (query.isEmpty()) {
                if (showContactsDrawer) contacts.take(40) else emptyList()
            } else {
                val lower = query.lowercase()
                val digitsQuery = ContactsHelper.normalizePhoneNumber(query)
                contacts.filter { contact ->
                    contact.name.lowercase().contains(lower) ||
                        contact.phoneNumber.contains(query) ||
                        (digitsQuery.isNotEmpty() && contact.normalizedNumber.contains(digitsQuery))
                }.take(30)
            }
        }
    }

    val isTypingDifferentFromSelected = selectedContact != null &&
        recipientInput.trim() != selectedContact?.phoneNumber

    LaunchedEffect(isTypingDifferentFromSelected) {
        if (isTypingDifferentFromSelected) {
            selectedContact = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "رسالة جديدة",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Selected contact pill if chosen
            AnimatedVisibility(visible = selectedContact != null) {
                selectedContact?.let { contact ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = contact.phoneNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            IconButton(
                                onClick = {
                                    selectedContact = null
                                    recipientInput = ""
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "تغيير المستلم")
                            }
                        }
                    }
                }
            }

            // Recipient Input with direct Contacts picker icon
            OutlinedTextField(
                value = recipientInput,
                onValueChange = {
                    recipientInput = it
                    if (it.isNotBlank()) showContactsDrawer = true
                },
                label = { Text("إلى (اكتب اسم جهة الاتصال أو الرقم)") },
                placeholder = { Text("ابحث بالاسم أو أدخل رقم الهاتف") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (recipientInput.isNotEmpty()) {
                            IconButton(onClick = {
                                recipientInput = ""
                                selectedContact = null
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "مسح")
                            }
                        }
                        IconButton(
                            onClick = {
                                val pickIntent = Intent(
                                    Intent.ACTION_PICK,
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                                )
                                contactPickerLauncher.launch(pickIntent)
                            },
                            modifier = Modifier.testTag("pick_contact_system_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contacts,
                                contentDescription = "اختيار من جهات الاتصال",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compose_recipient_input"),
                shape = RoundedCornerShape(14.dp)
            )

            // Quick Contacts Toggle & Permission Prompt
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        if (!hasContactsPermission) {
                            onRequestContactsPermission()
                        } else {
                            showContactsDrawer = !showContactsDrawer
                        }
                    },
                    modifier = Modifier.testTag("toggle_contacts_list_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Contacts,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            !hasContactsPermission -> "منح صلاحية جهات الاتصال للبحث السريع"
                            showContactsDrawer -> "إخفاء قائمة جهات الاتصال"
                            else -> "عرض جهات الاتصال (${contacts.size})"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (hasContactsPermission && filteredContacts.isNotEmpty() && selectedContact == null) {
                    Text(
                        text = "${filteredContacts.size} نتيجة",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Live Matching Contacts List
            AnimatedVisibility(
                visible = selectedContact == null && (filteredContacts.isNotEmpty() || (showContactsDrawer && hasContactsPermission))
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 210.dp)
                        .padding(vertical = 4.dp)
                ) {
                    if (filteredContacts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد جهات اتصال مطابقة للبحث",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("compose_contacts_suggestions")
                        ) {
                            items(
                                items = filteredContacts,
                                key = { "${it.id}_${it.normalizedNumber}" }
                            ) { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedContact = contact
                                            recipientInput = contact.phoneNumber
                                            showContactsDrawer = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = contact.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = contact.phoneNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!contact.label.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = contact.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SIM Selector Row (only if active SIMs detected)
            if (activeSims.isNotEmpty()) {
                Text(
                    text = "الإرسال عبر شريحة الاتصال:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeSims.forEachIndexed { index, sim ->
                        val isSelected = (selectedSimSlotIndex == index)
                        val simColor = if (sim.simSlotIndex == 0) Sim1Color else Sim2Color

                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectSimSlot(index) },
                            label = {
                                Text(
                                    text = "شريحة ${sim.simSlotIndex + 1}: ${sim.carrierName}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = null,
                                    tint = if (isSelected) simColor else MaterialTheme.colorScheme.outline
                                )
                            },
                            modifier = Modifier.testTag("compose_sim_chip_$index")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Message Body Field
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("نص الرسالة") },
                placeholder = { Text("اكتب محتوى الرسالة هنا...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .testTag("compose_message_input"),
                shape = RoundedCornerShape(14.dp),
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(14.dp))

            val targetNumber = selectedContact?.phoneNumber ?: recipientInput.trim()
            val canSend = targetNumber.isNotBlank() && messageText.isNotBlank()

            Button(
                onClick = {
                    if (canSend) {
                        onSend(targetNumber, messageText)
                        onDismiss()
                    }
                },
                enabled = canSend,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("compose_send_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إرسال الرسالة الآن", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
