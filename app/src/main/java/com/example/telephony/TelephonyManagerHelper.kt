package com.example.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.model.SimInfo

object TelephonyManagerHelper {

    fun isDefaultSmsApp(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                    return roleManager.isRoleHeld(RoleManager.ROLE_SMS)
                }
            }
            val defaultPackage = Telephony.Sms.getDefaultSmsPackage(context)
            defaultPackage != null && defaultPackage == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    fun createDefaultSmsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else {
                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                }
            }
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }
    }

    fun hasPhoneStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun getActiveSimCards(context: Context): List<SimInfo> {
        val simList = mutableListOf<SimInfo>()
        if (!hasPhoneStatePermission(context)) {
            return simList
        }

        val defaultSmsSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SubscriptionManager.getDefaultSmsSubscriptionId()
        } else {
            -1
        }

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val subs: List<SubscriptionInfo>? = subscriptionManager?.activeSubscriptionInfoList
            if (!subs.isNullOrEmpty()) {
                for (info in subs) {
                    val carrier = info.carrierName?.toString()?.ifBlank { null }
                        ?: info.displayName?.toString()?.ifBlank { null }
                        ?: "شريحة ${info.simSlotIndex + 1}"

                    val number = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            subscriptionManager.getPhoneNumber(info.subscriptionId).ifBlank { null }
                        } else {
                            @Suppress("DEPRECATION")
                            info.number?.ifBlank { null }
                        }
                    } catch (_: Exception) {
                        null
                    }

                    val mccMnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val mcc = info.mccString ?: ""
                        val mnc = info.mncString ?: ""
                        if (mcc.isNotEmpty() || mnc.isNotEmpty()) "$mcc/$mnc" else null
                    } else {
                        @Suppress("DEPRECATION")
                        "${info.mcc}/${info.mnc}"
                    }

                    simList.add(
                        SimInfo(
                            subscriptionId = info.subscriptionId,
                            simSlotIndex = info.simSlotIndex,
                            carrierName = carrier,
                            displayName = info.displayName?.toString() ?: carrier,
                            number = number,
                            countryIso = info.countryIso?.uppercase()?.ifBlank { null },
                            operatorNumeric = mccMnc,
                            isDefaultSms = (info.subscriptionId == defaultSmsSubId),
                            smscNumber = readSmscSafely(context, info.subscriptionId)
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }

        // Single-SIM fallback using real TelephonyManager only if an actual SIM is ready
        if (simList.isEmpty()) {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                if (telephonyManager != null && telephonyManager.simState == TelephonyManager.SIM_STATE_READY) {
                    val simOperatorName = telephonyManager.simOperatorName?.ifBlank { null }
                        ?: telephonyManager.networkOperatorName?.ifBlank { null }
                        ?: "شبكة الجوال"
                    val country = telephonyManager.simCountryIso?.uppercase()?.ifBlank { null }
                    val operator = telephonyManager.simOperator?.ifBlank { null }
                    simList.add(
                        SimInfo(
                            subscriptionId = if (defaultSmsSubId > 0) defaultSmsSubId else -1,
                            simSlotIndex = 0,
                            carrierName = simOperatorName,
                            displayName = simOperatorName,
                            number = null,
                            countryIso = country,
                            operatorNumeric = operator,
                            isDefaultSms = true,
                            smscNumber = readSmscSafely(context, defaultSmsSubId)
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }

        return simList.sortedBy { it.simSlotIndex }
    }

    @SuppressLint("MissingPermission")
    private fun readSmscSafely(context: Context, subId: Int): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val base = context.getSystemService(android.telephony.SmsManager::class.java)
                    if (subId > 0) base.createForSubscriptionId(subId) else base
                } else {
                    @Suppress("DEPRECATION")
                    if (subId > 0) android.telephony.SmsManager.getSmsManagerForSubscriptionId(subId)
                    else android.telephony.SmsManager.getDefault()
                }
                smsManager.smscAddress?.ifBlank { null }
            } else {
                null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun getContactName(context: Context, phoneNumber: String): String? {
        return ContactsHelper.getContactNameCached(context, phoneNumber)
    }
}
