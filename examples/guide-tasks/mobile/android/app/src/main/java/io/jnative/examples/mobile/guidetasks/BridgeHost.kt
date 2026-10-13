// Author: 张晓达
package io.jnative.examples.mobile.guidetasks

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Structured bridge host — whitelist methods only; no script/command execution.
 * File handles wrap content URIs (not pretend filesystem paths).
 */
class BridgeHost(
    private val activity: AppCompatActivity,
    private val webView: WebView,
) {
    companion object {
        const val VERSION = 1
        const val MAX_KEY = 128
        const val MAX_VAL = 4096
        const val MAX_FILE_BYTES = 1_048_576
        const val MAX_NAME = 180
        const val MAX_MIME = 128
        private val CAPS = listOf(
            "platform.getName",
            "platform.queryCapabilities",
            "storage.get",
            "storage.set",
            "storage.remove",
            "lifecycle",
            "file.pick",
            "file.read",
            "file.release",
            "file.export",
            "share.system",
        )
        private val ALLOWED = (CAPS + "bridge.hello").toSet()
    }

    private data class FileHandle(
        val uri: Uri,
        val name: String,
        val mime: String,
        val size: Int,
        val takePersistable: Boolean,
    )

    private val prefs = activity.getSharedPreferences("jnative_guide_tasks_kv", Activity.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val handles = ConcurrentHashMap<String, FileHandle>()
    private val pendingPick = AtomicReference<String?>(null)
    private val pendingExport = AtomicReference<PendingExport?>(null)
    @Volatile private var alive = true
    @Volatile private var permissionDeniedSticky = false

    private data class PendingExport(
        val requestId: String,
        val bytes: ByteArray,
        val mime: String,
    )

    var openDocumentLauncher: ActivityResultLauncher<Array<String>>? = null
    var createDocumentLauncher: ActivityResultLauncher<String>? = null

    fun destroy() {
        alive = false
        failPendingUi("DESTROYED", "bridge destroyed")
        for ((_, h) in handles) {
            if (h.takePersistable) {
                try {
                    activity.contentResolver.releasePersistableUriPermission(
                        h.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: Exception) {
                }
            }
        }
        handles.clear()
        cleanupCacheShares()
        executor.shutdownNow()
    }

    fun emitLifecycle(name: String) {
        if (!alive) return
        val json = JSONObject()
            .put("type", "event")
            .put("name", name)
            .put("params", JSONObject())
            .toString()
        dispatchJs(json)
    }

    fun onOpenDocument(uri: Uri?) {
        val id = pendingPick.getAndSet(null) ?: return
        if (uri == null) {
            replyError(id, "USER_CANCELLED", "user cancelled file pick")
            return
        }
        executor.execute {
            try {
                try {
                    activity.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: SecurityException) {
                    // transient grant only
                }
                val name = queryDisplayName(uri) ?: "file"
                val mime = activity.contentResolver.getType(uri) ?: "application/octet-stream"
                val size = querySize(uri)
                if (size < 0) {
                    replyError(id, "IO_ERROR", "unable to determine file size")
                    return@execute
                }
                if (size > MAX_FILE_BYTES) {
                    replyError(id, "SIZE_LIMIT", "file exceeds $MAX_FILE_BYTES bytes")
                    return@execute
                }
                val handleId = "ah-" + UUID.randomUUID()
                handles[handleId] = FileHandle(
                    uri = uri,
                    name = name.take(MAX_NAME),
                    mime = mime.take(MAX_MIME),
                    size = size,
                    takePersistable = true,
                )
                replyOk(
                    id,
                    JSONObject()
                        .put("handleId", handleId)
                        .put("name", name.take(MAX_NAME))
                        .put("mimeType", mime.take(MAX_MIME))
                        .put("size", size),
                )
            } catch (e: Exception) {
                replyError(id, "IO_ERROR", e.message ?: "open document failed")
            }
        }
    }

    fun onCreateDocument(uri: Uri?) {
        val pending = pendingExport.getAndSet(null) ?: return
        if (uri == null) {
            replyError(pending.requestId, "USER_CANCELLED", "user cancelled export")
            return
        }
        executor.execute {
            try {
                activity.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pending.bytes)
                    out.flush()
                } ?: run {
                    replyError(pending.requestId, "IO_ERROR", "cannot open export uri")
                    return@execute
                }
                replyOk(
                    pending.requestId,
                    JSONObject().put("exported", true).put("method", "createDocument"),
                )
            } catch (e: Exception) {
                replyError(pending.requestId, "IO_ERROR", e.message ?: "export failed")
            }
        }
    }

    @JavascriptInterface
    fun postMessage(raw: String) {
        if (!alive) return
        if (raw.length > MAX_FILE_BYTES * 2 + 8192) return
        executor.execute {
            try {
                handle(raw)
            } catch (_: Exception) {
            }
        }
    }

    private fun handle(raw: String) {
        val req = JSONObject(raw)
        val id = req.optString("id", "")
        if (id.isEmpty() || id.length > 80) return
        val v = req.optInt("v", -1)
        if (v != VERSION) {
            replyError(id, "PROTOCOL_MISMATCH", "unsupported bridge version")
            return
        }
        val method = req.optString("method", "")
        if (method !in ALLOWED) {
            replyError(id, "METHOD_NOT_ALLOWED", "unknown method: $method")
            return
        }
        val params = req.optJSONObject("params") ?: JSONObject()
        when (method) {
            "bridge.hello" -> {
                val arr = JSONArray()
                for (c in CAPS) arr.put(c)
                replyOk(
                    id,
                    JSONObject()
                        .put("version", VERSION)
                        .put("platform", "android")
                        .put("capabilities", arr),
                )
            }
            "platform.getName" -> replyOk(id, "android")
            "platform.queryCapabilities" -> {
                val arr = JSONArray()
                for (c in CAPS) {
                    val status = if (permissionDeniedSticky && c.startsWith("file.")) "denied" else "available"
                    arr.put(JSONObject().put("name", c).put("status", status))
                }
                replyOk(id, arr)
            }
            "storage.get" -> {
                val key = params.optString("key", "")
                val err = validateKey(key)
                if (err != null) {
                    replyError(id, err.first, err.second)
                    return
                }
                val value = prefs.getString(key, null)
                if (value == null) replyOkNull(id) else replyOk(id, value)
            }
            "storage.set" -> {
                val key = params.optString("key", "")
                if (!params.has("value") || params.isNull("value")) {
                    replyError(id, "BAD_VALUE", "value must be string length 0..$MAX_VAL")
                    return
                }
                val value = params.getString("value")
                val err = validateKey(key) ?: validateValue(value)
                if (err != null) {
                    replyError(id, err.first, err.second)
                    return
                }
                prefs.edit().putString(key, value).apply()
                replyOk(id, true)
            }
            "storage.remove" -> {
                val key = params.optString("key", "")
                val err = validateKey(key)
                if (err != null) {
                    replyError(id, err.first, err.second)
                    return
                }
                prefs.edit().remove(key).apply()
                replyOk(id, true)
            }
            "file.pick" -> startPick(id, params)
            "file.read" -> readFile(id, params)
            "file.release" -> {
                val handleId = params.optString("handleId", "")
                val h = handles.remove(handleId)
                if (h?.takePersistable == true) {
                    try {
                        activity.contentResolver.releasePersistableUriPermission(
                            h.uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    } catch (_: Exception) {
                    }
                }
                replyOk(id, true)
            }
            "file.export" -> startExport(id, params)
            "share.system" -> startShare(id, params)
        }
    }

    private fun startPick(id: String, params: JSONObject) {
        if (openDocumentLauncher == null) {
            replyError(id, "UNSUPPORTED", "document picker not wired")
            return
        }
        if (!pendingPick.compareAndSet(null, id)) {
            replyError(id, "BUSY", "another file pick is in progress")
            return
        }
        val accept = params.optJSONArray("accept")
        val mimeTypes = mutableListOf<String>()
        if (accept != null) {
            for (i in 0 until accept.length()) {
                val a = accept.optString(i, "")
                when {
                    a == "application/json" || a.endsWith(".json") || a.endsWith(".jnbackup") ->
                        mimeTypes.add("application/json")
                    a.contains("/") -> mimeTypes.add(a)
                }
            }
        }
        if (mimeTypes.isEmpty()) {
            mimeTypes.add("application/json")
            mimeTypes.add("text/plain")
            mimeTypes.add("*/*")
        }
        activity.runOnUiThread {
            try {
                openDocumentLauncher?.launch(mimeTypes.toTypedArray())
            } catch (e: Exception) {
                pendingPick.set(null)
                replyError(id, "IO_ERROR", e.message ?: "launch picker failed")
            }
        }
    }

    private fun readFile(id: String, params: JSONObject) {
        val handleId = params.optString("handleId", "")
        val h = handles[handleId]
        if (h == null) {
            replyError(id, "BAD_PARAMS", "unknown or released file handle")
            return
        }
        val maxBytes = params.optInt("maxBytes", MAX_FILE_BYTES).coerceIn(1, MAX_FILE_BYTES)
        if (h.size > maxBytes) {
            replyError(id, "SIZE_LIMIT", "file exceeds $maxBytes bytes")
            return
        }
        val asText = params.optString("as", "text") != "bytesBase64"
        try {
            val bytes = activity.contentResolver.openInputStream(h.uri)?.use { input ->
                val buf = ByteArrayOutputStream()
                val tmp = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(tmp)
                    if (n <= 0) break
                    total += n
                    if (total > maxBytes) {
                        replyError(id, "SIZE_LIMIT", "file exceeds $maxBytes bytes")
                        return
                    }
                    buf.write(tmp, 0, n)
                }
                buf.toByteArray()
            } ?: run {
                replyError(id, "IO_ERROR", "cannot open content uri")
                return
            }
            if (asText) {
                val text = bytes.toString(Charsets.UTF_8)
                replyOk(
                    id,
                    JSONObject()
                        .put("text", text)
                        .put("bytesRead", bytes.size)
                        .put("encoding", "utf-8"),
                )
            } else {
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                replyOk(
                    id,
                    JSONObject()
                        .put("bytesBase64", b64)
                        .put("bytesRead", bytes.size)
                        .put("encoding", "base64"),
                )
            }
        } catch (e: SecurityException) {
            permissionDeniedSticky = true
            replyError(id, "PERMISSION_DENIED", "read permission denied for content uri")
        } catch (e: Exception) {
            replyError(id, "IO_ERROR", e.message ?: "read failed")
        }
    }

    private fun startExport(id: String, params: JSONObject) {
        val name = params.optString("name", "")
        if (name.isEmpty() || name.length > MAX_NAME) {
            replyError(id, "BAD_PARAMS", "file name must be 1..$MAX_NAME chars")
            return
        }
        val mime = params.optString("mimeType", "application/octet-stream").take(MAX_MIME)
        val bytes = decodePayload(params) ?: run {
            replyError(id, "BAD_PARAMS", "text or bytesBase64 required")
            return
        }
        if (bytes.size > MAX_FILE_BYTES) {
            replyError(id, "SIZE_LIMIT", "export exceeds $MAX_FILE_BYTES bytes")
            return
        }
        if (createDocumentLauncher == null) {
            replyError(id, "UNSUPPORTED", "create document not wired")
            return
        }
        if (!pendingExport.compareAndSet(null, PendingExport(id, bytes, mime))) {
            replyError(id, "BUSY", "another export is in progress")
            return
        }
        activity.runOnUiThread {
            try {
                createDocumentLauncher?.launch(name)
            } catch (e: Exception) {
                pendingExport.set(null)
                replyError(id, "IO_ERROR", e.message ?: "launch export failed")
            }
        }
    }

    private fun startShare(id: String, params: JSONObject) {
        val title = params.optString("title", "").take(120)
        val text = if (params.has("text") && !params.isNull("text")) params.getString("text") else null
        val name = params.optString("name", "")
        val mime = params.optString("mimeType", "text/plain").take(MAX_MIME)
        val bytes = if (params.has("bytesBase64") && !params.isNull("bytesBase64")) {
            decodePayload(params)
        } else if (text != null) {
            text.toByteArray(Charsets.UTF_8)
        } else null
        if (bytes != null && bytes.size > MAX_FILE_BYTES) {
            replyError(id, "SIZE_LIMIT", "share payload exceeds limit")
            return
        }
        activity.runOnUiThread {
            try {
                val intent = Intent(Intent.ACTION_SEND)
                if (bytes != null && name.isNotEmpty()) {
                    val cache = File(activity.cacheDir, "share")
                    cache.mkdirs()
                    val file = File(cache, name.take(MAX_NAME).replace('/', '_'))
                    file.writeBytes(bytes)
                    val uri = FileProvider.getUriForFile(
                        activity,
                        activity.packageName + ".fileprovider",
                        file,
                    )
                    intent.type = mime
                    intent.putExtra(Intent.EXTRA_STREAM, uri)
                    intent.clipData = ClipData.newUri(activity.contentResolver, name, uri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (!text.isNullOrEmpty()) intent.putExtra(Intent.EXTRA_TEXT, text.take(2000))
                } else if (!text.isNullOrEmpty()) {
                    intent.type = "text/plain"
                    intent.putExtra(Intent.EXTRA_TEXT, text)
                    intent.putExtra(Intent.EXTRA_SUBJECT, title)
                } else {
                    replyError(id, "BAD_PARAMS", "share requires text or file payload")
                    return@runOnUiThread
                }
                val chooser = Intent.createChooser(intent, title.ifEmpty { "Share" })
                activity.startActivity(chooser)
                // System does not reliably report delivery
                replyOk(
                    id,
                    JSONObject()
                        .put("status", "unknown")
                        .put("note", "share sheet shown; delivery not confirmed"),
                )
            } catch (e: Exception) {
                replyError(id, "IO_ERROR", e.message ?: "share failed")
            }
        }
    }

    private fun decodePayload(params: JSONObject): ByteArray? {
        if (params.has("text") && !params.isNull("text")) {
            return params.getString("text").toByteArray(Charsets.UTF_8)
        }
        if (params.has("bytesBase64") && !params.isNull("bytesBase64")) {
            return try {
                Base64.decode(params.getString("bytesBase64"), Base64.DEFAULT)
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = activity.contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && it.moveToFirst()) return it.getString(idx)
        }
        return null
    }

    private fun querySize(uri: Uri): Int {
        val cursor = activity.contentResolver.query(uri, null, null, null, null) ?: return -1
        cursor.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (idx >= 0 && it.moveToFirst()) {
                val v = it.getLong(idx)
                return if (v > Int.MAX_VALUE) Int.MAX_VALUE else v.toInt()
            }
        }
        // Fallback: stream count (bounded)
        return try {
            activity.contentResolver.openInputStream(uri)?.use { input ->
                var total = 0
                val tmp = ByteArray(8192)
                while (true) {
                    val n = input.read(tmp)
                    if (n <= 0) break
                    total += n
                    if (total > MAX_FILE_BYTES) return MAX_FILE_BYTES + 1
                }
                total
            } ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    private fun failPendingUi(code: String, message: String) {
        pendingPick.getAndSet(null)?.let { replyError(it, code, message) }
        pendingExport.getAndSet(null)?.let { replyError(it.requestId, code, message) }
    }

    private fun cleanupCacheShares() {
        try {
            File(activity.cacheDir, "share").listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    private fun validateKey(key: String?): Pair<String, String>? {
        if (key == null || key.isEmpty() || key.length > MAX_KEY) {
            return "BAD_KEY" to "key must be string 1..$MAX_KEY"
        }
        return null
    }

    private fun validateValue(value: String?): Pair<String, String>? {
        if (value == null || value.length > MAX_VAL) {
            return "BAD_VALUE" to "value must be string length 0..$MAX_VAL"
        }
        return null
    }

    private fun replyOk(id: String, result: Any) {
        val json = JSONObject().put("id", id).put("ok", true).put("result", result).toString()
        dispatchJs(json)
    }

    private fun replyOkNull(id: String) {
        val json = JSONObject().put("id", id).put("ok", true).put("result", JSONObject.NULL).toString()
        dispatchJs(json)
    }

    private fun replyError(id: String, code: String, message: String) {
        val err = JSONObject().put("code", code).put("message", message)
        val json = JSONObject().put("id", id).put("ok", false).put("error", err).toString()
        dispatchJs(json)
    }

    private fun dispatchJs(json: String) {
        if (!alive) return
        val escaped = JSONObject.quote(json)
        webView.post {
            if (!alive) return@post
            webView.evaluateJavascript(
                "(function(){try{var m=JSON.parse($escaped);window.postMessage(m, window.location.origin||'*');}catch(e){}})();",
                null,
            )
        }
    }
}
