package server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AccountService {

    private static final int BEST_COUNT = 10;

    private final @NotNull Map<String, Account> accounts;

    public AccountService() {
        accounts = new HashMap<>();
    }

    public @NotNull Account addAccount(
            @NotNull String login,
            @NotNull String password,
            @NotNull String email) {
        final Account account = new Account(login, password, email, this);
        accounts.put(login, account);
        return account;
    }

    public @Nullable Account find(int id) {

        for (Account account : accounts.values()) {

            if (account.getId() == id) {

                return account;
            }
        }

        return null;
    }

    public @Nullable Account find(String login) {
        return accounts.get(login);
    }

    public boolean has(String login) {
        return (this.find(login) != null);
    }

    public SortedSet<Account> getBest() {

        final NavigableSet<Account> bestPlayers = new TreeSet<>(accounts.values()).descendingSet();
        while (bestPlayers.size() > BEST_COUNT) {

            bestPlayers.remove(bestPlayers.last());
        }

        return bestPlayers;
    }

    public void updateKey(@NotNull String oldLogin, @NotNull Account account) {

        if (!account.getLogin().equals(oldLogin)) {

            accounts.remove(oldLogin);
            accounts.put(account.getLogin(), account);
        }
    }

}
