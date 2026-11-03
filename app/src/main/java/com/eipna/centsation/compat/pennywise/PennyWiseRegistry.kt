package com.eipna.centsation.compat.pennywise

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class PennyWiseRegistry private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun saveAccountAliases(accounts: List<PennyWiseAccount>) {
        val editor = prefs.edit()
        val json = JSONObject()
        accounts.forEach { acc ->
            val key = "${acc.bankName.uppercase()}_${acc.accountLast4}"
            json.put(key, acc.alias ?: acc.displayLabel)
        }
        editor.putString(KEY_ACCOUNT_ALIASES, json.toString())
        editor.apply()
    }

    fun getAccountDisplayName(bankName: String?, last4: String?): String {
        if (bankName.isNullOrBlank()) return "Account ••${last4 ?: "0000"}"
        val key = "${bankName.uppercase()}_${last4 ?: ""}"
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
        val json = JSONObject()
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
        val jsonStr = prefs.getString(KEY_MERCHANT_ALIASES, null) ?: return trimmed
        return try {
            val json = JSONObject(jsonStr)
            if (json.has(trimmed)) {
                json.getString(trimmed)
            } else {
                // Try case-insensitive lookup
                var found: String? = null
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k.equals(trimmed, ignoreCase = true)) {
                        found = json.getString(k)
                        break
                    }
                }
                found ?: trimmed
            }
        } catch (e: Exception) {
            trimmed
        }
    }

    fun saveMerchantCategories(mappings: List<PennyWiseMerchantMapping>) {
        val editor = prefs.edit()
        val json = JSONObject()
        mappings.forEach { mm ->
            if (mm.merchantName.isNotBlank() && mm.category.isNotBlank()) {
                json.put(mm.merchantName.trim(), mm.category.trim())
            }
        }
        editor.putString(KEY_MERCHANT_CATEGORIES, json.toString())
        editor.apply()
    }

    fun resolveMerchantCategory(merchantName: String): String? {
        val trimmed = merchantName.trim()
        val jsonStr = prefs.getString(KEY_MERCHANT_CATEGORIES, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            if (json.has(trimmed)) {
                json.getString(trimmed)
            } else {
                var found: String? = null
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k.equals(trimmed, ignoreCase = true)) {
                        found = json.getString(k)
                        break
                    }
                }
                found
            }
        } catch (e: Exception) {
            null
        }
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
        val jsonStr = prefs.getString(KEY_MERCHANT_ALIASES, null) ?: return emptyMap()
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

    companion object {
        private const val PREFS_NAME = "supercenter_pennywise_registry"
        private const val KEY_ACCOUNT_ALIASES = "pennywise_account_aliases"
        private const val KEY_MERCHANT_ALIASES = "pennywise_merchant_aliases"
        private const val KEY_MERCHANT_CATEGORIES = "pennywise_merchant_categories"

        @Volatile
        private var instance: PennyWiseRegistry? = null

        fun getInstance(context: Context): PennyWiseRegistry {
            return instance ?: synchronized(this) {
                instance ?: PennyWiseRegistry(context).also { instance = it }
            }
        }
    }
}
