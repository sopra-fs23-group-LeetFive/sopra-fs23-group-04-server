package ch.uzh.ifi.hase.soprafs23.helper;

import ch.uzh.ifi.hase.soprafs23.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class UserHelper {

    private UserHelper() {}

    public static void checkIfUserExists(User user) {

        String errorMessage = "User does not exist. " +
                "Please register before playing!";

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    String.format(errorMessage));
        }
    }
    public static void checkIfQuoteValid(String quote){
        if (quote ==null || quote.length()>255){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The quote you are trying to add is either to long or too short ");
        }
    }
}
