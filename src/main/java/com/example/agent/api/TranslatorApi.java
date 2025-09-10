package com.example.agent.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface TranslatorApi {
    void learn(Path repoRoot, List<String> includeExts) throws IOException;
    String translate(String dialectSource) throws IOException;
    String fix(String dialectSource, String currentJava, String feedback) throws IOException;
}
