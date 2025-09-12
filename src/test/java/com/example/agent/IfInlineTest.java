package com.example.agent;

import com.example.agent.grammar.ManifestDrivenGrammarSeeder;
import com.example.agent.model.ir.IR;
import com.example.agent.rules.BlockEngine;
import com.example.agent.rules.RuleLoaderV2;
import com.example.agent.rules.SegmentEngine;
import com.example.agent.rules.StmtEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IfInlineTest {

    @Test
    void ifThenActionSameLine() throws Exception {
        Path runtime = Files.createTempDirectory("runtime");
        new ManifestDrivenGrammarSeeder(Path.of("spec/plplus_syntax_manifest.json"), runtime).seed();
        RuleLoaderV2 loader = new RuleLoaderV2(runtime);
        String src = "IF TRUE THEN P_A := 1; END IF;";
        List<String> tokens = new SegmentEngine().segment(src, loader.ofType("segment"));
        StmtEngine stmt = new StmtEngine(loader.ofType("stmt"));
        var ir = new BlockEngine().parse(tokens, loader.ofType("block"), stmt);
        assertFalse(ir.nodes.isEmpty());
        assertTrue(ir.nodes.get(0) instanceof IR.If);
        IR.If ifNode = (IR.If) ir.nodes.get(0);
        assertEquals(1, ifNode.thenBody.size());
        assertTrue(ifNode.thenBody.get(0) instanceof IR.Assign);
    }

    @Test
    void elseActionSameLine() throws Exception {
        Path runtime = Files.createTempDirectory("runtime");
        new ManifestDrivenGrammarSeeder(Path.of("spec/plplus_syntax_manifest.json"), runtime).seed();
        RuleLoaderV2 loader = new RuleLoaderV2(runtime);
        String src = "IF TRUE THEN P_A := 1; ELSE P_A := 2; END IF;";
        List<String> tokens = new SegmentEngine().segment(src, loader.ofType("segment"));
        StmtEngine stmt = new StmtEngine(loader.ofType("stmt"));
        var ir = new BlockEngine().parse(tokens, loader.ofType("block"), stmt);
        assertFalse(ir.nodes.isEmpty());
        assertTrue(ir.nodes.get(0) instanceof IR.If);
        IR.If ifNode = (IR.If) ir.nodes.get(0);
        assertEquals(1, ifNode.thenBody.size());
        assertEquals(1, ifNode.elseBody.size());
        assertTrue(ifNode.elseBody.get(0) instanceof IR.Assign);
    }
}

