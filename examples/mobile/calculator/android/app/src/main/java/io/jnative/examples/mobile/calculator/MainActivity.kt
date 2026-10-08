package io.jnative.examples.mobile.calculator

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
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

/**
 * Thin Android container: loads packaged WebUI/WASM from app assets only.
 * External http(s) links open in the system browser (no bridge there).
 */
class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var bridge: BridgeHost? = null

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
        val bridgeHost = BridgeHost(this, webView)
        bridge = bridgeHost

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.addJavascriptInterface(bridgeHost, "JNativeHost")

        // Serve assets via https://appassets.androidplatform.net/ — isolated origin.
        // Path "/" maps URL /www/... → assets/www/...
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                val host = url.host
                if (host == "appassets.androidplatform.net") {
                    return false
                }
                // External link: open outside the bridged WebView.
                startActivity(Intent(Intent.ACTION_VIEW, url))
                return true
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        webView.loadUrl("https://appassets.androidplatform.net/www/index.html")
    }

    override fun onResume() {
        super.onResume()
        bridge?.emitLifecycle("lifecycle.foreground")
    }

    override fun onPause() {
        bridge?.emitLifecycle("lifecycle.background")
        super.onPause()
    }

    override fun onDestroy() {
        bridge?.destroy()
        bridge = null
        webView.destroy()
        super.onDestroy()
    }
}
