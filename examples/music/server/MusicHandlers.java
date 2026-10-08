import jnative.web.*;
import jnative.json.*;

class TrackMapper implements RowMapper {
    public Object map(Row row) {
        TrackDto t = new TrackDto();
        t.id = row.getLong(0);
        t.albumId = row.getLong(1);
        t.artistId = row.getLong(2);
        t.title = row.getString(3);
        t.durationMs = row.getInt(4);
        t.trackNo = row.getInt(5);
        t.playCount = row.getInt(6);
        t.artistName = row.getString(7);
        t.albumTitle = row.getString(8);
        t.mediaUrl = "/media/tracks/" + CryptoParse.longToString(t.id);
        t.coverUrl = "/media/covers/" + CryptoParse.longToString(t.albumId);
        return t;
    }
}

class AlbumRowMapper implements RowMapper {
    public Object map(Row row) {
        AlbumDto a = new AlbumDto();
        a.id = row.getLong(0);
        a.artistId = row.getLong(1);
        a.title = row.getString(2);
        a.year = row.getInt(3);
        a.artistName = row.getString(4);
        a.coverUrl = "/media/covers/" + CryptoParse.longToString(a.id);
        return a;
    }
}

class ArtistRowMapper implements RowMapper {
    public Object map(Row row) {
        ArtistDto a = new ArtistDto();
        a.id = row.getLong(0);
        a.name = row.getString(1);
        a.bio = row.getString(2);
        a.coverUrl = "/media/covers/" + CryptoParse.longToString(a.id);
        return a;
    }
}

class PlaylistRowMapper implements RowMapper {
    public Object map(Row row) {
        PlaylistDto p = new PlaylistDto();
        p.id = row.getLong(0);
        p.userId = row.getLong(1);
        p.title = row.getString(2);
        p.description = row.getString(3);
        p.coverUrl = "";
        p.trackCount = row.getInt(4);
        return p;
    }
}

class UserRowMapper implements RowMapper {
    public Object map(Row row) {
        UserRow u = new UserRow();
        u.id = row.getLong(0);
        u.email = row.getString(1);
        u.displayName = row.getString(2);
        u.passwordHash = row.getString(3);
        u.role = row.getString(4);
        return u;
    }
}

class UserRow {
    long id;
    String email;
    String displayName;
    String passwordHash;
    String role;
}

class LyricMapper implements RowMapper {
    public Object map(Row row) {
        LyricLineDto l = new LyricLineDto();
        l.startMs = row.getInt(0);
        l.text = row.getString(1);
        return l;
    }
}

class MusSql {
    static String trackSelect() {
        return "SELECT t.id, t.album_id, t.artist_id, t.title, t.duration_ms, t.track_no, t.play_count, "
                + "a.name AS artist_name, al.title AS album_title "
                + "FROM tracks t JOIN artists a ON a.id=t.artist_id JOIN albums al ON al.id=t.album_id ";
    }
}

class MusLoginHandler implements AsyncWebHandler {
    EventLoop loop;
    MusLoginHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        if (Mus.loginLimiter.allow("login") == 0) {
            MusAuth.err(ctx, 429, "RATE_LIMITED", "too many login attempts");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String email = body.getString("email", "");
        String password = body.getString("password", "");
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(email);
        JnFuture q = Mus.db.queryOne(
                "SELECT id, email, display_name, password_hash, role FROM users WHERE email=?",
                p, new UserRowMapper());
        FutureBlocker.blockingWait(q);
        UserRow user = (UserRow) q.getValue();
        if (user == null || Password.verifyBlocking(password, user.passwordHash) == 0) {
            MusAuth.err(ctx, 401, "AUTH_REQUIRED", "invalid email or password");
            return JnFuture.completed(loop, null);
        }
        if (Password.needsRehash(user.passwordHash) != 0) {
            String neu = Password.hashBlocking(password);
            ArrayList<Object> up = new ArrayList<Object>();
            up.add(neu);
            up.add(CryptoParse.longToString(user.id));
            MusDb.exec("UPDATE users SET password_hash=? WHERE id=?", up);
        }
        Session session = MusAuth.load(ctx);
        session.rotateId();
        session.setString("userId", CryptoParse.longToString(user.id));
        String csrf = "csrf-" + CryptoParse.longToString(user.id) + "-"
                + CryptoParse.longToString(AsyncClockNative.wallMillis());
        session.setString("csrf", csrf);
        MusAuth.save(ctx, session);
        UserDto dto = new UserDto();
        dto.id = user.id;
        dto.email = user.email;
        dto.displayName = user.displayName;
        dto.role = user.role;
        ctx.jsonString("{\"user\":" + MusicJson.user(dto) + ",\"csrfToken\":" + MusicJson.str(csrf) + "}");
        return JnFuture.completed(loop, null);
    }
}

class MusRegisterHandler implements AsyncWebHandler {
    EventLoop loop;
    MusRegisterHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String email = body.getString("email", "");
        String password = body.getString("password", "");
        String name = body.getString("displayName", "");
        if (email.length() < 3 || password.length() < 6) {
            MusAuth.err(ctx, 400, "VALIDATION_ERROR", "email/password required");
            return JnFuture.completed(loop, null);
        }
        if (name.length() == 0) {
            name = email;
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(email);
        JnFuture q = Mus.db.queryOne("SELECT id FROM users WHERE email=?", p, new IdMapper());
        FutureBlocker.blockingWait(q);
        if (q.getValue() != null) {
            MusAuth.err(ctx, 409, "CONFLICT", "email taken");
            return JnFuture.completed(loop, null);
        }
        long now = AsyncClockNative.wallMillis();
        String hash = Password.hashBlocking(password);
        ArrayList<Object> u = new ArrayList<Object>();
        u.add(email);
        u.add(name);
        u.add(hash);
        u.add("user");
        u.add(CryptoParse.longToString(now));
        u.add(CryptoParse.longToString(now));
        MusDb.exec("INSERT INTO users(email, display_name, password_hash, role, created_at, updated_at) VALUES (?,?,?,?,?,?)", u);
        JnFuture uq = Mus.db.queryOne("SELECT id, email, display_name, password_hash, role FROM users WHERE email=?",
                p, new UserRowMapper());
        FutureBlocker.blockingWait(uq);
        UserRow user = (UserRow) uq.getValue();
        Session session = MusAuth.load(ctx);
        session.rotateId();
        session.setString("userId", CryptoParse.longToString(user.id));
        String csrf = "csrf-" + CryptoParse.longToString(user.id) + "-"
                + CryptoParse.longToString(AsyncClockNative.wallMillis());
        session.setString("csrf", csrf);
        MusAuth.save(ctx, session);
        UserDto dto = new UserDto();
        dto.id = user.id;
        dto.email = user.email;
        dto.displayName = user.displayName;
        dto.role = user.role;
        ctx.jsonString("{\"user\":" + MusicJson.user(dto) + ",\"csrfToken\":" + MusicJson.str(csrf) + "}");
        return JnFuture.completed(loop, null);
    }
}

class MusLogoutHandler implements AsyncWebHandler {
    EventLoop loop;
    MusLogoutHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        Session session = MusAuth.load(ctx);
        session.setString("userId", "");
        session.setString("csrf", "");
        MusAuth.save(ctx, session);
        ctx.jsonString("{\"ok\":true}");
        return JnFuture.completed(loop, null);
    }
}

class MusMeHandler implements AsyncWebHandler {
    EventLoop loop;
    MusMeHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        long uid = MusAuth.requireUserId(ctx);
        if (uid <= 0L) {
            MusAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(uid));
        JnFuture q = Mus.db.queryOne(
                "SELECT id, email, display_name, password_hash, role FROM users WHERE id=?",
                p, new UserRowMapper());
        FutureBlocker.blockingWait(q);
        UserRow user = (UserRow) q.getValue();
        if (user == null) {
            MusAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        Session session = MusAuth.load(ctx);
        String csrf = session.getString("csrf");
        if (csrf == null || csrf.length() == 0) {
            csrf = "csrf-" + CryptoParse.longToString(user.id) + "-"
                    + CryptoParse.longToString(AsyncClockNative.wallMillis());
            session.setString("csrf", csrf);
            MusAuth.save(ctx, session);
        }
        UserDto dto = new UserDto();
        dto.id = user.id;
        dto.email = user.email;
        dto.displayName = user.displayName;
        dto.role = user.role;
        ctx.jsonString("{\"user\":" + MusicJson.user(dto) + ",\"csrfToken\":" + MusicJson.str(csrf) + "}");
        return JnFuture.completed(loop, null);
    }
}

class MusHomeHandler implements AsyncWebHandler {
    EventLoop loop;
    MusHomeHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ArrayList<Object> empty = new ArrayList<Object>();
        ArrayList<Object> tracks = MusDb.mapAll(Mus.db.query(
                MusSql.trackSelect() + "ORDER BY t.id LIMIT 20", empty), new TrackMapper());
        ArrayList<Object> albums = MusDb.mapAll(Mus.db.query(
                "SELECT al.id, al.artist_id, al.title, al.year, a.name FROM albums al JOIN artists a ON a.id=al.artist_id ORDER BY al.id LIMIT 12",
                empty), new AlbumRowMapper());
        ArrayList<Object> artists = MusDb.mapAll(Mus.db.query(
                "SELECT id, name, bio FROM artists ORDER BY id LIMIT 10", empty), new ArtistRowMapper());
        ArrayList<Object> playlists = MusDb.mapAll(Mus.db.query(
                "SELECT p.id, p.user_id, p.title, p.description, "
                        + "(SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id=p.id) AS track_count "
                        + "FROM playlists p ORDER BY p.id LIMIT 8", empty), new PlaylistRowMapper());
        ctx.jsonString("{\"recommended\":" + MusicJson.trackList(tracks)
                + ",\"newAlbums\":" + MusicJson.albumList(albums)
                + ",\"featuredArtists\":" + MusicJson.artistList(artists)
                + ",\"charts\":" + MusicJson.playlistList(playlists) + "}");
        return JnFuture.completed(loop, null);
    }
}

class MusGetTrackHandler implements AsyncWebHandler {
    EventLoop loop;
    MusGetTrackHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        long id = MusNum.parseLong(ctx.query("id"));
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(id));
        JnFuture q = Mus.db.queryOne(MusSql.trackSelect() + "WHERE t.id=?", p, new TrackMapper());
        FutureBlocker.blockingWait(q);
        TrackDto t = (TrackDto) q.getValue();
        if (t == null) {
            MusAuth.err(ctx, 404, "NOT_FOUND", "track not found");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(MusicJson.track(t));
        return JnFuture.completed(loop, null);
    }
}

class MusGetAlbumHandler implements AsyncWebHandler {
    EventLoop loop;
    MusGetAlbumHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        long id = MusNum.parseLong(ctx.query("id"));
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(id));
        JnFuture q = Mus.db.queryOne(
                "SELECT al.id, al.artist_id, al.title, al.year, a.name FROM albums al JOIN artists a ON a.id=al.artist_id WHERE al.id=?",
                p, new AlbumRowMapper());
        FutureBlocker.blockingWait(q);
        AlbumDto a = (AlbumDto) q.getValue();
        if (a == null) {
            MusAuth.err(ctx, 404, "NOT_FOUND", "album not found");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> tracks = MusDb.mapAll(Mus.db.query(
                MusSql.trackSelect() + "WHERE t.album_id=? ORDER BY t.track_no, t.id", p), new TrackMapper());
        ctx.jsonString("{\"album\":" + MusicJson.album(a) + ",\"tracks\":" + MusicJson.trackList(tracks) + "}");
        return JnFuture.completed(loop, null);
    }
}

class MusGetArtistHandler implements AsyncWebHandler {
    EventLoop loop;
    MusGetArtistHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        long id = MusNum.parseLong(ctx.query("id"));
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(id));
        JnFuture q = Mus.db.queryOne("SELECT id, name, bio FROM artists WHERE id=?", p, new ArtistRowMapper());
        FutureBlocker.blockingWait(q);
        ArtistDto a = (ArtistDto) q.getValue();
        if (a == null) {
            MusAuth.err(ctx, 404, "NOT_FOUND", "artist not found");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> albums = MusDb.mapAll(Mus.db.query(
                "SELECT al.id, al.artist_id, al.title, al.year, a.name FROM albums al JOIN artists a ON a.id=al.artist_id WHERE al.artist_id=? ORDER BY al.year DESC",
                p), new AlbumRowMapper());
        ArrayList<Object> tracks = MusDb.mapAll(Mus.db.query(
                MusSql.trackSelect() + "WHERE t.artist_id=? ORDER BY t.id LIMIT 40", p), new TrackMapper());
        ctx.jsonString("{\"artist\":" + MusicJson.artist(a)
                + ",\"albums\":" + MusicJson.albumList(albums)
                + ",\"tracks\":" + MusicJson.trackList(tracks) + "}");
        return JnFuture.completed(loop, null);
    }
}

class MusGetPlaylistHandler implements AsyncWebHandler {
    EventLoop loop;
    MusGetPlaylistHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        long id = MusNum.parseLong(ctx.query("id"));
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(id));
        JnFuture q = Mus.db.queryOne(
                "SELECT p.id, p.user_id, p.title, p.description, "
                        + "(SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id=p.id) AS track_count "
                        + "FROM playlists p WHERE p.id=?",
                p, new PlaylistRowMapper());
        FutureBlocker.blockingWait(q);
        PlaylistDto pl = (PlaylistDto) q.getValue();
        if (pl == null) {
            MusAuth.err(ctx, 404, "NOT_FOUND", "playlist not found");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(MusicJson.playlist(pl));
        return JnFuture.completed(loop, null);
    }
}

class MusPlaylistTracksHandler implements AsyncWebHandler {
    EventLoop loop;
    MusPlaylistTracksHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        long id = MusNum.parseLong(ctx.query("id"));
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(id));
        ArrayList<Object> tracks = MusDb.mapAll(Mus.db.query(
                MusSql.trackSelect() + "JOIN playlist_tracks pt ON pt.track_id=t.id WHERE pt.playlist_id=? ORDER BY pt.position",
                p), new TrackMapper());
        ctx.jsonString(MusicJson.trackList(tracks));
        return JnFuture.completed(loop, null);
    }
}

class MusSearchHandler implements AsyncWebHandler {
    EventLoop loop;
    MusSearchHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        String q = ctx.query("q");
        if (q == null) {
            q = "";
        }
        q = q.trim();
        if (q.length() == 0) {
            ctx.jsonString("{\"tracks\":[],\"albums\":[],\"artists\":[],\"playlists\":[]}");
            return JnFuture.completed(loop, null);
        }
        /* reject injection-ish patterns */
        if (q.indexOf(";") >= 0 || q.indexOf("--") >= 0) {
            MusAuth.err(ctx, 400, "VALIDATION_ERROR", "invalid query");
            return JnFuture.completed(loop, null);
        }
        String like = "%" + q + "%";
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(like);
        ArrayList<Object> tracks = MusDb.mapAll(Mus.db.query(
                MusSql.trackSelect() + "WHERE t.title LIKE ? ORDER BY t.id LIMIT 30", p), new TrackMapper());
        ArrayList<Object> albums = MusDb.mapAll(Mus.db.query(
                "SELECT al.id, al.artist_id, al.title, al.year, a.name FROM albums al JOIN artists a ON a.id=al.artist_id WHERE al.title LIKE ? LIMIT 20",
                p), new AlbumRowMapper());
        ArrayList<Object> artists = MusDb.mapAll(Mus.db.query(
                "SELECT id, name, bio FROM artists WHERE name LIKE ? LIMIT 20", p), new ArtistRowMapper());
        ArrayList<Object> playlists = MusDb.mapAll(Mus.db.query(
                "SELECT p.id, p.user_id, p.title, p.description, "
                        + "(SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id=p.id) AS track_count "
                        + "FROM playlists p WHERE p.title LIKE ? LIMIT 20", p), new PlaylistRowMapper());
        ctx.jsonString("{\"tracks\":" + MusicJson.trackList(tracks)
                + ",\"albums\":" + MusicJson.albumList(albums)
                + ",\"artists\":" + MusicJson.artistList(artists)
                + ",\"playlists\":" + MusicJson.playlistList(playlists) + "}");
        return JnFuture.completed(loop, null);
    }
}

class MusFavoritesHandler implements AsyncWebHandler {
    EventLoop loop;
    MusFavoritesHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        long uid = MusAuth.requireUserId(ctx);
        if (uid <= 0L) {
            MusAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(uid));
        ArrayList<Object> tracks = MusDb.mapAll(Mus.db.query(
                MusSql.trackSelect() + "JOIN favorites f ON f.track_id=t.id WHERE f.user_id=? ORDER BY f.created_at DESC",
                p), new TrackMapper());
        ctx.jsonString(MusicJson.trackList(tracks));
        return JnFuture.completed(loop, null);
    }
}

class MusHistoryHandler implements AsyncWebHandler {
    EventLoop loop;
    MusHistoryHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        long uid = MusAuth.requireUserId(ctx);
        if (uid <= 0L) {
            MusAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(uid));
        ArrayList<Object> tracks = MusDb.mapAll(Mus.db.query(
                MusSql.trackSelect() + "JOIN play_history h ON h.track_id=t.id WHERE h.user_id=? ORDER BY h.played_at DESC LIMIT 50",
                p), new TrackMapper());
        ctx.jsonString(MusicJson.trackList(tracks));
        return JnFuture.completed(loop, null);
    }
}

class MusMyPlaylistsHandler implements AsyncWebHandler {
    EventLoop loop;
    MusMyPlaylistsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        long uid = MusAuth.requireUserId(ctx);
        if (uid <= 0L) {
            MusAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(uid));
        ArrayList<Object> list = MusDb.mapAll(Mus.db.query(
                "SELECT p.id, p.user_id, p.title, p.description, "
                        + "(SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id=p.id) AS track_count "
                        + "FROM playlists p WHERE p.user_id=? ORDER BY p.updated_at DESC",
                p), new PlaylistRowMapper());
        ctx.jsonString(MusicJson.playlistList(list));
        return JnFuture.completed(loop, null);
    }
}

class MusChartsHandler implements AsyncWebHandler {
    EventLoop loop;
    MusChartsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ArrayList<Object> empty = new ArrayList<Object>();
        ArrayList<Object> tracks = MusDb.mapAll(Mus.db.query(
                MusSql.trackSelect() + "ORDER BY t.play_count DESC, t.id LIMIT 50", empty), new TrackMapper());
        ctx.jsonString(MusicJson.trackList(tracks));
        return JnFuture.completed(loop, null);
    }
}

class MusLyricsHandler implements AsyncWebHandler {
    EventLoop loop;
    MusLyricsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        long id = MusNum.parseLong(ctx.query("trackId"));
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(id));
        ArrayList<Object> lines = MusDb.mapAll(Mus.db.query(
                "SELECT start_ms, text FROM lyrics WHERE track_id=? ORDER BY line_no", p), new LyricMapper());
        String out = "[";
        int i = 0;
        while (i < lines.size()) {
            if (i > 0) {
                out = out + ",";
            }
            LyricLineDto l = (LyricLineDto) lines.get(i);
            out = out + "{\"startMs\":" + String.valueOf(l.startMs) + ",\"text\":" + MusicJson.str(l.text) + "}";
            i = i + 1;
        }
        ctx.jsonString(out + "]");
        return JnFuture.completed(loop, null);
    }
}

class MusFavoriteHandler implements AsyncWebHandler {
    EventLoop loop;
    MusFavoriteHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        if (MusAuth.checkCsrf(ctx) == 0) {
            MusAuth.err(ctx, 403, "FORBIDDEN", "csrf rejected");
            return JnFuture.completed(loop, null);
        }
        long uid = MusAuth.requireUserId(ctx);
        if (uid <= 0L) {
            MusAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long trackId = MusNum.parseLong(body.getString("trackId", "0"));
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(uid));
        p.add(CryptoParse.longToString(trackId));
        p.add(CryptoParse.longToString(AsyncClockNative.wallMillis()));
        MusDb.exec("INSERT OR IGNORE INTO favorites(user_id, track_id, created_at) VALUES (?,?,?)", p);
        ctx.jsonString("{\"ok\":true}");
        return JnFuture.completed(loop, null);
    }
}

class MusUnfavoriteHandler implements AsyncWebHandler {
    EventLoop loop;
    MusUnfavoriteHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        if (MusAuth.checkCsrf(ctx) == 0) {
            MusAuth.err(ctx, 403, "FORBIDDEN", "csrf rejected");
            return JnFuture.completed(loop, null);
        }
        long uid = MusAuth.requireUserId(ctx);
        if (uid <= 0L) {
            MusAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long trackId = MusNum.parseLong(body.getString("trackId", "0"));
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(uid));
        p.add(CryptoParse.longToString(trackId));
        MusDb.exec("DELETE FROM favorites WHERE user_id=? AND track_id=?", p);
        ctx.jsonString("{\"ok\":true}");
        return JnFuture.completed(loop, null);
    }
}

class MusRecordPlayHandler implements AsyncWebHandler {
    EventLoop loop;
    MusRecordPlayHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        long uid = MusAuth.requireUserId(ctx);
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long trackId = MusNum.parseLong(body.getString("trackId", "0"));
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(CryptoParse.longToString(trackId));
        MusDb.exec("UPDATE tracks SET play_count = play_count + 1 WHERE id=?", up);
        if (uid > 0L) {
            ArrayList<Object> h = new ArrayList<Object>();
            h.add(CryptoParse.longToString(uid));
            h.add(CryptoParse.longToString(trackId));
            h.add(CryptoParse.longToString(AsyncClockNative.wallMillis()));
            MusDb.exec("INSERT INTO play_history(user_id, track_id, played_at) VALUES (?,?,?)", h);
        }
        ctx.jsonString("{\"ok\":true}");
        return JnFuture.completed(loop, null);
    }
}

class MusCreatePlaylistHandler implements AsyncWebHandler {
    EventLoop loop;
    MusCreatePlaylistHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        if (MusAuth.checkCsrf(ctx) == 0) {
            MusAuth.err(ctx, 403, "FORBIDDEN", "csrf rejected");
            return JnFuture.completed(loop, null);
        }
        long uid = MusAuth.requireUserId(ctx);
        if (uid <= 0L) {
            MusAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String title = body.getString("title", "New Playlist");
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(uid));
        p.add(title);
        p.add("");
        p.add("");
        p.add(CryptoParse.longToString(now));
        p.add(CryptoParse.longToString(now));
        MusDb.exec("INSERT INTO playlists(user_id, title, description, cover_path, created_at, updated_at) VALUES (?,?,?,?,?,?)", p);
        ArrayList<Object> q = new ArrayList<Object>();
        q.add(CryptoParse.longToString(uid));
        q.add(title);
        JnFuture f = Mus.db.queryOne(
                "SELECT p.id, p.user_id, p.title, p.description, 0 AS track_count FROM playlists p WHERE p.user_id=? AND p.title=? ORDER BY p.id DESC LIMIT 1",
                q, new PlaylistRowMapper());
        FutureBlocker.blockingWait(f);
        PlaylistDto pl = (PlaylistDto) f.getValue();
        ctx.jsonString(MusicJson.playlist(pl));
        return JnFuture.completed(loop, null);
    }
}

class MusAddPlaylistHandler implements AsyncWebHandler {
    EventLoop loop;
    MusAddPlaylistHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        if (MusAuth.checkCsrf(ctx) == 0) {
            MusAuth.err(ctx, 403, "FORBIDDEN", "csrf rejected");
            return JnFuture.completed(loop, null);
        }
        long uid = MusAuth.requireUserId(ctx);
        if (uid <= 0L) {
            MusAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long playlistId = MusNum.parseLong(body.getString("playlistId", "0"));
        long trackId = MusNum.parseLong(body.getString("trackId", "0"));
        ArrayList<Object> own = new ArrayList<Object>();
        own.add(CryptoParse.longToString(playlistId));
        own.add(CryptoParse.longToString(uid));
        JnFuture check = Mus.db.queryOne("SELECT id FROM playlists WHERE id=? AND user_id=?", own, new IdMapper());
        FutureBlocker.blockingWait(check);
        if (check.getValue() == null) {
            MusAuth.err(ctx, 403, "FORBIDDEN", "not your playlist");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> cnt = new ArrayList<Object>();
        cnt.add(CryptoParse.longToString(playlistId));
        JnFuture cq = Mus.db.queryOne("SELECT COUNT(*) AS id FROM playlist_tracks WHERE playlist_id=?", cnt, new IdMapper());
        FutureBlocker.blockingWait(cq);
        int pos = WebUtil.parseIntOr((String) cq.getValue(), 0);
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(playlistId));
        p.add(CryptoParse.longToString(trackId));
        p.add(String.valueOf(pos));
        MusDb.exec("INSERT OR IGNORE INTO playlist_tracks(playlist_id, track_id, position) VALUES (?,?,?)", p);
        ctx.jsonString("{\"ok\":true}");
        return JnFuture.completed(loop, null);
    }
}

class MusRemovePlaylistHandler implements AsyncWebHandler {
    EventLoop loop;
    MusRemovePlaylistHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        if (MusAuth.checkCsrf(ctx) == 0) {
            MusAuth.err(ctx, 403, "FORBIDDEN", "csrf rejected");
            return JnFuture.completed(loop, null);
        }
        long uid = MusAuth.requireUserId(ctx);
        if (uid <= 0L) {
            MusAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long playlistId = MusNum.parseLong(body.getString("playlistId", "0"));
        long trackId = MusNum.parseLong(body.getString("trackId", "0"));
        ArrayList<Object> own = new ArrayList<Object>();
        own.add(CryptoParse.longToString(playlistId));
        own.add(CryptoParse.longToString(uid));
        JnFuture check = Mus.db.queryOne("SELECT id FROM playlists WHERE id=? AND user_id=?", own, new IdMapper());
        FutureBlocker.blockingWait(check);
        if (check.getValue() == null) {
            MusAuth.err(ctx, 403, "FORBIDDEN", "not your playlist");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(playlistId));
        p.add(CryptoParse.longToString(trackId));
        MusDb.exec("DELETE FROM playlist_tracks WHERE playlist_id=? AND track_id=?", p);
        ctx.jsonString("{\"ok\":true}");
        return JnFuture.completed(loop, null);
    }
}
