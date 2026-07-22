package com.mc4.scoreboard.api.config;

import lombok.Builder;

/** Immutable configuration for a scoreboard instance. */
@Builder
public record ScoreboardConfig(int historyLimit) {

    public static final int DEFAULT_HISTORY_LIMIT = 1_000;

    public ScoreboardConfig {
        if (historyLimit < 1) {
            throw new IllegalArgumentException("historyLimit must be at least 1: " + historyLimit);
        }
    }

    /** Pre-seeds the generated builder with the documented default. */
    public static class ScoreboardConfigBuilder {
        private int historyLimit = DEFAULT_HISTORY_LIMIT;
    }
}
