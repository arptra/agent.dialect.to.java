package com.example.agent.config;

public class Config {
    public final String apiBase;
    public final String apiKey;
    public final String model;
    public final String provider;
    public final String certPath;
    public final String keyPath;
    public final String caPath;

    public Config(String apiBase, String apiKey, String model,
                  String provider, String certPath, String keyPath, String caPath) {
        this.apiBase = apiBase;
        this.apiKey = apiKey;
        this.model = model;
        this.provider = provider;
        this.certPath = certPath;
        this.keyPath = keyPath;
        this.caPath = caPath;
    }

    public static Config fromEnv() {
        String base = propOrEnv("GIGACHAT_API_BASE", "http://localhost:8000");
        String key  = propOrEnv("GIGACHAT_API_KEY", "");
        String model= propOrEnv("GIGACHAT_MODEL", "gigachat");
        String provider = propOrEnv("LLM_PROVIDER", "openai");
        String cert = propOrEnv("GIGACHAT_CERT_FILE", "");
        String kpath = propOrEnv("GIGACHAT_KEY_FILE", "");
        String ca   = propOrEnv("GIGACHAT_CA_FILE", "");
        return new Config(base, key, model, provider, cert, kpath, ca);
    }

    private static String propOrEnv(String k, String def) {
        String v = System.getProperty(k);
        if (v != null && !v.isBlank()) return v;
        v = System.getenv(k);
        return v == null || v.isBlank() ? def : v;
    }
}
