package com.example.agent.translate;

import com.example.agent.knowledge.RuleStore;
import com.example.agent.providers.LlmProvider;
import com.example.agent.rag.SimpleIndexer;
import com.example.agent.rules.RuleLoaderV2;
import com.example.agent.rules.RuleV2;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class TranslatorAgentTest {

    @Test
    void translatesCallsWithoutUnknown() throws Exception {
        Path runtime = Files.createTempDirectory("runtime");
        RuleLoaderV2 loader = new RuleLoaderV2(runtime);
        RuleV2 call = new RuleV2();
        call.id = "call";
        call.type = "stmt";
        call.irType = "Call";
        call.regex = "^\\s*([A-Za-z_][A-Za-z0-9_]*)(?:\\s*(?:\\.|::)\\s*([A-Za-z_][A-Za-z0-9_]*))?\\s*\\((.*)\\)\\s*;\\s*$";
        call.fields = new String[]{"ns", "callee", "args"};
        call.listFields = new String[]{"args"};
        loader.addOrMerge(call);

        LlmProvider llm = (msgs, t) -> "";
        SimpleIndexer indexer = new SimpleIndexer();
        RuleStore store = new RuleStore(Files.createTempDirectory("proc"));
        TranslatorAgent agent = new TranslatorAgent(llm, indexer, store, loader);

        String src = "createLog('log'); deleteTune('t'); msg('done');";
        String java = agent.translate(src);

        assertTrue(java.contains("createLog"));
        assertTrue(java.contains("deleteTune"));
        assertTrue(java.contains("msg"));
        assertFalse(java.contains("/* UNKNOWN"));
    }
}
