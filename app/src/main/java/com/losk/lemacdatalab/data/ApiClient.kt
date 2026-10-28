package com.losk.lemacdatalab.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

class ApiException(val status: Int, message: String) : IOException(message)

class ApiClient(private val baseUrl: String) {
    init {
        val uri = URI(baseUrl)
        require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Ingresa una URL de API válida, por ejemplo http://10.0.2.2:8080."
        }
        require(uri.scheme == "https" || uri.host in listOf("10.0.2.2", "127.0.0.1", "localhost")) {
            "Usa HTTPS para una API remota. HTTP está habilitado solo para pruebas locales."
        }
    }

    fun request(path: String, body: JSONObject, token: String? = null): JSONObject {
        val conn = URI(baseUrl.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 6000; conn.readTimeout = 15000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = try { JSONObject(raw) } catch (_: Exception) { throw IOException("La API devolvió una respuesta no válida (HTTP $code).") }
            if (code !in 200..299) throw ApiException(code, json.optString("error", "Error de API: $code"))
            return json
        } finally { conn.disconnect() }
    }
}
