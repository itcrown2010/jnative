import SwiftUI

/// Native shell for the JNative stock research server. The app has no data or logic of its own: it opens the server's web UI
/// (the same pages the browser gets) in a full-screen WKWebView and remembers which server to talk to.
@main
struct StockLabApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.dark)
        }
    }
}

enum Theme {
    static let background = Color(red: 15 / 255, green: 20 / 255, blue: 25 / 255)
    static let accent = Color(red: 79 / 255, green: 140 / 255, blue: 255 / 255)
}

/// The server address the user configured, e.g. http://192.168.1.29:18900
final class ServerStore: ObservableObject {
    static let key = "serverURL"
    @Published var url: String {
        didSet { UserDefaults.standard.set(url, forKey: ServerStore.key) }
    }

    init() {
        url = UserDefaults.standard.string(forKey: ServerStore.key) ?? ""
    }

    static func normalize(_ raw: String) -> String {
        var t = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.isEmpty { return "" }
        if !t.lowercased().hasPrefix("http://") && !t.lowercased().hasPrefix("https://") {
            t = "http://" + t
        }
        while t.hasSuffix("/") { t.removeLast() }
        return t
    }

    /// Asks the server's /api/ping; returns nil when it answers, otherwise a message for the user.
    static func check(_ address: String) async -> String? {
        guard let u = URL(string: address + "/api/ping") else { return "地址格式不对" }
        var req = URLRequest(url: u)
        req.timeoutInterval = 6
        do {
            let (data, resp) = try await URLSession.shared.data(for: req)
            guard let http = resp as? HTTPURLResponse, http.statusCode == 200 else { return "服务器有响应，但不是股票研究室" }
            let text = String(data: data, encoding: .utf8) ?? ""
            return text.contains("\"ok\":true") ? nil : "服务器有响应，但不是股票研究室"
        } catch {
            return "连不上：请确认电脑上的服务已启动，手机和电脑在同一个 Wi-Fi（或已连上 Tailscale）"
        }
    }
}

struct RootView: View {
    @StateObject private var store = ServerStore()
    @State private var showSettings = false
    @State private var reloadToken = 0

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            if store.url.isEmpty {
                SetupView(store: store, canCancel: false) {}
            } else {
                WebContainer(address: store.url, reloadToken: reloadToken, onChangeServer: { showSettings = true })
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        Button {
                            showSettings = true
                        } label: {
                            Image(systemName: "gearshape.fill")
                                .font(.system(size: 14))
                                .foregroundColor(.white.opacity(0.75))
                                .frame(width: 30, height: 30)
                                .background(Color.black.opacity(0.35))
                                .clipShape(Circle())
                        }
                        .padding(.trailing, 8)
                        .padding(.bottom, 6)
                    }
                }
            }
        }
        .sheet(isPresented: $showSettings) {
            SetupView(store: store, canCancel: true) {
                showSettings = false
                reloadToken += 1
            }
        }
    }
}

struct SetupView: View {
    @ObservedObject var store: ServerStore
    let canCancel: Bool
    let onDone: () -> Void
    @State private var text = ""
    @State private var busy = false
    @State private var error: String?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("服务器地址"), footer: Text("填运行股票研究室服务的电脑地址，例如 http://192.168.1.29:18900。手机和电脑需在同一个 Wi-Fi；在外面用请先安装并登录 Tailscale，再填电脑的 Tailscale 地址。")) {
                    TextField("http://192.168.1.29:18900", text: $text)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                }
                if let error = error {
                    Section { Text(error).foregroundColor(.red).font(.footnote) }
                }
                Section {
                    Button {
                        connect()
                    } label: {
                        HStack {
                            Text(busy ? "正在检查…" : "连接")
                            if busy { Spacer(); ProgressView() }
                        }
                    }
                    .disabled(busy || text.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                if canCancel && !store.url.isEmpty {
                    Section {
                        Button("重新加载页面") { onDone() }
                    }
                }
            }
            .navigationTitle("股票研究室")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if canCancel {
                    ToolbarItem(placement: .cancellationAction) { Button("关闭") { dismiss() } }
                }
            }
        }
        .onAppear {
            text = store.url.isEmpty ? "http://192.168.1.29:18900" : store.url
        }
    }

    private func connect() {
        let addr = ServerStore.normalize(text)
        busy = true
        error = nil
        Task {
            let msg = await ServerStore.check(addr)
            busy = false
            if let msg = msg {
                error = msg
            } else {
                store.url = addr
                onDone()
            }
        }
    }
}
