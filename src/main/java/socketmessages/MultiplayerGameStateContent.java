package socketmessages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@SuppressWarnings("unused")
public class MultiplayerGameStateContent extends BaseGameContent {

    public static final String ROLE_ATTR = "role";
    public static final String PLAYERS_ATTR = "players";
    public static final String WORD_ATTR = "word";

    private final @NotNull PlayerRole role;
    private final @NotNull ArrayList<PlayerInfo> players;
    private final @NotNull ArrayList<PicturePointContent> points;
    private final @Nullable String word;

    public MultiplayerGameStateContent(
        float timePassed,
        @NotNull PlayerRole role,
        @NotNull ArrayList<PlayerInfo> players,
        @NotNull ArrayList<PicturePointContent> points,
        @Nullable String word) {

        super(GameType.MULTIPLAYER, timePassed);
        this.role = role;
        this.players = players;
        this.points = points;
        this.word = (role == PlayerRole.PAINTER) ? word : null;
    }

    @JsonProperty(ROLE_ATTR)
    public @NotNull String getRole() {
        return role.toString();
    }

    @JsonProperty(PLAYERS_ATTR)
    public @NotNull ArrayList<PlayerInfo> getPlayers() {
        return players;
    }

    @JsonProperty(POINTS_ATTR)
    public @NotNull ArrayList<PicturePointContent> getPoints() {
        return points;
    }

    @JsonInclude(NON_NULL)
    @JsonProperty(WORD_ATTR)
    public @Nullable String getWord() {
        return word;
    }
}
