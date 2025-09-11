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
}

