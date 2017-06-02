package websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import database.AccountService;
import database.AccountServiceDb;
import entities.SingleplayerGame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import socketmessages.*;

import javax.naming.AuthenticationException;
import java.io.IOException;

public class GameSocketHandler extends TextWebSocketHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(GameSocketHandler.class);

    private final GameManagerService gameManagerService;
    private final AccountService accountService;
    private final WebSocketMessageHandler webSocketMessageHandler;

    public GameSocketHandler(
        GameManagerService gameManagerService,
        AccountServiceDb accountService,
        WebSocketMessageHandler webSocketMessageHandler) {

        this.gameManagerService = gameManagerService;
        this.accountService = accountService;
        this.webSocketMessageHandler = webSocketMessageHandler;

        webSocketMessageHandler.setHandler(
            MessageType.START_SINGLEPLAYER_GAME,
            (WebSocketSession s, TextMessage m) -> handleStartSingleplayerGame(s));

        webSocketMessageHandler.setHandler(
            MessageType.START_MULTIPLAYER_GAME,
            (WebSocketSession s, TextMessage m) -> handleStartMultiplayerGame(s));

        webSocketMessageHandler.setHandler(
            MessageType.CHECK_ANSWER,
            this::handleCheckAnswer);

        webSocketMessageHandler.setHandler(
            MessageType.NEW_POINT,
            this::handleAddPoint);

        webSocketMessageHandler.setHandler(
            MessageType.VOTE_ANSWER,
            this::handleAddVote);

        webSocketMessageHandler.setHandler(
            MessageType.GET_STATE,
            (WebSocketSession s, TextMessage m) -> handleGetState(s));

        webSocketMessageHandler.setHandler(
            MessageType.CLEAR,
            (WebSocketSession s, TextMessage m) -> handleClearCanvas(s));


        webSocketMessageHandler.setHandler(
            MessageType.EXIT_GAME,
            (WebSocketSession s, TextMessage m) -> handleExitGame(s));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws AuthenticationException, JsonProcessingException {

        final String login = SessionOperator.getLogin(session);

        if (accountService.findAccount(login) == null) {

            LOGGER.debug("Unlogged user tried to start the game.");
            throw new AuthenticationException("only logged users are allowed to play the game");
        }

        LOGGER.info("Got websocket connection from user {}.", login);
        gameManagerService.clearData(session);
    }

    @Override
    @SuppressWarnings("OverlyBroadCatchBlock")
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {

        try {
            final WebSocketMessage message = readMessage(textMessage, EmptyContent.class);

            if (message.getTypeEnum() != MessageType.UPDATE) {

                LOGGER.info("Got websocket message type {} from user {}.",
                    message.getTypeString(), SessionOperator.getLogin(session));

                webSocketMessageHandler.handle(session, textMessage, message.getTypeEnum());
            }

        } catch (Exception exception) {

            LOGGER.error("{}", exception.getCause());
            throw exception;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {

        LOGGER.info("Websocket connection with user {} closed with reason {}.",
            SessionOperator.getLogin(session),
            status.getReason());

        gameManagerService.clearData(session);
        super.afterConnectionClosed(session, status);
    }

    private void handleStartSingleplayerGame(WebSocketSession session) throws DataAccessException {

        final SingleplayerGame game = gameManagerService.createSingleplayerGame(session);
        gameManagerService.startTimer(game.getId(), GameType.SINGLEPLAYER);
    }

    private void handleStartMultiplayerGame(WebSocketSession session) {

        gameManagerService.queueForMultiplayerGame(session, PlayerRole.ANYONE);
    }

    private void handleCheckAnswer(WebSocketSession session, TextMessage textMessage) throws IOException {

        final String login = SessionOperator.getLogin(session);

        final AnswerContent answerContent = (AnswerContent) readMessage(textMessage, AnswerContent.class).getContent();
        LOGGER.info("Got answer {} from user {}", answerContent.getWord(), login);

        gameManagerService.checkAnswer(session, answerContent.getWord());
    }

    private void handleAddPoint(WebSocketSession session, TextMessage textMessage) throws IOException {

        final PicturePointContent point = (PicturePointContent) readMessage(textMessage, PicturePointContent.class).getContent();
        gameManagerService.addPoint(session, point);
    }

    private void handleAddVote(WebSocketSession session, TextMessage textMessage) throws IOException {

        final AnswerVoteContent vote = (AnswerVoteContent) readMessage(textMessage, AnswerVoteContent.class).getContent();
        gameManagerService.addAnswerVote(session, vote.getId(), vote.isVotePositive());
    }

    private void handleGetState(WebSocketSession session) {

        gameManagerService.sendGameState(session);
    }

    private void handleClearCanvas(WebSocketSession session) {

        gameManagerService.clearCanvas(session);
    }

    private void handleExitGame(WebSocketSession session) {

        gameManagerService.clearData(session);
    }

    @SuppressWarnings("OverlyBroadThrowsClause")
    private WebSocketMessage<?> readMessage(TextMessage textMessage, Class contentClass) throws IOException {

        final JavaType type = OBJECT_MAPPER.getTypeFactory().constructParametricType(WebSocketMessage.class, contentClass);

        return OBJECT_MAPPER.
            readValue(textMessage.getPayload(), type);
    }
}
