package com.example.agent;

import com.example.agent.model.ir.IR;
import com.example.agent.translate.DynamicDialectParser;
import com.example.agent.knowledge.Rule;
import com.example.agent.knowledge.RuleStore;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ParserSmokeTest {

    @Test
    void dynamicParserLoadsRules() throws Exception {
        var store = new RuleStore(Path.of("runtime"));
        // No rules yet — just ensure it loads without error
        var parser = new DynamicDialectParser(store);
        assertNotNull(parser);
    }
}
