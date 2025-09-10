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
        String base = getenvOr("GIGACHAT_API_BASE", "http://localhost:8000");
        String key  = getenvOr("GIGACHAT_API_KEY", "");
        String model= getenvOr("GIGACHAT_MODEL", "gigachat");
        String provider = getenvOr("LLM_PROVIDER", "openai");
        String cert = getenvOr("GIGACHAT_CERT_FILE", "");
        String kpath = getenvOr("GIGACHAT_KEY_FILE", "");
        String ca   = getenvOr("GIGACHAT_CA_FILE", "");
        return new Config(base, key, model, provider, cert, kpath, ca);
    }

    private static String getenvOr(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : v;
    }
}
