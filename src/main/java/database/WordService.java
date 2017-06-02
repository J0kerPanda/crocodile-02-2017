package database;

import entities.Dashes;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataAccessException;

public interface WordService {

    boolean checkWord(@NotNull String word, int dashesId);

    void addUsedDashes(@NotNull String login, int dashesId) throws DataAccessException;

    @NotNull Dashes getRandomDashes(@NotNull String login) throws DataAccessException;

    @NotNull Dashes getRandomDashes() throws DataAccessException;

    @NotNull String getRandomWord() throws DataAccessException;
}
