package websocket;

import database.AccountService;
import database.AccountServiceDb;
import database.WordService;
import database.WordServiceDb;
import entities.Dashes;
import entities.MultiplayerGame;
import entities.SingleplayerGame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import socketmessages.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class GameManagerService {

    public static final int SINGLEPLAYER_TIME_LIMIT = 60;
    public static final int SINGLEPLAYER_GAME_SCORE = 1;
    public static final int MULTIPLAYER_LOWER_GUESSERS_LIMIT = 1;
    public static final int MULTIPLAYER_UPPER_GUESSERS_LIMIT = 4;
    public static final int MULTIPLAYER_PLAYERS_LIMIT = MULTIPLAYER_UPPER_GUESSERS_LIMIT + 1;
    public static final int MULTIPLAYER_GAME_SCORE = 3;
    public static final int MULTIPLAYER_TIME_LIMIT = 120;
    public static final int QUEUE_REFRESH_TIME = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(GameManagerService.class);
    private static final AtomicInteger ANSWER_ID_GEN = new AtomicInteger(1);

    private final AccountService accountService;
    private final WordService wordService;

    //todo concurrent collections?
    private final GameRelationManager gameRelationManager = new GameRelationManager();
    private final LinkedHashMap<String, QueueRelation> queuedPlayers = new LinkedHashMap<>();

    private final SingleplayerScheduledGameManager singleplayerManager;
    private final MultiplayerScheduledGameManager multiplayerManager;

    @Autowired
    public GameManagerService(
        AccountServiceDb accountService,
        WordServiceDb wordService) {

        this.accountService = accountService;
        this.wordService = wordService;

        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);

        singleplayerManager = new SingleplayerScheduledGameManager(scheduler, gameRelationManager);
        multiplayerManager = new MultiplayerScheduledGameManager(scheduler, gameRelationManager);

        scheduler.scheduleAtFixedRate(
            new QueueManager()::checkQueue,
            QUEUE_REFRESH_TIME, QUEUE_REFRESH_TIME, TimeUnit.SECONDS);
    }

    private static final class QueueRelation {

        private final PlayerRole role;
        private final WebSocketSession session;

        QueueRelation(PlayerRole role, WebSocketSession session) {

            this.role = role;
            this.session = session;
        }

        public PlayerRole getRole() {
            return role;
        }

        public WebSocketSession getSession() {
            return session;
        }
    }

    private final class QueueManager {

        private volatile boolean running = false;

        private void checkQueue() {

            if (running) {
                return;
            }

            running = true;

            final ArrayList<String> possiblePainters = new ArrayList<>();
            possiblePainters.addAll(
                queuedPlayers.values().stream()
                    .filter(e -> e.getRole() != PlayerRole.GUESSER)
                    .map(e -> SessionOperator.getLogin(e.getSession()))
                    .collect(Collectors.toList()));

            final ArrayList<String> possibleGuessers = new ArrayList<>();
            possibleGuessers.addAll(
                queuedPlayers.values().stream()
                    .filter(e -> e.getRole() != PlayerRole.PAINTER)
                    .map(e -> SessionOperator.getLogin(e.getSession()))
                    .collect(Collectors.toList()));

            while (!possiblePainters.isEmpty()) {

                final String painterLogin = possiblePainters.get(0);
                final boolean canBeGuesser = possibleGuessers.remove(painterLogin);
                possiblePainters.remove(painterLogin);

                if (possibleGuessers.size() >= MULTIPLAYER_LOWER_GUESSERS_LIMIT) {

                    final ArrayList<String> guesserLogins = new ArrayList<>();
                    final int playersCount = Math.min(possibleGuessers.size(), MULTIPLAYER_UPPER_GUESSERS_LIMIT);
                    guesserLogins.addAll(possibleGuessers.subList(0, playersCount));
                    possibleGuessers.removeAll(guesserLogins);
                    final MultiplayerGame game = createMultiplayerGame(painterLogin, guesserLogins);
                    startTimer(game.getId(), GameType.MULTIPLAYER);

                } else {

                    if (canBeGuesser) {
                        possibleGuessers.add(painterLogin);
                    }

                    break;
                }
            }

            if (!possibleGuessers.isEmpty() && !multiplayerManager.getScheduledGames().isEmpty()) {
                distributeToAvailableGames(possibleGuessers);
            }

            running = false;
        }

        private void distributeToAvailableGames(ArrayList<String> guessers) {

            final ArrayList<MultiplayerGame> availableGames = new ArrayList<>(multiplayerManager.getScheduledGames().stream()
                .map(e -> (MultiplayerGame) e.getGame())
                .filter(e -> e.getUserLogins().size() < MULTIPLAYER_PLAYERS_LIMIT)
                .collect(Collectors.toList()));

            for (MultiplayerGame game : availableGames) {

                final int freeSpace = MULTIPLAYER_PLAYERS_LIMIT - game.getUserLogins().size();
                final ArrayList<String> playersToConnect = new ArrayList<>(guessers.subList(0, Math.min(guessers.size(), freeSpace)));
                guessers.removeAll(playersToConnect);

                final ScheduledGame<MultiplayerGame> scheduledGame = multiplayerManager.getScheduledGame(game.getId());
                if (scheduledGame == null) {
                    continue;
                }
                final ArrayList<Integer> availableIds = gameRelationManager.getAvailableIds(scheduledGame);
                final ArrayList<WebSocketSession> initialSessions = gameRelationManager.getGameSessions(scheduledGame);

                for (String player : playersToConnect) {

                    final WebSocketSession session = queuedPlayers.get(player).getSession();
                    final int playerId = availableIds.get(0);
                    availableGames.remove(0);

                    game.getUserLogins().add(player);
                    gameRelationManager.addGuesserRelation(
                        session, scheduledGame, playerId);

                    final WebSocketMessage<BaseGameContent> gameState = scheduledGame.getJoinGameMessage(player);
                    SessionOperator.sendMessage(session, gameState);
                }

                sendPlayersConnected(initialSessions, playersToConnect);
                playersToConnect.forEach(queuedPlayers::remove);
                if (guessers.isEmpty()) {
                    break;
                }
            }
        }
    }

    public SingleplayerGame createSingleplayerGame(WebSocketSession session) {

        clearData(session);
        final String login = SessionOperator.getLogin(session);
        final Dashes dashes = wordService.getRandomDashes(login);

        LOGGER.info("Got dashes #{}, {} for {}", dashes.getId(), dashes.getWord(), login);

        final SingleplayerGame game = new SingleplayerGame(login, dashes);
        final ScheduledGame scheduledGame = singleplayerManager.createScheduledGame(game);

        gameRelationManager.addGuesserRelation(session, scheduledGame, 1);
        return game;
    }

    public synchronized void queueForMultiplayerGame(WebSocketSession session, PlayerRole role) {

        clearData(session);
        final String login = SessionOperator.getLogin(session);
        queuedPlayers.put(login, new QueueRelation(role, session));
    }

    public void addPoint(WebSocketSession session, PicturePointContent point) {

        final String login = SessionOperator.getLogin(session);
        final ScheduledGame scheduledGame = getUserScheduledGame(login);

        if ((scheduledGame == null) || (scheduledGame.getType() == GameType.SINGLEPLAYER)) {
            LOGGER.warn("User {} tried to add point to a non-existent multiplayer game.", login);
            return;
        }

        final ArrayList<WebSocketSession> recieverSessions = gameRelationManager.getGameSessions(scheduledGame);
        recieverSessions.removeIf(e -> SessionOperator.getLogin(e).equals(login));
        scheduledGame.addPoint(point);
        recieverSessions.forEach(
            (WebSocketSession reciever) ->
                SessionOperator.sendMessage(
                    reciever,
                    new WebSocketMessage<>(
                        MessageType.NEW_POINT.toString(),
                        point)));
    }

    public void sendGameState(WebSocketSession session) {

        final String login = SessionOperator.getLogin(session);
        final ScheduledGame scheduledGame = getUserScheduledGame(login);

        if (scheduledGame == null) {
            LOGGER.warn("Tried to send state to user {} for a non-existent game.", login);
            return;
        }

        SessionOperator.sendMessage(
            session,
            scheduledGame.getGameStateMessage(login));
    }

    public void startTimer(int gameId, GameType gameType) {

        final ScheduledGame scheduledGame = getScheduledGame(gameId, gameType);

        if (scheduledGame == null) {
            LOGGER.warn("Tried to start timer for a {} game #{} that does not exist.", gameType.toString().toUpperCase(), gameId);
            return;
        }

        scheduledGame.rechedule(
            () -> scheduledGame.runLoseTask(GameResult.GAME_LOST),
            scheduledGame.getFinishTime());
        scheduledGame.cancelShutdown();

        final ArrayList<WebSocketSession> playerSessions = gameRelationManager.getGameSessions(scheduledGame);

        for (WebSocketSession session : playerSessions) {

            final String login = SessionOperator.getLogin(session);
            SessionOperator.sendMessage(session, scheduledGame.getJoinGameMessage(login));
        }

        scheduledGame.resumeShutdown();

        LOGGER.info("{} game #{} started, timer: {}.",
            gameType.toString().toUpperCase(), scheduledGame.getGame().getId(), scheduledGame.getTimeLeft());
    }

    public synchronized void checkAnswer(WebSocketSession session, @Nullable String word) {

        final String login = SessionOperator.getLogin(session);
        final ScheduledGame scheduledGame = getUserScheduledGame(login);

        if (scheduledGame == null) {
            LOGGER.warn("Got answer to a game that does not exist from user {}", login);
            return;
        }

        if (!scheduledGame.cancelShutdown()) {
            return;
        }

        LOGGER.debug("Time left: {}.", scheduledGame.getTimeLeft());

        final boolean answerCorrect = scheduledGame.getGame().isCorrectAnswer(word);
        final int playerNumber = gameRelationManager.getRelation(login).getPlayerNumber();
        final PlayerInfo senderInfo = new PlayerInfo(login, playerNumber);
        resendAnswer(gameRelationManager.getGameSessions(scheduledGame), word, answerCorrect, senderInfo);

        if (answerCorrect) {

            runWinTask(scheduledGame, login);

        } else {
            scheduledGame.resumeShutdown();
        }
    }

    public void clearData(WebSocketSession session) {

        final String login = SessionOperator.getLogin(session);
        final ScheduledGame scheduledGame = getUserScheduledGame(login);
        queuedPlayers.remove(login);

        if (scheduledGame != null) {

            final PlayerRole role = gameRelationManager.getRelation(login).getRole();
            gameRelationManager.removeRelation(login);
            final int gameId = scheduledGame.getGame().getId();

            if (scheduledGame.getType() == GameType.MULTIPLAYER) {

                if (role == PlayerRole.PAINTER) {

                    scheduledGame.runLoseTask(GameResult.PAINTER_LEFT);
                } else {

                    //noinspection unchecked
                    disconnectPlayer(scheduledGame, login);
                }
            }

            if (scheduledGame.getType() == GameType.SINGLEPLAYER) {

                singleplayerManager.removeScheduledGame(gameId);
            }
        }
    }

    public void addAnswerVote(WebSocketSession session, int answerId, boolean isPositive) {

        final String login = SessionOperator.getLogin(session);
        final ScheduledGame scheduledGame = getUserScheduledGame(login);

        if (scheduledGame == null) {
            LOGGER.warn("User {} tried to add answer vote for a non-existent game.", login);
            return;
        }

        final ArrayList<WebSocketSession> sessions = gameRelationManager.getGameSessions(scheduledGame);
        sessions.remove(session);
        final AnswerVoteContent vote = new AnswerVoteContent(answerId, isPositive);

        sessions.forEach(
            (WebSocketSession reciever) ->
                SessionOperator.sendMessage(
                    reciever,
                    new WebSocketMessage<>(
                        MessageType.NEW_VOTE.toString(),
                        vote)));
    }

    private void sendPlayersConnected(ArrayList<WebSocketSession> initialPlayers, ArrayList<String> connectedPlayers) {

        final ArrayList<PlayerInfo> playerInfos = new ArrayList<>(
            connectedPlayers.stream()
                .map(e -> new PlayerInfo(e, gameRelationManager.getRelation(e).getPlayerNumber()))
                .collect(Collectors.toList()));

        final PlayerConnectContent playerConnectContent = new PlayerConnectContent(playerInfos);

        initialPlayers.forEach(
            (WebSocketSession reciever) ->
                SessionOperator.sendMessage(
                    reciever,
                    new WebSocketMessage<>(
                        MessageType.PLAYERS_CONNECT.toString(),
                        playerConnectContent)));
    }

    private void disconnectPlayer(@NotNull ScheduledGame<MultiplayerGame> scheduledGame, String login) {

        final MultiplayerGame game = scheduledGame.getGame();
        final int gameId = game.getId();
        game.getUserLogins().remove(login);

        if (game.getUserLogins().isEmpty()) {
            multiplayerManager.removeScheduledGame(gameId);
        } else {

            final ArrayList<WebSocketSession> sessions = gameRelationManager.getGameSessions(scheduledGame);
            final PlayerDisconnectContent playerDisconnectContent = new PlayerDisconnectContent(login);

            sessions.forEach(
                (WebSocketSession reciever) ->
                    SessionOperator.sendMessage(
                        reciever,
                        new WebSocketMessage<>(
                            MessageType.PLAYER_DISCONNECT.toString(),
                            playerDisconnectContent)));
        }
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private MultiplayerGame createMultiplayerGame(String painterLogin, ArrayList<String> guesserLogins) {

        final ArrayList<String> players = guesserLogins;
        players.add(painterLogin);
        final String word = wordService.getRandomWord();

        final MultiplayerGame game = new MultiplayerGame(word, players);
        final ScheduledGame scheduledGame = multiplayerManager.createScheduledGame(game);
        LOGGER.info("Got word {} for multiplayer game #{}", word, game.getId());

        guesserLogins.forEach(
            (String login) ->
                gameRelationManager.addGuesserRelation(
                    queuedPlayers.get(login).getSession(),
                    scheduledGame,
                    guesserLogins.indexOf(login) + 1));

        gameRelationManager.addPainterRelation(
            queuedPlayers.get(painterLogin).getSession(),
            scheduledGame,
            guesserLogins.size());

        players.forEach(queuedPlayers::remove);
        return game;
    }

    private void runWinTask(ScheduledGame scheduledGame, String winnerLogin) {

        accountService.updateAccountRating(winnerLogin, scheduledGame.getWinScore());
        if (scheduledGame instanceof SingleplayerScheduledGameManager.SingleplayerScheduledGame) {
            wordService.addUsedDashes(winnerLogin, ((SingleplayerGame) scheduledGame.getGame()).getDashes().getId());
        }
        scheduledGame.runWinTask(winnerLogin);
    }

    private void resendAnswer(ArrayList<WebSocketSession> sessions, @Nullable String answer, boolean answerCorrect, PlayerInfo senderInfo) {

        final int answerId = ANSWER_ID_GEN.getAndIncrement();

        for (WebSocketSession session : sessions) {

            final WebSocketMessage data = new WebSocketMessage<>(
                MessageType.CHECK_ANSWER.toString(), new AnswerResponseContent(answerId, answer, answerCorrect, senderInfo));
            SessionOperator.sendMessage(session, data);
        }
    }

    private @Nullable ScheduledGame getScheduledGame(int gameId, GameType gameType) {

        return (gameType == GameType.SINGLEPLAYER) ?
            singleplayerManager.getScheduledGame(gameId) :
            multiplayerManager.getScheduledGame(gameId);
    }

    private @Nullable ScheduledGame getUserScheduledGame(String login) {

        final GameRelationManager.GameRelation gameRelation = gameRelationManager.getRelation(login);
        if (gameRelation == null) {
            return null;
        }

        final int gameId = gameRelation.getGameId();

        return (gameRelation.getType() == GameType.SINGLEPLAYER) ?
            singleplayerManager.getScheduledGame(gameId) :
            multiplayerManager.getScheduledGame(gameId);
    }
}
