package com.eipna.centsation.sms.scanner

import android.content.Context
import android.provider.Telephony
import com.eipna.centsation.sms.processor.SmsTransactionProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

object HistoricalSmsScanner {

    data class ScanProgress(
        val totalScanned: Int = 0,
        val importedCount: Int = 0,
        val duplicatesCount: Int = 0,
        val isFinished: Boolean = false,
        val error: String? = null
    )

    enum class ScanWindow(val days: Int, val displayName: String) {
        LAST_30_DAYS(30, "Last 30 Days"),
        LAST_90_DAYS(90, "Last 90 Days"),
        ALL_TIME(-1, "All Time")
    }

    fun scanInbox(
        context: Context,
        window: ScanWindow
    ): Flow<ScanProgress> = flow {
        var scanned = 0
        var imported = 0
        var duplicates = 0

        emit(ScanProgress(0, 0, 0, false))

        val resolver = context.contentResolver
        val uri = Telephony.Sms.Inbox.CONTENT_URI

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val selection: String?
        val selectionArgs: Array<String>?

        if (window.days > 0) {
            val cutoff = System.currentTimeMillis() - (window.days.toLong() * 24 * 60 * 60 * 1000L)
            selection = "${Telephony.Sms.DATE} >= ?"
            selectionArgs = arrayOf(cutoff.toString())
        } else {
            selection = null
            selectionArgs = null
        }

        try {
            val cursor = resolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )

            if (cursor == null) {
                emit(ScanProgress(0, 0, 0, true, "Could not open SMS inbox cursor"))
                return@flow
            }

            cursor.use {
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    scanned++
                    val address = it.getString(addressIdx) ?: ""
                    val body = it.getString(bodyIdx) ?: ""
                    val date = it.getLong(dateIdx)

                    if (body.isNotBlank()) {
                        val result = SmsTransactionProcessor.processSms(
                            context = context,
                            sender = address,
                            body = body,
                            timestamp = date,
                            showNotification = false // Don't spam notifications during batch scan
                        )

                        if (result != null) {
                            imported++
                        } else {
                            duplicates++
                        }
                    }

                    // Emit progress every 10 SMS or when first few scanned
                    if (scanned % 10 == 0 || scanned <= 5) {
                        emit(ScanProgress(scanned, imported, duplicates, false))
                    }
                }
            }

            emit(ScanProgress(scanned, imported, duplicates, true))
        } catch (e: SecurityException) {
            emit(ScanProgress(scanned, imported, duplicates, true, "READ_SMS permission not granted"))
        } catch (e: Exception) {
            emit(ScanProgress(scanned, imported, duplicates, true, e.message ?: "Unknown scanning error"))
        }
    }.flowOn(Dispatchers.IO)
}
