package com.example.agent.providers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Generic interface for large language model providers.
 */
public interface LlmProvider {
    /**
     * Sends chat messages to the model and returns the generated response.
     *
     * @param messages    list of role/content message maps
     * @param temperature sampling temperature
     * @return model output text
     */
    String chat(List<Map<String, String>> messages, double temperature) throws IOException;
}
