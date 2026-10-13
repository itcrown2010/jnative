// Author: 张晓达
import Foundation
import UIKit
import UniformTypeIdentifiers
import WebKit

/// Structured bridge host — whitelist only; file handles wrap security-scoped URLs.
final class BridgeHost: NSObject, WKScriptMessageHandler, UIDocumentPickerDelegate, UIActivityItemSource {
    static let version = 1
    static let maxKey = 128
    static let maxVal = 4096
    static let maxFileBytes = 1_048_576
    static let maxName = 180
    static let maxMime = 128
    static let caps = [
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
    ]
    static let allowed: Set<String> = Set(caps + ["bridge.hello"])

    private struct FileHandle {
        let url: URL
        let name: String
        let mime: String
        let size: Int
        var scoped: Bool
    }

    private weak var webView: WKWebView?
    private weak var presenter: UIViewController?
    private let defaults = UserDefaults.standard
    private let prefix = "jnative.guide-tasks."
    private var alive = true
    private var handles: [String: FileHandle] = [:]
    private var pendingPickId: String?
    private var pendingExport: (id: String, data: Data, name: String)?
    private var sharePayload: (data: Data, name: String, mime: String)?
    private var permissionDeniedSticky = false
    private let queue = DispatchQueue(label: "io.jnative.guidetasks.bridge")

    init(webView: WKWebView, presenter: UIViewController) {
        self.webView = webView
        self.presenter = presenter
        super.init()
    }

    func destroy() {
        alive = false
        if let id = pendingPickId {
            pendingPickId = nil
            replyError(id: id, code: "DESTROYED", message: "bridge destroyed")
        }
        if let exp = pendingExport {
            pendingExport = nil
            replyError(id: exp.id, code: "DESTROYED", message: "bridge destroyed")
        }
        for (_, h) in handles where h.scoped {
            h.url.stopAccessingSecurityScopedResource()
        }
        handles.removeAll()
        cleanupTemp()
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "jnativeBridge")
        webView = nil
        presenter = nil
    }

    func emitLifecycle(_ name: String) {
        guard alive else { return }
        dispatch([
            "type": "event",
            "name": name,
            "params": [:] as [String: Any],
        ])
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard alive, message.name == "jnativeBridge" else { return }
        let body: [String: Any]
        if let dict = message.body as? [String: Any] {
            body = dict
        } else if let text = message.body as? String,
                  let data = text.data(using: .utf8),
                  let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            body = obj
        } else {
            return
        }
        queue.async { self.handle(body) }
    }

    private func handle(_ req: [String: Any]) {
        guard let id = req["id"] as? String, !id.isEmpty, id.count <= 80 else { return }
        let v = req["v"] as? Int ?? -1
        if v != Self.version {
            replyError(id: id, code: "PROTOCOL_MISMATCH", message: "unsupported bridge version")
            return
        }
        let method = req["method"] as? String ?? ""
        if !Self.allowed.contains(method) {
            replyError(id: id, code: "METHOD_NOT_ALLOWED", message: "unknown method: \(method)")
            return
        }
        let params = req["params"] as? [String: Any] ?? [:]
        switch method {
        case "bridge.hello":
            replyOk(id: id, result: [
                "version": Self.version,
                "platform": "ios",
                "capabilities": Self.caps,
            ] as [String: Any])
        case "platform.getName":
            replyOk(id: id, result: "ios")
        case "platform.queryCapabilities":
            let list: [[String: String]] = Self.caps.map { name in
                let status: String
                if permissionDeniedSticky && name.hasPrefix("file.") {
                    status = "denied"
                } else {
                    status = "available"
                }
                return ["name": name, "status": status]
            }
            replyOk(id: id, result: list)
        case "storage.get":
            guard let key = params["key"] as? String, validateKey(key) == nil else {
                let err = validateKey(params["key"] as? String) ?? ("BAD_KEY", "bad key")
                replyError(id: id, code: err.0, message: err.1)
                return
            }
            if let value = defaults.string(forKey: prefix + key) {
                replyOk(id: id, result: value)
            } else {
                replyOkNull(id: id)
            }
        case "storage.set":
            guard let key = params["key"] as? String, let value = params["value"] as? String else {
                replyError(id: id, code: "BAD_PARAMS", message: "key/value required")
                return
            }
            if let err = validateKey(key) ?? validateValue(value) {
                replyError(id: id, code: err.0, message: err.1)
                return
            }
            defaults.set(value, forKey: prefix + key)
            replyOk(id: id, result: true)
        case "storage.remove":
            guard let key = params["key"] as? String, validateKey(key) == nil else {
                let err = validateKey(params["key"] as? String) ?? ("BAD_KEY", "bad key")
                replyError(id: id, code: err.0, message: err.1)
                return
            }
            defaults.removeObject(forKey: prefix + key)
            replyOk(id: id, result: true)
        case "file.pick":
            startPick(id: id)
        case "file.read":
            readFile(id: id, params: params)
        case "file.release":
            if let handleId = params["handleId"] as? String, let h = handles.removeValue(forKey: handleId) {
                if h.scoped {
                    h.url.stopAccessingSecurityScopedResource()
                }
            }
            replyOk(id: id, result: true)
        case "file.export":
            startExport(id: id, params: params)
        case "share.system":
            startShare(id: id, params: params)
        default:
            replyError(id: id, code: "METHOD_NOT_ALLOWED", message: "unknown method")
        }
    }

    private func startPick(id: String) {
        if pendingPickId != nil {
            replyError(id: id, code: "BUSY", message: "another file pick is in progress")
            return
        }
        pendingPickId = id
        DispatchQueue.main.async {
            guard let presenter = self.presenter else {
                self.pendingPickId = nil
                self.replyError(id: id, code: "DESTROYED", message: "no presenter")
                return
            }
            let types: [UTType] = [.json, .plainText, .data]
            let picker = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: true)
            picker.delegate = self
            picker.allowsMultipleSelection = false
            presenter.present(picker, animated: true)
        }
    }

    private func startExport(id: String, params: [String: Any]) {
        guard let name = params["name"] as? String, !name.isEmpty, name.count <= Self.maxName else {
            replyError(id: id, code: "BAD_PARAMS", message: "file name must be 1..\(Self.maxName) chars")
            return
        }
        guard let data = decodePayload(params) else {
            replyError(id: id, code: "BAD_PARAMS", message: "text or bytesBase64 required")
            return
        }
        if data.count > Self.maxFileBytes {
            replyError(id: id, code: "SIZE_LIMIT", message: "export exceeds \(Self.maxFileBytes) bytes")
            return
        }
        if pendingExport != nil {
            replyError(id: id, code: "BUSY", message: "another export is in progress")
            return
        }
        pendingExport = (id, data, name)
        DispatchQueue.main.async {
            let tmp = FileManager.default.temporaryDirectory.appendingPathComponent(name)
            do {
                try data.write(to: tmp, options: .atomic)
                guard let presenter = self.presenter else {
                    self.pendingExport = nil
                    self.replyError(id: id, code: "DESTROYED", message: "no presenter")
                    return
                }
                let picker = UIDocumentPickerViewController(forExporting: [tmp], asCopy: true)
                picker.delegate = self
                presenter.present(picker, animated: true)
            } catch {
                self.pendingExport = nil
                self.replyError(id: id, code: "IO_ERROR", message: error.localizedDescription)
            }
        }
    }

    private func startShare(id: String, params: [String: Any]) {
        let title = (params["title"] as? String ?? "").prefix(120)
        let text = params["text"] as? String
        let name = params["name"] as? String ?? "share.json"
        let mime = (params["mimeType"] as? String ?? "application/json").prefix(Self.maxMime)
        let data = decodePayload(params) ?? text?.data(using: .utf8)
        guard let payload = data else {
            replyError(id: id, code: "BAD_PARAMS", message: "share requires text or file payload")
            return
        }
        if payload.count > Self.maxFileBytes {
            replyError(id: id, code: "SIZE_LIMIT", message: "share payload exceeds limit")
            return
        }
        DispatchQueue.main.async {
            guard let presenter = self.presenter else {
                self.replyError(id: id, code: "DESTROYED", message: "no presenter")
                return
            }
            self.sharePayload = (payload, String(name.prefix(Self.maxName)), String(mime))
            let vc = UIActivityViewController(activityItems: [self], applicationActivities: nil)
            vc.completionWithItemsHandler = { _, completed, _, error in
                self.sharePayload = nil
                self.cleanupTemp()
                if let error {
                    self.replyError(id: id, code: "IO_ERROR", message: error.localizedDescription)
                    return
                }
                // completed=true only means user picked an activity; not delivery proof
                self.replyOk(id: id, result: [
                    "status": completed ? "unknown" : "dismissed",
                    "note": "share sheet finished; delivery not confirmed",
                    "title": String(title),
                ] as [String: Any])
            }
            presenter.present(vc, animated: true)
        }
    }

    private func readFile(id: String, params: [String: Any]) {
        guard let handleId = params["handleId"] as? String, let h = handles[handleId] else {
            replyError(id: id, code: "BAD_PARAMS", message: "unknown or released file handle")
            return
        }
        let maxBytes = min(params["maxBytes"] as? Int ?? Self.maxFileBytes, Self.maxFileBytes)
        if h.size > maxBytes {
            replyError(id: id, code: "SIZE_LIMIT", message: "file exceeds \(maxBytes) bytes")
            return
        }
        do {
            let data = try Data(contentsOf: h.url, options: [.mappedIfSafe])
            if data.count > maxBytes {
                replyError(id: id, code: "SIZE_LIMIT", message: "file exceeds \(maxBytes) bytes")
                return
            }
            let asBase64 = (params["as"] as? String) == "bytesBase64"
            if asBase64 {
                replyOk(id: id, result: [
                    "bytesBase64": data.base64EncodedString(),
                    "bytesRead": data.count,
                    "encoding": "base64",
                ] as [String: Any])
            } else {
                guard let text = String(data: data, encoding: .utf8) else {
                    replyError(id: id, code: "IO_ERROR", message: "not valid UTF-8 text")
                    return
                }
                replyOk(id: id, result: [
                    "text": text,
                    "bytesRead": data.count,
                    "encoding": "utf-8",
                ] as [String: Any])
            }
        } catch {
            permissionDeniedSticky = true
            replyError(id: id, code: "IO_ERROR", message: error.localizedDescription)
        }
    }

    // MARK: UIDocumentPickerDelegate

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        if let pickId = pendingPickId {
            pendingPickId = nil
            guard let url = urls.first else {
                replyError(id: pickId, code: "USER_CANCELLED", message: "user cancelled file pick")
                return
            }
            queue.async {
                let scoped = url.startAccessingSecurityScopedResource()
                do {
                    let values = try url.resourceValues(forKeys: [.fileSizeKey, .nameKey, .contentTypeKey])
                    let size: Int
                    if let fileSize = values.fileSize {
                        size = fileSize
                    } else {
                        size = try Data(contentsOf: url).count
                    }
                    if size > Self.maxFileBytes {
                        if scoped { url.stopAccessingSecurityScopedResource() }
                        self.replyError(id: pickId, code: "SIZE_LIMIT", message: "file exceeds \(Self.maxFileBytes) bytes")
                        return
                    }
                    let name = (values.name ?? url.lastPathComponent).prefix(Self.maxName)
                    let mime = values.contentType?.preferredMIMEType ?? "application/octet-stream"
                    let handleId = "ih-" + UUID().uuidString
                    self.handles[handleId] = FileHandle(
                        url: url,
                        name: String(name),
                        mime: String(mime.prefix(Self.maxMime)),
                        size: size,
                        scoped: scoped,
                    )
                    self.replyOk(id: pickId, result: [
                        "handleId": handleId,
                        "name": String(name),
                        "mimeType": String(mime.prefix(Self.maxMime)),
                        "size": size,
                    ] as [String: Any])
                } catch {
                    if scoped { url.stopAccessingSecurityScopedResource() }
                    self.replyError(id: pickId, code: "IO_ERROR", message: error.localizedDescription)
                }
            }
            return
        }
        if let exp = pendingExport {
            pendingExport = nil
            cleanupTemp()
            replyOk(id: exp.id, result: ["exported": true, "method": "documentPickerExport"] as [String: Any])
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        if let pickId = pendingPickId {
            pendingPickId = nil
            replyError(id: pickId, code: "USER_CANCELLED", message: "user cancelled file pick")
        }
        if let exp = pendingExport {
            pendingExport = nil
            cleanupTemp()
            replyError(id: exp.id, code: "USER_CANCELLED", message: "user cancelled export")
        }
    }

    // MARK: UIActivityItemSource

    func activityViewControllerPlaceholderItem(_ activityViewController: UIActivityViewController) -> Any {
        sharePayload?.name ?? "share"
    }

    func activityViewController(_ activityViewController: UIActivityViewController, itemForActivityType activityType: UIActivity.ActivityType?) -> Any? {
        guard let payload = sharePayload else { return nil }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(payload.name)
        try? payload.data.write(to: url, options: .atomic)
        return url
    }

    private func decodePayload(_ params: [String: Any]) -> Data? {
        if let text = params["text"] as? String {
            return text.data(using: .utf8)
        }
        if let b64 = params["bytesBase64"] as? String {
            return Data(base64Encoded: b64)
        }
        return nil
    }

    private func cleanupTemp() {
        let dir = FileManager.default.temporaryDirectory
        if let files = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) {
            for f in files where f.pathExtension == "jnbackup" || f.lastPathComponent.hasPrefix("任务") {
                try? FileManager.default.removeItem(at: f)
            }
        }
    }

    private func validateKey(_ key: String?) -> (String, String)? {
        guard let key, !key.isEmpty, key.count <= Self.maxKey else {
            return ("BAD_KEY", "key must be string 1..\(Self.maxKey)")
        }
        return nil
    }

    private func validateValue(_ value: String?) -> (String, String)? {
        guard let value, value.count <= Self.maxVal else {
            return ("BAD_VALUE", "value must be string length 0..\(Self.maxVal)")
        }
        return nil
    }

    private func replyOk(id: String, result: Any) {
        dispatch(["id": id, "ok": true, "result": result])
    }

    private func replyOkNull(id: String) {
        dispatch(["id": id, "ok": true, "result": NSNull()])
    }

    private func replyError(id: String, code: String, message: String) {
        dispatch([
            "id": id,
            "ok": false,
            "error": ["code": code, "message": message],
        ])
    }

    private func dispatch(_ payload: [String: Any]) {
        guard alive, let webView else { return }
        guard let data = try? JSONSerialization.data(withJSONObject: payload),
              let json = String(data: data, encoding: .utf8) else { return }
        let js = "(function(){try{var m=\(json);window.postMessage(m, window.location.origin||'*');}catch(e){}})();"
        DispatchQueue.main.async {
            webView.evaluateJavaScript(js, completionHandler: nil)
        }
    }
}
