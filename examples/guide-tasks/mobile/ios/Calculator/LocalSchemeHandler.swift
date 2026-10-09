// Author: 张晓达
import Foundation
import WebKit

/// Serves bundled www/ over a custom scheme so WKWebView can load
/// ES modules and application/wasm offline (file:// breaks both).
final class LocalSchemeHandler: NSObject, WKURLSchemeHandler {
    static let scheme = "jnative-app"

    private let root: URL

    init(root: URL) {
        self.root = root.standardizedFileURL
        super.init()
    }

    func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        guard let url = urlSchemeTask.request.url else {
            urlSchemeTask.didFailWithError(URLError(.badURL))
            return
        }
        var rel = url.path
        if rel.hasPrefix("/") { rel.removeFirst() }
        if rel.isEmpty { rel = "index.html" }
        if rel.contains("..") {
            fail(urlSchemeTask, url: url, status: 403)
            return
        }
        let file = root.appendingPathComponent(rel).standardizedFileURL
        guard file.path.hasPrefix(root.path),
              let data = try? Data(contentsOf: file) else {
            fail(urlSchemeTask, url: url, status: 404)
            return
        }
        let mime = mimeType(for: file.pathExtension)
        let headers = [
            "Content-Type": mime,
            "Content-Length": "\(data.count)",
            "Access-Control-Allow-Origin": "*",
        ]
        guard let response = HTTPURLResponse(
            url: url,
            statusCode: 200,
            httpVersion: "HTTP/1.1",
            headerFields: headers
        ) else {
            urlSchemeTask.didFailWithError(URLError(.badServerResponse))
            return
        }
        urlSchemeTask.didReceive(response)
        urlSchemeTask.didReceive(data)
        urlSchemeTask.didFinish()
    }

    func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {}

    private func fail(_ task: WKURLSchemeTask, url: URL, status: Int) {
        let headers = ["Content-Type": "text/plain"]
        if let response = HTTPURLResponse(
            url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: headers
        ) {
            task.didReceive(response)
            task.didReceive(Data("missing".utf8))
            task.didFinish()
        } else {
            task.didFailWithError(URLError(.fileDoesNotExist))
        }
    }

    private func mimeType(for ext: String) -> String {
        switch ext.lowercased() {
        case "html", "htm": return "text/html"
        case "js", "mjs": return "text/javascript"
        case "css": return "text/css"
        case "wasm": return "application/wasm"
        case "json": return "application/json"
        case "png": return "image/png"
        case "svg": return "image/svg+xml"
        case "wat": return "text/plain"
        default: return "application/octet-stream"
        }
    }
}
