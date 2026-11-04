package com.eipna.centsation.sms.processor

import android.database.sqlite.SQLiteDatabase
import com.eipna.centsation.data.Database
import com.eipna.centsation.sms.parser.ParsedBankTransaction
import java.util.Collections
import java.util.LinkedHashMap
import kotlin.math.abs

object TransactionDeduplication {

    private const val MAX_CACHE_SIZE = 200
    private const val DUPLICATE_TIME_WINDOW_MS = 5 * 60 * 1000L // 5 minutes for carrier retries
    private const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L

    // In-memory LRU cache of recent transaction signatures -> timestamp
    private val recentSignatures = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > MAX_CACHE_SIZE
            }
        }
    )

    fun isDuplicate(
        db: SQLiteDatabase,
        tx: ParsedBankTransaction
    ): Boolean {
        val now = tx.timestamp
        val signature = buildSignature(tx)

        // 1. Check in-memory LRU cache
        val cachedTime = recentSignatures[signature]
        if (cachedTime != null && abs(now - cachedTime) <= TWENTY_FOUR_HOURS_MS) {
            return true
        }

        // 2. Query database for exact timestamp match or near-window match
        val typeString = if (tx.isExpense) "Withdraw" else "Deposit"
        val windowStart = now - DUPLICATE_TIME_WINDOW_MS
        val windowEnd = now + DUPLICATE_TIME_WINDOW_MS

        val cursor = db.query(
            Database.TABLE_TRANSACTION,
            arrayOf(
                Database.COLUMN_TRANSACTION_ID,
                Database.COLUMN_TRANSACTION_AMOUNT,
                Database.COLUMN_TRANSACTION_DATE,
                Database.COLUMN_TRANSACTION_TYPE
            ),
            "${Database.COLUMN_TRANSACTION_AMOUNT} = ? AND ${Database.COLUMN_TRANSACTION_TYPE} = ? AND ${Database.COLUMN_TRANSACTION_DATE} BETWEEN ? AND ?",
            arrayOf(tx.amount.toString(), typeString, windowStart.toString(), windowEnd.toString()),
            null, null, null, "1"
        )

        val existsInDb = cursor.use { it.moveToFirst() }
        if (existsInDb) {
            recentSignatures[signature] = now
            return true
        }

        // Remember signature
        recentSignatures[signature] = now
        return false
    }

    private fun buildSignature(tx: ParsedBankTransaction): String {
        val refPart = tx.reference ?: tx.accountLast4 ?: tx.rawMerchant.take(15)
        return "${tx.amount}_${tx.isExpense}_${refPart}_${tx.bankName}"
    }

    fun clearCache() {
        recentSignatures.clear()
    }
}
