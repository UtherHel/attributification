package dev.utherhel.attributification;

import dev.utherhel.attributification.config.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logger for Attributification mod.
 * Provides consistent logging across all components with configurable log levels.
 */
public final class AttributificationLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("Attributification");
    
    private AttributificationLogger() {
        // Private constructor to hide implicit public one
    }
    
    private static LogLevel getLogLevel() {
        try {
            return Attributifaction.getConfig().debug.logLevel;
        } catch (Exception e) {
            return LogLevel.WARNINGS;
        }
    }
    
    public static void info(String message) {
        if (getLogLevel().shouldLogInfo()) {
            LOGGER.info(message);
        }
    }
    
    public static void warn(String message) {
        if (getLogLevel().shouldLogWarning()) {
            LOGGER.warn(message);
        }
    }
    
    public static void error(String message) {
        if (getLogLevel().shouldLogError()) {
            LOGGER.error(message);
        }
    }
    
    public static void error(String message, Throwable throwable) {
        if (getLogLevel().shouldLogError()) {
            LOGGER.error(message, throwable);
        }
    }
}
