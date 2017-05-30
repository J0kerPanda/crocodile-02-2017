package websocket;

import database.MultiplayerGamesService;
import entities.MultiplayerGame;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.socket.WebSocketSession;
import socketmessages.*;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

public class MultiplayerScheduledGameManager extends ScheduledGameManager<MultiplayerGame> {

    private final MultiplayerGamesService multiplayerGamesService;
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
        WebSocketMessage<BaseGameContent> getGameStateMessage(@NotNull String login) {

            final MessageType messageType = MessageType.STATE;

            final MultiplayerGame multiplayerGame = getGame();
            final ArrayList<PlayerInfo> playerInfos = new ArrayList<>(
                multiplayerGame.getUserLogins().stream()
                    .map(e -> new PlayerInfo(e, gameRelationManager.getRelation(e).getPlayerNumber()))
                    .collect(Collectors.toList()));

            return new WebSocketMessage<>(
                messageType.toString(),
                new MultiplayerGameStateContent(
                    getTimeLeft(),
                    GameManagerService.MULTIPLAYER_TIME_LIMIT,
                    gameRelationManager.getRelation(login).getRole(),
                    playerInfos,
                    multiplayerGame.getWord()));
        }

        @NotNull
        @Override
        WebSocketMessage<BaseGameContent> getJoinGameMessage(@NotNull String login) {

            final MultiplayerGame multiplayerGame = getGame();
            final MessageType messageType = MessageType.START_MULTIPLAYER_GAME;
            final ArrayList<PlayerInfo> playerInfos = new ArrayList<>(
                multiplayerGame.getUserLogins().stream()
                    .map(e -> new PlayerInfo(e, gameRelationManager.getRelation(e).getPlayerNumber()))
                    .collect(Collectors.toList()));

            return new WebSocketMessage<>(
                messageType.toString(),
                new MultiplayerGameStateContent(
                    getTimeLeft(),
                    GameManagerService.MULTIPLAYER_TIME_LIMIT,
                    gameRelationManager.getRelation(login).getRole(),
                    playerInfos,
                    multiplayerGame.getWord()));
        }

        private synchronized void endMultiplayerGame(GameResult gameResult, @NotNull String winnerLogin) {

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

                losers.forEach(
                    (WebSocketSession session) -> {

                        SessionOperator.sendMessage(
                            session, new WebSocketMessage<>(
                                MessageType.STOP_GAME.toString(),
                                new FinishGameContent(
                                    GameResult.GAME_LOST, 0,
                                    winnerLogin, word)));

                        gameRelationManager.removeRelation(SessionOperator.getLogin(session));
                    });

                currentGames.remove(gameId);
                multiplayerGamesService.shutdownGame(gameId);
                LOGGER.info("Multiplayer game #{} ended with result {}.", gameId, gameResult.asInt());
            }
        }

        @Override
        synchronized void runWinTask(@NotNull String winnerLogin) {

            endMultiplayerGame(GameResult.GAME_WON, winnerLogin);
        }

        @Override
        synchronized void runLoseTask() {

            endMultiplayerGame(GameResult.GAME_LOST, "");
        }

        @Override
        int getWinScore() {
            return GameManagerService.MULTIPLAYER_GAME_SCORE;
        }

        @Override
        int getFinishTime() {
            return GameManagerService.MULTIPLAYER_TIME_LIMIT;
        }
    }

    public MultiplayerScheduledGameManager(
        ScheduledExecutorService scheduler,
        MultiplayerGamesService service,
        GameRelationManager gameRelationManager) {

        super(scheduler, new ConcurrentHashMap<>());
        this.multiplayerGamesService = service;
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
