package org.flennn;


import java.util.logging.Logger;

public class Utils {
    public static class Log {
        private static final Logger logger = Logger.getLogger("LOGGER");

        private static final String RESET = "\u001B[0m";

        private static final String BLUE = "\u001B[94m";
        private static final String YELLOW = "\u001B[93m";
        private static final String RED = "\u001B[91m";

        static {
            logger.setUseParentHandlers(false);
        }

        public static void info(String message) {
            System.out.println(BLUE + "[LOGGER] " + BLUE + "[INFO] " + RESET + " " + message);
        }

        public static void warning(String message) {
            System.out.println(BLUE + "[LOGGER] " + YELLOW + "[WARN] " + RESET + " " + message);
        }

        public static void severe(String message) {
            System.out.println(BLUE + "[LOGGER] " + RED + "[ERROR] " + RESET + " " + message);
        }
    }

}