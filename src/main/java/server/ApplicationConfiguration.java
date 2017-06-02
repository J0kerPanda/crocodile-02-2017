package server;

import database.AccountServiceDb;
import database.WordServiceDb;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.handler.PerConnectionWebSocketHandler;
import websocket.GameManagerService;
import websocket.GameSocketHandler;
import websocket.WebSocketMessageHandler;

@SuppressWarnings("SpringJavaAutowiringInspection")
@Configuration
@EnableAsync
public class ApplicationConfiguration {

    @Bean
    public AccountServiceDb accountService(NamedParameterJdbcTemplate database) {
        return new AccountServiceDb(database);
    }

    @Bean
    public WordServiceDb dashesService(NamedParameterJdbcTemplate database) {
        return new WordServiceDb(database);
    }

    @Bean
    public WebSocketMessageHandler webSocketMessageHandler() {
        return new WebSocketMessageHandler();
    }

    @Bean
    public GameManagerService gameManagerService(AccountServiceDb accountServiceDb, WordServiceDb wordService) {
        return new GameManagerService(accountServiceDb, wordService);
    }

    @Bean
    public WebSocketHandler gameWebSocketHandler() {
        return new PerConnectionWebSocketHandler(GameSocketHandler.class);
    }
}

