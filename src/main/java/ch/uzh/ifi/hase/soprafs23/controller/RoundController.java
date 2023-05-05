package ch.uzh.ifi.hase.soprafs23.controller;

import ch.uzh.ifi.hase.soprafs23.constant.RoundStatus;
import ch.uzh.ifi.hase.soprafs23.entity.game.Round;
import ch.uzh.ifi.hase.soprafs23.service.GameService;
import ch.uzh.ifi.hase.soprafs23.service.RoundService;
import ch.uzh.ifi.hase.soprafs23.service.WebSocketService;
import ch.uzh.ifi.hase.soprafs23.websocket.DTO.LetterDTO;
import ch.uzh.ifi.hase.soprafs23.websocket.DTO.RoundEndDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class RoundController {

    private final RoundService roundService;
    private final WebSocketService webSocketService;
    public static final String FinalDestination = "/topic/lobbies/";

    Logger log = LoggerFactory.getLogger(RoundController.class);

    RoundController(RoundService roundService,
                    SimpMessagingTemplate messagingTemplate,
                    WebSocketService webSocketService) {
        this.roundService = roundService;
        this.webSocketService=webSocketService;
    }

    @PutMapping("/games/{gamePin}/{roundNumber}/start")
    public void gameStart(@PathVariable("gamePin") int gamePin,
                          @PathVariable("roundNumber") int roundNumber) {

        LetterDTO letterDTO = roundService.startRound(gamePin, roundNumber);

        webSocketService.sendMessageToClients(FinalDestination+gamePin,letterDTO);

        roundService.startRoundTime(gamePin);
    }

    @PutMapping("/games/{gamePin}/{roundNumber}/end")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public void endRound(@PathVariable("gamePin") int gamePin,
                         @PathVariable("roundNumber") int roundNumber,
                         @RequestHeader("Authorization") String userToken) {
        log.info(" "+gamePin + roundNumber+ userToken);

        roundService.endRound(gamePin, userToken, roundNumber);

        String fill="roundEnd";
        RoundEndDTO roundEndDTO=new RoundEndDTO();
        roundEndDTO.setRounded(fill);
        webSocketService.sendMessageToClients(FinalDestination+gamePin, roundEndDTO);
    }
}
