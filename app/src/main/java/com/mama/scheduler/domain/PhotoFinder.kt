package com.mama.scheduler.domain

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.mama.scheduler.BuildConfig
import com.mama.scheduler.data.remote.Content
import com.mama.scheduler.data.remote.GeminiClient
import com.mama.scheduler.data.remote.GeminiRequest
import com.mama.scheduler.data.remote.GenerationConfig
import com.mama.scheduler.data.remote.Part
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Finds photos taken during an event window and screens them with AI. */
@Singleton
class PhotoFinder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PhotoFinder"
        private const val BUFFER_MINUTES = 30L
        private const val MAX_PHOTOS_TO_SCREEN = 10
        private const val TOP_PHOTOS_PER_EVENT = 3
    }

    data class PhotoInfo(
        val uri: Uri,
        val displayName: String,
        val timestamp: Long,
        val formattedTime: String
    )

    suspend fun findEventPhotos(
        eventTitle: String,
        kidName: String?,
        startTimeMillis: Long,
        endTimeMillis: Long,
        apiKeyOverride: String? = null
    ): List<String> = withContext(Dispatchers.IO) {
        val bufferMillis = BUFFER_MINUTES * 60_000
        val candidates = getPhotosInTimeWindow(startTimeMillis - bufferMillis, endTimeMillis + bufferMillis)
        if (candidates.isEmpty()) return@withContext emptyList()

        screenPhotosWithAI(
            photos = candidates.take(MAX_PHOTOS_TO_SCREEN),
            eventTitle = eventTitle,
            kidName = kidName,
            apiKeyOverride = apiKeyOverride
        )
    }

    private suspend fun screenPhotosWithAI(
        photos: List<PhotoInfo>,
        eventTitle: String,
        kidName: String?,
        apiKeyOverride: String?
    ): List<String> = withContext(Dispatchers.IO) {
        if (photos.isEmpty()) return@withContext emptyList()

        val apiKey = apiKeyOverride?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "YOUR_GEMINI_API_KEY_HERE" || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "No API key - cannot screen photos")
            return@withContext emptyList()
        }

        val kidContext = kidName?.let { " of child $it" } ?: ""
        val photoDescriptions = photos.mapIndexed { index, photo ->
            "[Photo $index]: Taken at ${photo.formattedTime} - ${photo.displayName}"
        }.joinToString("\n")

        val systemPrompt = """
            You are an expert photo curator for a family scheduler app. Select the BEST photos that match a specific event.

            EVENT DETAILS:
            - Event: "$eventTitle"$kidContext

            PHOTOS TO EVALUATE:
            $photoDescriptions

            SELECTION CRITERIA:
            1. Photos most relevant to the event type (e.g., swimming photos for swim class)
            2. Photos clearly showing the activity or child participating
            3. Good quality, in-focus photos

            OUTPUT FORMAT:
            Return ONLY a JSON array of photo indices, maximum $TOP_PHOTOS_PER_EVENT photos.
            Example: [0, 2, 5]
            If none are relevant, return: []
        """.trimIndent()

        try {
            val request = GeminiRequest(
                contents = listOf(Content(parts = listOf(Part(text = "Select the best photos for: $eventTitle$kidContext")))),
                generationConfig = GenerationConfig(responseMimeType = "application/json"),
                systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
            )
            val response = GeminiClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!jsonText.isNullOrEmpty()) {
                val selectedIndices = parseIndexArray(GeminiClient.cleanJson(jsonText))
                return@withContext selectedIndices
                    .filter { it in photos.indices }
                    .map { photos[it].uri.toString() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI photo screening failed: ${e.message}", e)
        }

        // Fallback: first N photos
        photos.take(TOP_PHOTOS_PER_EVENT).map { it.uri.toString() }
    }

    private fun parseIndexArray(json: String): List<Int> = try {
        val cleaned = json.replace(Regex("[^0-9,\\[\\] ]"), "")
        Regex("\\[([0-9, ]*)]").find(cleaned)
            ?.groupValues?.get(1)
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun getPhotosInTimeWindow(windowStart: Long, windowEnd: Long): List<PhotoInfo> {
        val photos = mutableListOf<PhotoInfo>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN
        )
        val selection =
            "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} <= ?"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf(windowStart.toString(), windowEnd.toString()),
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val dateTaken = cursor.getLong(dateColumn)
                    photos.add(
                        PhotoInfo(
                            uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                            displayName = cursor.getString(nameColumn) ?: "Unknown",
                            timestamp = dateTaken,
                            formattedTime = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(Date(dateTaken))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying photos: ${e.message}", e)
        }
        return photos
    }
}
