package com.example.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.model.ContactInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object ContactsHelper {

    private val nameCache = ConcurrentHashMap<String, String>()
    private val noContactSet = ConcurrentHashMap.newKeySet<String>()

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun normalizePhoneNumber(phone: String): String {
        val trimmed = phone.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (hasPlus && digits.isNotEmpty()) "+$digits" else digits
    }

    fun clearCache() {
        nameCache.clear()
        noContactSet.clear()
    }

    fun getContactNameCached(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank() || !hasContactsPermission(context)) return null
        val key = normalizePhoneNumber(phoneNumber).ifEmpty { phoneNumber }
        nameCache[key]?.let { return it }
        if (noContactSet.contains(key)) return null

        val lookedUp = queryContactName(context, phoneNumber)
        if (lookedUp != null) {
            nameCache[key] = lookedUp
        } else {
            noContactSet.add(key)
        }
        return lookedUp
    }

    private fun queryContactName(context: Context, phoneNumber: String): String? {
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
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return contactName
    }

    suspend fun loadAllContacts(context: Context): List<ContactInfo> = withContext(Dispatchers.IO) {
        if (!hasContactsPermission(context)) return@withContext emptyList()

        val contacts = mutableListOf<ContactInfo>()
        val seenKeys = HashSet<String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )

            cursor?.use { c ->
                val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                while (c.moveToNext()) {
                    val contactId = if (idIdx != -1) c.getLong(idIdx) else 0L
                    val name = if (nameIdx != -1) c.getString(nameIdx)?.trim() else null
                    val rawNumber = if (numIdx != -1) c.getString(numIdx)?.trim() else null
                    val phoneType = if (typeIdx != -1) c.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE

                    if (!rawNumber.isNullOrBlank()) {
                        val normalized = normalizePhoneNumber(rawNumber)
                        val displayName = name?.ifBlank { rawNumber } ?: rawNumber
                        val dedupKey = "$displayName|$normalized"
                        if (normalized.isNotEmpty() && seenKeys.add(dedupKey)) {
                            nameCache[normalized] = displayName
                            noContactSet.remove(normalized)
                            contacts.add(
                                ContactInfo(
                                    id = contactId,
                                    name = displayName,
                                    phoneNumber = rawNumber,
                                    normalizedNumber = normalized,
                                    label = getPhoneTypeLabel(phoneType)
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        contacts
    }

    suspend fun resolvePickedContactUri(context: Context, uri: Uri): ContactInfo? = withContext(Dispatchers.IO) {
        try {
            // Try querying directly as a Phone data URI first (ACTION_PICK Phone.CONTENT_URI)
            val phoneProjection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
            )
            context.contentResolver.query(uri, phoneProjection, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val typeIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                    val number = if (numIdx != -1) c.getString(numIdx)?.trim() else null
                    if (!number.isNullOrBlank()) {
                        val name = (if (nameIdx != -1) c.getString(nameIdx)?.trim() else null) ?: number
                        val id = if (idIdx != -1) c.getLong(idIdx) else 0L
                        val type = if (typeIdx != -1) c.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        return@withContext ContactInfo(
                            id = id,
                            name = name,
                            phoneNumber = number,
                            normalizedNumber = normalizePhoneNumber(number),
                            label = getPhoneTypeLabel(type)
                        )
                    }
                }
            }

            // Fallback: URI is a Contact URI (PickContact contract)
            if (hasContactsPermission(context)) {
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { contactCursor ->
                    if (contactCursor.moveToFirst()) {
                        val contactId = contactCursor.getLong(0)
                        val name = contactCursor.getString(1) ?: ""
                        context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(
                                ContactsContract.CommonDataKinds.Phone.NUMBER,
                                ContactsContract.CommonDataKinds.Phone.TYPE
                            ),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId.toString()),
                            null
                        )?.use { numCursor ->
                            if (numCursor.moveToFirst()) {
                                val number = numCursor.getString(0)?.trim()
                                val type = numCursor.getInt(1)
                                if (!number.isNullOrBlank()) {
                                    return@withContext ContactInfo(
                                        id = contactId,
                                        name = name.ifBlank { number },
                                        phoneNumber = number,
                                        normalizedNumber = normalizePhoneNumber(number),
                                        label = getPhoneTypeLabel(type)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        null
    }

    private fun getPhoneTypeLabel(type: Int): String {
        return when (type) {
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "جوال"
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "منزل"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "عمل"
            ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "رئيسي"
            else -> "هاتف"
        }
    }
}
