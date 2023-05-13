package ch.uzh.ifi.hase.soprafs23.repository;

import ch.uzh.ifi.hase.soprafs23.entity.game.SkipManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;

public class SkipRepository {
    private static final HashMap<Integer, SkipManager> skipRepo = new HashMap<>();

    public static void addGame(int gamePin){
        skipRepo.put(gamePin, new SkipManager());
    }
    public static SkipManager findByGameId(int gamePin) {
        SkipManager skipManager =skipRepo.get(gamePin);
        if (skipManager== null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This lobby does not exist!");
        }
        return skipManager;
    }

}
