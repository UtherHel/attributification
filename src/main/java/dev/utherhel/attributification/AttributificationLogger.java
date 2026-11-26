package dev.utherhel.attributification;

import dev.utherhel.attributification.config.LogLevel;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logger for Attributification mod. Messages are filtered by the configured
 * {@code debug.logLevel} (info only with ALL), as on the 1.20.1 branch.
 */
public final class AttributificationLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        "Attributification"
    );

    /** Keys of the warnings already logged by {@link #warnOnce}; cleared when the mappings are reloaded. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private AttributificationLogger() {
        // Private constructor to hide implicit public one
    }

    private static LogLevel getLogLevel() {
        try {
            return Attributifaction.getConfig().getLogLevel();
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

    /**
     * A warning from code that runs every tick or for every attribute lookup: logged once per key until the
     * mappings are reloaded, so one misconfigured mapping does not flood the log.
     */
    public static void warnOnce(String key, String message) {
        if (getLogLevel().shouldLogWarning() && WARNED.add(key)) {
            LOGGER.warn(message);
        }
    }

    /** Like {@link #warnOnce}, at the info level (shown with ALL). */
    public static void infoOnce(String key, String message) {
        if (getLogLevel().shouldLogInfo() && WARNED.add(key)) {
            LOGGER.info(message);
        }
    }

    /** The one-line overview of the loaded mappings: shown at every level except NONE. */
    public static void summary(String message) {
        if (getLogLevel().shouldLogError()) {
            LOGGER.info(message);
        }
    }

    public static void resetWarnOnce() {
        WARNED.clear();
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
