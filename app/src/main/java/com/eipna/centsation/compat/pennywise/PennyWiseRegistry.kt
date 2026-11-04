package com.eipna.centsation.compat.pennywise

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class PennyWiseRegistry private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun saveAccountAliases(accounts: List<PennyWiseAccount>) {
        val editor = prefs.edit()
        val json = JSONObject()
        accounts.forEach { acc ->
            val key = "${acc.bankName.uppercase(Locale.ROOT)}_${acc.accountLast4}"
            json.put(key, acc.alias ?: acc.displayLabel)
        }
        editor.putString(KEY_ACCOUNT_ALIASES, json.toString())
        editor.apply()
    }

    fun getAccountDisplayName(bankName: String?, last4: String?): String {
        if (bankName.isNullOrBlank()) return "Account ••${last4 ?: "0000"}"
        val key = "${bankName.uppercase(Locale.ROOT)}_${last4 ?: ""}"
        val jsonStr = prefs.getString(KEY_ACCOUNT_ALIASES, null) ?: return "$bankName ••${last4 ?: ""}"
        return try {
            val json = JSONObject(jsonStr)
            if (json.has(key)) json.getString(key) else "$bankName ••${last4 ?: ""}"
        } catch (e: Exception) {
            "$bankName ••${last4 ?: ""}"
        }
    }

    fun saveMerchantAliases(aliases: List<PennyWiseMerchantAlias>) {
        val editor = prefs.edit()
        val jsonStr = prefs.getString(KEY_MERCHANT_ALIASES, null)
        val json = if (jsonStr != null) {
            try { JSONObject(jsonStr) } catch (e: Exception) { JSONObject() }
        } else JSONObject()

        aliases.forEach { ma ->
            if (ma.merchantName.isNotBlank() && ma.alias.isNotBlank()) {
                json.put(ma.merchantName.trim(), ma.alias.trim())
            }
        }
        editor.putString(KEY_MERCHANT_ALIASES, json.toString())
        editor.apply()
    }

    fun resolveMerchantAlias(rawMerchant: String): String {
        val trimmed = rawMerchant.trim()
        if (trimmed.isBlank()) return "Unknown Merchant"

        // 1. Check user-saved preferences
        val jsonStr = prefs.getString(KEY_MERCHANT_ALIASES, null)
        if (jsonStr != null) {
            try {
                val json = JSONObject(jsonStr)
                if (json.has(trimmed)) {
                    return json.getString(trimmed)
                }
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k.equals(trimmed, ignoreCase = true) ||
                        trimmed.contains(k, ignoreCase = true)
                    ) {
                        return json.getString(k)
                    }
                }
            } catch (ignored: Exception) {}
        }

        // 2. Check hyper-personalized profile defaults
        for ((k, alias) in PersonalizedProfileDefaults.DEFAULT_MERCHANT_ALIASES) {
            if (k.equals(trimmed, ignoreCase = true) || trimmed.contains(k, ignoreCase = true)) {
                return alias
            }
        }

        return trimmed
    }

    fun saveMerchantCategories(mappings: List<PennyWiseMerchantMapping>) {
        val editor = prefs.edit()
        val jsonStr = prefs.getString(KEY_MERCHANT_CATEGORIES, null)
        val json = if (jsonStr != null) {
            try { JSONObject(jsonStr) } catch (e: Exception) { JSONObject() }
        } else JSONObject()

        mappings.forEach { mm ->
            if (mm.merchantName.isNotBlank() && mm.category.isNotBlank()) {
                json.put(mm.merchantName.trim(), mm.category.trim())
            }
        }
        editor.putString(KEY_MERCHANT_CATEGORIES, json.toString())
        editor.apply()
    }

    fun resolveMerchantCategory(merchantOrAlias: String): String {
        val trimmed = merchantOrAlias.trim()
        if (trimmed.isBlank()) return "General"

        // 1. Check user-saved categories
        val jsonStr = prefs.getString(KEY_MERCHANT_CATEGORIES, null)
        if (jsonStr != null) {
            try {
                val json = JSONObject(jsonStr)
                if (json.has(trimmed)) {
                    return json.getString(trimmed)
                }
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k.equals(trimmed, ignoreCase = true) ||
                        trimmed.contains(k, ignoreCase = true)
                    ) {
                        return json.getString(k)
                    }
                }
            } catch (ignored: Exception) {}
        }

        // 2. Check hyper-personalized defaults
        for ((k, cat) in PersonalizedProfileDefaults.DEFAULT_CATEGORY_MAPPINGS) {
            if (k.equals(trimmed, ignoreCase = true) || trimmed.contains(k, ignoreCase = true)) {
                return cat
            }
        }

        // 3. Fall back to smart keyword classification
        return PersonalizedProfileDefaults.inferCategoryByKeyword(trimmed)
    }

    fun isSmsAutoTrackingEnabled(): Boolean {
        return prefs.getBoolean(KEY_SMS_AUTO_TRACKING, true)
    }

    fun setSmsAutoTrackingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMS_AUTO_TRACKING, enabled).apply()
    }

    fun recordAutoLoggedSms(
        amount: Double,
        isExpense: Boolean,
        merchant: String,
        category: String,
        bankName: String,
        last4: String?,
        timestamp: Long = System.currentTimeMillis()
    ) {
        try {
            val list = getRecentAutoLoggedList().toMutableList()
            val obj = JSONObject().apply {
                put("amount", amount)
                put("isExpense", isExpense)
                put("merchant", merchant)
                put("category", category)
                put("bankName", bankName)
                put("last4", last4 ?: "")
                put("timestamp", timestamp)
            }
            list.add(0, obj)
            // Keep latest 50
            val trimmedList = list.take(50)
            val arr = JSONArray()
            trimmedList.forEach { arr.put(it) }
            prefs.edit().putString(KEY_RECENT_SMS_LOGS, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getRecentAutoLoggedList(): List<JSONObject> {
        val jsonStr = prefs.getString(KEY_RECENT_SMS_LOGS, null) ?: return emptyList()
        val result = mutableListOf<JSONObject>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                result.add(obj)
            }
        } catch (ignored: Exception) {}
        return result
    }

    fun getAllAccountAliases(): Map<String, String> {
        val jsonStr = prefs.getString(KEY_ACCOUNT_ALIASES, null) ?: return emptyMap()
        val map = mutableMapOf<String, String>()
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = json.getString(k)
            }
        } catch (ignored: Exception) {}
        return map
    }

    fun getAllMerchantAliases(): Map<String, String> {
        val jsonStr = prefs.getString(KEY_MERCHANT_ALIASES, null)
        val map = mutableMapOf<String, String>()
        // Defaults first
        map.putAll(PersonalizedProfileDefaults.DEFAULT_MERCHANT_ALIASES)
        if (jsonStr != null) {
            try {
                val json = JSONObject(jsonStr)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = json.getString(k)
                }
            } catch (ignored: Exception) {}
        }
        return map
    }

    companion object {
        private const val PREFS_NAME = "supercenter_pennywise_registry"
        private const val KEY_ACCOUNT_ALIASES = "pennywise_account_aliases"
        private const val KEY_MERCHANT_ALIASES = "pennywise_merchant_aliases"
        private const val KEY_MERCHANT_CATEGORIES = "pennywise_merchant_categories"
        private const val KEY_SMS_AUTO_TRACKING = "pennywise_sms_auto_tracking"
        private const val KEY_RECENT_SMS_LOGS = "pennywise_recent_sms_logs"

        @Volatile
        private var instance: PennyWiseRegistry? = null

        fun getInstance(context: Context): PennyWiseRegistry {
            return instance ?: synchronized(this) {
                instance ?: PennyWiseRegistry(context).also { instance = it }
            }
        }
    }
}
