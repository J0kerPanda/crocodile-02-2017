package websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import socketmessages.MessageType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;

@Service
public class WebSocketMessageHandler {

    public static final ArrayList<MessageType> IGNORE_LOGGING_MSGS = new ArrayList<>(Arrays.asList(MessageType.UPDATE, MessageType.NEW_POINT));
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketMessageHandler.class);

    private final EnumMap<MessageType, MessageHandler> handlers = new EnumMap<>(MessageType.class);

    public void setHandler(MessageType messageType, MessageHandler handler) {

        handlers.put(messageType, handler);
    }

    public void handle(WebSocketSession session, TextMessage textMessage, MessageType messageType) throws Exception {

        final MessageHandler handler = handlers.get(messageType);

        if (handler != null) {

            handler.handle(session, textMessage);

        } else {

            if (!IGNORE_LOGGING_MSGS.contains(messageType)) {
                LOGGER.warn("Handler for websocket message of type {} does not exist.", messageType.toString());
            }
        }
    }
}
