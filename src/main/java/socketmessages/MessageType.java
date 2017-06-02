package socketmessages;

import org.jetbrains.annotations.NotNull;

public enum MessageType {

    EMPTY(""),
    STATE("STATE"),
    UPDATE("UPDATE"),
    GET_STATE("GET_STATE"),
    NEW_POINT("NEW_POINT"),
    START_SINGLEPLAYER_GAME("START_SP_GAME"),
    START_MULTIPLAYER_GAME("START_MP_GAME"),
    CHECK_ANSWER("GET_ANSWER"),
    STOP_GAME("STOP_GAME"),
    TIMER_STATE("TIMER_STATE"),
    EXIT_GAME("EXIT"),
    PLAYERS_CONNECT("PLAYERS_CONNECT"),
    PLAYER_DISCONNECT("PLAYER_DISCONNECT"),
    VOTE_ANSWER("VOTE_ANSWER"),
    NEW_VOTE("NEW_VOTE"),
    CLEAR("CLEAR");

    private final String type;

    MessageType(String type) {
        this.type = type;
    }

    public static MessageType fromString(String stringType) {

        for (MessageType messageType : MessageType.values()) {

            if (messageType.type.equalsIgnoreCase(stringType)) {

                return messageType;
            }
        }

        return EMPTY;
    }

    @Override
    public @NotNull String toString() {
        return type;
    }
}
