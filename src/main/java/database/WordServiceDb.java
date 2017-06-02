package database;

import entities.Dashes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;

@Service
public class WordServiceDb implements WordService {

    private static final String LOGIN_PARAM = "plogin";
    private static final String ID_PARAM = "pid";
    private static final String WORD_PARAM = "pword";

    private static final Random RANDOM = new Random();
    private static final DashRowMapper DASH_MAPPER = new DashRowMapper();

    private final NamedParameterJdbcTemplate database;

    public WordServiceDb(NamedParameterJdbcTemplate database) {
        this.database = database;
    }

    private static class DashRowMapper implements RowMapper<Dashes> {

        @Override
        public Dashes mapRow(ResultSet resultSet, int i) throws SQLException {

            return new Dashes(
                resultSet.getInt("id"),
                resultSet.getString("word"),
                resultSet.getString("points"));
        }
    }

    @Override
    public boolean checkWord(@Nullable String word, int dashesId) {

        final MapSqlParameterSource source = new MapSqlParameterSource();
        source.addValue(ID_PARAM, dashesId);
        source.addValue(WORD_PARAM, word);

        final String checkWordSql = String.format(
            " SELECT lower( :%1$s ) = lower( dashes.word ) FROM dashes" +
                " WHERE id = :%2$s",
            WORD_PARAM, ID_PARAM);

        return database.queryForObject(checkWordSql, source, Boolean.class);
    }

    @Override
    public void addUsedDashes(@NotNull String login, int dashesId) throws DataRetrievalFailureException {

        final MapSqlParameterSource source = new MapSqlParameterSource();
        source.addValue(ID_PARAM, dashesId);
        source.addValue(LOGIN_PARAM, login);

        final String insertDashesRecordText = String.format(
            " INSERT INTO account_dashes ( accountid, dashesid )" +
                " SELECT account.id, :%1$s from account" +
                " WHERE login = :%2$s",
            ID_PARAM, LOGIN_PARAM);

        final int insertsCount = database.update(insertDashesRecordText, source);
        if (insertsCount != 1) {
            throw new DataRetrievalFailureException("used words insertion error");
        }
    }

    @Override
    public @NotNull Dashes getRandomDashes(@NotNull String login) throws DataRetrievalFailureException {

        if (checkAllWordsUsed(login)) {
            removeUsedWords(login);
        }

        final MapSqlParameterSource source = new MapSqlParameterSource();
        source.addValue(LOGIN_PARAM, login);

        final String selectDashSql = String.format(
            " SELECT * FROM dashes" +
                " WHERE dashes.id NOT IN (" +
                " SELECT dashesid FROM account_dashes" +
                " JOIN account ON account.login = :%1$s AND account.id = accountid )",
            LOGIN_PARAM);

        final List<Dashes> result = database.query(selectDashSql, source, DASH_MAPPER);
        if (result.isEmpty()) {
            throw new DataRetrievalFailureException("dashes retrieval error");
        }

        return result.get(RANDOM.nextInt(result.size()));
    }

    @Override
    public @NotNull Dashes getRandomDashes() throws DataRetrievalFailureException {

        final MapSqlParameterSource source = new MapSqlParameterSource();
        final String selectDashSql = " SELECT * FROM dashes";

        final List<Dashes> result = database.query(selectDashSql, source, DASH_MAPPER);
        if (result.isEmpty()) {
            throw new DataRetrievalFailureException("dashes retrieval error");
        }

        return result.get(RANDOM.nextInt(result.size()));
    }

    @Override
    public @NotNull String getRandomWord() throws DataRetrievalFailureException {

        final MapSqlParameterSource source = new MapSqlParameterSource();
        final String selectWordSql =
            " SELECT word.word FROM word " +
                " OFFSET floor(random() * ( SELECT count(*) FROM word )) " +
                " LIMIT 1;";

        final List<String> result = database.queryForList(selectWordSql, source, String.class);
        if (result.isEmpty()) {
            throw new DataRetrievalFailureException("word retrieval error");
        }

        return result.get(0);
    }

    private boolean checkAllWordsUsed(@NotNull String login) {

        final MapSqlParameterSource source = new MapSqlParameterSource();
        source.addValue(LOGIN_PARAM, login);

        final String checkWordsSql = String.format(
            " SELECT count(*) FROM dashes" +
                " WHERE dashes.id NOT IN (" +
                " SELECT dashesid FROM account_dashes" +
                " JOIN account ON account.login = :%1$s AND account.id = accountid )",
            LOGIN_PARAM);

        final int result = database.queryForObject(checkWordsSql, source, Integer.class);
        return result == 0;
    }

    private void removeUsedWords(@NotNull String login) {

        final MapSqlParameterSource source = new MapSqlParameterSource();
        source.addValue(LOGIN_PARAM, login);

        final String checkWordsSql = String.format(
            " DELETE FROM account_dashes USING account" +
                " WHERE account.login = :%1$s",
            LOGIN_PARAM);

        database.update(checkWordsSql, source);
    }
}
