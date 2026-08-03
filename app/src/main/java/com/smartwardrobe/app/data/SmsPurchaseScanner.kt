package com.smartwardrobe.app.data

import android.content.Context
import android.provider.Telephony
import com.smartwardrobe.app.domain.PurchaseReminder
import java.util.UUID

class SmsPurchaseScanner {
    fun scanRecentClothingPurchases(context: Context, limit: Int = 80): List<PurchaseReminder> {
        val reminders = mutableListOf<PurchaseReminder>()
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        ) ?: return emptyList()
        cursor.use {
            val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            var scanned = 0
            while (it.moveToNext() && scanned < limit && reminders.size < 5) {
                scanned += 1
                val body = it.getString(bodyIndex).orEmpty()
                if (body.looksLikeClothingPurchase()) {
                    reminders.add(
                        PurchaseReminder(
                            id = UUID.randomUUID().toString(),
                            merchant = body.extractMerchant(),
                            amount = body.extractAmount(),
                            source = "SMS"
                        )
                    )
                }
            }
        }
        return reminders
    }

    private fun String.looksLikeClothingPurchase(): Boolean {
        val text = lowercase()
        val purchase = listOf("debited", "spent", "paid", "upi", "purchase").any { it in text }
        val clothing = listOf("myntra", "ajio", "zara", "h&m", "max", "lifestyle", "pantaloons", "westside", "nike", "adidas", "clothing", "apparel").any { it in text }
        return purchase && clothing
    }

    private fun String.extractAmount(): String =
        Regex("""(?:rs\.?|inr|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
            .find(this)?.value ?: "Amount not found"

    private fun String.extractMerchant(): String {
        val lower = lowercase()
        return listOf("Myntra", "Ajio", "Zara", "H&M", "Max", "Lifestyle", "Pantaloons", "Westside", "Nike", "Adidas")
            .firstOrNull { it.lowercase() in lower } ?: "Clothing purchase"
    }
}
