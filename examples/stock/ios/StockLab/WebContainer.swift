import SwiftUI
import WebKit

/// Full-screen web view of the server UI with a native error screen when the server cannot be reached.
struct WebContainer: View {
    let address: String
    let reloadToken: Int
    let onChangeServer: () -> Void
    @State private var failure: String?

    var body: some View {
        ZStack {
            WebView(address: address, reloadToken: reloadToken, failure: $failure)
            if let failure = failure {
                VStack(spacing: 14) {
                    Image(systemName: "wifi.exclamationmark").font(.system(size: 38)).foregroundColor(.orange)
                    Text("连不上服务器").font(.headline)
                    Text(address).font(.footnote).foregroundColor(.gray)
                    Text(failure).font(.footnote).foregroundColor(.gray).multilineTextAlignment(.center).padding(.horizontal, 30)
                    HStack(spacing: 12) {
                        Button("重试") { self.failure = nil; NotificationCenter.default.post(name: .stockLabReload, object: nil) }
                            .buttonStyle(.borderedProminent)
                        Button("修改地址") { onChangeServer() }
                            .buttonStyle(.bordered)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Theme.background)
            }
        }
    }
}

extension Notification.Name {
    static let stockLabReload = Notification.Name("stockLabReload")
}

struct WebView: UIViewRepresentable {
    let address: String
    let reloadToken: Int
    @Binding var failure: String?

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> WKWebView {
        let cfg = WKWebViewConfiguration()
        cfg.allowsInlineMediaPlayback = true
        let web = WKWebView(frame: .zero, configuration: cfg)
        web.isOpaque = false
        web.backgroundColor = UIColor(red: 15 / 255, green: 20 / 255, blue: 25 / 255, alpha: 1)
        web.scrollView.backgroundColor = web.backgroundColor
        web.allowsBackForwardNavigationGestures = true
        web.navigationDelegate = context.coordinator
        web.uiDelegate = context.coordinator
        if #available(iOS 16.4, *) { web.isInspectable = true }
        let refresh = UIRefreshControl()
        refresh.tintColor = .lightGray
        refresh.addTarget(context.coordinator, action: #selector(Coordinator.pulled(_:)), for: .valueChanged)
        web.scrollView.refreshControl = refresh
        context.coordinator.web = web
        context.coordinator.loadedAddress = address
        context.coordinator.lastToken = reloadToken
        web.load(URLRequest(url: URL(string: address)!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15))
        NotificationCenter.default.addObserver(context.coordinator, selector: #selector(Coordinator.reloadNow), name: .stockLabReload, object: nil)
        return web
    }

    func updateUIView(_ web: WKWebView, context: Context) {
        let c = context.coordinator
        c.parent = self
        if c.loadedAddress != address || c.lastToken != reloadToken {
            c.loadedAddress = address
            c.lastToken = reloadToken
            web.load(URLRequest(url: URL(string: address)!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15))
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        var parent: WebView
        weak var web: WKWebView?
        var loadedAddress = ""
        var lastToken = 0

        init(_ parent: WebView) { self.parent = parent }

        @objc func pulled(_ sender: UIRefreshControl) {
            reloadNow()
        }

        @objc func reloadNow() {
            guard let web = web else { return }
            if web.url == nil {
                web.load(URLRequest(url: URL(string: parent.address)!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15))
            } else {
                web.reload()
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            webView.scrollView.refreshControl?.endRefreshing()
            DispatchQueue.main.async { self.parent.failure = nil }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            report(webView, error)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            report(webView, error)
        }

        private func report(_ webView: WKWebView, _ error: Error) {
            webView.scrollView.refreshControl?.endRefreshing()
            let ns = error as NSError
            if ns.code == NSURLErrorCancelled { return }
            DispatchQueue.main.async { self.parent.failure = ns.localizedDescription }
        }

        /// Links that ask for a new window (target=_blank) open in the same view.
        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            if navigationAction.targetFrame == nil { webView.load(navigationAction.request) }
            return nil
        }

        // MARK: JavaScript dialogs (alert / confirm / prompt) are not shown by WKWebView unless implemented.
        private func top() -> UIViewController? {
            let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
            var vc = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController
            while let p = vc?.presentedViewController { vc = p }
            return vc
        }

        func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
            let a = UIAlertController(title: nil, message: message, preferredStyle: .alert)
            a.addAction(UIAlertAction(title: "好", style: .default) { _ in completionHandler() })
            if let vc = top() { vc.present(a, animated: true) } else { completionHandler() }
        }

        func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
            let a = UIAlertController(title: nil, message: message, preferredStyle: .alert)
            a.addAction(UIAlertAction(title: "取消", style: .cancel) { _ in completionHandler(false) })
            a.addAction(UIAlertAction(title: "确定", style: .default) { _ in completionHandler(true) })
            if let vc = top() { vc.present(a, animated: true) } else { completionHandler(false) }
        }

        func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String, defaultText: String?, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (String?) -> Void) {
            let a = UIAlertController(title: nil, message: prompt, preferredStyle: .alert)
            a.addTextField { $0.text = defaultText }
            a.addAction(UIAlertAction(title: "取消", style: .cancel) { _ in completionHandler(nil) })
            a.addAction(UIAlertAction(title: "确定", style: .default) { _ in completionHandler(a.textFields?.first?.text) })
            if let vc = top() { vc.present(a, animated: true) } else { completionHandler(nil) }
        }
    }
}
