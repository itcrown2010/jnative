/**
 * Typed API contracts for JNative Music (documentation + shared surface).
 * Server mounts REST mirrors in MusicServer.
 * @author 张晓达
 */
interface MusicAuthApi {
    AuthSessionDto login(LoginRequest request);

    AuthSessionDto register(RegisterRequest request);

    void logout();

    UserDto currentUser();
}

interface MusicApi {
    HomeFeedDto getHome();

    TrackDto getTrack(long id);

    AlbumDto getAlbum(long id);

    ArtistDto getArtist(long id);

    PlaylistDto getPlaylist(long id);

    ArrayList<Object> playlistTracks(long id);

    SearchResultDto search(String query);

    ArrayList<Object> myFavorites();

    ArrayList<Object> recentlyPlayed();

    ArrayList<Object> myPlaylists();

    ArrayList<Object> charts();

    ArrayList<Object> lyrics(long trackId);

    void favorite(long trackId);

    void unfavorite(long trackId);

    void recordPlay(long trackId);

    PlaylistDto createPlaylist(String title);

    void addToPlaylist(long playlistId, long trackId);

    void removeFromPlaylist(long playlistId, long trackId);
}

class RepeatMode {
    static int SEQUENCE = 0;
    static int ALL = 1;
    static int ONE = 2;
    static int SHUFFLE = 3;
}
