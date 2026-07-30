package dev.utherhel.attributification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logger for Attributification mod.
 * Provides consistent logging across all components.
 */
public final class AttributificationLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        "Attributification"
    );

    private AttributificationLogger() {
        // Private constructor to hide implicit public one
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void warn(String message) {
        LOGGER.warn(message);
    }

    public static void error(String message) {
        LOGGER.error(message);
    }

    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }
}
