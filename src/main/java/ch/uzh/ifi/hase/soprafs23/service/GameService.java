package ch.uzh.ifi.hase.soprafs23.service;

import ch.uzh.ifi.hase.soprafs23.constant.*;
import ch.uzh.ifi.hase.soprafs23.entity.game.*;
import ch.uzh.ifi.hase.soprafs23.entity.User;
import ch.uzh.ifi.hase.soprafs23.helper.GameHelper;
import ch.uzh.ifi.hase.soprafs23.repository.*;
import ch.uzh.ifi.hase.soprafs23.rest.dto.game.LeaderboardGetDTO;
import ch.uzh.ifi.hase.soprafs23.rest.dto.game.ScoreboardGetDTO;
import ch.uzh.ifi.hase.soprafs23.rest.dto.game.WinnerGetDTO;
import ch.uzh.ifi.hase.soprafs23.rest.dto.user.GameCategoriesDTO;
import ch.uzh.ifi.hase.soprafs23.websocketDto.GameUsersDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

import static ch.uzh.ifi.hase.soprafs23.helper.GameHelper.*;
import static ch.uzh.ifi.hase.soprafs23.helper.UserHelper.*;

@Service
@Transactional
public class GameService {

    private final Logger logger = LoggerFactory.getLogger(GameService.class);
    private final Random rand = new Random();
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final RoundRepository roundRepository;
    private final AnswerRepository answerRepository;
    private final VoteRepository voteRepository;
    private final RoundService roundService;
    private final ScoreCalculationService scoreCalculationService;
    private final WebSocketService webSocketService;

    @Autowired
    public GameService(@Qualifier("gameRepository") GameRepository gameRepository,
                       @Qualifier("roundRepository") RoundRepository roundRepository,
                       @Qualifier("answerRepository") AnswerRepository answerRepository,
                       @Qualifier("userRepository") UserRepository userRepository,
                       @Qualifier("voteRepository")VoteRepository voteRepository,
                       RoundService roundService,
                       ScoreCalculationService scoreCalculationService,
                       WebSocketService webSocketService) {
        this.gameRepository = gameRepository;
        this.roundRepository = roundRepository;
        this.answerRepository = answerRepository;
        this.userRepository = userRepository;
        this.voteRepository = voteRepository;

        this.roundService = roundService;
        this.scoreCalculationService = scoreCalculationService;

        this.webSocketService = webSocketService;
    }

    public Game createAndReturnGame(Game newGame, String userToken) {

        User user = getUserByToken(userToken);

        checkIfUserExists(user);

        checkIfHostIsEligible(user.getId());

        GameHelper.checkIfNotToManyCategories(newGame);

        newGame.setGamePin(generateUniqueGamePin());
        newGame.setStatus(GameStatus.OPEN);
        newGame.setHostId(user.getId());
        newGame.addPlayer(user);
        newGame.setRoundLetters(generateRandomLetters(newGame.getRounds()));
        newGame.setCurrentRound(0);
        newGame.setNumberOfCategories(newGame.getCategories().size());

        newGame = gameRepository.save(newGame);
        gameRepository.flush();

        roundService.createAllRounds(newGame);

        logger.debug("Created following game: {}", newGame);

        return newGame;
    }

    public void joinGame(int gamePin, String userToken) {

        User user = getUserByToken(userToken);

        checkIfUserExists(user);

        checkIfUserCanJoin(user.getId());

        Game gameToJoin = gameRepository.findByGamePin(gamePin);

        checkIfGameExists(gameToJoin);
        checkIfGameIsOpen(gameToJoin);

        gameToJoin.addPlayer(user);

        GameUsersDTO gameUsersDTO = getHostAndAllUserNamesOfGame(gameToJoin);

        webSocketService.sendMessageToClients(Constant.DEFAULT_DESTINATION + gamePin, gameUsersDTO);
    }


    public void leaveGame(int gamePin, String userToken) {

        User user = getUserByToken(userToken);

        checkIfUserExists(user);

        Game game = gameRepository.findByGamePin(gamePin);

        checkIfGameExists(game);

        checkIfUserIsInGame(game, user);

        Boolean userIsHost = checkIfUserIsHost(user, game);

        game.removePlayer(user);

        Boolean gameHasUsers = checkIfGameHasUsers(game);

        GameUsersDTO gameUsersDTO = new GameUsersDTO();

        if (Boolean.FALSE.equals(gameHasUsers)) {
            game.setStatus(GameStatus.CLOSED);
            gameRepository.save(game); // update the game status to CLOSED
        } else {
            if (Boolean.TRUE.equals(userIsHost)) {
                setNewHost(game);
            }
            gameUsersDTO = getHostAndAllUserNamesOfGame(game);
        }

        try {
            checkIfGameExists(getGameByGamePin(gamePin));
            webSocketService.sendMessageToClients(Constant.DEFAULT_DESTINATION + gamePin, gameUsersDTO);
        }
        catch (ResponseStatusException ignored) {
            logger.debug("Something went wrong while leaving the game.");
        }
    }

    public void setUpGameForStart(int gamePin){

        Game game = gameRepository.findByGamePin(gamePin);
        checkIfGameExists(game);
        checkIfGameIsOpen(game);

        game.setStatus(GameStatus.RUNNING);

        gameRepository.saveAndFlush(game);

    }

    public static GameCategoriesDTO getStandardCategories() {

        GameCategoriesDTO gameCategoriesDTO = new GameCategoriesDTO();
        gameCategoriesDTO.setCategories(GameCategory.getCategories());

        return gameCategoriesDTO;
    }

    public GameCategoriesDTO getGameCategoriesByGamePin(int gamePin) {
        Game game = getGameByGamePin(gamePin);

        List<String> gameCategoryNames = getCategoryNamesByGame(game);

        GameCategoriesDTO gameCategoriesDTO = new GameCategoriesDTO();
        gameCategoriesDTO.setCategories(gameCategoryNames);

        return gameCategoriesDTO;
    }

    public GameUsersDTO getGameUsersByGamePin(int gamePin) {

        Game game = getGameByGamePin(gamePin);

        return getHostAndAllUserNamesOfGame(game);

    }

    public Game getGameByGamePin(int gamePin) {

        Game game = gameRepository.findByGamePin(gamePin);

        checkIfGameExists(game);

        return game;
    }

    public static List<String> getCategoryNamesByGame(Game game) {
        List<Category> gameCategories = game.getCategories();

        List<String> gameCategoryNames = new ArrayList<>();

        for (Category gameCategory : gameCategories) {
            gameCategoryNames.add(gameCategory.getName());
        }
        return gameCategoryNames;
    }

    private List<Game> getOpenGames() {
        return gameRepository.findByStatus(GameStatus.OPEN);
    }

    private List<Game> getRunningGames() {

        return gameRepository.findByStatus(GameStatus.RUNNING);
    }

    private List<Game> getOpenOrRunningGames() {

        List<Game> openOrRunningGames = getOpenGames();
        openOrRunningGames.addAll(getRunningGames());

        return openOrRunningGames;
    }

    private static List<Integer> getGameUsersId(Game game) {
        List<Integer> usersId = new ArrayList<>();
        for (User user : game.getUsers()) {
            usersId.add(user.getId());
        }
        return usersId;
    }

    private void checkIfHostIsEligible(int hostId) {
        List<Game> openOrRunningGames = getOpenOrRunningGames();

        String errorMessage = "You are already part of a game." +
                "You cannot host another game!";
        for (Game game : openOrRunningGames) {
            List<Integer> userIds = getGameUsersId(game);
            if (userIds.contains(hostId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        String.format(errorMessage));
            }
        }
    }

    private static List<Character> generateRandomLetters(int numberOfRounds){
        List<Character> letters = new ArrayList<>();

        for (char letter = 'A'; letter <= 'Z'; letter++) {
            letters.add(letter);
        }

        Collections.shuffle(letters);

        return letters.subList(0, numberOfRounds);
    }

    private void checkIfUserCanJoin(int userId) {

        List<Game> openOrRunningGames = getOpenOrRunningGames();

        String errorMessage = "You are already part of a game." +
                "You cannot join another game!";

        for (Game game : openOrRunningGames) {
            List<Integer> userIds = getGameUsersId(game);
            if (userIds.contains(userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        String.format(errorMessage));
            }
        }
    }

    private Boolean checkIfUserIsHost(User user, Game game) {
        int hostId = game.getHostId();

        return hostId == user.getId();
    }

    private Boolean checkIfGameHasUsers(Game game) {
        List<User> users = game.getUsers();
        return !users.isEmpty();
    }


    private void setNewHost(Game game) {
        List<User> users = game.getUsers();
        // Check if there are users left in the game.
        if (!users.isEmpty()) {
            User hostCandidate = users.get(rand.nextInt(users.size()));
            game.setHostId(hostCandidate.getId());
        }
    }


    private GameUsersDTO getHostAndAllUserNamesOfGame(Game gameToJoin) {

        User host = userRepository.findById(gameToJoin.getHostId()).orElse(null);

        List<User> users = gameToJoin.getUsers();
        List<String> usernames = new ArrayList<>();

        for (User user : users) {
            if (!user.equals(host)) {
                usernames.add(user.getUsername());
            }
        }
        GameUsersDTO gameUsersDTO = new GameUsersDTO();

        checkIfUserExists(host);

        gameUsersDTO.setHostUsername(host.getUsername());
        gameUsersDTO.setUsernames(usernames);

        return gameUsersDTO;
    }

    private User getUserByToken(String userToken) {
        return userRepository.findByToken(userToken);
    }


    private int generateUniqueGamePin() {
        int newGamePin = generateGamePin();
        Game game = gameRepository.findByGamePin(newGamePin);
        while (game != null) {
            newGamePin = generateGamePin();
            game = gameRepository.findByGamePin(newGamePin);
        }
        return newGamePin;
    }

    private int generateGamePin() {

        return rand.nextInt(9000) + 1000;
    }



    public List<WinnerGetDTO> getWinner(int gamePin) {
        Map<User, Integer> userScores = scoreCalculationService.calculateUserScores(gamePin);

        List<WinnerGetDTO> winners = new ArrayList<>();
        int maxScore = -1;
        for (Map.Entry<User, Integer> entry : userScores.entrySet()) {
            if (entry.getValue() > maxScore) {
                winners.clear();
                WinnerGetDTO winnerGetDTO = new WinnerGetDTO();
                winnerGetDTO.setUsername(entry.getKey().getUsername());
                winnerGetDTO.setScore(entry.getValue());
                winnerGetDTO.setQuote(entry.getKey().getQuote());
                winners.add(winnerGetDTO);
                maxScore = entry.getValue();
            } else if (entry.getValue() == maxScore) {
                WinnerGetDTO winnerGetDTO = new WinnerGetDTO();
                winnerGetDTO.setUsername(entry.getKey().getUsername());
                winnerGetDTO.setScore(entry.getValue());
                winnerGetDTO.setQuote(entry.getKey().getQuote());
                winners.add(winnerGetDTO);
            }
        }
        return winners;
    }

    public List<ScoreboardGetDTO> getScoreboard(int gameId) {
        Map<User, Integer> userScores = scoreCalculationService.calculateUserScores(gameId);

        List<ScoreboardGetDTO> scoreboard = new ArrayList<>();
        for (Map.Entry<User, Integer> entry : userScores.entrySet()) {
            ScoreboardGetDTO scoreboardGetDTO = new ScoreboardGetDTO();
            scoreboardGetDTO.setUsername(entry.getKey().getUsername());
            scoreboardGetDTO.setScore(entry.getValue());
            scoreboard.add(scoreboardGetDTO);
        }

        // Sort the scoreboard in descending order of score
        scoreboard.sort((dto1, dto2) -> dto2.getScore() - dto1.getScore());

        return scoreboard;
    }

    public List<LeaderboardGetDTO> getLeaderboard() {

        List<User> users = userRepository.findAll();

        Map<User, Integer> leaderboard = new HashMap<>();

        for (User user : users) {
            int totalUserScore = 0;

            // Get all answers provided by the user
            List<Answer> userAnswers = answerRepository.findAllByUser(user);

            // For each answer, get the votes by other users and calculate the score
            for (Answer answer : userAnswers) {
                List<Vote> votesForAnswer = voteRepository.findAllByAnswer(answer);
                int answerScore = scoreCalculationService.calculateScore(votesForAnswer);
                totalUserScore += answerScore;
            }

            leaderboard.put(user, totalUserScore);
        }

        // Sort the leaderboard map by scores in descending order and create LeaderboardGetDTO objects
        return leaderboard.entrySet().stream()
                .sorted(Map.Entry.<User, Integer>comparingByValue().reversed())
                .map(entry -> {
                    LeaderboardGetDTO leaderboardGetDTO = new LeaderboardGetDTO();
                    leaderboardGetDTO.setUsername(entry.getKey().getUsername());
                    leaderboardGetDTO.setAccumulatedScore(entry.getValue());
                    return leaderboardGetDTO;
                })
                .collect(Collectors.toList());
    }

}
