package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SimInfo
import com.example.ui.theme.Sim1Color
import com.example.ui.theme.Sim2Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeMessageDialog(
    activeSims: List<SimInfo>,
    selectedSimSlotIndex: Int,
    onSelectSimSlot: (Int) -> Unit,
    onSend: (recipient: String, message: String) -> Unit,
    onDismiss: () -> Unit
) {
    var recipient by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }

    val currentSim = activeSims.getOrNull(selectedSimSlotIndex) ?: activeSims.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
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

            Spacer(modifier = Modifier.height(14.dp))

            // Recipient Field
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = { Text("إلى (رقم الهاتف أو جهة الاتصال)") },
                placeholder = { Text("مثال: +9665XXXXXXXX أو 05XXXXXXXX") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compose_recipient_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // SIM Selector Row
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Message Body Field
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("نص الرسالة") },
                placeholder = { Text("اكتب محتوى الرسالة هنا...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .testTag("compose_message_input"),
                shape = RoundedCornerShape(12.dp),
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Send Button
            Button(
                onClick = {
                    if (recipient.isNotBlank() && messageText.isNotBlank()) {
                        onSend(recipient, messageText)
                        onDismiss()
                    }
                },
                enabled = recipient.isNotBlank() && messageText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("compose_send_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إرسال الرسالة الآن", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
