/**
 * Auth-gated media upload (base64 JSON bytes). Not multipart; not public static links.
 * @author 张晓达
 */
class WcMediaService {
    static String rootDir() {
        String d = Env.get("WC_MEDIA_DIR");
        if (d == null || d.length() == 0) {
            return "./build/wonder-campus/media";
        }
        return d;
    }

    static WcResult uploadBase64(long orgId, long ownerUserId, long studentId, long sessionId, long feedbackId,
            String mime, String dataBase64, String idem) {
        if (idem == null || idem.length() == 0) {
            return WcResult.fail("IDEMPOTENCY_REQUIRED", "需要幂等键");
        }
        ArrayList<Object> ik2 = new ArrayList<Object>();
        ik2.add(CryptoParse.longToString(orgId));
        ik2.add("%|idem:" + idem);
        Object byNote = WcDb.queryOne(
                "SELECT id FROM wc_media_assets WHERE org_id=? AND storage_path LIKE ? LIMIT 1", ik2,
                new LongMapper());
        if (byNote != null) {
            return WcResult.success("OK", "idempotent media",
                    "{\"mediaId\":" + CryptoParse.longToString(WcNum.asLong(byNote)) + "}");
        }
        if (dataBase64 == null || dataBase64.length() == 0) {
            return WcResult.fail("EMPTY", "缺少图片数据");
        }
        /* strip data-url prefix if present */
        String b64 = dataBase64;
        int comma = b64.indexOf(",");
        if (b64.indexOf("base64") >= 0 && comma >= 0) {
            b64 = b64.substring(comma + 1, b64.length());
        }
        byte[] raw = Base64Codec.decode(b64);
        if (raw == null || raw.length == 0) {
            return WcResult.fail("DECODE", "无法解码图片");
        }
        if (raw.length > 2097152) {
            return WcResult.fail("TOO_LARGE", "图片超过 2MB");
        }
        String detected = detectMime(raw);
        if (detected.length() == 0) {
            return WcResult.fail("BAD_TYPE", "仅支持 JPEG/PNG");
        }
        if (mime == null || mime.length() == 0) {
            mime = detected;
        }
        if (mime.equals("image/jpeg") == false && mime.equals("image/png") == false
                && mime.equals(detected) == false) {
            return WcResult.fail("BAD_TYPE", "类型与内容不一致");
        }
        byte[] stored = raw;
        if (detected.equals("image/jpeg")) {
            stored = stripJpegApp1(raw);
        }
        String dir = rootDir() + "/" + CryptoParse.longToString(orgId);
        Files.createDirectories(FsPath.of(dir));
        String name = "m-" + CryptoParse.longToString(AsyncClockNative.wallMillis()) + ".bin";
        String path = dir + "/" + name;
        File.writeBytes(path, stored);
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(ownerUserId));
        p.add(CryptoParse.longToString(studentId));
        p.add(CryptoParse.longToString(sessionId));
        p.add(CryptoParse.longToString(feedbackId));
        p.add(path + "|idem:" + idem);
        p.add(detected);
        p.add(CryptoParse.longToString(stored.length));
        p.add(CryptoParse.longToString(now));
        p.add("0");
        long mid = WcDb.insertReturningId(
                "INSERT INTO wc_media_assets(org_id, owner_user_id, student_id, session_id, feedback_id, storage_path, mime, size_bytes, created_at, deleted_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                p);
        return WcResult.success("OK", "uploaded",
                "{\"mediaId\":" + CryptoParse.longToString(mid) + ",\"bytes\":"
                        + CryptoParse.longToString(stored.length) + ",\"mime\":\"" + detected + "\"}");
    }

    static String detectMime(byte[] raw) {
        if (raw.length >= 3) {
            int b0 = raw[0];
            int b1 = raw[1];
            int b2 = raw[2];
            if (b0 < 0) {
                b0 = b0 + 256;
            }
            if (b1 < 0) {
                b1 = b1 + 256;
            }
            if (b2 < 0) {
                b2 = b2 + 256;
            }
            if (b0 == 255 && b1 == 216 && b2 == 255) {
                return "image/jpeg";
            }
            if (b0 == 137 && b1 == 80 && b2 == 78) {
                return "image/png";
            }
        }
        return "";
    }

    /** Drop JPEG APP1 (EXIF) segments; keep other markers. Best-effort metadata reduction. */
    static byte[] stripJpegApp1(byte[] raw) {
        if (raw.length < 4) {
            return raw;
        }
        ByteBuf out = new ByteBuf();
        out.appendByte(255);
        out.appendByte(216);
        int i = 2;
        while (i + 3 < raw.length) {
            int m0 = raw[i];
            int m1 = raw[i + 1];
            if (m0 < 0) {
                m0 = m0 + 256;
            }
            if (m1 < 0) {
                m1 = m1 + 256;
            }
            if (m0 != 255) {
                break;
            }
            if (m1 == 218) {
                /* SOS — copy rest */
                while (i < raw.length) {
                    out.appendByte(raw[i]);
                    i = i + 1;
                }
                return out.toArray();
            }
            if (m1 == 216 || m1 == 217) {
                out.appendByte(255);
                out.appendByte(m1);
                i = i + 2;
            } else {
                int lenHi = raw[i + 2];
                int lenLo = raw[i + 3];
                if (lenHi < 0) {
                    lenHi = lenHi + 256;
                }
                if (lenLo < 0) {
                    lenLo = lenLo + 256;
                }
                int segLen = lenHi * 256 + lenLo;
                if (segLen < 2 || i + 2 + segLen > raw.length) {
                    break;
                }
                if (m1 == 225) {
                    /* APP1 skip */
                    i = i + 2 + segLen;
                } else {
                    int end = i + 2 + segLen;
                    while (i < end) {
                        out.appendByte(raw[i]);
                        i = i + 1;
                    }
                }
            }
        }
        while (i < raw.length) {
            out.appendByte(raw[i]);
            i = i + 1;
        }
        return out.toArray();
    }

    static String filePathOf(long mediaId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(mediaId));
        Object path = WcDb.queryOne(
                "SELECT storage_path FROM wc_media_assets WHERE id=? AND deleted_at=0", p, new StringMapper());
        if (path == null) {
            return "";
        }
        String s = (String) path;
        int pipe = s.indexOf("|");
        if (pipe >= 0) {
            return s.substring(0, pipe);
        }
        /* keep full path */
        return s;
    }

    static WcResult deleteMedia(long orgId, long mediaId, long actor) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(AsyncClockNative.wallMillis()));
        p.add(CryptoParse.longToString(mediaId));
        p.add(CryptoParse.longToString(orgId));
        WcDb.exec("UPDATE wc_media_assets SET deleted_at=? WHERE id=? AND org_id=?", p);
        WcServices.audit(orgId, actor, "MEDIA_DELETE", "media", mediaId, "");
        return WcResult.success("OK", "deleted", "{\"mediaId\":" + CryptoParse.longToString(mediaId) + "}");
    }

    static int cleanupOrphans(long orgId, long olderThanMs) {
        long cutoff = AsyncClockNative.wallMillis() - olderThanMs;
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(cutoff));
        DbQueryResult r = WcDb.query(
                "SELECT id FROM wc_media_assets WHERE org_id=? AND feedback_id=0 AND deleted_at=0 AND created_at<?",
                p);
        int n = 0;
        int i = 0;
        while (i < r.rows.size()) {
            deleteMedia(orgId, r.rows.get(i).getLong(0), 0L);
            n = n + 1;
            i = i + 1;
        }
        return n;
    }
}
