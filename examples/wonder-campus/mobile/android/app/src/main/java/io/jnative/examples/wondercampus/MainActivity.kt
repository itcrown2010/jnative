// Author: 张晓达
package io.jnative.examples.wondercampus

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

/**
 * Independent Wonder Campus shell: WKWebView-equivalent Android WebView.
 * UI is HTML/CSS/JS (not Jetpack Compose native UI). Talks to JNative HTTP API.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val root = findViewById<ViewGroup>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        val debuggable =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(debuggable)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        val configJs = """
            window.WC_CONFIG = {
              env: "${BuildConfig.WC_ENV}",
              apiBase: "${BuildConfig.API_BASE}",
              allowDemoLogin: ${BuildConfig.ALLOW_DEMO_LOGIN},
              note: "webview-shell; not native Compose"
            };
        """.trimIndent()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val path = request.url.path ?: ""
                if (path.endsWith("/config.js") || path == "/www/config.js") {
                    return WebResourceResponse(
                        "application/javascript",
                        "utf-8",
                        ByteArrayInputStream(configJs.toByteArray(Charsets.UTF_8)),
                    )
                }
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        // Packaged assets; API calls go to BuildConfig.API_BASE (10.0.2.2 on emulator).
        webView.loadUrl("https://appassets.androidplatform.net/www/index.html")
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
