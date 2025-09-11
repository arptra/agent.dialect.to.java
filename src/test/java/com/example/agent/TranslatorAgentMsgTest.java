package com.example.agent;

import com.example.agent.providers.LlmProvider;
import com.example.agent.translate.TranslatorAgent;
import com.example.agent.rag.SimpleIndexer;
import com.example.agent.knowledge.RuleStore;
import com.example.agent.rules.RuleLoaderV2;
import com.example.agent.rules.RuleV2;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TranslatorAgentMsgTest {
    @Test
    void translatesAmpMsg() throws Exception {
        Path runtime = Files.createTempDirectory("runtime");
        RuleLoaderV2 loader = new RuleLoaderV2(runtime);
        RuleStore store = new RuleStore(runtime);

        RuleV2 rule = new RuleV2();
        rule.id = "amp_call";
        rule.type = "stmt";
        rule.irType = "Call";
        rule.regex = "^&([A-Za-z_][A-Za-z0-9_]*)\\(([^)]*)\\)$";
        loader.addOrMerge(rule);

        LlmProvider llm = new LlmProvider() {
            boolean first = true;
            @Override
            public String chat(List<Map<String, String>> messages, double temperature) throws IOException {
                if (first) {
                    first = false;
                    return "public class TranslatedProgram { public static void main(String[] args) { msg(\"ok\"); } }";
                }
                return "";
            }
        };

        TranslatorAgent agent = new TranslatorAgent(llm, new SimpleIndexer(), store, loader);
        String java = agent.translate("&msg('ok')");
        assertTrue(java.contains("msg(\"ok\");"), java);
        assertFalse(java.contains("/* UNKNOWN */"), java);
    }
}
