import UIKit
import WebKit

/// Thin WKWebView container — calculation runs entirely in bundled WASM.
final class ViewController: UIViewController, WKNavigationDelegate, WKUIDelegate {
    private var webView: WKWebView!
    private var schemeHandler: LocalSchemeHandler?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.05, green: 0.05, blue: 0.06, alpha: 1)

        guard let www = Bundle.main.resourceURL?.appendingPathComponent("www", isDirectory: true) else {
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

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.translatesAutoresizingMaskIntoConstraints = false
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.scrollView.bounces = false
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        view.addSubview(webView)
        NSLayoutConstraint.activate([
            webView.topAnchor.constraint(equalTo: view.topAnchor),
            webView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
        ])
        self.webView = webView

        var urlStr = "\(LocalSchemeHandler.scheme)://localhost/index.html"
        if ProcessInfo.processInfo.arguments.contains("-autodemo") {
            urlStr += "?autodemo=1"
        }
        webView.load(URLRequest(url: URL(string: urlStr)!))
    }

    deinit {
        webView?.navigationDelegate = nil
        webView?.uiDelegate = nil
        webView?.stopLoading()
        schemeHandler = nil
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationAction: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.allow)
            return
        }
        if url.scheme == LocalSchemeHandler.scheme || url.isFileURL {
            decisionHandler(.allow)
            return
        }
        // External links leave the app container.
        UIApplication.shared.open(url)
        decisionHandler(.cancel)
    }
}
