import jnative.web.*;
import jnative.json.*;

/**
 * JNative Music — native validation server.
 *
 * Default DB: sqlite:./build/music/music.db
 * PG validation: DATABASE_URL=postgresql://... (md5/trust until SCRAM / RH-1).
 *
 * Media: GET /media/tracks/{id} streaming Range; GET /media/covers/{id}
 */
public class MusicServer {
    public static void main(String[] args) {
        Mus.loop = new EventLoop();
        Mus.sessions = new InMemorySessionStore();
        Mus.sessions.idleTimeoutMs = 86400000L;
        Mus.loginLimiter = new RateLimiter();
        Mus.loginLimiter.capacity = 40;
        Mus.loginLimiter.refillPerSec = 8;
        Mus.storageRoot = "examples/music/storage";
        Mus.requestSeq = 0L;
        MusDb.setup(Mus.loop);
        MusicSeed.ensure();

        WebApp app = new WebApp();
        app.bindEventLoop(Mus.loop);
        app.use(Cors.defaults());
        app.use(WebMiddleware.logger());
        app.staticFiles("/", "examples/music/public");
        /* WASM build artifacts (app.js / app.wasm) — not business JS. */
        app.staticFiles("/wasm", "examples/music/.jnative/build/browser");

        app.get("/health", new MusHealthHandler());
        app.get("/ready", new MusReadyHandler());

        app.get("/media/tracks/:id", new MediaTrackHandler());
        app.get("/media/covers/:id", new MediaCoverHandler());

        app.futurePost("/api/auth/login", new MusLoginHandler(Mus.loop));
        app.futurePost("/api/auth/register", new MusRegisterHandler(Mus.loop));
        app.futurePost("/api/auth/logout", new MusLogoutHandler(Mus.loop));
        app.futureGet("/api/auth/currentUser", new MusMeHandler(Mus.loop));

        app.futureGet("/api/music/getHome", new MusHomeHandler(Mus.loop));
        app.futureGet("/api/music/getTrack", new MusGetTrackHandler(Mus.loop));
        app.futureGet("/api/music/getAlbum", new MusGetAlbumHandler(Mus.loop));
        app.futureGet("/api/music/getArtist", new MusGetArtistHandler(Mus.loop));
        app.futureGet("/api/music/getPlaylist", new MusGetPlaylistHandler(Mus.loop));
        app.futureGet("/api/music/playlistTracks", new MusPlaylistTracksHandler(Mus.loop));
        app.futureGet("/api/music/search", new MusSearchHandler(Mus.loop));
        app.futureGet("/api/music/myFavorites", new MusFavoritesHandler(Mus.loop));
        app.futureGet("/api/music/recentlyPlayed", new MusHistoryHandler(Mus.loop));
        app.futureGet("/api/music/myPlaylists", new MusMyPlaylistsHandler(Mus.loop));
        app.futureGet("/api/music/charts", new MusChartsHandler(Mus.loop));
        app.futureGet("/api/music/lyrics", new MusLyricsHandler(Mus.loop));
        app.futurePost("/api/music/favorite", new MusFavoriteHandler(Mus.loop));
        app.futurePost("/api/music/unfavorite", new MusUnfavoriteHandler(Mus.loop));
        app.futurePost("/api/music/recordPlay", new MusRecordPlayHandler(Mus.loop));
        app.futurePost("/api/music/createPlaylist", new MusCreatePlaylistHandler(Mus.loop));
        app.futurePost("/api/music/addToPlaylist", new MusAddPlaylistHandler(Mus.loop));
        app.futurePost("/api/music/removeFromPlaylist", new MusRemovePlaylistHandler(Mus.loop));

        System.out.println("JNative Music on http://127.0.0.1:18500");
        System.out.println("Demo: music@jnative.dev / music1234");
        System.out.println("DB: " + Mus.dialect + " storage=" + Mus.storageRoot);
        app.listenWithLoop(18500);
    }
}

class Mus {
    static EventLoop loop;
    static Database db;
    static String dialect;
    static InMemorySessionStore sessions;
    static RateLimiter loginLimiter;
    static String storageRoot;
    static long requestSeq;
}

class MusDb {
    static void setup(EventLoop loop) {
        String url = Env.get("DATABASE_URL");
        if (url == null || url.length() == 0) {
            url = "sqlite:./build/music/music.db";
            Mus.dialect = "sqlite";
        } else {
            Mus.dialect = DatabaseUrl.parse(url).scheme;
        }
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.url = url;
        cfg.loop = loop;
        JnFuture dbF = Database.connectWithConfig(cfg);
        FutureBlocker.blockingWait(dbF);
        Mus.db = (Database) dbF.getValue();
        ArrayList<Object> empty = new ArrayList<Object>();
        exec("CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "email TEXT NOT NULL UNIQUE,"
                + "display_name TEXT NOT NULL,"
                + "password_hash TEXT NOT NULL,"
                + "role TEXT NOT NULL DEFAULT 'user',"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL)", empty);
        exec("CREATE TABLE IF NOT EXISTS artists ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "bio TEXT NOT NULL DEFAULT '',"
                + "cover_path TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL)", empty);
        exec("CREATE TABLE IF NOT EXISTS albums ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "artist_id INTEGER NOT NULL,"
                + "title TEXT NOT NULL,"
                + "year INTEGER NOT NULL DEFAULT 0,"
                + "cover_path TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL)", empty);
        exec("CREATE TABLE IF NOT EXISTS tracks ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "album_id INTEGER NOT NULL,"
                + "artist_id INTEGER NOT NULL,"
                + "title TEXT NOT NULL,"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "track_no INTEGER NOT NULL DEFAULT 1,"
                + "audio_path TEXT NOT NULL,"
                + "cover_path TEXT NOT NULL DEFAULT '',"
                + "play_count INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL)", empty);
        exec("CREATE TABLE IF NOT EXISTS playlists ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "user_id INTEGER NOT NULL,"
                + "title TEXT NOT NULL,"
                + "description TEXT NOT NULL DEFAULT '',"
                + "cover_path TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL)", empty);
        exec("CREATE TABLE IF NOT EXISTS playlist_tracks ("
                + "playlist_id INTEGER NOT NULL,"
                + "track_id INTEGER NOT NULL,"
                + "position INTEGER NOT NULL,"
                + "PRIMARY KEY (playlist_id, track_id))", empty);
        exec("CREATE TABLE IF NOT EXISTS favorites ("
                + "user_id INTEGER NOT NULL,"
                + "track_id INTEGER NOT NULL,"
                + "created_at INTEGER NOT NULL,"
                + "PRIMARY KEY (user_id, track_id))", empty);
        exec("CREATE TABLE IF NOT EXISTS play_history ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "user_id INTEGER NOT NULL,"
                + "track_id INTEGER NOT NULL,"
                + "played_at INTEGER NOT NULL)", empty);
        exec("CREATE TABLE IF NOT EXISTS lyrics ("
                + "track_id INTEGER NOT NULL,"
                + "line_no INTEGER NOT NULL,"
                + "start_ms INTEGER NOT NULL,"
                + "text TEXT NOT NULL,"
                + "PRIMARY KEY (track_id, line_no))", empty);
        exec("CREATE INDEX IF NOT EXISTS idx_tracks_title ON tracks(title)", empty);
        exec("CREATE INDEX IF NOT EXISTS idx_tracks_artist ON tracks(artist_id)", empty);
        exec("CREATE INDEX IF NOT EXISTS idx_play_history_user ON play_history(user_id, played_at)", empty);
    }

    static void exec(String sql, ArrayList<Object> params) {
        FutureBlocker.blockingWait(Mus.db.execute(sql, params));
    }

    static ArrayList<Object> mapAll(JnFuture q, RowMapper mapper) {
        FutureBlocker.blockingWait(q);
        DbQueryResult result = (DbQueryResult) q.getValue();
        ArrayList<Object> out = new ArrayList<Object>();
        if (result == null || result.rows == null) {
            return out;
        }
        int i = 0;
        while (i < result.rows.size()) {
            out.add(mapper.map(result.rows.get(i)));
            i = i + 1;
        }
        return out;
    }
}

class MusNum {
    static long parseLong(String s) {
        if (s == null || s.length() == 0) {
            return 0L;
        }
        return WebUtil.parseLongOr(s, 0L);
    }
}

class MusAuth {
    static String nextRequestId() {
        Mus.requestSeq = Mus.requestSeq + 1L;
        return "req-" + CryptoParse.longToString(Mus.requestSeq);
    }

    static Session load(WebContext ctx) {
        SessionManager mgr = new SessionManager();
        mgr.store = Mus.sessions;
        return mgr.loadOrCreate(ctx);
    }

    static void save(WebContext ctx, Session session) {
        SessionManager mgr = new SessionManager();
        mgr.store = Mus.sessions;
        mgr.save(ctx, session);
    }

    static long requireUserId(WebContext ctx) {
        Session s = load(ctx);
        String uid = s.getString("userId");
        if (uid == null || uid.length() == 0) {
            return 0L;
        }
        return MusNum.parseLong(uid);
    }

    static int checkCsrf(WebContext ctx) {
        Session s = load(ctx);
        String expect = s.getString("csrf");
        String got = ctx.getHeader("X-CSRF-Token");
        if (got == null || got.length() == 0) {
            got = ctx.query("csrf");
        }
        if (expect == null || expect.length() == 0 || got == null || expect.equals(got) == false) {
            return 0;
        }
        return 1;
    }

    static void err(WebContext ctx, int status, String code, String message) {
        JsonObject o = new JsonObject();
        o.putString("code", code);
        o.putString("message", message);
        o.putString("requestId", nextRequestId());
        ctx.status(status).jsonString(Json.stringify(o));
    }
}

class MusHealthHandler implements WebHandler {
    public void handle(WebContext ctx) {
        ctx.jsonString("{\"ok\":true,\"service\":\"jnative-music\"}");
    }
}

class MusReadyHandler implements WebHandler {
    public void handle(WebContext ctx) {
        int dbOk = 0;
        int storageOk = 0;
        try {
            ArrayList<Object> p = new ArrayList<Object>();
            JnFuture f = Mus.db.queryOne("SELECT 1 AS id", p, new IdMapper());
            FutureBlocker.blockingWait(f);
            if (f.getValue() != null) {
                dbOk = 1;
            }
        } catch (Exception ex) {
            dbOk = 0;
        }
        if (File.exists(Mus.storageRoot + "/audio")) {
            storageOk = 1;
        }
        if (dbOk != 0 && storageOk != 0) {
            ctx.jsonString("{\"ready\":true,\"db\":\"" + Mus.dialect + "\"}");
        } else {
            ctx.status(503).jsonString("{\"ready\":false,\"dbOk\":" + String.valueOf(dbOk)
                    + ",\"storageOk\":" + String.valueOf(storageOk) + "}");
        }
    }
}

class MediaTrackHandler implements WebHandler {
    public void handle(WebContext ctx) {
        long id = MusNum.parseLong(ctx.param("id"));
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(id));
        JnFuture f = Mus.db.queryOne("SELECT audio_path FROM tracks WHERE id=?", p, new PathMapper());
        FutureBlocker.blockingWait(f);
        Object pathObj = f.getValue();
        if (pathObj == null) {
            ctx.status(404).text("track not found");
            return;
        }
        String path = (String) pathObj;
        if (path.indexOf("..") >= 0) {
            ctx.status(400).text("bad path");
            return;
        }
        String full = path;
        if (File.exists(full) == false) {
            full = Mus.storageRoot + "/" + path;
        }
        if (File.exists(full) == false) {
            ctx.status(404).text("audio missing");
            return;
        }
        MediaStreamer.serveFile(ctx, full, "audio/wav");
    }
}

class MediaCoverHandler implements WebHandler {
    public void handle(WebContext ctx) {
        String path = Mus.storageRoot + "/covers/cover.bin";
        if (File.exists(path) == false) {
            ctx.status(404).text("cover missing");
            return;
        }
        long total = File.size(path);
        String etag = "\"" + WebUtil.longToString(total) + "-cover\"";
        String inm = ctx.getHeader("If-None-Match");
        if (inm != null && inm.equals(etag)) {
            ctx.status(304).setHeader("ETag", etag).text("");
            return;
        }
        ctx.setHeader("ETag", etag);
        ctx.setHeader("Cache-Control", "public, max-age=86400");
        ctx.status(200);
        ctx.fileBody(path, "application/octet-stream");
    }
}

class IdMapper implements RowMapper {
    public Object map(Row row) {
        return CryptoParse.longToString(row.getLong(0));
    }
}

class PathMapper implements RowMapper {
    public Object map(Row row) {
        return row.getString(0);
    }
}
