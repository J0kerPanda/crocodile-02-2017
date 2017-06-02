package websocket;

import entities.SingleplayerGame;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.socket.WebSocketSession;
import socketmessages.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

public class SingleplayerScheduledGameManager extends ScheduledGameManager<SingleplayerGame> {

    private final GameRelationManager gameRelationManager;

    public final class SingleplayerScheduledGame extends ScheduledGame<SingleplayerGame> {

        private SingleplayerScheduledGame(ScheduledExecutorService scheduler, SingleplayerGame game) {
            super(scheduler, game);
        }

        @Override
        GameType getType() {
            return GameType.SINGLEPLAYER;
        }

        @Override
        public @NotNull WebSocketMessage<BaseGameContent> getGameStateMessage(@NotNull String login) {

            return getGameMessage(MessageType.STATE);
        }

        @Override
        public @NotNull WebSocketMessage<BaseGameContent> getJoinGameMessage(@NotNull String login) {

            return getGameMessage(MessageType.START_SINGLEPLAYER_GAME);
        }

        private WebSocketMessage<BaseGameContent> getGameMessage(MessageType messageType) {

            return new WebSocketMessage<>(
                messageType.toString(),
                new SingleplayerGameStateContent(
                    game.getDashes(),
                    this.getTimeLeft()));
        }

        private synchronized void endSingleplayerGame(GameResult gameResult) {

            final int gameId = this.getGame().getId();

            if (currentGames.containsKey(gameId)) {

                cancelAll();

                final WebSocketSession session = gameRelationManager.getGameSessions(this).get(0);
                SessionOperator.sendMessage(session, new WebSocketMessage<>(
                    MessageType.STOP_GAME.toString(),
                    new FinishGameContent(
                        gameResult, getWinScore(),
                        SessionOperator.getLogin(session),
                        getGame().getWord())));

                currentGames.remove(gameId);
                gameRelationManager.removeRelation(SessionOperator.getLogin(session));
                LOGGER.info("Singleplayer game #{} ended with result {}.", gameId, gameResult.asInt());
            }
        }


        @Override
        public synchronized void runWinTask(@NotNull String winnerLogin) {

            endSingleplayerGame(GameResult.GAME_WON);
        }

        @Override
        public synchronized void runLoseTask(@NotNull GameResult result) {

            endSingleplayerGame(GameResult.GAME_LOST);
        }

        @Override
        public int getWinScore() {
            return GameManagerService.SINGLEPLAYER_GAME_SCORE;
        }

        @Override
        public int getFinishTime() {
            return GameManagerService.SINGLEPLAYER_TIME_LIMIT;
        }
    }

    public SingleplayerScheduledGameManager(
        ScheduledExecutorService scheduler,
        GameRelationManager gameRelationManager) {

        super(scheduler, new ConcurrentHashMap<>());
        this.gameRelationManager = gameRelationManager;
    }

    @Override
    public @NotNull SingleplayerScheduledGame createScheduledGame(SingleplayerGame game) {

        final SingleplayerScheduledGame scheduledGame = new SingleplayerScheduledGame(scheduler, game);
        currentGames.put(game.getId(), scheduledGame);
        return scheduledGame;
    }
}
