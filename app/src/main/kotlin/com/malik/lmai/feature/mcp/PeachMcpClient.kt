package com.malik.lmai.feature.mcp

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Singleton
class PeachMcpClient @Inject constructor(
    private val httpClient: HttpClient,
    private val oauth: PeachMcpOAuthCoordinator,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val nextId = AtomicLong(1)
    private val protocolMutex = Mutex()

    @Volatile private var mode: ProtocolMode? = null
    @Volatile private var sessionId: String? = null

    data class RemoteTool(
        val name: String,
        val description: String,
        val inputSchema: JsonElement,
    )

    suspend fun listTools(): List<RemoteTool> {
        val response = invoke("tools/list", buildJsonObject {})
        val tools = response["result"]?.jsonObject?.get("tools") as? JsonArray ?: return emptyList()
        return tools.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            RemoteTool(
                name = name,
                description = (obj["description"] as? JsonPrimitive)?.content.orEmpty(),
                inputSchema = obj["inputSchema"] ?: buildJsonObject { put("type", "object") },
            )
        }
    }

    suspend fun callTool(name: String, arguments: JsonObject): JsonElement {
        require(name.isNotBlank()) { "Peach MCP tool name is required" }
        val response = invoke(
            "tools/call",
            buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            },
            toolName = name,
        )
        response["error"]?.let { error("Peach MCP tool failed: $it") }
        return response["result"] ?: response
    }

    suspend fun healthCheck(): Result<Int> = runCatching { listTools().size }

    fun resetSession() {
        mode = null
        sessionId = null
    }

    private suspend fun invoke(
        method: String,
        params: JsonObject,
        toolName: String? = null,
    ): JsonObject {
        ensureProtocol()
        val selectedMode = mode ?: error("Peach MCP protocol was not initialized")
        return request(
            method = method,
            params = params,
            protocolVersion = selectedMode.version,
            includeSession = selectedMode == ProtocolMode.LEGACY,
            toolName = toolName,
        )
    }

    private suspend fun ensureProtocol() {
        if (mode != null) return
        protocolMutex.withLock {
            if (mode != null) return

            val latest = runCatching {
                request(
                    method = "tools/list",
                    params = buildJsonObject {},
                    protocolVersion = ProtocolMode.STATELESS.version,
                    includeSession = false,
                )
            }.getOrNull()
            if (latest?.get("result") != null && latest["error"] == null) {
                mode = ProtocolMode.STATELESS
                return
            }

            initializeLegacy()
            mode = ProtocolMode.LEGACY
        }
    }

    private suspend fun initializeLegacy() {
        val token = oauth.validAccessToken() ?: error("Peach is not connected to H")
        val id = nextId.getAndIncrement()
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "initialize")
            put("params", buildJsonObject {
                put("protocolVersion", ProtocolMode.LEGACY.version)
                put("capabilities", buildJsonObject {})
                put("clientInfo", buildJsonObject {
                    put("name", "lm_AI H")
                    put("version", "2.1.1")
                })
            })
        }
        val response = httpClient.post(PeachMcpOAuthCoordinator.SERVER_URL) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            header(HttpHeaders.Authorization, "Bearer $token")
            header("MCP-Protocol-Version", ProtocolMode.LEGACY.version)
            setBody(payload.toString())
        }
        check(response.status.value in 200..299) {
            "Peach MCP initialize failed (${response.status.value}): ${response.body<String>().take(300)}"
        }
        sessionId = response.headers["Mcp-Session-Id"] ?: response.headers["MCP-Session-Id"]
        val initialized = parseResponse(response.body<String>())
        initialized["error"]?.let { error("Peach MCP initialize failed: $it") }

        val notification = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "notifications/initialized")
        }
        httpClient.post(PeachMcpOAuthCoordinator.SERVER_URL) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            header(HttpHeaders.Authorization, "Bearer $token")
            header("MCP-Protocol-Version", ProtocolMode.LEGACY.version)
            sessionId?.let { header("Mcp-Session-Id", it) }
            setBody(notification.toString())
        }
    }

    private suspend fun request(
        method: String,
        params: JsonObject,
        protocolVersion: String,
        includeSession: Boolean,
        toolName: String? = null,
    ): JsonObject {
        val token = oauth.validAccessToken() ?: error("Peach is not connected to H")
        val id = nextId.getAndIncrement()
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
            if (protocolVersion == ProtocolMode.STATELESS.version) {
                put("_meta", buildJsonObject {
                    put("io.modelcontextprotocol/clientInfo", buildJsonObject {
                        put("name", "lm_AI H")
                        put("version", "2.1.1")
                    })
                })
            }
        }
        val response = httpClient.post(PeachMcpOAuthCoordinator.SERVER_URL) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            header(HttpHeaders.Authorization, "Bearer $token")
            header("MCP-Protocol-Version", protocolVersion)
            header("Mcp-Method", method)
            toolName?.let { header("Mcp-Name", it) }
            if (includeSession) sessionId?.let { header("Mcp-Session-Id", it) }
            setBody(payload.toString())
        }
        if (response.status.value == 401) {
            oauth.disconnect()
            resetSession()
            error("Peach authorization expired. Reconnect H to Peach.")
        }
        check(response.status.value in 200..299) {
            "Peach MCP request failed (${response.status.value}): ${response.body<String>().take(300)}"
        }
        val parsed = parseResponse(response.body<String>())
        parsed["error"]?.let { error("Peach MCP error: $it") }
        return parsed
    }

    private fun parseResponse(body: String): JsonObject {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) return json.parseToJsonElement(trimmed).jsonObject

        val dataPayloads = trimmed.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.startsWith("{") }
            .toList()
        val payload = dataPayloads.lastOrNull()
            ?: error("Peach MCP returned an unsupported response")
        return json.parseToJsonElement(payload).jsonObject
    }

    private enum class ProtocolMode(val version: String) {
        STATELESS("2026-07-28"),
        LEGACY("2025-11-25"),
    }
}
