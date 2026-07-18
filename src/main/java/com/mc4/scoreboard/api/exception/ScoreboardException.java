package com.mc4.scoreboard.api.exception;

/** Base class for library-specific failures. */
public class ScoreboardException extends RuntimeException {
    public ScoreboardException(String message) {
        super(message);
    }

    public ScoreboardException(String message, Throwable cause) {
        super(message, cause);
    }
}
