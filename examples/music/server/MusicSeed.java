/**
 * Seeds ~10 artists / 20 albums / 100 tracks + demo user + playlists.
 * Generates a few real WAV fixtures; remaining tracks share fixture files.
 */
class MusicSeed {
    static void ensure() {
        ensureDirs();
        ensureFixtures();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add("music@jnative.dev");
        JnFuture q = Mus.db.queryOne("SELECT id FROM users WHERE email=?", p, new IdMapper());
        FutureBlocker.blockingWait(q);
        if (q.getValue() != null) {
            return;
        }
        long now = AsyncClockNative.wallMillis();
        String hash = Password.hashBlocking("music1234");
        ArrayList<Object> u = new ArrayList<Object>();
        u.add("music@jnative.dev");
        u.add("Music Demo");
        u.add(hash);
        u.add("admin");
        u.add(CryptoParse.longToString(now));
        u.add(CryptoParse.longToString(now));
        MusDb.exec("INSERT INTO users(email, display_name, password_hash, role, created_at, updated_at) VALUES (?,?,?,?,?,?)", u);
        JnFuture uq = Mus.db.queryOne("SELECT id FROM users WHERE email=?", p, new IdMapper());
        FutureBlocker.blockingWait(uq);
        long userId = MusNum.parseLong((String) uq.getValue());

        int ai = 1;
        while (ai <= 10) {
            ArrayList<Object> ar = new ArrayList<Object>();
            ar.add("Artist " + String.valueOf(ai));
            ar.add("Bio for artist " + String.valueOf(ai));
            ar.add("covers/cover.bin");
            ar.add(CryptoParse.longToString(now));
            MusDb.exec("INSERT INTO artists(name, bio, cover_path, created_at) VALUES (?,?,?,?)", ar);
            ai = ai + 1;
        }

        int al = 0;
        while (al < 20) {
            al = al + 1;
            int artistId = ((al - 1) % 10) + 1;
            ArrayList<Object> ab = new ArrayList<Object>();
            ab.add(String.valueOf(artistId));
            ab.add("Album " + String.valueOf(al));
            ab.add(String.valueOf(2010 + (al % 15)));
            ab.add("covers/cover.bin");
            ab.add(CryptoParse.longToString(now));
            MusDb.exec("INSERT INTO albums(artist_id, title, year, cover_path, created_at) VALUES (?,?,?,?,?)", ab);
        }

        int t = 1;
        while (t <= 100) {
            int albumId = ((t - 1) % 20) + 1;
            int artistId = ((albumId - 1) % 10) + 1;
            int fixture = ((t - 1) % 5) + 1;
            String audio = "audio/tone" + String.valueOf(fixture) + ".wav";
            ArrayList<Object> tr = new ArrayList<Object>();
            tr.add(String.valueOf(albumId));
            tr.add(String.valueOf(artistId));
            tr.add("Track " + String.valueOf(t));
            tr.add(String.valueOf(2000 + (t % 8) * 500));
            tr.add(String.valueOf(((t - 1) % 5) + 1));
            tr.add(audio);
            tr.add("covers/cover.bin");
            tr.add("0");
            tr.add(CryptoParse.longToString(now));
            MusDb.exec("INSERT INTO tracks(album_id, artist_id, title, duration_ms, track_no, audio_path, cover_path, play_count, created_at) VALUES (?,?,?,?,?,?,?,?,?)", tr);
            t = t + 1;
        }

        /* lyrics for first 3 tracks */
        int li = 1;
        while (li <= 3) {
            int line = 0;
            while (line < 4) {
                ArrayList<Object> ly = new ArrayList<Object>();
                ly.add(String.valueOf(li));
                ly.add(String.valueOf(line));
                ly.add(String.valueOf(line * 500));
                ly.add("Line " + String.valueOf(line + 1) + " of track " + String.valueOf(li));
                MusDb.exec("INSERT INTO lyrics(track_id, line_no, start_ms, text) VALUES (?,?,?,?)", ly);
                line = line + 1;
            }
            li = li + 1;
        }

        ArrayList<Object> pl = new ArrayList<Object>();
        pl.add(CryptoParse.longToString(userId));
        pl.add("Morning Mix");
        pl.add("Seed playlist");
        pl.add("");
        pl.add(CryptoParse.longToString(now));
        pl.add(CryptoParse.longToString(now));
        MusDb.exec("INSERT INTO playlists(user_id, title, description, cover_path, created_at, updated_at) VALUES (?,?,?,?,?,?)", pl);
        JnFuture pq = Mus.db.queryOne("SELECT id FROM playlists WHERE user_id=? AND title=?",
                list2(CryptoParse.longToString(userId), "Morning Mix"), new IdMapper());
        FutureBlocker.blockingWait(pq);
        long playlistId = MusNum.parseLong((String) pq.getValue());
        int pos = 0;
        while (pos < 10) {
            ArrayList<Object> pt = new ArrayList<Object>();
            pt.add(CryptoParse.longToString(playlistId));
            pt.add(String.valueOf(pos + 1));
            pt.add(String.valueOf(pos));
            MusDb.exec("INSERT INTO playlist_tracks(playlist_id, track_id, position) VALUES (?,?,?)", pt);
            pos = pos + 1;
        }

        ArrayList<Object> charts = new ArrayList<Object>();
        charts.add(CryptoParse.longToString(userId));
        charts.add("Charts Top 20");
        charts.add("Popular tracks");
        charts.add("");
        charts.add(CryptoParse.longToString(now));
        charts.add(CryptoParse.longToString(now));
        MusDb.exec("INSERT INTO playlists(user_id, title, description, cover_path, created_at, updated_at) VALUES (?,?,?,?,?,?)", charts);
    }

    static ArrayList<Object> list2(String a, String b) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(a);
        p.add(b);
        return p;
    }

    static void ensureDirs() {
        FsNative.createDirectories(Mus.storageRoot + "/audio");
        FsNative.createDirectories(Mus.storageRoot + "/covers");
        FsNative.createDirectories("build/music");
    }

    static void ensureFixtures() {
        int i = 1;
        while (i <= 5) {
            String path = Mus.storageRoot + "/audio/tone" + String.valueOf(i) + ".wav";
            if (File.exists(path) == false) {
                writeToneWav(path, 440 + i * 40, 2);
            }
            i = i + 1;
        }
        String cover = Mus.storageRoot + "/covers/cover.bin";
        if (File.exists(cover) == false) {
            byte[] pngish = new byte[64];
            int j = 0;
            while (j < 64) {
                pngish[j] = CryptoBytes.toByte(j * 3 + 17);
                j = j + 1;
            }
            File.writeBytes(cover, pngish);
        }
        /* also copy into fixtures for repo visibility */
        String fx = "examples/music/fixtures/tone1.wav";
        if (File.exists(fx) == false) {
            FsNative.createDirectories("examples/music/fixtures");
            writeToneWav(fx, 440, 1);
        }
    }

    /** Minimal PCM WAV: mono 16-bit 22050 Hz. */
    static void writeToneWav(String path, int freqHz, int seconds) {
        int sampleRate = 22050;
        int numSamples = sampleRate * seconds;
        int dataBytes = numSamples * 2;
        int fileSize = 44 + dataBytes;
        byte[] wav = new byte[fileSize];
        putAscii(wav, 0, "RIFF");
        putInt32LE(wav, 4, fileSize - 8);
        putAscii(wav, 8, "WAVE");
        putAscii(wav, 12, "fmt ");
        putInt32LE(wav, 16, 16);
        putInt16LE(wav, 20, 1);
        putInt16LE(wav, 22, 1);
        putInt32LE(wav, 24, sampleRate);
        putInt32LE(wav, 28, sampleRate * 2);
        putInt16LE(wav, 32, 2);
        putInt16LE(wav, 34, 16);
        putAscii(wav, 36, "data");
        putInt32LE(wav, 40, dataBytes);
        int i = 0;
        while (i < numSamples) {
            /* simple square-ish tone without floating math */
            int period = sampleRate / freqHz;
            if (period < 1) {
                period = 1;
            }
            int phase = i % period;
            int sample = -8000;
            if (phase * 2 < period) {
                sample = 8000;
            }
            int off = 44 + i * 2;
            putInt16LE(wav, off, sample);
            i = i + 1;
        }
        File.writeBytes(path, wav);
    }

    static void putAscii(byte[] b, int off, String s) {
        int i = 0;
        while (i < s.length()) {
            b[off + i] = CryptoBytes.toByte(s.charAt(i));
            i = i + 1;
        }
    }

    static void putInt16LE(byte[] b, int off, int v) {
        int u = v;
        if (u < 0) {
            u = u + 65536;
        }
        b[off] = CryptoBytes.toByte(u % 256);
        b[off + 1] = CryptoBytes.toByte((u / 256) % 256);
    }

    static void putInt32LE(byte[] b, int off, int v) {
        int u = v;
        if (u < 0) {
            u = 0;
        }
        b[off] = CryptoBytes.toByte(u % 256);
        int x = u / 256;
        b[off + 1] = CryptoBytes.toByte(x % 256);
        x = x / 256;
        b[off + 2] = CryptoBytes.toByte(x % 256);
        x = x / 256;
        b[off + 3] = CryptoBytes.toByte(x % 256);
    }
}
