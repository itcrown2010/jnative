class MusicJson {
    static String esc(String s) {
        if (s == null) {
            return "";
        }
        String out = "";
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c == 34) {
                out = out + "\\\"";
            } else {
                if (c == 92) {
                    out = out + "\\\\";
                } else {
                    if (c == 10) {
                        out = out + "\\n";
                    } else {
                        if (c == 13) {
                            out = out + "\\r";
                        } else {
                            out = out + WebUtil.byteChar(c);
                        }
                    }
                }
            }
            i = i + 1;
        }
        return out;
    }

    static String str(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + esc(s) + "\"";
    }

    static String num(long v) {
        return CryptoParse.longToString(v);
    }

    static String track(TrackDto t) {
        if (t == null) {
            return "null";
        }
        return "{\"id\":" + num(t.id)
                + ",\"albumId\":" + num(t.albumId)
                + ",\"artistId\":" + num(t.artistId)
                + ",\"title\":" + str(t.title)
                + ",\"artistName\":" + str(t.artistName)
                + ",\"albumTitle\":" + str(t.albumTitle)
                + ",\"durationMs\":" + String.valueOf(t.durationMs)
                + ",\"trackNo\":" + String.valueOf(t.trackNo)
                + ",\"mediaUrl\":" + str(t.mediaUrl)
                + ",\"coverUrl\":" + str(t.coverUrl)
                + ",\"playCount\":" + String.valueOf(t.playCount)
                + "}";
    }

    static String album(AlbumDto a) {
        if (a == null) {
            return "null";
        }
        return "{\"id\":" + num(a.id)
                + ",\"artistId\":" + num(a.artistId)
                + ",\"title\":" + str(a.title)
                + ",\"year\":" + String.valueOf(a.year)
                + ",\"artistName\":" + str(a.artistName)
                + ",\"coverUrl\":" + str(a.coverUrl)
                + "}";
    }

    static String artist(ArtistDto a) {
        if (a == null) {
            return "null";
        }
        return "{\"id\":" + num(a.id)
                + ",\"name\":" + str(a.name)
                + ",\"bio\":" + str(a.bio)
                + ",\"coverUrl\":" + str(a.coverUrl)
                + "}";
    }

    static String playlist(PlaylistDto p) {
        if (p == null) {
            return "null";
        }
        return "{\"id\":" + num(p.id)
                + ",\"userId\":" + num(p.userId)
                + ",\"title\":" + str(p.title)
                + ",\"description\":" + str(p.description)
                + ",\"coverUrl\":" + str(p.coverUrl)
                + ",\"trackCount\":" + String.valueOf(p.trackCount)
                + "}";
    }

    static String user(UserDto u) {
        if (u == null) {
            return "null";
        }
        return "{\"id\":" + num(u.id)
                + ",\"email\":" + str(u.email)
                + ",\"displayName\":" + str(u.displayName)
                + ",\"role\":" + str(u.role)
                + "}";
    }

    static String trackList(ArrayList<Object> list) {
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + track((TrackDto) list.get(i));
            i = i + 1;
        }
        return out + "]";
    }

    static String albumList(ArrayList<Object> list) {
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + album((AlbumDto) list.get(i));
            i = i + 1;
        }
        return out + "]";
    }

    static String artistList(ArrayList<Object> list) {
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + artist((ArtistDto) list.get(i));
            i = i + 1;
        }
        return out + "]";
    }

    static String playlistList(ArrayList<Object> list) {
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + playlist((PlaylistDto) list.get(i));
            i = i + 1;
        }
        return out + "]";
    }
}
