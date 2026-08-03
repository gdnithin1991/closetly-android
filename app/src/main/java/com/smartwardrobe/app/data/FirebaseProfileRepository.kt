package com.smartwardrobe.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.smartwardrobe.app.domain.Availability
import com.smartwardrobe.app.domain.AppSettings
import com.smartwardrobe.app.domain.ClothingItem
import com.smartwardrobe.app.domain.Formality
import com.smartwardrobe.app.domain.ItemType
import com.smartwardrobe.app.domain.NotificationPreferences
import com.smartwardrobe.app.domain.Owner
import com.smartwardrobe.app.domain.PurchaseReminder
import com.smartwardrobe.app.domain.WardrobeSection

/** Small callback repository for Spark-friendly Firestore metadata and compressed item photos. */
class FirebaseProfileRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun createProfileAndHousehold(onComplete: (Result<String>) -> Unit) {
        val user = auth.currentUser ?: return onComplete(Result.failure(IllegalStateException("Sign in required")))
        val householdId = user.uid
        val profile = mapOf(
            "displayName" to (user.displayName ?: "Closetly member"),
            "email" to (user.email ?: ""),
            "householdId" to householdId,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        val household = mapOf(
            "name" to "My household",
            "memberIds" to listOf(user.uid),
            "createdBy" to user.uid,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        val batch = firestore.batch()
        batch.set(firestore.collection("users").document(user.uid), profile, SetOptions.merge())
        batch.set(firestore.collection("households").document(householdId), household, SetOptions.merge())
        batch.commit()
            .addOnSuccessListener { onComplete(Result.success(householdId)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun createHouseholdInvite(householdId: String, onComplete: (Result<String>) -> Unit) {
        val user = auth.currentUser ?: return onComplete(Result.failure(IllegalStateException("Sign in required")))
        val code = (100000..999999).random().toString()
        val invite = mapOf(
            "householdId" to householdId,
            "createdBy" to user.uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "used" to false
        )
        firestore.collection("householdInvites").document(code)
            .set(invite, SetOptions.merge())
            .addOnSuccessListener { onComplete(Result.success(code)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun acceptHouseholdInvite(code: String, onComplete: (Result<String>) -> Unit) {
        val user = auth.currentUser ?: return onComplete(Result.failure(IllegalStateException("Sign in required")))
        firestore.collection("householdInvites").document(code.trim())
            .get()
            .addOnSuccessListener { invite ->
                val householdId = invite.getString("householdId")
                if (householdId.isNullOrBlank()) {
                    onComplete(Result.failure(IllegalArgumentException("Invite code not found.")))
                    return@addOnSuccessListener
                }
                val batch = firestore.batch()
                batch.update(firestore.collection("households").document(householdId), "memberIds", FieldValue.arrayUnion(user.uid))
                batch.set(
                    firestore.collection("users").document(user.uid),
                    mapOf(
                        "householdId" to householdId,
                        "displayName" to (user.displayName ?: "Closetly member"),
                        "email" to (user.email ?: "")
                    ),
                    SetOptions.merge()
                )
                batch.set(firestore.collection("householdInvites").document(code.trim()), mapOf("used" to true, "usedBy" to user.uid), SetOptions.merge())
                batch.commit()
                    .addOnSuccessListener { onComplete(Result.success(householdId)) }
                    .addOnFailureListener { onComplete(Result.failure(it)) }
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun saveWardrobeItem(householdId: String, item: ClothingItem, onComplete: (Result<Unit>) -> Unit) {
        val user = auth.currentUser ?: return onComplete(Result.failure(IllegalStateException("Sign in required")))
        val data = mapOf(
            "name" to item.name,
            "owner" to item.owner.name,
            "type" to item.type.name,
            "color" to item.color,
            "formality" to item.formality.name,
            "availability" to item.availability.name,
            "lastWornEpochDay" to item.lastWornEpochDay,
            "privateToOwner" to item.privateToOwner,
            "wardrobeSection" to item.wardrobeSection,
            "photoUri" to item.photoUri,
            "notes" to item.notes,
            "createdBy" to user.uid,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        firestore.collection("households").document(householdId).collection("items").document(item.id)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun saveItemPhotoInFirestore(
        context: Context,
        householdId: String,
        itemId: String,
        localUri: String,
        onComplete: (Result<Pair<String, Long>>) -> Unit
    ) {
        val user = auth.currentUser ?: return onComplete(Result.failure(IllegalStateException("Sign in required")))
        val imageBytes = compressImageForFirestore(context, Uri.parse(localUri))
        if (imageBytes == null) {
            onComplete(Result.failure(IllegalArgumentException("Could not read selected image.")))
            return
        }
        val encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        firestore.collection("households").document(householdId)
            .collection("items").document(itemId)
            .set(
                mapOf(
                    "photoData" to encoded,
                    "photoContentType" to "image/jpeg",
                    "photoByteSize" to imageBytes.size,
                    "photoUri" to "firestore:$itemId",
                    "photoUploadedBy" to user.uid,
                    "photoUploadedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener { onComplete(Result.success(encoded to imageBytes.size.toLong())) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun loadWardrobeItems(householdId: String, onComplete: (Result<List<ClothingItem>>) -> Unit) {
        firestore.collection("households").document(householdId).collection("items")
            .get()
            .addOnSuccessListener { snapshot ->
                onComplete(Result.success(snapshot.documents.mapNotNull { document ->
                    val data = document.data ?: return@mapNotNull null
                    ClothingItem(
                        id = document.id,
                        name = data["name"] as? String ?: "Unnamed item",
                        owner = enumValue(data["owner"] as? String, Owner.ME),
                        type = enumValue(data["type"] as? String, ItemType.TOP),
                        color = data["color"] as? String ?: "Unknown",
                        formality = enumValue(data["formality"] as? String, Formality.SMART_CASUAL),
                        availability = enumValue(data["availability"] as? String, Availability.AVAILABLE),
                        lastWornEpochDay = data["lastWornEpochDay"] as? Long,
                        privateToOwner = data["privateToOwner"] as? Boolean ?: false,
                        wardrobeSection = data["wardrobeSection"] as? String ?: "Unassigned",
                        photoUri = data["photoUri"] as? String,
                        photoData = data["photoData"] as? String,
                        photoByteSize = data["photoByteSize"] as? Long ?: 0,
                        notes = data["notes"] as? String ?: ""
                    )
                }))
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun loadWardrobeSections(householdId: String, onComplete: (Result<List<WardrobeSection>>) -> Unit) {
        firestore.collection("households").document(householdId).collection("sections")
            .get()
            .addOnSuccessListener { snapshot ->
                onComplete(Result.success(snapshot.documents.mapNotNull { document ->
                    val data = document.data ?: return@mapNotNull null
                    WardrobeSection(
                        id = document.id,
                        name = data["name"] as? String ?: "Section",
                        capacity = (data["capacity"] as? Long)?.toInt() ?: 10,
                        layoutPhotoUri = data["layoutPhotoUri"] as? String
                    )
                }))
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun loadPurchaseReminders(householdId: String, onComplete: (Result<List<PurchaseReminder>>) -> Unit) {
        firestore.collection("households").document(householdId).collection("purchaseReminders")
            .get()
            .addOnSuccessListener { snapshot ->
                onComplete(Result.success(snapshot.documents.mapNotNull { document ->
                    val data = document.data ?: return@mapNotNull null
                    PurchaseReminder(
                        id = document.id,
                        merchant = data["merchant"] as? String ?: "",
                        amount = data["amount"] as? String ?: "",
                        source = data["source"] as? String ?: "Manual",
                        handled = data["handled"] as? Boolean ?: false
                    )
                }))
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun loadAppSettings(householdId: String, onComplete: (Result<AppSettings>) -> Unit) {
        firestore.collection("households").document(householdId).collection("settings").document("app")
            .get()
            .addOnSuccessListener { document ->
                val data = document.data.orEmpty()
                onComplete(
                    Result.success(
                        AppSettings(
                            homeCity = data["homeCity"] as? String ?: "Set home city",
                            secondProfileName = data["secondProfileName"] as? String ?: "Household member",
                            defaultFunctionContext = data["defaultFunctionContext"] as? String ?: "",
                            defaultTravelDestination = data["defaultTravelDestination"] as? String ?: "",
                            aiSuggestionsUsedToday = (data["aiSuggestionsUsedToday"] as? Long)?.toInt() ?: 0,
                            aiSuggestionEpochDay = data["aiSuggestionEpochDay"] as? Long ?: 0,
                            hostedAiEndpoint = data["hostedAiEndpoint"] as? String ?: "",
                            hostedAiApiKey = data["hostedAiApiKey"] as? String ?: "",
                            lastWeatherSummary = data["lastWeatherSummary"] as? String ?: "Weather not fetched yet"
                        )
                    )
                )
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    private fun compressImageForFirestore(context: Context, uri: Uri): ByteArray? {
        val original = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return null
        val maxSide = 384
        val scale = minOf(1f, maxSide.toFloat() / maxOf(original.width, original.height).toFloat())
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt().coerceAtLeast(1),
                (original.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            original
        }
        val output = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 58, output)
        if (resized !== original) resized.recycle()
        original.recycle()
        return output.toByteArray().takeIf { it.size < 500 * 1024 }
    }

    fun saveWardrobeSection(householdId: String, section: WardrobeSection, onComplete: (Result<Unit>) -> Unit) {
        val data = mapOf(
            "name" to section.name,
            "capacity" to section.capacity,
            "layoutPhotoUri" to section.layoutPhotoUri,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        firestore.collection("households").document(householdId).collection("sections").document(section.id)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun saveNotificationPreferences(householdId: String, prefs: NotificationPreferences, onComplete: (Result<Unit>) -> Unit) {
        firestore.collection("households").document(householdId).collection("settings").document("notifications")
            .set(prefs, SetOptions.merge())
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun saveAppSettings(householdId: String, settings: AppSettings, onComplete: (Result<Unit>) -> Unit) {
        firestore.collection("households").document(householdId).collection("settings").document("app")
            .set(settings, SetOptions.merge())
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun savePurchaseReminder(householdId: String, reminder: PurchaseReminder, onComplete: (Result<Unit>) -> Unit) {
        firestore.collection("households").document(householdId).collection("purchaseReminders").document(reminder.id)
            .set(reminder, SetOptions.merge())
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
