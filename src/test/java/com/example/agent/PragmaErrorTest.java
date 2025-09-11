package com.example.agent;

import com.example.agent.grammar.ManifestDrivenGrammarSeeder;
import com.example.agent.model.ir.IR;
import com.example.agent.rules.BlockEngine;
import com.example.agent.rules.RuleLoaderV2;
import com.example.agent.rules.SegmentEngine;
import com.example.agent.rules.StmtEngine;
import com.example.agent.translate.IRToJava;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PragmaErrorTest {
    @Test
    void pragmaErrorTranslatesToThrow() throws Exception {
        Path runtime = Files.createTempDirectory("runtime");
        new ManifestDrivenGrammarSeeder(Path.of("spec/plplus_syntax_manifest.json"), runtime).seed();
        RuleLoaderV2 loader = new RuleLoaderV2(runtime);
        String src = "PRAGMA ERROR('msg');";
        List<String> tokens = new SegmentEngine().segment(src, loader.ofType("segment"));
        StmtEngine stmt = new StmtEngine(loader.ofType("stmt"));
        IR ir = new BlockEngine().parse(tokens, loader.ofType("block"), stmt);
        assertFalse(ir.nodes.isEmpty());
        assertTrue(ir.nodes.get(0) instanceof IR.Pragma);
        String java = new IRToJava().generate(ir, "Demo");
        assertTrue(java.contains("throw new RuntimeException('msg');"));
        assertFalse(java.contains("UNKNOWN"));
    }
}
