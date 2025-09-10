package com.example.agent.config;

public class Config {
    public final String apiBase;
    public final String apiKey;
    public final String model;

    public Config(String apiBase, String apiKey, String model) {
        this.apiBase = apiBase;
        this.apiKey = apiKey;
        this.model = model;
    }

    public static Config fromEnv() {
        String base = getenvOr("GIGACHAT_API_BASE", "http://localhost:8000");
        String key  = getenvOr("GIGACHAT_API_KEY", "CHANGE_ME");
        String model= getenvOr("GIGACHAT_MODEL", "gigachat");
        return new Config(base, key, model);
    }

    private static String getenvOr(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : v;
    }
}
