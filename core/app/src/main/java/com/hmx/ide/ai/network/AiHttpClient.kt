package com.hmx.ide.ai.network

import com.hmx.ide.activities.HttpConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader

data class HttpResponse(
  val code: Int,
  val body: String,
)

class AiHttpClient(
  private val connectTimeout: Long = 10_000,
  private val readTimeout: Long = 30_000,
  private val maxRetries: Int = 3,
  private val baseRetryDelay: Long = 1_000,
) {

  private val log = LoggerFactory.getLogger(AiHttpClient::class.java)

  suspend fun execute(
    config: HttpConfig,
    body: String? = null,
  ): HttpResponse = withContext(Dispatchers.IO) {
    retry(config, body) { cfg, b -> executeSync(cfg, b) }
  }

  fun stream(
    config: HttpConfig,
    body: String? = null,
  ): Flow<String> = flow {
    val attempt = retry(config, body) { cfg, b -> streamSync(cfg, b) }
    attempt.forEach { line ->
      if (!currentCoroutineContext().isActive) return@forEach
      emit(line)
    }
  }.flowOn(Dispatchers.IO)

  private fun buildConnection(config: HttpConfig): java.net.HttpURLConnection {
    val url = java.net.URL(config.url)
    val conn = url.openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = connectTimeout.toInt()
    conn.readTimeout = readTimeout.toInt()
    conn.requestMethod = config.method
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("User-Agent", "HMX-IDE/1.0")
    for ((name, value) in config.headers) {
      if (value.isNotEmpty()) conn.setRequestProperty(name, value)
    }
    return conn
  }

  private fun executeSync(config: HttpConfig, body: String?): HttpResponse {
    val conn = buildConnection(config)
    if (body != null) {
      conn.doOutput = true
      java.io.OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
    }
    val code = conn.responseCode
    val responseBody = if (code in 200..299) {
      conn.inputStream.bufferedReader().readText()
    } else {
      conn.errorStream?.bufferedReader()?.readText().orEmpty()
    }
    log.debug("{} {} → HTTP {}", config.method, config.url, code)
    return HttpResponse(code, responseBody)
  }

  private fun streamSync(config: HttpConfig, body: String?): Sequence<String> = sequence {
    val conn = buildConnection(config)
    if (body != null) {
      conn.doOutput = true
      java.io.OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
    }
    val code = conn.responseCode
    if (code !in 200..299) {
      val errBody = conn.errorStream?.bufferedReader()?.readText().orEmpty()
      log.warn("{} {} → HTTP {}: {}", config.method, config.url, code, errBody)
      return@sequence
    }
    val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
    reader.use { r ->
      r.lineSequence().forEach { line ->
        if (line.startsWith("data: ")) {
          val data = line.removePrefix("data: ").trim()
          if (data != "[DONE]") yield(data)
        }
      }
    }
  }

  private suspend fun <T> retry(
    config: HttpConfig,
    body: String?,
    block: (HttpConfig, String?) -> T,
  ): T {
    var lastError: Exception? = null
    for (attempt in 0..maxRetries) {
      try {
        return block(config, body)
      } catch (e: Exception) {
        lastError = e
        if (attempt < maxRetries) {
          val delay = baseRetryDelay * (1L shl attempt)
          log.warn("{} {} failed (attempt {}/{}), retrying in {}ms: {}",
            config.method, config.url, attempt + 1, maxRetries, delay, e.message)
          delay(delay)
        }
      }
    }
    throw lastError ?: RuntimeException("Request failed after $maxRetries retries")
  }
}
