/**
 * Productization 1.5 — config from environment (file via shell export).
 * Sensitive values must never be logged.
 */
class ErpProductConfig {
    static String version = "1.5.0-productization";
    static String buildTime = "";
    static String targetTriple = "host-native";
    static String databaseUrl = "";
    static String listenHost = "127.0.0.1";
    static int listenPort = 18700;
    static String storagePath = "./build/erp/storage";
    static String backupPath = "./build/erp/backups";
    static String publicPath = "examples/erp/public";
    static String wasmPath = "examples/erp/.jnative/build/browser";
    static String logLevel = "info";
    static int demoMode = 0;
    static int setupCompleted = 0;

    static void load() {
        String v = Env.get("JNATIVE_ONE_VERSION");
        if (v != null && v.length() > 0) {
            version = v;
        }
        String bt = Env.get("JNATIVE_ONE_BUILD_TIME");
        if (bt != null && bt.length() > 0) {
            buildTime = bt;
        }
        String tt = Env.get("JNATIVE_ONE_TARGET");
        if (tt != null && tt.length() > 0) {
            targetTriple = tt;
        }
        String url = Env.get("DATABASE_URL");
        if (url != null && url.length() > 0) {
            databaseUrl = url;
        }
        String host = Env.get("ERP_LISTEN_HOST");
        if (host != null && host.length() > 0) {
            listenHost = host;
        }
        String port = Env.get("ERP_LISTEN_PORT");
        if (port != null && port.length() > 0) {
            listenPort = ErpNum.parseInt(port);
            if (listenPort <= 0) {
                listenPort = 18700;
            }
        }
        String listen = Env.get("ERP_LISTEN");
        if (listen != null && listen.length() > 0) {
            int colon = listen.indexOf(":");
            if (colon > 0) {
                listenHost = "";
                int i = 0;
                while (i < colon) {
                    listenHost = listenHost + WebUtil.byteChar(listen.charAt(i));
                    i = i + 1;
                }
                String ps = "";
                int j = colon + 1;
                while (j < listen.length()) {
                    ps = ps + WebUtil.byteChar(listen.charAt(j));
                    j = j + 1;
                }
                listenPort = ErpNum.parseInt(ps);
                if (listenPort <= 0) {
                    listenPort = 18700;
                }
            }
        }
        String st = Env.get("ERP_STORAGE_PATH");
        if (st != null && st.length() > 0) {
            storagePath = st;
        }
        String bp = Env.get("ERP_BACKUP_PATH");
        if (bp != null && bp.length() > 0) {
            backupPath = bp;
        }
        String pub = Env.get("ERP_PUBLIC");
        if (pub != null && pub.length() > 0) {
            publicPath = pub;
        }
        String wasm = Env.get("ERP_STATIC_WASM");
        if (wasm != null && wasm.length() > 0) {
            wasmPath = wasm;
        }
        String ll = Env.get("ERP_LOG_LEVEL");
        if (ll != null && ll.length() > 0) {
            logLevel = ll;
        }
        String demo = Env.get("ERP_DEMO_MODE");
        if (demo != null && (demo.equals("1") || demo.equals("true") || demo.equals("TRUE"))) {
            demoMode = 1;
        }
    }

    static String publicVersionJson() {
        return "{\"version\":" + ErpJson.str(version)
                + ",\"buildTime\":" + ErpJson.str(buildTime)
                + ",\"target\":" + ErpJson.str(targetTriple)
                + ",\"demoMode\":" + String.valueOf(demoMode) + "}";
    }

    static void logInfo(String module, String operation, String requestId, long companyId, long durationMs,
            String errorCode) {
        if (requestId == null) {
            requestId = "";
        }
        if (errorCode == null) {
            errorCode = "";
        }
        /* structured single-line log — never include passwords/tokens/DB URL */
        System.out.println("{\"timestamp\":" + CryptoParse.longToString(AsyncClockNative.wallMillis())
                + ",\"level\":\"INFO\",\"requestId\":" + ErpJson.str(requestId)
                + ",\"companyId\":" + CryptoParse.longToString(companyId)
                + ",\"module\":" + ErpJson.str(module)
                + ",\"operation\":" + ErpJson.str(operation)
                + ",\"duration\":" + CryptoParse.longToString(durationMs)
                + ",\"errorCode\":" + ErpJson.str(errorCode) + "}");
    }
}

class ErpDemoGuard {
    /** Destructive reset/wipe only when ERP_DEMO_MODE=1. */
    static void requireDemoModeOrThrow(String action) {
        if (ErpProductConfig.demoMode == 0) {
            throw new TransactionException("FORBIDDEN:DEMO_MODE_REQUIRED:" + action);
        }
    }
}
