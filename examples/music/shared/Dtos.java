/**
 * @author 张晓达
 */
class UserDto {
    long id;
    String email;
    String displayName;
    String role;
}

class AuthSessionDto {
    UserDto user;
    String csrfToken;
}

class LoginRequest {
    String email;
    String password;
}

class RegisterRequest {
    String email;
    String password;
    String displayName;
}

class ArtistDto {
    long id;
    String name;
    String bio;
    String coverUrl;
}

class AlbumDto {
    long id;
    long artistId;
    String title;
    int year;
    String artistName;
    String coverUrl;
}

class TrackDto {
    long id;
    long albumId;
    long artistId;
    String title;
    String artistName;
    String albumTitle;
    int durationMs;
    int trackNo;
    String mediaUrl;
    String coverUrl;
    int playCount;
}

class PlaylistDto {
    long id;
    long userId;
    String title;
    String description;
    String coverUrl;
    int trackCount;
}

class HomeFeedDto {
    ArrayList<TrackDto> recommended;
    ArrayList<AlbumDto> newAlbums;
    ArrayList<ArtistDto> featuredArtists;
    ArrayList<PlaylistDto> charts;
}

class SearchResultDto {
    ArrayList<TrackDto> tracks;
    ArrayList<AlbumDto> albums;
    ArrayList<ArtistDto> artists;
    ArrayList<PlaylistDto> playlists;
}

class LyricLineDto {
    int startMs;
    String text;
}
