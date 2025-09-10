package com.example.agent.api;

import com.example.agent.bootstrap.Learner;
import com.example.agent.config.Config;
import com.example.agent.knowledge.RuleStore;
import com.example.agent.providers.GigaChatCertificateClient;
import com.example.agent.providers.GigaChatOpenAIClient;
import com.example.agent.providers.LlmProvider;
import com.example.agent.rag.SimpleIndexer;
import com.example.agent.translate.TranslatorAgent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class TranslatorEngine implements TranslatorApi {

    private final LlmProvider llm;
    private final RuleStore rules;
    private final SimpleIndexer indexer;

    public TranslatorEngine(LlmProvider llm, Path runtimeDir) throws IOException {
        this.llm = Objects.requireNonNull(llm);
        this.rules = new RuleStore(Objects.requireNonNull(runtimeDir));
        this.indexer = new SimpleIndexer();
    }

    public static TranslatorEngine fromEnv(Path runtimeDir) throws IOException {
        var cfg = Config.fromEnv();
        LlmProvider provider;
        if ("cert".equalsIgnoreCase(cfg.provider)) {
            try {
                provider = new GigaChatCertificateClient(cfg.apiBase, cfg.model,
                        Path.of(cfg.certPath), Path.of(cfg.keyPath), Path.of(cfg.caPath));
            } catch (Exception e) {
                throw new IOException("Failed to initialize certificate client", e);
            }
        } else {
            provider = new GigaChatOpenAIClient(cfg.apiBase, cfg.apiKey, cfg.model);
        }
        return new TranslatorEngine(provider, runtimeDir);
    }

    @Override
    public void learn(Path repoRoot, List<String> includeExts) throws IOException {
        var learner = new Learner(llm, rules, indexer);
        learner.learnFromRepo(repoRoot, includeExts);
        rules.save();
    }

    @Override
    public String translate(String dialectSource) throws IOException {
        var agent = new TranslatorAgent(llm, indexer, rules);
        String out = agent.translate(dialectSource);
        rules.save();
        return out;
    }

    @Override
    public String fix(String dialectSource, String currentJava, String feedback) throws IOException {
        var agent = new TranslatorAgent(llm, indexer, rules);
        String out = agent.applyUserFix(dialectSource, currentJava, feedback);
        rules.save();
        return out;
    }
}
