package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.BlockedNumberEntity
import com.example.ui.theme.DirectCallBlue
import com.example.ui.theme.Sim1Color
import com.example.ui.theme.Sim2Color
import com.example.ui.theme.StatusDelivered
import com.example.ui.theme.WhatsAppGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: MainUiState,
    blockedNumbers: List<BlockedNumberEntity>,
    onBack: () -> Unit,
    onSetDefaultSms: () -> Unit,
    onToggleDeliveryReports: (Boolean) -> Unit,
    onSaveCustomSmsc: (String) -> Unit,
    onSaveSignature: (Boolean, String) -> Unit,
    onSetThemeMode: (Int) -> Unit,
    onBlockNumber: (String) -> Unit,
    onUnblockNumber: (String) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var customSmscInput by remember(uiState.customSmsc) { mutableStateOf(uiState.customSmsc) }
    var signatureInput by remember(uiState.signatureText) { mutableStateOf(uiState.signatureText) }
    var signatureEnabledState by remember(uiState.signatureEnabled) { mutableStateOf(uiState.signatureEnabled) }
    var newBlockNumberInput by remember { mutableStateOf("") }
    var showAddBlockDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                title = {
                    Text(
                        text = "إعدادات TALAL SMS",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("settings_list"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 1. Default SMS App Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.isDefaultSmsApp)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (uiState.isDefaultSmsApp) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (uiState.isDefaultSmsApp) StatusDelivered else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تطبيق الرسائل الافتراضي",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (uiState.isDefaultSmsApp)
                                "TALAL SMS هو التطبيق الافتراضي للرسائل في هذا الجهاز. يعمل بكامل المزايا وفي الخلفية."
                            else
                                "التطبيق ليس الافتراضي حالياً. يُرجى تعيينه لضمان استلام الرسائل تلقائياً وتقارير التسليم.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onSetDefaultSms,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("set_default_sms_settings_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isDefaultSmsApp) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                text = if (uiState.isDefaultSmsApp) "تغيير التطبيق الافتراضي" else "تعيين TALAL SMS كافتراضي الآن",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. Multi-SIM & Network Operators Section
            item {
                SettingsSectionHeader(title = "الشرائح ومشغلو شبكات الجوال (Dual SIM)", icon = Icons.Default.SimCard)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (uiState.activeSimCards.isEmpty()) {
                            Text(
                                text = "لا توجد شرائح اتصال نشطة مكتشفة حالياً، أو لم يتم منح صلاحية حالة الهاتف.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.activeSimCards.forEach { sim ->
                                val simColor = if (sim.simSlotIndex == 0) Sim1Color else Sim2Color
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = simColor.copy(alpha = 0.1f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, simColor.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.SimCard, contentDescription = null, tint = simColor, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "شريحة ${sim.simSlotIndex + 1}: ${sim.carrierName}",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                            }
                                            if (sim.isDefaultSms) {
                                                Badge(containerColor = simColor) {
                                                    Text("الافتراضية للرسائل", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "رمز المشغل: ${sim.operatorNumeric ?: "غير متوفر"} • رمز الدولة: ${sim.countryIso ?: "غير محدد"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "مركز الرسائل (SMSC): ${sim.smscNumber ?: "تلقائي من الشريحة"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. SMS Center (SMSC) Section
            item {
                SettingsSectionHeader(title = "مركز خدمة الرسائل النصية (SMSC)", icon = Icons.Default.SendToMobile)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "اتركه فارغاً لاستخدام مركز الرسائل التلقائي الخاص بشريحة الاتصال، أو أدخل رقم مركز الرسائل لمشغل الشبكة يدوياً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customSmscInput,
                            onValueChange = { customSmscInput = it },
                            label = { Text("رقم مركز الرسائل (SMSC) - اختياري") },
                            placeholder = { Text("اتركه فارغاً للوضع التلقائي") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("smsc_input_field"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            FilledTonalButton(
                                onClick = {
                                    onSaveCustomSmsc(customSmscInput)
                                    Toast.makeText(context, "تم حفظ إعدادات مركز الرسائل", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("save_smsc_button")
                            ) {
                                Text("حفظ رقم المركز")
                            }
                        }
                    }
                }
            }

            // 4. Delivery Reports Section
            item {
                SettingsSectionHeader(title = "تقارير التسليم (Delivery Reports)", icon = Icons.Default.DoneAll)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "طلب تقرير تسليم لكل رسالة",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "إشعار فوري وتأكيد استلام الرسالة على هاتف المستقبل بعلامة (✓✓) خضراء.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.deliveryReportsEnabled,
                            onCheckedChange = onToggleDeliveryReports,
                            modifier = Modifier.testTag("delivery_reports_switch")
                        )
                    }
                }
            }

            // 5. Signature Section
            item {
                SettingsSectionHeader(title = "تذييل الرسائل (التوقيع)", icon = Icons.Default.EditNote)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "إضافة توقيع تلقائي للرسائل",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Switch(
                                checked = signatureEnabledState,
                                onCheckedChange = {
                                    signatureEnabledState = it
                                    onSaveSignature(it, signatureInput)
                                },
                                modifier = Modifier.testTag("signature_switch")
                            )
                        }
                        if (signatureEnabledState) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = signatureInput,
                                onValueChange = {
                                    signatureInput = it
                                    onSaveSignature(true, it)
                                },
                                label = { Text("نص التوقيع") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // 6. Blocked Numbers Section
            item {
                SettingsSectionHeader(title = "الأرقام المحظورة والرسائل المزعجة", icon = Icons.Default.Block)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "قائمة الأرقام المحظورة (${blockedNumbers.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showAddBlockDialog = true }) {
                                Icon(Icons.Default.AddCircle, contentDescription = "إضافة رقم للحظر", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        if (blockedNumbers.isEmpty()) {
                            Text(
                                text = "لا توجد أرقام محظورة حالياً.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            blockedNumbers.forEach { blocked ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(blocked.phoneNumber, fontWeight = FontWeight.Bold)
                                        Text(blocked.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { onUnblockNumber(blocked.phoneNumber) }) {
                                        Text("إلغاء الحظر", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7. Theme Mode Section
            item {
                SettingsSectionHeader(title = "المظهر والألوان", icon = Icons.Default.Palette)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.themeMode == 0,
                                onClick = { onSetThemeMode(0) },
                                label = { Text("تلقائي النظام") }
                            )
                            FilterChip(
                                selected = uiState.themeMode == 1,
                                onClick = { onSetThemeMode(1) },
                                label = { Text("فاتح") }
                            )
                            FilterChip(
                                selected = uiState.themeMode == 2,
                                onClick = { onSetThemeMode(2) },
                                label = { Text("داكن") }
                            )
                        }
                    }
                }
            }

            // 8. Developer & Programmer Rights Section
            item {
                SettingsSectionHeader(title = "حقوق المبرمج والمطور", icon = Icons.Default.Code)
                DeveloperRightsCard(context = context)
            }

            // 9. About TALAL SMS
            item {
                SettingsSectionHeader(title = "حول TALAL SMS", icon = Icons.Default.Info)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "TALAL SMS v1.0", fontWeight = FontWeight.Bold)
                        Text(text = "اسم الحزمة: com.talalsms.com", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "تطبيق رسمي متكامل للرسائل النصية يدعم جميع إصدارات أندرويد والشرائح المتعددة وتقارير التسليم ومركز الرسائل.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Add Block Number Dialog
    if (showAddBlockDialog) {
        AlertDialog(
            onDismissRequest = { showAddBlockDialog = false },
            title = { Text("حظر رقم هاتف") },
            text = {
                OutlinedTextField(
                    value = newBlockNumberInput,
                    onValueChange = { newBlockNumberInput = it },
                    label = { Text("رقم الهاتف أو المعرّف") },
                    placeholder = { Text("أدخل الرقم المراد حظره") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newBlockNumberInput.isNotBlank()) {
                            onBlockNumber(newBlockNumberInput.trim())
                            newBlockNumberInput = ""
                            showAddBlockDialog = false
                        }
                    }
                ) {
                    Text("حظر")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBlockDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

@Composable
fun DeveloperRightsCard(context: Context) {
    val devNumber = "778215553"
    val whatsappIntlNumber = "967778215553"

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("developer_rights_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "المبرمج والمطور: عبدالحميد داوؤد",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "رقم التواصل: $devNumber",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Direct Call Button
                Button(
                    onClick = { launchDirectCall(context, devNumber) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DirectCallBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("developer_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "اتصال مباشر بالمطور",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("اتصال مباشر", fontWeight = FontWeight.Bold)
                }

                // WhatsApp Button
                Button(
                    onClick = { launchWhatsApp(context, whatsappIntlNumber) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WhatsAppGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("developer_whatsapp_button")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_whatsapp),
                        contentDescription = "تواصل واتساب مع المطور",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("واتساب", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "جميع الحقوق محفوظة © المطور عبدالحميد داوؤد",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun launchDirectCall(context: Context, phoneNumber: String) {
    val hasCallPerm = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CALL_PHONE
    ) == PackageManager.PERMISSION_GRANTED
    try {
        val action = if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:$phoneNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "تعذر فتح تطبيق الهاتف", Toast.LENGTH_SHORT).show()
        }
    }
}

fun launchWhatsApp(context: Context, intlPhoneNumber: String) {
    val url = "https://wa.me/$intlPhoneNumber"
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$intlPhoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Exception) {
            Toast.makeText(context, "تطبيق واتساب غير مثبت على الجهاز", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
