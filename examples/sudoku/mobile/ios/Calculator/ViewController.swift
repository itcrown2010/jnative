import UIKit
import WebKit

final class ViewController: UIViewController, WKNavigationDelegate, WKUIDelegate {
    private var webView: WKWebView!
    private var bridge: BridgeHost!

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        let config = WKWebViewConfiguration()
        config.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")
        let ucc = config.userContentController

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.translatesAutoresizingMaskIntoConstraints = false
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.scrollView.contentInsetAdjustmentBehavior = .always
        view.addSubview(webView)
        NSLayoutConstraint.activate([
            webView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            webView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
        ])
        self.webView = webView

        let bridge = BridgeHost(webView: webView)
        ucc.add(bridge, name: "jnativeBridge")
        self.bridge = bridge

        loadBundledApp()
    }

    private func loadBundledApp() {
        guard let www = Bundle.main.resourceURL?.appendingPathComponent("www", isDirectory: true) else {
            assertionFailure("missing www bundle")
            return
        }
        let index = www.appendingPathComponent("index.html")
        // loadFileURL with read access to www/ so relative app.js / app.wasm resolve.
        webView.loadFileURL(index, allowingReadAccessTo: www)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        bridge.emitLifecycle("lifecycle.foreground")
    }

    override func viewWillDisappear(_ animated: Bool) {
        bridge.emitLifecycle("lifecycle.background")
        super.viewWillDisappear(animated)
    }

    deinit {
        bridge?.destroy()
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.allow)
            return
        }
        if url.isFileURL {
            decisionHandler(.allow)
            return
        }
        // External links leave the bridged container.
        UIApplication.shared.open(url)
        decisionHandler(.cancel)
    }
}
