package socketmessages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseGameContent extends EmptyContent {

    public static final String TYPE_ATTR = "type";
    public static final String TIME_PASSED_ATTR = "current_time";
    public static final String POINTS_ATTR = "points";

    protected final @NotNull GameType gameType;
    protected final float timeLeft;

    protected BaseGameContent(@NotNull GameType gameType, float timeLeft) {

        this.gameType = gameType;
        this.timeLeft = timeLeft;
    }

    @JsonProperty(TYPE_ATTR)
    public @NotNull String getType() {
        return gameType.toString();
    }

    @JsonProperty(TIME_PASSED_ATTR)
    public float getTimeLeft() {
        return timeLeft;
    }
}
