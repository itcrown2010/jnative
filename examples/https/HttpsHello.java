// JNative 0.31 — Production HTTPS + ALPN hello server.
// Build: jnative examples/https/HttpsHello.java -o https-hello -O3
// Certs: jnative cert dev --out examples/https/certs
// Run:   ./https-hello

/**
 * @author 张晓达
 */
class HelloRootHandler implements WebHandler {
    public void handle(WebContext ctx) {
        ctx.text("Hello HTTPS");
    }
}

class HelloProtoHandler implements WebHandler {
    public void handle(WebContext ctx) {
        ctx.text(ctx.protocol);
    }
}

public class HttpsHello {
    public static void main(String[] args) {
        WebApp app = new WebApp();
        HealthEndpoints.mount(app);
        app.use(TransportSecurity.defaults());

        app.get("/", new HelloRootHandler());
        app.get("/proto", new HelloProtoHandler());

        String cert = "examples/https/certs/server.crt";
        String key = "examples/https/certs/server.key";
        if (args.length >= 2) {
            cert = args[0];
            key = args[1];
        }

        TlsConfig tls = TlsConfig.builder()
                .certificate(cert)
                .privateKey(key)
                .alpn("h2,http/1.1")
                .build();

        int port = 8443;
        String envPort = Env.get("PORT");
        if (envPort != null && envPort.length() > 0) {
            port = WebUtil.parseIntOr(envPort, 8443);
        }

        System.out.println("TLS compiler intrinsics: NONE");
        System.out.println("HTTP/2 compiler intrinsics: NONE");
        System.out.println("HTTPS compiler intrinsics: NONE");
        app.listenTls(port, tls);
    }
}
