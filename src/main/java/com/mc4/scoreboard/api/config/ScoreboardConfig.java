package com.mc4.scoreboard.api.config;

/** Immutable configuration for a scoreboard instance. */
public record ScoreboardConfig(int historyLimit) {

    public static final int DEFAULT_HISTORY_LIMIT = 1_000;

    public ScoreboardConfig {
        if (historyLimit < 1) {
            throw new IllegalArgumentException("historyLimit must be at least 1: " + historyLimit);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder initialized with the documented defaults. */
    public static final class Builder {
        private int historyLimit = DEFAULT_HISTORY_LIMIT;

        private Builder() {
        }

        public Builder historyLimit(int limit) {
            this.historyLimit = limit;
            return this;
        }

        public ScoreboardConfig build() {
            return new ScoreboardConfig(historyLimit);
        }
    }
}
