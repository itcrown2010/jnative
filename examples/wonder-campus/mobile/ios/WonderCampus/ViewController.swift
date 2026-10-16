// Author: 张晓达
import UIKit
import WebKit

/// 奇想校园独立 iOS 壳：WKWebView 加载触屏网页界面，不是原生 SwiftUI/UIKit 业务 UI。
final class ViewController: UIViewController, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
    private var webView: WKWebView!
    private var apiBase = "http://127.0.0.1:18880"
    private var envName = "dev"
    private var allowDemo = true
    private var loadRemote = true

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 1, green: 0.97, blue: 0.94, alpha: 1)
        loadRuntimeConfig()

        let config = WKWebViewConfiguration()
        config.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")
        config.websiteDataStore = .default()
        config.userContentController.add(self, name: "wcNative")

        let acceptLogin = ProcessInfo.processInfo.environment["WC_ACCEPT_LOGIN"] ?? ""
        let acceptIdentity = ProcessInfo.processInfo.environment["WC_ACCEPT_IDENTITY"] ?? "ADMIN"
        let acceptBlock: String
        if allowDemo && !acceptLogin.isEmpty {
            acceptBlock = """
            document.addEventListener('DOMContentLoaded', function () {
              var tries = 0;
              function go() {
                var s = document.getElementById('loginSel');
                var i = document.getElementById('idSel');
                var b = document.getElementById('btnLogin');
                if (s && i && b) { s.value = '\(acceptLogin)'; i.value = '\(acceptIdentity)'; b.click(); return; }
                if (++tries < 50) setTimeout(go, 120);
              }
              setTimeout(go, 80);
            });
            """
        } else {
            acceptBlock = ""
        }
        let js = """
        window.WC_CONFIG = {
          env: "\(envName)",
          apiBase: "\(loadRemote ? "" : apiBase)",
          allowDemoLogin: \(allowDemo ? "true" : "false"),
          note: "webview-shell; not native SwiftUI/UIKit"
        };
        window.wcNativeBridge = {
          clearSession: function () {
            try { window.webkit.messageHandlers.wcNative.postMessage({ type: "clearSession" }); } catch (e) {}
          },
          saveHint: function (identity) {
            try { window.webkit.messageHandlers.wcNative.postMessage({ type: "saveHint", identity: String(identity || "") }); } catch (e) {}
          }
        };
        \(acceptBlock)
        """
        config.userContentController.addUserScript(
            WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: true)
        )

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.translatesAutoresizingMaskIntoConstraints = false
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.scrollView.contentInsetAdjustmentBehavior = .always
        webView.scrollView.keyboardDismissMode = .interactive
        if #available(iOS 16.4, *) {
            webView.isInspectable = true
        }
        view.addSubview(webView)
        NSLayoutConstraint.activate([
            webView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            webView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
        self.webView = webView

        NotificationCenter.default.addObserver(
            self, selector: #selector(keyboardWillChange(_:)),
            name: UIResponder.keyboardWillChangeFrameNotification, object: nil
        )

        if loadRemote {
            loadRemoteApp()
        } else {
            loadBundledApp()
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "wcNative")
    }

    private func loadRuntimeConfig() {
        let envOverride = ProcessInfo.processInfo.environment["WC_ENV"] ?? "dev"
        envName = envOverride
        let plistName: String
        switch envOverride {
        case "prod", "production": plistName = "Config.prod"
        case "test": plistName = "Config.test"
        default: plistName = "Config.dev"
        }
        if let url = Bundle.main.url(forResource: plistName, withExtension: "plist"),
           let dict = NSDictionary(contentsOf: url) as? [String: Any] {
            if let v = dict["WC_API_BASE"] as? String, !v.isEmpty { apiBase = v }
            if let v = dict["WC_ENV"] as? String { envName = v }
            if let v = dict["WC_ALLOW_DEMO_LOGIN"] as? Bool { allowDemo = v }
            if let v = dict["WC_LOAD_REMOTE"] as? Bool { loadRemote = v }
        }
        if let v = ProcessInfo.processInfo.environment["WC_API_BASE"], !v.isEmpty { apiBase = v }
        if let v = Bundle.main.object(forInfoDictionaryKey: "WCApiBase") as? String, !v.isEmpty {
            apiBase = v
        }
        if let v = ProcessInfo.processInfo.environment["WC_ALLOW_DEMO_LOGIN"] {
            allowDemo = (v == "1" || v.lowercased() == "true")
        }
        if let v = ProcessInfo.processInfo.environment["WC_LOAD_REMOTE"] {
            loadRemote = (v == "1" || v.lowercased() == "true")
        }
        #if targetEnvironment(simulator)
        if ProcessInfo.processInfo.environment["WC_LOAD_REMOTE"] == nil {
            loadRemote = true
        }
        #endif
        if let saved = KeychainStore.get(account: "apiBaseOverride"), !saved.isEmpty {
            apiBase = saved
        }
        // 正式构建强制关闭演示登录
        if envName == "prod" || envName == "production" {
            allowDemo = false
        }
    }

    private func loadRemoteApp() {
        let base = apiBase.hasSuffix("/") ? String(apiBase.dropLast()) : apiBase
        var comps = URLComponents(string: base + "/")
        // 验收辅助：URL 查询串触发演示登录。环境变量需 SIMCTL_CHILD_ 前缀注入；也可读 Info.plist。
        let login = ProcessInfo.processInfo.environment["WC_ACCEPT_LOGIN"]
            ?? (Bundle.main.object(forInfoDictionaryKey: "WCAcceptLogin") as? String)
            ?? ""
        let identity = ProcessInfo.processInfo.environment["WC_ACCEPT_IDENTITY"]
            ?? (Bundle.main.object(forInfoDictionaryKey: "WCAcceptIdentity") as? String)
            ?? "ADMIN"
        if allowDemo && !login.isEmpty {
            comps?.queryItems = [
                URLQueryItem(name: "wc_accept", value: login),
                URLQueryItem(name: "wc_identity", value: identity)
            ]
        }
        guard let url = comps?.url else {
            showFatal("服务地址无效：\(apiBase)")
            return
        }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        req.setValue("WonderCampus-iOS", forHTTPHeaderField: "User-Agent")
        webView.load(req)
    }

    private func loadBundledApp() {
        guard let www = Bundle.main.resourceURL?.appendingPathComponent("www", isDirectory: true) else {
            showFatal("缺少内置 www 资源")
            return
        }
        let index = www.appendingPathComponent("index.html")
        webView.loadFileURL(index, allowingReadAccessTo: www)
    }

    private func showFatal(_ message: String) {
        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.numberOfLines = 0
        label.textAlignment = .center
        label.textColor = UIColor(red: 0.77, green: 0.29, blue: 0.23, alpha: 1)
        label.font = .preferredFont(forTextStyle: .body)
        label.adjustsFontForContentSizeCategory = true
        label.text = message + "\n\n请检查 WC_API_BASE / 测试服务是否已启动。不会静默使用假数据。"
        label.accessibilityLabel = message
        view.addSubview(label)
        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: view.layoutMarginsGuide.leadingAnchor),
            label.trailingAnchor.constraint(equalTo: view.layoutMarginsGuide.trailingAnchor),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }

    @objc private func keyboardWillChange(_ note: Notification) {
        guard let frame = note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else { return }
        let converted = view.convert(frame, from: nil)
        let overlap = max(0, view.bounds.maxY - converted.minY - view.safeAreaInsets.bottom)
        webView.scrollView.contentInset.bottom = overlap
        webView.scrollView.verticalScrollIndicatorInsets.bottom = overlap
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.name == "wcNative",
              let body = message.body as? [String: Any],
              let type = body["type"] as? String else { return }
        if type == "clearSession" {
            KeychainStore.clearSessionHints()
            let types: Set<String> = [WKWebsiteDataTypeCookies, WKWebsiteDataTypeLocalStorage, WKWebsiteDataTypeSessionStorage]
            WKWebsiteDataStore.default().fetchDataRecords(ofTypes: types) { records in
                WKWebsiteDataStore.default().removeData(ofTypes: types, for: records) {}
            }
        } else if type == "saveHint" {
            if let identity = body["identity"] as? String {
                KeychainStore.set(identity, account: "lastIdentity")
                KeychainStore.set("1", account: "sessionHint")
            }
        }
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        if loadRemote {
            let msg = "无法连接服务 \(apiBase)：\(error.localizedDescription)"
            webView.loadHTMLString("""
            <!doctype html><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/>
            <body style="font-family:-apple-system;padding:24px;background:#fff7ef;color:#2a2118">
            <h1>奇想校园</h1>
            <p role="alert">\(msg)</p>
            <p>请确认电脑上已启动 wonder-server（默认 18880）。真机请把地址改为电脑局域网 IP，不要用 127.0.0.1。</p>
            <p>不会切换到假数据。</p>
            </body>
            """, baseURL: nil)
        }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        // 验收辅助：仅当环境变量显式设置时自动点登录（不进入正式包）
        guard allowDemo,
              let login = ProcessInfo.processInfo.environment["WC_ACCEPT_LOGIN"],
              !login.isEmpty else { return }
        let identity = ProcessInfo.processInfo.environment["WC_ACCEPT_IDENTITY"] ?? "ADMIN"
        let js = """
        (function(){
          var tries=0;
          function go(){
            var s=document.getElementById('loginSel');
            var i=document.getElementById('idSel');
            var b=document.getElementById('btnLogin');
            if(s&&i&&b){ s.value='\(login)'; i.value='\(identity)'; b.click(); return; }
            if(++tries<40) setTimeout(go,150);
          }
          go();
        })();
        """
        webView.evaluateJavaScript(js, completionHandler: nil)
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.allow)
            return
        }
        if url.isFileURL || url.scheme == "http" || url.scheme == "https" || url.scheme == "about" {
            decisionHandler(.allow)
            return
        }
        decisionHandler(.cancel)
    }
}
