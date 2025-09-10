package com.example.agent.api;

import com.example.agent.bootstrap.Learner;
import com.example.agent.config.Config;
import com.example.agent.knowledge.RuleStore;
import com.example.agent.providers.GigaChatOpenAIClient;
import com.example.agent.rag.SimpleIndexer;
import com.example.agent.translate.TranslatorAgent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class TranslatorEngine implements TranslatorApi {

    private final GigaChatOpenAIClient llm;
    private final RuleStore rules;
    private final SimpleIndexer indexer;

    public TranslatorEngine(String apiBase, String apiKey, String model, Path runtimeDir) throws IOException {
        this.llm = new GigaChatOpenAIClient(Objects.requireNonNull(apiBase), Objects.requireNonNull(apiKey), Objects.requireNonNull(model));
        this.rules = new RuleStore(Objects.requireNonNull(runtimeDir));
        this.indexer = new SimpleIndexer();
    }

    public static TranslatorEngine fromEnv(Path runtimeDir) throws IOException {
        var cfg = Config.fromEnv();
        return new TranslatorEngine(cfg.apiBase, cfg.apiKey, cfg.model, runtimeDir);
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
