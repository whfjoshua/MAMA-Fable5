package com.mama.scheduler.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ---------- Request / response models ----------

data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

data class Content(val parts: List<Part>)

data class Part(val text: String? = null)

data class GenerationConfig(val responseMimeType: String? = null)

data class GeminiResponse(val candidates: List<Candidate>?)

data class Candidate(val content: Content?)

// Structured result of natural-language schedule parsing
data class ParsedScheduleResult(
    val title: String,
    val kidName: String?,
    val dateString: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val travelBufferMinutes: Int,
    val isRecurring: Boolean = false,
    val recurrenceRule: String? = null,
    val recurringEndDate: String? = null
)

data class ParsedScheduleListResult(val events: List<ParsedScheduleResult>)

// ---------- Service ----------

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    /** Strips markdown code fences that some model responses wrap around JSON. */
    fun cleanJson(raw: String): String = raw.trim()
        .replace("^```json".toRegex(), "")
        .replace("^```".toRegex(), "")
        .replace("```$".toRegex(), "")
        .trim()
}
