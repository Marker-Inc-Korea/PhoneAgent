package ai.markr.phoneagent.llm

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Shared HTTP + JSON plumbing for the LLM clients. */
object LlmHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().build()
    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

    fun toJson(body: Map<String, Any?>): String = mapAdapter.toJson(body)

    fun parse(json: String): Map<String, Any?> =
        mapAdapter.fromJson(json) ?: emptyMap()

    /** POST a JSON body and return the response string, throwing on non-2xx. */
    fun post(url: String, headers: Map<String, String>, jsonBody: String): String {
        val req = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(JSON_MEDIA))
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw LlmException("HTTP ${resp.code}: ${text.take(300)}")
            }
            return text
        }
    }

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
}

class LlmException(message: String) : Exception(message)
