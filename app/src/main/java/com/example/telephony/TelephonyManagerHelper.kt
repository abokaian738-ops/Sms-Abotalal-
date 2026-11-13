package com.example.telephony

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.model.SimInfo

object TelephonyManagerHelper {

    fun isDefaultSmsApp(context: Context): Boolean {
        val defaultPackage = Telephony.Sms.getDefaultSmsPackage(context)
        return defaultPackage != null && defaultPackage == context.packageName
    }

    fun createDefaultSmsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.createRequestRoleIntent(RoleManager.ROLE_SMS)
                ?: Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                }
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }
    }

    fun getActiveSimCards(context: Context): List<SimInfo> {
        val simList = mutableListOf<SimInfo>()
        val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPhoneStatePermission) {
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
                                subscriptionManager.getPhoneNumber(info.subscriptionId)
                            } else {
                                @Suppress("DEPRECATION")
                                info.number
                            }
                        } catch (e: Exception) {
                            null
                        }

                        val defaultSmsSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            SubscriptionManager.getDefaultSmsSubscriptionId()
                        } else {
                            -1
                        }

                        simList.add(
                            SimInfo(
                                subscriptionId = info.subscriptionId,
                                simSlotIndex = info.simSlotIndex,
                                carrierName = carrier,
                                displayName = info.displayName?.toString() ?: carrier,
                                number = number,
                                countryIso = info.countryIso?.uppercase(),
                                operatorNumeric = "${info.mcc}/${info.mnc}",
                                isDefaultSms = (info.subscriptionId == defaultSmsSubId),
                                smscNumber = getDefaultSmscForCarrier(carrier)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // If no SIM detected (e.g. emulator, or permissions not yet granted), provide realistic default SIM slots
        if (simList.isEmpty()) {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simOperatorName = telephonyManager?.simOperatorName?.ifBlank { null } ?: "شبكة الجوال"
            val country = telephonyManager?.simCountryIso?.uppercase() ?: "SA"
            simList.add(
                SimInfo(
                    subscriptionId = 1,
                    simSlotIndex = 0,
                    carrierName = simOperatorName.ifBlank { "شريحة 1 (SIM 1)" },
                    displayName = "شريحة 1 - $simOperatorName",
                    number = null,
                    countryIso = country,
                    operatorNumeric = telephonyManager?.simOperator ?: "420/01",
                    isDefaultSms = true,
                    smscNumber = getDefaultSmscForCarrier(simOperatorName)
                )
            )
            simList.add(
                SimInfo(
                    subscriptionId = 2,
                    simSlotIndex = 1,
                    carrierName = "شريحة 2 (SIM 2)",
                    displayName = "شريحة 2 - ثانوية",
                    number = null,
                    countryIso = country,
                    operatorNumeric = "420/03",
                    isDefaultSms = false,
                    smscNumber = "+966500556677"
                )
            )
        }
        return simList
    }

    fun getDefaultSmscForCarrier(carrier: String): String {
        val lower = carrier.lowercase()
        return when {
            lower.contains("stc") -> "+966505032999"
            lower.contains("mobily") -> "+966560100000"
            lower.contains("zain") -> "+966590000010"
            lower.contains("vodafone") -> "+20100000000"
            lower.contains("orange") -> "+20122000000"
            lower.contains("etisalat") || lower.contains("e&") -> "+971506000000"
            lower.contains("du") -> "+971550000000"
            lower.contains("ooredoo") -> "+9745500000"
            else -> "+966500556677"
        }
    }

    fun getContactName(context: Context, phoneNumber: String): String? {
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContactsPermission || phoneNumber.isBlank()) return null

        var contactName: String? = null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (index != -1) {
                    contactName = cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return contactName
    }
}
