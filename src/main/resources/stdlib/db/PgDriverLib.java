/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative PostgreSQL wire protocol driver (0.24) — AsyncTcpSocket, extended query, MD5 auth.
// 0.31: TLS reserved via SecureTransport / TlsConnection (sslmode=require foundation).

/**
 * @author 张晓达
 */
class PgType {
    static int BOOL = 16;
    static int INT2 = 21;
    static int INT4 = 23;
    static int INT8 = 20;
    static int FLOAT4 = 700;
    static int FLOAT8 = 701;
    static int TEXT = 25;
    static int VARCHAR = 1043;
    static int BYTEA = 17;
    static int JSON = 114;
    static int JSONB = 3802;
}

class PgMsg {
    static int AUTH = 82;
    static int BACKEND_KEY = 75;
    static int READY = 90;
    static int ROW_DESC = 84;
    static int DATA_ROW = 68;
    static int CMD_COMPLETE = 67;
    static int ERROR = 69;
    static int PASSWORD = 112;
    static int QUERY = 81;
    static int PARSE = 80;
    static int BIND = 66;
    static int DESCRIBE = 68;
    static int EXECUTE = 69;
    static int SYNC = 83;
    static int TERMINATE = 88;
}

class DbPgBootstrap {
    static void register() {
        DriverRegistry.register("postgresql", new PostgreSQLDriver());
    }
}

class PgMessage {
    int tag;
    byte[] payload;

    PgMessage(int tag, byte[] payload) {
        this.tag = tag;
        if (payload == null) {
            this.payload = new byte[0];
        } else {
            this.payload = payload;
        }
    }
}

class PgMessageReader {
    DbByteBuffer buf;

    PgMessageReader() {
        buf = new DbByteBuffer(512);
    }

    void feed(byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            return;
        }
        buf.putBytes(chunk);
    }

    void clear() {
        buf.setPosition(0);
        buf.setLimit(0);
    }

    PgMessage poll() {
        int base = buf.getPosition();
        int avail = buf.getLimit() - base;
        if (avail < 5) {
            return null;
        }
        int tag = buf.peekByteAt(base);
        int len = buf.peekInt32At(base + 1);
        if (len < 4) {
            throw new ConnectionException("invalid pg message length");
        }
        int total = 1 + len;
        if (avail < total) {
            return null;
        }
        buf.setPosition(base + 1);
        buf.getInt32();
        int payloadLen = len - 4;
        byte[] payload = buf.getBytes(payloadLen);
        return new PgMessage(tag, payload);
    }
}

class PgErrorInfo {
    String sqlState;
    String severity;
    String message;
    String detail;

    PgErrorInfo() {
        sqlState = "00000";
        severity = "";
        message = "";
        detail = "";
    }

    static PgErrorInfo parse(byte[] payload) {
        PgErrorInfo info = new PgErrorInfo();
        DbByteBuffer b = new DbByteBuffer(payload.length + 8);
        b.putBytes(payload);
        b.flip();
        int done = 0;
        while (b.remaining() > 0 && done == 0) {
            int field = b.getByte();
            if (field == 0) {
                done = 1;
            } else {
                String val = b.getCString();
                if (field == 67) {
                    info.sqlState = val;
                } else if (field == 83) {
                    info.severity = val;
                } else if (field == 77) {
                    info.message = val;
                } else if (field == 68) {
                    info.detail = val;
                }
            }
        }
        return info;
    }

    QueryException toException() {
        String text = message;
        if (detail.length() > 0) {
            text = text + " (" + detail + ")";
        }
        return new QueryException(text + " [SQLSTATE " + sqlState + "]");
    }
}

class PgHex {
    static String encode(byte[] data) {
        if (data == null) {
            return "";
        }
        String hex = "";
        int i = 0;
        while (i < data.length) {
            int v = DbByteUtil.unsignedByte(data[i]);
            hex = hex + nibble(BitOps.ushr(v, 4)) + nibble(BitOps.and(v, 15));
            i = i + 1;
        }
        return hex;
    }

    static String nibble(int n) {
        if (n == 0) { return "0"; }
        if (n == 1) { return "1"; }
        if (n == 2) { return "2"; }
        if (n == 3) { return "3"; }
        if (n == 4) { return "4"; }
        if (n == 5) { return "5"; }
        if (n == 6) { return "6"; }
        if (n == 7) { return "7"; }
        if (n == 8) { return "8"; }
        if (n == 9) { return "9"; }
        if (n == 10) { return "a"; }
        if (n == 11) { return "b"; }
        if (n == 12) { return "c"; }
        if (n == 13) { return "d"; }
        if (n == 14) { return "e"; }
        return "f";
    }
}

class PgMd5 {
    static int[] T = new int[64];
    static int[] INIT = new int[4];

    static void initTables() {
        if (INIT[0] != 0) {
            return;
        }
        INIT[0] = 1732584193;
        INIT[1] = -271733879;
        INIT[2] = -1732584194;
        INIT[3] = 271733878;
        T[0] = -680876936; T[1] = -389564586; T[2] = 606105819; T[3] = -1044525330;
        T[4] = -176418897; T[5] = 1200080426; T[6] = -1473231341; T[7] = -45705983;
        T[8] = 1770035416; T[9] = -1958414417; T[10] = -42063; T[11] = -1990404162;
        T[12] = 1804603682; T[13] = -40341101; T[14] = -1502002290; T[15] = 1236535329;
        T[16] = -165796510; T[17] = -1069501632; T[18] = 643717713; T[19] = -373897302;
        T[20] = -701558691; T[21] = 38016083; T[22] = -660478335; T[23] = -405537848;
        T[24] = 568446438; T[25] = -1019803690; T[26] = -187363961; T[27] = 1163531501;
        T[28] = -1444681467; T[29] = -51403784; T[30] = 1735328473; T[31] = -1926607734;
        T[32] = -378558; T[33] = -2022574463; T[34] = 1839030562; T[35] = -35309556;
        T[36] = -1530992060; T[37] = 1272893353; T[38] = -155497632; T[39] = -1094730640;
        T[40] = 681279174; T[41] = -358537222; T[42] = -722521979; T[43] = 76029189;
        T[44] = -640364487; T[45] = -421815835; T[46] = 530742520; T[47] = -995338651;
        T[48] = -198630844; T[49] = 1126891415; T[50] = -1416354905; T[51] = -57434055;
        T[52] = 1700485571; T[53] = -1894986606; T[54] = -1051523; T[55] = -2054922799;
        T[56] = 1873313359; T[57] = -30611744; T[58] = -1560198380; T[59] = 1309151649;
        T[60] = -145523070; T[61] = -1120210379; T[62] = 717897087; T[63] = -343485551;
    }

    static String hashBytes(byte[] msg) {
        initTables();
        int[] state = new int[4];
        state[0] = INIT[0];
        state[1] = INIT[1];
        state[2] = INIT[2];
        state[3] = INIT[3];
        int bitLen = msg.length * 8;
        int padLen = 56 - ((msg.length + 1) % 64);
        if (padLen <= 0) {
            padLen = padLen + 64;
        }
        byte[] padded = new byte[msg.length + padLen + 8];
        Bytes.copy(msg, 0, padded, 0, msg.length);
        padded[msg.length] = -128;
        padded[padded.length - 8] = DbByteUtil.byteAt(bitLen, 0);
        padded[padded.length - 7] = DbByteUtil.byteAt(bitLen, 1);
        padded[padded.length - 6] = DbByteUtil.byteAt(bitLen, 2);
        padded[padded.length - 5] = DbByteUtil.byteAt(bitLen, 3);
        int off = 0;
        while (off < padded.length) {
            int[] x = new int[16];
            int j = 0;
            while (j < 16) {
                int p = off + j * 4;
                x[j] = BitOps.or(
                        BitOps.or(BitOps.or(
                                BitOps.shl(DbByteUtil.unsignedByte(padded[p]), 24),
                                BitOps.shl(DbByteUtil.unsignedByte(padded[p + 1]), 16)),
                                BitOps.shl(DbByteUtil.unsignedByte(padded[p + 2]), 8)),
                        DbByteUtil.unsignedByte(padded[p + 3]));
                j = j + 1;
            }
            md5Block(state, x);
            off = off + 64;
        }
        byte[] out = new byte[16];
        writeLe(out, 0, state[0]);
        writeLe(out, 4, state[1]);
        writeLe(out, 8, state[2]);
        writeLe(out, 12, state[3]);
        return PgHex.encode(out);
    }

    static void writeLe(byte[] b, int off, int v) {
        b[off] = DbByteUtil.byteAt(v, 0);
        b[off + 1] = DbByteUtil.byteAt(v, 1);
        b[off + 2] = DbByteUtil.byteAt(v, 2);
        b[off + 3] = DbByteUtil.byteAt(v, 3);
    }

    static int rotl(int x, int n) {
        return BitOps.or(BitOps.shl(x, n), BitOps.ushr(x, 32 - n));
    }

    static void md5Block(int[] state, int[] x) {
        int a = state[0];
        int b = state[1];
        int c = state[2];
        int d = state[3];
        int[] k = PgMd5.kTable();
        int[] s = PgMd5.sTable();
        int i = 0;
        while (i < 16) {
            int f = BitOps.or(BitOps.and(b, c), BitOps.and(BitOps.not(b), d));
            int temp = d;
            d = c;
            c = b;
            b = b + rotl(a + f + x[k[i]] + T[i], s[i]);
            a = temp;
            i = i + 1;
        }
        while (i < 32) {
            int f = BitOps.or(BitOps.and(d, b), BitOps.and(BitOps.not(d), c));
            int temp = d;
            d = c;
            c = b;
            b = b + rotl(a + f + x[k[i]] + T[i], s[i]);
            a = temp;
            i = i + 1;
        }
        while (i < 48) {
            int f = BitOps.xor(b, BitOps.xor(c, d));
            int temp = d;
            d = c;
            c = b;
            b = b + rotl(a + f + x[k[i]] + T[i], s[i]);
            a = temp;
            i = i + 1;
        }
        while (i < 64) {
            int f = BitOps.xor(c, BitOps.or(b, BitOps.not(d)));
            int temp = d;
            d = c;
            c = b;
            b = b + rotl(a + f + x[k[i]] + T[i], s[i]);
            a = temp;
            i = i + 1;
        }
        state[0] = state[0] + a;
        state[1] = state[1] + b;
        state[2] = state[2] + c;
        state[3] = state[3] + d;
    }

    static int[] kTable() {
        int[] k = new int[64];
        k[0] = 0; k[1] = 1; k[2] = 2; k[3] = 3; k[4] = 4; k[5] = 5; k[6] = 6; k[7] = 7;
        k[8] = 8; k[9] = 9; k[10] = 10; k[11] = 11; k[12] = 12; k[13] = 13; k[14] = 14; k[15] = 15;
        k[16] = 1; k[17] = 6; k[18] = 11; k[19] = 0; k[20] = 5; k[21] = 10; k[22] = 15; k[23] = 4;
        k[24] = 9; k[25] = 14; k[26] = 3; k[27] = 8; k[28] = 13; k[29] = 2; k[30] = 7; k[31] = 12;
        k[32] = 5; k[33] = 8; k[34] = 11; k[35] = 14; k[36] = 1; k[37] = 4; k[38] = 7; k[39] = 10;
        k[40] = 13; k[41] = 0; k[42] = 3; k[43] = 6; k[44] = 9; k[45] = 12; k[46] = 15; k[47] = 2;
        k[48] = 0; k[49] = 7; k[50] = 14; k[51] = 9; k[52] = 4; k[53] = 11; k[54] = 2; k[55] = 13;
        k[56] = 6; k[57] = 15; k[58] = 8; k[59] = 1; k[60] = 10; k[61] = 3; k[62] = 12; k[63] = 6;
        return k;
    }

    static int[] sTable() {
        int[] s = new int[64];
        s[0] = 7; s[1] = 12; s[2] = 17; s[3] = 22;
        s[4] = 7; s[5] = 12; s[6] = 17; s[7] = 22;
        s[8] = 7; s[9] = 12; s[10] = 17; s[11] = 22;
        s[12] = 7; s[13] = 12; s[14] = 17; s[15] = 22;
        s[16] = 5; s[17] = 9; s[18] = 14; s[19] = 20;
        s[20] = 5; s[21] = 9; s[22] = 14; s[23] = 20;
        s[24] = 5; s[25] = 9; s[26] = 14; s[27] = 20;
        s[28] = 5; s[29] = 9; s[30] = 14; s[31] = 20;
        s[32] = 4; s[33] = 11; s[34] = 16; s[35] = 23;
        s[36] = 4; s[37] = 11; s[38] = 16; s[39] = 23;
        s[40] = 4; s[41] = 11; s[42] = 16; s[43] = 23;
        s[44] = 4; s[45] = 11; s[46] = 16; s[47] = 23;
        s[48] = 6; s[49] = 10; s[50] = 15; s[51] = 21;
        s[52] = 6; s[53] = 10; s[54] = 15; s[55] = 21;
        s[56] = 6; s[57] = 10; s[58] = 15; s[59] = 21;
        s[60] = 6; s[61] = 10; s[62] = 15; s[63] = 21;
        return s;
    }

    static String passwordHash(String user, String password, byte[] salt) {
        byte[] inner = Bytes.concat(Bytes.fromString(password), Bytes.fromString(user));
        String innerHex = hashBytes(inner);
        byte[] outerInput = Bytes.concat(Bytes.fromString(innerHex), salt);
        return "md5" + hashBytes(outerInput);
    }
}

class PgWire {
    static byte[] startup(String user, String database) {
        DbByteBuffer b = new DbByteBuffer(128);
        int start = b.getLimit();
        b.putInt32(0);
        b.putInt32(196608);
        b.putCString("user");
        b.putCString(user);
        b.putCString("database");
        b.putCString(database);
        b.putCString("application_name");
        b.putCString("jnative");
        b.putByte(0);
        byte[] raw = b.toArray();
        int total = raw.length;
        raw[start] = DbByteUtil.byteAt(total, 3);
        raw[start + 1] = DbByteUtil.byteAt(total, 2);
        raw[start + 2] = DbByteUtil.byteAt(total, 1);
        raw[start + 3] = DbByteUtil.byteAt(total, 0);
        return raw;
    }

    static byte[] passwordMessage(String pass) {
        return frontendMsg(PgMsg.PASSWORD, pass);
    }

    static byte[] simpleQuery(String sql) {
        return frontendMsg(PgMsg.QUERY, sql);
    }

    static String bindQuestions(String sql, ArrayList<Object> params) {
        String out = "";
        int i = 0;
        int p = 0;
        while (i < sql.length()) {
            int c = sql.charAt(i);
            if (c == 63) {
                Object val = null;
                if (params != null && p < params.size()) {
                    val = params.get(p);
                }
                p = p + 1;
                out = out + PgParam.literal(val);
                i = i + 1;
            } else {
                out = out + sql.substring(i, i + 1);
                i = i + 1;
            }
        }
        return out;
    }

    static byte[] frontendMsg(int tag, String body) {
        DbByteBuffer b = new DbByteBuffer(body.utf8Length() + 16);
        b.putByte(tag);
        b.putInt32(0);
        b.putCString(body);
        byte[] raw = b.toArray();
        int len = raw.length - 1;
        raw[1] = DbByteUtil.byteAt(len, 3);
        raw[2] = DbByteUtil.byteAt(len, 2);
        raw[3] = DbByteUtil.byteAt(len, 1);
        raw[4] = DbByteUtil.byteAt(len, 0);
        return raw;
    }

    static byte[] extendedQuery(String sql, ArrayList<Object> params) {
        DbByteBuffer b = new DbByteBuffer(sql.utf8Length() + 256);
        int parseOff = b.getLimit();
        b.putByte(PgMsg.PARSE);
        b.putInt32(0);
        b.putCString("");
        b.putCString(sql);
        b.putInt16(0);
        patchMsg(b, parseOff);
        int bindOff = b.getLimit();
        b.putByte(PgMsg.BIND);
        b.putInt32(0);
        b.putCString("");
        b.putCString("");
        b.putInt16(0);
        int n = 0;
        if (params != null) {
            n = params.size();
        }
        b.putInt16(n);
        int i = 0;
        while (i < n) {
            PgParam.write(b, params.get(i));
            i = i + 1;
        }
        b.putInt16(0);
        patchMsg(b, bindOff);
        b.putByte(PgMsg.EXECUTE);
        b.putInt32(9);
        b.putCString("");
        b.putInt32(0);
        b.putByte(PgMsg.SYNC);
        b.putInt32(4);
        return b.toArray();
    }

    static void patchMsg(DbByteBuffer b, int off) {
        /* PostgreSQL length excludes the tag byte. */
        int len = b.getLimit() - off - 1;
        b.data[off + 1] = DbByteUtil.byteAt(len, 3);
        b.data[off + 2] = DbByteUtil.byteAt(len, 2);
        b.data[off + 3] = DbByteUtil.byteAt(len, 1);
        b.data[off + 4] = DbByteUtil.byteAt(len, 0);
    }

    static byte[] terminate() {
        DbByteBuffer b = new DbByteBuffer(8);
        b.putByte(PgMsg.TERMINATE);
        b.putInt32(4);
        return b.toArray();
    }
}

class PgCast {
    static AsyncTcpSocket asTcpSocket(Object v) {
        AsyncTcpSocket src = (AsyncTcpSocket)v;
        AsyncTcpSocket out = new AsyncTcpSocket();
        out.loop = src.loop;
        out.socket = src.socket;
        return out;
    }
}

class PgBytesParam {
    byte[] data;

    PgBytesParam(byte[] data) {
        this.data = data;
    }
}

class PgParam {
    static void write(DbByteBuffer b, Object val) {
        if (val == null) {
            b.putInt32(-1);
            return;
        }
        if (val instanceof String) {
            byte[] raw = Bytes.fromString((String)val);
            b.putInt32(raw.length);
            b.putBytes(raw);
            return;
        }
        if (val instanceof PgBytesParam) {
            PgBytesParam blob = (PgBytesParam)val;
            byte[] raw = blob.data;
            b.putInt32(raw.length);
            b.putBytes(raw);
            return;
        }
        String text = PgParam.format(val);
        byte[] raw = Bytes.fromString(text);
        b.putInt32(raw.length);
        b.putBytes(raw);
    }

    static String format(Object val) {
        if (val instanceof String) {
            return (String)val;
        }
        if (val instanceof DbBindInt) {
            DbBindInt bi = (DbBindInt)val;
            return DbParseUtil.intToString(bi.value);
        }
        if (val instanceof PgBytesParam) {
            return "";
        }
        return "";
    }

    static String literal(Object val) {
        if (val == null) {
            return "NULL";
        }
        String text = format(val);
        String esc = "";
        int i = 0;
        while (i < text.length()) {
            int c = text.charAt(i);
            if (c == 39) {
                esc = esc + "''";
            } else {
                esc = esc + text.substring(i, i + 1);
            }
            i = i + 1;
        }
        return "'" + esc + "'";
    }
}

class PgChunk {
    byte[] data;

    PgChunk(byte[] data) {
        this.data = data;
    }
}

class AsyncTcpReadBytesSupplier implements BlockingSupplier {
    TcpSocket socket;
    int maxBytes;

    public Object get() {
        byte[] raw = socket.readBytes(maxBytes);
        PgChunk chunk = new PgChunk(raw);
        return chunk;
    }
}

class PgSocket {
    EventLoop loop;
    AsyncTcpSocket tcp;
    PgMessageReader reader;

    PgSocket(EventLoop loop, AsyncTcpSocket tcp) {
        this.loop = loop;
        this.tcp = tcp;
        reader = new PgMessageReader();
    }

    JnFuture write(byte[] data) {
        return tcp.writeAll(data);
    }

    JnFuture readChunk() {
        AsyncTcpReadBytesSupplier sup = new AsyncTcpReadBytesSupplier();
        sup.socket = tcp.socket;
        sup.maxBytes = 4096;
        return Async.blockingOnLoop(loop, sup);
    }

    void close() {
        tcp.close();
    }
}

class PostgreSQLDriver implements DatabaseDriver {
    public JnFuture connect(DatabaseConfig config) {
        EventLoop loop = config.loop;
        if (loop == null) {
            loop = Async.defaultLoop();
        }
        DatabaseUrl parsed = DatabaseUrl.parse(config.url);
        PgConnectSupplier sup = new PgConnectSupplier();
        sup.loop = loop;
        sup.parsed = parsed;
        return Async.blockingOnLoop(loop, sup);
    }
}

class PgConnectSupplier implements BlockingSupplier {
    EventLoop loop;
    DatabaseUrl parsed;

    public Object get() {
        DbLoopGuard.checkNotOnLoop(loop, "pg connect");
        Object tcpObj = FutureBlocker.blockingGet(
                AsyncTcpSocket.connect(loop, parsed.host, parsed.port));
        AsyncTcpSocket tcp = PgCast.asTcpSocket(tcpObj);
        /* 0.31 SecureTransport reuse point: when sslmode=require, wrap tcp with TlsConnection before PgSocket. */
        PgSocket sock = new PgSocket(loop, tcp);
        PgConnection inner = new PgConnection(loop, sock, parsed.user, parsed.password, parsed.database);
        FutureBlocker.blockingWait(inner.handshake());
        if (inner.broken != 0) {
            throw new ConnectionException("postgresql handshake failed");
        }
        DbConnection conn = new DbConnection();
        conn.sqliteBackend = inner;
        return conn;
    }
}

class PgConnection implements DatabaseBackend {
    EventLoop loop;
    PgSocket sock;
    String user;
    String password;
    int backendPid;
    int backendKey;
    int broken;
    int busy;
    int closed;
    JnFuture handshakeFuture;

    PgConnection(EventLoop loop, PgSocket sock, String user, String password, String database) {
        this.loop = loop;
        this.sock = sock;
        this.user = user;
        this.password = password;
        backendPid = 0;
        backendKey = 0;
        broken = 0;
        busy = 0;
        closed = 0;
        handshakeFuture = JnFuture.createPending(loop);
        String db = database;
        if (db == null || db.length() == 0) {
            db = user;
        }
        if (db.length() == 0) {
            db = "postgres";
        }
        sock.write(PgWire.startup(user, db)).then(new PgHandshakeStart(this));
    }

    JnFuture handshake() {
        return handshakeFuture;
    }

    int getBackendPid() {
        return backendPid;
    }

    int getBackendKey() {
        return backendKey;
    }

    public JnFuture query(String sql, ArrayList<Object> params) {
        if (broken != 0 || closed != 0) {
            return JnFuture.failed(loop, new ConnectionException("connection broken"));
        }
        if (busy != 0) {
            return JnFuture.failed(loop, new QueryException("one active query per connection"));
        }
        busy = 1;
        JnFuture out = JnFuture.createPending(loop);
        byte[] req;
        String sendSql = sql;
        if (params != null && params.size() > 0) {
            sendSql = PgWire.bindQuestions(sql, params);
        }
        req = PgWire.simpleQuery(sendSql);
        PgQueryState qs = new PgQueryState();
        qs.conn = this;
        qs.out = out;
        qs.result = new DbQueryResult();
        sock.write(req).then(new PgQueryStart(qs));
        return out;
    }

    public JnFuture queryOne(String sql, ArrayList<Object> params, RowMapper mapper) {
        return query(sql, params).then(new PgQueryOneMap(mapper));
    }

    public JnFuture execute(String sql, ArrayList<Object> params) {
        return query(sql, params).then(new PgExecuteMap());
    }

    public JnFuture beginTransaction() {
        return execute("BEGIN", null);
    }

    public JnFuture commit() {
        return execute("COMMIT", null);
    }

    public JnFuture rollback() {
        return execute("ROLLBACK", null);
    }

    public void release() {
        if (closed != 0) {
            return;
        }
        closed = 1;
        if (broken != 0) {
            sock.close();
            return;
        }
        sock.write(PgWire.terminate()).then(new PgCloseDone(this));
    }

    public int isBroken() {
        return broken;
    }

    void markBroken() {
        broken = 1;
        busy = 0;
    }
}

class PgQueryState {
    PgConnection conn;
    JnFuture out;
    DbQueryResult result;
}

class PgQueryOneMap implements Function<Object, Object> {
    RowMapper mapper;

    PgQueryOneMap(RowMapper mapper) {
        this.mapper = mapper;
    }

    public Object apply(Object v) {
        DbQueryResult res = (DbQueryResult)v;
        if (res.rows.size() == 0) {
            return null;
        }
        return mapper.map(res.rows.get(0));
    }
}

class PgExecuteMap implements Function<Object, Object> {
    public Object apply(Object v) {
        DbQueryResult res = (DbQueryResult)v;
        return res;
    }
}

class PgHandshakeStart implements Function<Object, Object> {
    PgConnection conn;

    PgHandshakeStart(PgConnection conn) {
        this.conn = conn;
    }

    public Object apply(Object v) {
        PgReadLoop loop = new PgReadLoop();
        loop.conn = conn;
        loop.mode = 1;
        loop.start();
        return v;
    }
}

class PgQueryStart implements Function<Object, Object> {
    PgQueryState qs;

    PgQueryStart(PgQueryState qs) {
        this.qs = qs;
    }

    public Object apply(Object v) {
        PgReadLoop loop = new PgReadLoop();
        loop.conn = qs.conn;
        loop.qs = qs;
        loop.mode = 2;
        loop.start();
        return v;
    }
}

class PgCloseDone implements Function<Object, Object> {
    PgConnection conn;

    PgCloseDone(PgConnection conn) {
        this.conn = conn;
    }

    public Object apply(Object v) {
        conn.sock.close();
        return v;
    }
}

class PgReadLoop {
    PgConnection conn;
    PgQueryState qs;
    int mode;

    void start() {
        readNext();
    }

    void readNext() {
        conn.sock.readChunk().then(new PgReadChunkCallback(this));
    }

    void onChunk(PgChunk chunkObj) {
        byte[] chunk = new byte[0];
        if (chunkObj != null && chunkObj.data != null) {
            chunk = chunkObj.data;
        }
        conn.sock.reader.feed(chunk);
        PgMessage msg = conn.sock.reader.poll();
        while (msg != null) {
            int done = handleMessage(msg);
            if (done != 0) {
                return;
            }
            msg = conn.sock.reader.poll();
        }
        if (chunk.length == 0) {
            if (mode == 2 && qs != null) {
                failQuery(new ConnectionException("connection closed"));
            } else if (mode == 1) {
                conn.handshakeFuture.tryFail(new ConnectionException("connection closed during auth"));
                conn.markBroken();
            }
            return;
        }
        readNext();
    }

    int handleMessage(PgMessage msg) {
        if (mode == 1) {
            return handleAuth(msg);
        }
        return handleQuery(msg);
    }

    int handleAuth(PgMessage msg) {
        if (msg.tag == PgMsg.AUTH) {
            DbByteBuffer b = new DbByteBuffer(msg.payload.length + 4);
            b.putBytes(msg.payload);
            b.flip();
            int authType = b.getInt32();
            if (authType == 0) {
                return 0;
            }
            if (authType == 5) {
                byte[] salt = b.getBytes(4);
                String pass = PgMd5.passwordHash(conn.user, conn.password, salt);
                conn.sock.write(PgWire.passwordMessage(pass)).then(new PgAuthWriteCallback(this));
                return 1;
            }
            if (authType == 10) {
                conn.handshakeFuture.tryFail(new AuthenticationException(
                        "SCRAM-SHA-256 not yet supported (0.24 partial) — use md5/trust auth for local tests"));
                conn.markBroken();
                return 1;
            }
            conn.handshakeFuture.tryFail(new AuthenticationException(
                    "unsupported auth type " + String.valueOf(authType)));
            conn.markBroken();
            return 1;
        }
        if (msg.tag == PgMsg.BACKEND_KEY) {
            DbByteBuffer b = new DbByteBuffer(msg.payload.length + 4);
            b.putBytes(msg.payload);
            b.flip();
            conn.backendPid = b.getInt32();
            conn.backendKey = b.getInt32();
            return 0;
        }
        if (msg.tag == PgMsg.READY) {
            conn.handshakeFuture.tryComplete(conn);
            return 1;
        }
        if (msg.tag == PgMsg.ERROR) {
            PgErrorInfo err = PgErrorInfo.parse(msg.payload);
            conn.handshakeFuture.tryFail(err.toException());
            conn.markBroken();
            return 1;
        }
        return 0;
    }

    int handleQuery(PgMessage msg) {
        if (msg.tag == PgMsg.DATA_ROW) {
            Row row = PgRowParser.parse(msg.payload);
            qs.result.rows.add(row);
            return 0;
        }
        if (msg.tag == PgMsg.CMD_COMPLETE) {
            String tag = Bytes.toString(msg.payload);
            qs.result.affectedRows = PgRowParser.parseAffected(tag);
            return 0;
        }
        if (msg.tag == PgMsg.READY) {
            qs.conn.busy = 0;
            while (conn.sock.reader.poll() != null) {
            }
            if (qs.out.getState() == JnFuture.FAILED) {
                return 1;
            }
            qs.out.tryComplete(qs.result);
            return 1;
        }
        if (msg.tag == PgMsg.ERROR) {
            PgErrorInfo err = PgErrorInfo.parse(msg.payload);
            failQuery(err.toException());
            return 0;
        }
        return 0;
    }

    void failQuery(Exception ex) {
        qs.conn.busy = 0;
        qs.out.tryFail(ex);
    }
}

class PgRowParser {
    static Row parse(byte[] payload) {
        DbByteBuffer b = new DbByteBuffer(payload.length + 4);
        b.putBytes(payload);
        b.flip();
        int cols = b.getInt16();
        Row row = new Row(cols);
        row.count = cols;
        int i = 0;
        while (i < cols) {
            int len = b.getInt32();
            String val = null;
            int isNull = 0;
            if (len < 0) {
                isNull = 1;
            } else {
                byte[] raw = b.getBytes(len);
                val = Bytes.toString(raw);
            }
            row.setColumn(i, "c" + String.valueOf(i), DbColumnType.TEXT, isNull);
            row.stringValues[i] = val;
            i = i + 1;
        }
        return row;
    }

    static int parseAffected(String tag) {
        int i = tag.length() - 1;
        int end = tag.length();
        while (i >= 0) {
            int c = tag.charAt(i);
            if (c >= 48 && c <= 57) {
                end = i + 1;
                i = i - 1;
            } else {
                i = -1;
            }
        }
        if (end <= tag.length()) {
            return DbParseUtil.parseInt(tag.substring(end - 1, end));
        }
        return 0;
    }
}

class PgReadChunkCallback implements Function<Object, Object> {
    PgReadLoop loop;

    PgReadChunkCallback(PgReadLoop loop) {
        this.loop = loop;
    }

    public Object apply(Object v) {
        loop.onChunk((PgChunk)v);
        return v;
    }
}

class PgAuthWriteCallback implements Function<Object, Object> {
    PgReadLoop loop;

    PgAuthWriteCallback(PgReadLoop loop) {
        this.loop = loop;
    }

    public Object apply(Object v) {
        loop.readNext();
        return v;
    }
}

class PgProtocolTestKit {
    static byte[] readyForQueryIdle() {
        DbByteBuffer b = new DbByteBuffer(8);
        b.putByte(PgMsg.READY);
        b.putInt32(5);
        b.putByte(73);
        return b.toArray();
    }

    static byte[] errorResponse(String sqlState, String severity, String message) {
        DbByteBuffer b = new DbByteBuffer(64);
        b.putByte(PgMsg.ERROR);
        b.putInt32(0);
        b.putByte(83);
        b.putCString(severity);
        b.putByte(67);
        b.putCString(sqlState);
        b.putByte(77);
        b.putCString(message);
        b.putByte(0);
        byte[] raw = b.toArray();
        int len = raw.length - 1;
        raw[1] = DbByteUtil.byteAt(len, 3);
        raw[2] = DbByteUtil.byteAt(len, 2);
        raw[3] = DbByteUtil.byteAt(len, 1);
        raw[4] = DbByteUtil.byteAt(len, 0);
        return raw;
    }

    static int feedAndCount(PgMessageReader reader, byte[] full, int chunkSize) {
        int count = 0;
        int off = 0;
        while (off < full.length) {
            int len = chunkSize;
            if (off + len > full.length) {
                len = full.length - off;
            }
            byte[] chunk = Bytes.slice(full, off, off + len);
            reader.feed(chunk);
            PgMessage msg = reader.poll();
            while (msg != null) {
                count = count + 1;
                msg = reader.poll();
            }
            off = off + len;
        }
        return count;
    }

    static String bindSecurityCheck(String sql, String param) {
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(param);
        byte[] wire = PgWire.extendedQuery(sql, params);
        byte[] needle = Bytes.fromString("' OR 1=1 --");
        byte[] sqlBytes = Bytes.fromString(sql);
        int parseEnd = PgProtocolTestKit.indexOf(wire, sqlBytes);
        if (parseEnd < 0) {
            return "INSECURE";
        }
        parseEnd = parseEnd + sqlBytes.length;
        int bindStart = PgProtocolTestKit.indexOfByte(wire, PgMsg.BIND);
        if (bindStart < 0) {
            return "INSECURE";
        }
        int i = 0;
        while (i <= wire.length - needle.length) {
            if (i < parseEnd) {
                int match = 1;
                int j = 0;
                while (j < needle.length) {
                    if (wire[i + j] != needle[j]) {
                        match = 0;
                        break;
                    }
                    j = j + 1;
                }
                if (match != 0) {
                    return "INSECURE";
                }
            }
            i = i + 1;
        }
        return "SECURE";
    }

    static int indexOf(byte[] hay, byte[] needle) {
        int i = 0;
        while (i <= hay.length - needle.length) {
            int j = 0;
            int ok = 1;
            while (j < needle.length) {
                if (hay[i + j] != needle[j]) {
                    ok = 0;
                    break;
                }
                j = j + 1;
            }
            if (ok != 0) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    static int indexOfByte(byte[] hay, int b) {
        int i = 0;
        while (i < hay.length) {
            if (hay[i] == b) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }
}
