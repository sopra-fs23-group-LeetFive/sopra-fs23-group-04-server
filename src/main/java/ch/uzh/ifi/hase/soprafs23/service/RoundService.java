package ch.uzh.ifi.hase.soprafs23.service;

import ch.uzh.ifi.hase.soprafs23.constant.Constant;
import ch.uzh.ifi.hase.soprafs23.constant.RoundStatus;
import ch.uzh.ifi.hase.soprafs23.entity.User;
import ch.uzh.ifi.hase.soprafs23.entity.game.Game;
import ch.uzh.ifi.hase.soprafs23.entity.game.Round;
import ch.uzh.ifi.hase.soprafs23.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs23.repository.RoundRepository;
import ch.uzh.ifi.hase.soprafs23.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs23.websocket.DTO.LetterDTO;
import ch.uzh.ifi.hase.soprafs23.websocket.DTO.RoundEndDTO;
import ch.uzh.ifi.hase.soprafs23.websocket.DTO.RoundTimerDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import static ch.uzh.ifi.hase.soprafs23.constant.RoundStatus.NOT_STARTED;
import static ch.uzh.ifi.hase.soprafs23.helper.GameHelper.*;
import static ch.uzh.ifi.hase.soprafs23.helper.UserHelper.*;

@Service
@Transactional
public class RoundService {

    private final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final RoundRepository roundRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final WebSocketService webSocketService;
    private final VoteService voteService;



    @Autowired
    public RoundService(@Qualifier("roundRepository") RoundRepository roundRepository,
                        @Qualifier("gameRepository")GameRepository gameRepository,
                        @Qualifier("userRepository") UserRepository userRepository,
                        VoteService voteService,
                        WebSocketService webSocketService) {
        this.roundRepository = roundRepository;
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.webSocketService=webSocketService;
        this.voteService=voteService;
    }

    public void createAllRounds(Game game) {
        int roundCounter = 1;
        List<Character> letters = game.getRoundLetters();
        for (Character letter : letters) {
            Round newRound = new Round();
            newRound.setGame(game);
            newRound.setRoundNumber(roundCounter);
            newRound.setStatus(NOT_STARTED);
            newRound.setLetter(letter);
            roundRepository.save(newRound);
            roundCounter++;
        }
        roundRepository.flush();
    }
    //todo decide to use startRound or nextRound funciton

    public LetterDTO startRound(int gamePin, int roundNumber){

        Game game = gameRepository.findByGamePin(gamePin);
        checkIfGameExists(game);
        checkIfGameIsRunning(game);

        Round round = roundRepository.findByGameAndRoundNumber(game, roundNumber);
        checkIfRoundExists(round);

        round.setStatus(RoundStatus.RUNNING);
        roundRepository.save(round);

        return nextRound(gamePin);

    }

    public void endRound(int gamePin, String userToken, int roundNumber) {

        Game game = gameRepository.findByGamePin(gamePin);
        checkIfGameExists(game);
        checkIfGameIsRunning(game);

        User user = userRepository.findByToken(userToken);
        checkIfUserExists(user);
        checkIfUserIsInGame(game, user);


        Round round = roundRepository.findByGameAndRoundNumber(game, roundNumber);
        checkIfRoundExists(round);
        checkIfRoundIsRunning(round);
        round.setStatus(RoundStatus.FINISHED);

        roundRepository.saveAndFlush(round);
        voteService.voteTimeControl(gamePin);
    }

    public LetterDTO nextRound(int gamePin) {

        Game game = gameRepository.findByGamePin(gamePin);
        Round round = roundRepository.findByGameAndRoundNumber(game,game.getCurrentRound());

        round.setStatus(RoundStatus.RUNNING);
        roundRepository.save(round);

        LetterDTO letterDTO = new LetterDTO();
        letterDTO.setLetter(round.getLetter());
        letterDTO.setRound(round.getRoundNumber());


        return letterDTO;
    }

    public void startRoundTime(int gamePin){
        System.out.println("starting");
        Game game = gameRepository.findByGamePin(gamePin);
        Round round = roundRepository.findByGameAndRoundNumber(game, game.getCurrentRound());
        assert round.getStatus()==RoundStatus.RUNNING;
        int roundLength = game.getRoundLength().getDuration();
        System.out.println(roundLength);
        AtomicInteger remainingTime = new AtomicInteger(roundLength);

        Timer timer = new Timer();
        TimerTask updateTask = new TimerTask() {

            @Override
            public void run() {
                int timeLeft = remainingTime.addAndGet(-1);

                if (isRoundFinished(gamePin)){
                    logger.info("Round is finsihed");
                    timer.cancel();

                }
                //no more time remaining
                else if (timeLeft <= 0) {



                    round.setStatus(RoundStatus.FINISHED);
                    roundRepository.save(round);
                    System.out.println(timeLeft);
                    String fill="roundEnd";
                    RoundEndDTO roundEndDTO=new RoundEndDTO();
                    roundEndDTO.setRounded(fill);
                    webSocketService.sendMessageToClients(Constant.defaultDestination +gamePin, roundEndDTO);
                    timer.cancel();
                    voteService.voteTimeControl(gamePin);

                }

                else{
                    logger.info("Timeleft to answer "+ timeLeft + " current round Status " + round.getStatus());

                    RoundTimerDTO roundTimerDTO = new RoundTimerDTO();
                    roundTimerDTO.setTimeRemaining(timeLeft);
                    webSocketService.sendMessageToClients(Constant.defaultDestination + gamePin, roundTimerDTO);
                }
            }
        };

        timer.scheduleAtFixedRate(updateTask, 1500, 1000); // Schedule the task to run every 3 seconds (3000 ms)
    }

    private boolean isRoundFinished(int gamePin){
        Game game = gameRepository.findByGamePin(gamePin);
        Round round = roundRepository.findByGameAndRoundNumber(game, game.getCurrentRound());
        return round.getStatus()==RoundStatus.FINISHED;
    }


    /**
     * Helper methods to aid in the game creation, modification and deletion
     */

    private void checkIfRoundExists(Round round) {
        String errorMessage = "Round does not exist.";

        if (round == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, errorMessage);
        }
    }

    private void checkIfRoundIsRunning(Round round) {
        String errorMessage = "Round is not running anymore. Not possible to save your answers!";

        if (!round.getStatus().equals(RoundStatus.RUNNING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, errorMessage);
        }
    }

}
