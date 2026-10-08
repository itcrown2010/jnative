package io.jnative.examples.mobile.calculator

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Structured bridge host for Android WebView.
 * Ordinary SharedPreferences KV — not a secure store for secrets.
 */
class BridgeHost(
    private val context: Context,
    private val webView: WebView,
) {
    companion object {
        const val VERSION = 1
        const val MAX_KEY = 128
        const val MAX_VAL = 4096
        private val CAPS = listOf(
            "platform.getName",
            "storage.get",
            "storage.set",
            "storage.remove",
            "lifecycle",
        )
        private val ALLOWED = (CAPS + "bridge.hello").toSet()
    }

    private val prefs = context.getSharedPreferences("jnative_calc_kv", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var alive = true

    fun destroy() {
        alive = false
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

    @JavascriptInterface
    fun postMessage(raw: String) {
        if (!alive) return
        executor.execute {
            try {
                handle(raw)
            } catch (e: Exception) {
                // Best-effort: cannot reply without id
            }
        }
    }

    private fun handle(raw: String) {
        val req = JSONObject(raw)
        val id = req.optString("id", "")
        if (id.isEmpty()) return
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
                replyOk(id, JSONObject()
                    .put("version", VERSION)
                    .put("platform", "android")
                    .put("capabilities", arr))
            }
            "platform.getName" -> replyOk(id, "android")
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
