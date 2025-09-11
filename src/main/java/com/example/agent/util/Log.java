package com.example.agent.util;

/** Simple logging utility controlled by the "log" system property. */
public final class Log {
    /** Whether logging is enabled. */
    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("log", "false"));

    private Log() {}

    /** Logs message to stdout when logging is enabled. */
    public static void info(String msg) {
        if (ENABLED) {
            System.out.println(msg);
        }
    }

    /** Logs message and optional stack trace to stderr when logging is enabled. */
    public static void error(String msg, Throwable t) {
        if (ENABLED) {
            System.err.println(msg);
            if (t != null) {
                t.printStackTrace(System.err);
            }
        }
    }

    /** Logs message to stderr when logging is enabled. */
    public static void error(String msg) {
        error(msg, null);
    }
}

