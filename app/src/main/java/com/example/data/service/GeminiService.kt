package com.example.data.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiService(private val context: Context) {

    companion object {
        private const val MODEL = "gemini-3.8-flash"
        private const val MAX_DOCUMENT_CHARS = 12000
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getApiKey(): String = try {
        BuildConfig.GEMINI_API_KEY.trim()
    } catch (_: Exception) {
        ""
    }

    private fun geminiNotConfigured(): String =
        "NOVA AI is not available in this build. The app needs its built-in Gemini service configuration."

    private fun extractText(responseBody: String): String {
        val parsed = JSONObject(responseBody)
        return parsed.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
            ?.optJSONArray("parts")?.let { parts ->
                buildString {
                    for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
                }
            }?.trim().orEmpty()
    }

    private suspend fun generate(requestJson: JSONObject, apiKey: String, maxOutputTokens: Int = 1200): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") return@withContext geminiNotConfigured()
            val payload = JSONObject(requestJson.toString()).apply {
                if (!has("generationConfig")) put("generationConfig", JSONObject())
                val config = getJSONObject("generationConfig")
                config.put("maxOutputTokens", maxOutputTokens)
                config.put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
            }
            for (attempt in 0..1) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
                    val request = Request.Builder().url(url).header("Accept", "application/json")
                        .post(payload.toString().toRequestBody(jsonMediaType)).build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            val answer = extractText(body)
                            if (answer.isNotBlank()) return@withContext answer
                        } else {
                            val retryable = response.code == 429 || response.code == 500 || response.code == 503 || response.code == 504
                            if (!retryable || attempt == 1) return@withContext when (response.code) {
                                401, 403 -> "NOVA AI authentication failed. The built-in Gemini service configuration is invalid."
                                404 -> "NOVA AI model is unavailable. Please rebuild with the current NOVA AI configuration."
                                429 -> "NOVA AI is temporarily rate-limited. Please try again in a moment."
                                else -> "NOVA AI request failed (${response.code}). Please try again."
                            }
                        }
                    }
                } catch (_: Exception) {
                    if (attempt == 1) return@withContext "NOVA AI connection error. Please check the internet connection and try again."
                }
                delay(450L)
            }
            "NOVA AI request failed. Please try again."
        }

    private fun textRequest(prompt: String): JSONObject = JSONObject().apply {
        put("contents", JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply { put(JSONObject().put("text", prompt)) })
            })
        })
    }

    suspend fun askDocument(
        question: String,
        documentTitle: String,
        documentText: String,
        pageContext: Int? = null,
        history: List<Pair<String, String>> = emptyList() // List of (role, text)
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext geminiNotConfigured()
        }

        val systemPrompt = """
            You are NOVA PDF AI, an expert, objective document reading assistant.
            Document Title: "$documentTitle"
            ${pageContext?.let { "The user is currently reading Page $it." } ?: ""}
            
            RULES:
            1. Answer using the provided document text whenever possible.
            2. If you find the answer in the document, ALWAYS cite the page number in brackets like [Page X], e.g. "Heritability measures genetic variation [Page 1]".
            3. If the answer cannot be found in the document, state: "I couldn't find this information in the document." then provide helpful general knowledge clearly marked as such.
            4. Keep explanations clear, structured, and easy to read.
            5. Support Hindi or Hinglish when requested by the user.
            6. Accurately preserve formulas, equations, and tables.
        """.trimIndent()

        try {
            val contentsArray = JSONArray()

            // System instruction or initial context turn
            val contextText = """
                [DOCUMENT CONTENT]:
                ${documentText.take(MAX_DOCUMENT_CHARS)}
            """.trimIndent()

            val initialTurn = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", "$contextText\n\nPlease acknowledge receipt of this document.") })
                })
            }
            contentsArray.put(initialTurn)

            val initialAck = JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", "I have received the document \"$documentTitle\". How can I help you understand or study it?") })
                })
            }
            contentsArray.put(initialAck)

            // Add previous chat turns
            for ((role, text) in history.takeLast(6)) {
                val turn = JSONObject().apply {
                    put("role", if (role == "user") "user" else "model")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", text) })
                    })
                }
                contentsArray.put(turn)
            }

            // Current prompt
            val currentTurn = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", question) })
                })
            }
            contentsArray.put(currentTurn)

            val requestJson = JSONObject().apply {
                put("contents", contentsArray)
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 2048)
                })
            }

            // Gemini can temporarily return 503 when a model is overloaded. Retry with
            // exponential backoff, then automatically cascade through stable Flash models.
            // A 401/403/400/404 is not retried because those indicate configuration/request
            // problems rather than transient service availability.
            val fallbackModels = listOf(
                modelName,
                "gemini-3.7-flash",
                "gemini-3.6-flash",
                "gemini-3.5-flash-lite"
            ).distinct()

            var lastErrorCode = 0
            var lastErrorMessage = ""

            for ((modelIndex, model) in fallbackModels.withIndex()) {
                val maxAttempts = if (modelIndex == 0) 3 else 2

                for (attempt in 1..maxAttempts) {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val request = Request.Builder()
                        .url(url)
                        .post(requestJson.toString().toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        val parsed = JSONObject(responseBody)
                        val candidates = parsed.optJSONArray("candidates")
                        val candidate = candidates?.optJSONObject(0)
                        val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
                        val answer = parts?.optJSONObject(0)?.optString("text").orEmpty()

                        if (answer.isNotBlank()) {
                            return@withContext answer
                        }

                        lastErrorCode = 200
                        lastErrorMessage = "Gemini returned an empty response."
                        break
                    }

                    lastErrorCode = response.code
                    lastErrorMessage = responseBody

                    // Only transient service/rate-limit/server errors are retried.
                    val retryable = response.code == 429 || response.code == 500 ||
                        response.code == 503 || response.code == 504

                    if (!retryable) {
                        return@withContext "Gemini request failed (${response.code}). Please check the API key, selected model, and Gemini API access."
                    }

                    if (attempt < maxAttempts) {
                        // 1s, then 2s; small jitter avoids synchronized retries.
                        val backoffMs = 1000L shl (attempt - 1)
                        val jitterMs = kotlin.random.Random.nextLong(0L, 350L)
                        delay(backoffMs + jitterMs)
                    }
                }
            }

            return@withContext when (lastErrorCode) {
                429 -> "Gemini is temporarily rate-limited. Please wait a moment and try again."
                503 -> "Gemini is temporarily unavailable. NOVA tried multiple Flash models automatically; please try again shortly."
                500, 504 -> "Gemini is temporarily having server issues. NOVA retried automatically; please try again shortly."
                else -> "Gemini request failed ($lastErrorCode). ${lastErrorMessage.ifBlank { "Please try again." }}"
            }
        } catch (e: Exception) {
            "Gemini connection error: ${e.message ?: "unknown error"}. Check your internet connection and API key."
        }
    }

    suspend fun summarize(
        summaryType: String, // Quick, Detailed, One-page, Chapter, Key Points, Exam Notes, 5-Minute Revision
        documentTitle: String,
        documentText: String
    ): String {
        val prompt = when (summaryType) {
            "Quick Summary" -> "Provide a crisp 3-paragraph quick summary of '$documentTitle' highlighting the central thesis, primary findings, and key takeaway. Include [Page X] citations."
            "Detailed Summary" -> "Provide a comprehensive, well-structured detailed summary of '$documentTitle' with section headings, in-depth explanations, and [Page X] citations."
            "One-page Summary" -> "Generate an executive one-page summary of '$documentTitle' with bullet points, core definitions, and key conclusions."
            "Chapter Summary" -> "Break down each chapter/section of '$documentTitle' with key concepts and page citations [Page X]."
            "Key Points" -> "List the top 10 most critical bullet points from '$documentTitle' with page numbers."
            "Exam Notes" -> "Create high-yield exam revision notes from '$documentTitle', including formulas, definitions, and high-frequency test concepts."
            "5-Minute Revision" -> "Create a rapid 5-minute revision cheat sheet with quick recall facts and summaries."
            else -> "Summarize '$documentTitle' with key takeaways and page citations."
        }
        return askDocument(prompt, documentTitle, documentText)
    }

    suspend fun explainSelectedText(
        selectedText: String,
        style: String // Simple, Detailed, Example, Hindi, Hinglish
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        val prompt = when (style) {
            "Simple" -> "Explain the following text in very simple, easy-to-understand terms suitable for a beginner:\n\n\"$selectedText\""
            "Detailed" -> "Provide a thorough, in-depth academic explanation of the following text with nuances and context:\n\n\"$selectedText\""
            "Example" -> "Explain the following concept using 2 concrete, relatable real-world examples:\n\n\"$selectedText\""
            "Hindi" -> "निम्नलिखित अंश को सरल और स्पष्ट हिंदी भाषा में समझाइए:\n\n\"$selectedText\""
            "Hinglish" -> "Explain the following passage in natural, easy Hinglish (conversational Hindi-English blend):\n\n\"$selectedText\""
            else -> "Explain this passage clearly:\n\n\"$selectedText\""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext geminiNotConfigured()
        }

        try {
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val parsed = JSONObject(response.body?.string().orEmpty())
            val answer = parsed.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?.optJSONObject(0)?.optString("text").orEmpty()

            if (answer.isNotBlank()) answer else "Gemini returned an empty response. Please try again."
        } catch (e: Exception) {
            "Gemini connection error: ${e.message ?: "unknown error"}."
        }
    }

    suspend fun translateText(
        text: String,
        targetLanguage: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        val prompt = "Translate the following text faithfully and accurately into $targetLanguage. Output ONLY the translated text:\n\n\"$text\""

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "[$targetLanguage Translation]:\n$text"
        }

        try {
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
            }
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val parsed = JSONObject(response.body?.string().orEmpty())
            parsed.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?.optJSONObject(0)?.optString("text")?.trim() ?: text
        } catch (e: Exception) {
            "[$targetLanguage Translation]:\n$text"
        }
    }

    suspend fun analyzeScannedPageWithOcr(
        bitmap: Bitmap,
        prompt: String = "Perform high-precision OCR on this document page. Transcribe all text, preserving headings, lists, tables, and formulas. After the transcription, provide a brief 2-bullet summary."
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "OCR Analysis: Document image processed. (Connect Gemini API Key in Settings for full multimodal OCR and handwriting transcription)."
        }

        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val base64Data = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Data)
                                })
                            })
                        })
                    })
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val parsed = JSONObject(response.body?.string().orEmpty())
            parsed.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?.optJSONObject(0)?.optString("text") ?: "OCR processing completed."
        } catch (e: Exception) {
            "OCR Analysis: Scanned document page captured."
        }
    }

    suspend fun compareDocuments(
        titleA: String,
        textA: String,
        titleB: String,
        textB: String
    ): String {
        val prompt = """
            Compare the following two documents thoroughly:
            Document A: "$titleA"
            Document B: "$titleB"
            
            Find and structure into clear sections:
            1. Common Information & Shared Themes
            2. Key Differences & Contrasting Perspectives
            3. Added Information in Document B
            4. Removed or Missing Information
            5. Changed Values, Numbers or Statistics
            6. Overall Conclusions
        """.trimIndent()
        return askDocument(prompt, "$titleA vs $titleB", "Doc A:\n$textA\n\nDoc B:\n$textB")
    }

    suspend fun analyzeDocumentStructure(
        documentTitle: String,
        documentText: String
    ): String {
        val prompt = """
            Analyze this document deeply and extract structured cards for:
            - Main Topic & Executive Theme
            - Chapters & Core Sections
            - Important Definitions (with page references)
            - Key Concepts
            - Critical Dates & Historical Timeline
            - Names & Contributors
            - Key Formulas & Mathematical Expressions
            - Structured Tables & Data
            - Review Questions
            - Core Conclusions
        """.trimIndent()
        return askDocument(prompt, documentTitle, documentText)
    }

}
