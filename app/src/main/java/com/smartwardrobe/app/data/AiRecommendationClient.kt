package com.smartwardrobe.app.data

import com.smartwardrobe.app.domain.ClothingItem
import com.smartwardrobe.app.domain.Occasion
import com.smartwardrobe.app.domain.OutfitRecommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AiRecommendationClient {
    suspend fun enhance(
        endpoint: String,
        apiKey: String,
        occasion: Occasion,
        context: String,
        candidates: List<OutfitRecommendation>
    ): Result<List<OutfitRecommendation>> = withContext(Dispatchers.IO) {
        runCatching {
            require(endpoint.isNotBlank()) { "Configure hosted AI endpoint in Settings." }
            val payload = JSONObject()
                .put("occasion", occasion.name)
                .put("context", context)
                .put("candidates", JSONArray(candidates.map { recommendation ->
                    JSONObject()
                        .put("score", recommendation.score)
                        .put("reason", recommendation.reason)
                        .put("items", JSONArray(recommendation.items.map(ClothingItem::name)))
                }))
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            OutputStreamWriter(connection.outputStream).use { it.write(payload.toString()) }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val enhancedReason = json.optString("reason", "")
            if (enhancedReason.isBlank()) {
                candidates
            } else {
                candidates.mapIndexed { index, recommendation ->
                    if (index == 0) recommendation.copy(reason = enhancedReason, score = (recommendation.score + 5).coerceAtMost(100))
                    else recommendation
                }
            }
        }
    }
}
