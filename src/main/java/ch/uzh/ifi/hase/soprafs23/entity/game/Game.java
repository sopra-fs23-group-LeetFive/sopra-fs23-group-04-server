package ch.uzh.ifi.hase.soprafs23.entity.game;

import ch.uzh.ifi.hase.soprafs23.constant.RoundLength;
import ch.uzh.ifi.hase.soprafs23.constant.GameStatus;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

public class Game {
    @Id
    @GeneratedValue
    private Long gameId;

    @Column(nullable = false, unique = true)
    private Long gamePin;

    @Column(nullable = false)
    private RoundLength length;

    @Column(nullable = false)
    private Long rounds;

    @Column(nullable = false)
    private GameStatus status;

    @Column(nullable = false)
    private Long hostId;

    public Long getId() {
        return gameId;
    }

    public Long getGamePin() {
        return gamePin;
    }

    public void setGamePin(Long gamePin) {
        this.gamePin = gamePin;
    }

    public RoundLength getLength() {
        return length;
    }

    public void setLength(RoundLength length) {
        this.length = length;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public Long getHostId() {
        return hostId;
    }

    public void setHostId(Long hostId) {
        this.hostId = hostId;
    }
}
