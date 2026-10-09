// Author: 张晓达
import UIKit
import WebKit

final class ViewController: UIViewController, WKNavigationDelegate, WKUIDelegate {
    private var webView: WKWebView!
    private var bridge: BridgeHost!
    private var schemeHandler: LocalSchemeHandler!

    override func viewDidLoad() {
        super.viewDidLoad()
        // Warm paper fallback while page loads (matches touch shell CSS).
        view.backgroundColor = UIColor(red: 0.96, green: 0.94, blue: 0.91, alpha: 1)

        guard let www = Bundle.main.resourceURL?.appendingPathComponent("www", isDirectory: true),
              FileManager.default.fileExists(atPath: www.appendingPathComponent("index.html").path) else {
            assertionFailure("missing www bundle")
            return
        }

        let handler = LocalSchemeHandler(root: www)
        self.schemeHandler = handler

        let config = WKWebViewConfiguration()
        config.setURLSchemeHandler(handler, forURLScheme: LocalSchemeHandler.scheme)
        if #available(iOS 14.0, *) {
            config.defaultWebpagePreferences.allowsContentJavaScript = true
        }
        let ucc = config.userContentController

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.translatesAutoresizingMaskIntoConstraints = false
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
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

        let url = URL(string: "\(LocalSchemeHandler.scheme)://localhost/index.html")!
        webView.load(URLRequest(url: url))
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
        webView?.navigationDelegate = nil
        webView?.uiDelegate = nil
        webView?.stopLoading()
        schemeHandler = nil
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.allow)
            return
        }
        if url.scheme == LocalSchemeHandler.scheme || url.isFileURL {
            decisionHandler(.allow)
            return
        }
        UIApplication.shared.open(url)
        decisionHandler(.cancel)
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        NSLog("Sudoku WKWebView provisional fail: \(error.localizedDescription)")
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        NSLog("Sudoku WKWebView fail: \(error.localizedDescription)")
    }
}
