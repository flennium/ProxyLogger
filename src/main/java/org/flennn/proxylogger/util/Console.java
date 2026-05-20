package org.flennn.proxylogger.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class Console {
    private static final String RESET = "\u001B[0m";
    private static final String GRAY = "\u001B[90m";
    private static final String BLUE = "\u001B[94m";
    private static final String GREEN = "\u001B[92m";
    private static final String YELLOW = "\u001B[93m";
    private static final String RED = "\u001B[91m";

    private Console() {
    }

    public static void info(Logger logger, String message) {
        logger.info(format(BLUE, "INFO", message));
    }

    public static void success(Logger logger, String message) {
        logger.info(format(GREEN, "OK", message));
    }

    public static void warn(Logger logger, String message) {
        logger.warning(format(YELLOW, "WARN", message));
    }

    public static void error(Logger logger, String message) {
        logger.severe(format(RED, "ERROR", message));
    }

    public static void error(Logger logger, String message, Throwable throwable) {
        logger.log(Level.SEVERE, format(RED, "ERROR", message), throwable);
    }

    private static String format(String color, String level, String message) {
        return GRAY + "[" + BLUE + "ProxyLogger" + GRAY + "] "
                + color + level + RESET + " " + message;
    }
}
