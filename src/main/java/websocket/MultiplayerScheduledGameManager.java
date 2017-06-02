package websocket;

import entities.MultiplayerGame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.socket.WebSocketSession;
import socketmessages.*;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

public class MultiplayerScheduledGameManager extends ScheduledGameManager<MultiplayerGame> {

    private final GameRelationManager gameRelationManager;

    public final class MultiplayerScheduledGame extends ScheduledGame<MultiplayerGame> {

        private MultiplayerScheduledGame(ScheduledExecutorService scheduler, MultiplayerGame game) {
            super(scheduler, game);
        }

        @Override
        GameType getType() {
            return GameType.MULTIPLAYER;
        }

        @Override
        public @NotNull WebSocketMessage<BaseGameContent> getGameStateMessage(@NotNull String login) {

            return getGameMessage(login, MessageType.STATE);
        }

        @Override
        public @NotNull WebSocketMessage<BaseGameContent> getJoinGameMessage(@NotNull String login) {

            return getGameMessage(login, MessageType.START_MULTIPLAYER_GAME);
        }

        @Override
        public synchronized void runWinTask(@NotNull String winnerLogin) {

            endMultiplayerGame(GameResult.GAME_WON, winnerLogin);
        }

        @Override
        public synchronized void runLoseTask(@NotNull GameResult result) {

            endMultiplayerGame(GameResult.PAINTER_LEFT, null);
        }

        @Override
        public int getWinScore() {
            return GameManagerService.MULTIPLAYER_GAME_SCORE;
        }

        @Override
        public int getFinishTime() {
            return GameManagerService.MULTIPLAYER_TIME_LIMIT;
        }

        private WebSocketMessage<BaseGameContent> getGameMessage(@NotNull String login, MessageType messageType) {

            final MultiplayerGame multiplayerGame = getGame();
            final ArrayList<PlayerInfo> playerInfos = new ArrayList<>(
                multiplayerGame.getUserLogins().stream()
                    .map(e -> new PlayerInfo(e, gameRelationManager.getRelation(e).getPlayerNumber()))
                    .collect(Collectors.toList()));

            return new WebSocketMessage<>(
                messageType.toString(),
                new MultiplayerGameStateContent(
                    getTimeLeft(),
                    gameRelationManager.getRelation(login).getRole(),
                    playerInfos,
                    getPoints(),
                    multiplayerGame.getWord()));
        }

        private synchronized void endMultiplayerGame(GameResult gameResult, @Nullable String winnerLogin) {

            final int gameId = getGame().getId();

            if (currentGames.containsKey(gameId)) {

                cancelAll();
                final ArrayList<WebSocketSession> losers = gameRelationManager.getGameSessions(this);
                final String word = getGame().getWord();

                if (gameResult == GameResult.GAME_WON) {

                    SessionOperator.sendMessage(
                        gameRelationManager.getRelation(winnerLogin).getSession(),
                        new WebSocketMessage<>(
                            MessageType.STOP_GAME.toString(),
                            new FinishGameContent(
                                gameResult, getWinScore(),
                                winnerLogin, word)));

                    losers.remove(gameRelationManager.getRelation(winnerLogin).getSession());
                    gameRelationManager.removeRelation(winnerLogin);
                }

                final GameResult losersResult = (gameResult == GameResult.GAME_WON) ?
                    GameResult.GAME_LOST :
                    gameResult;

                losers.forEach(
                    (WebSocketSession session) -> {

                        SessionOperator.sendMessage(
                            session, new WebSocketMessage<>(
                                MessageType.STOP_GAME.toString(),
                                new FinishGameContent(
                                    losersResult, 0,
                                    winnerLogin, word)));

                        gameRelationManager.removeRelation(SessionOperator.getLogin(session));
                    });

                currentGames.remove(gameId);
                LOGGER.info("Multiplayer game #{} ended with result {}.", gameId, gameResult.asInt());
            }
        }
    }

    public MultiplayerScheduledGameManager(
        ScheduledExecutorService scheduler,
        GameRelationManager gameRelationManager) {

        super(scheduler, new ConcurrentHashMap<>());
        this.gameRelationManager = gameRelationManager;
    }

    @Override
    @NotNull ScheduledGame<MultiplayerGame> createScheduledGame(MultiplayerGame game) {

        final MultiplayerScheduledGame scheduledGame = new MultiplayerScheduledGame(scheduler, game);
        currentGames.put(game.getId(), scheduledGame);
        return scheduledGame;
    }

    public ArrayList<ScheduledGame> getScheduledGames() {

        return new ArrayList<>(currentGames.values());
    }
}
