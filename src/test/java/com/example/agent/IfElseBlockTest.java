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

/** Verifies ELSE branch statements are parsed correctly. */
public class IfElseBlockTest {

    @Test
    void elseAssignmentIsParsed() throws Exception {
        Path runtime = Files.createTempDirectory("runtime");
        new ManifestDrivenGrammarSeeder(Path.of("spec/plplus_syntax_manifest.json"), runtime).seed();
        RuleLoaderV2 loader = new RuleLoaderV2(runtime);

        String src = "IF TRUE THEN P_DEPART_GROUP := 1; ELSE P_DEPART_GROUP := NULL; END IF;";
        List<String> tokens = new SegmentEngine().segment(src, loader.ofType("segment"));

        StmtEngine stmt = new StmtEngine(loader.ofType("stmt"));
        var ir = new BlockEngine().parse(tokens, loader.ofType("block"), stmt);

        assertEquals(1, ir.nodes.size());
        assertTrue(ir.nodes.get(0) instanceof IR.If);
        IR.If ifNode = (IR.If) ir.nodes.get(0);

        assertEquals(1, ifNode.elseBody.size());
        assertTrue(ifNode.elseBody.get(0) instanceof IR.Assign);
        IR.Assign assign = (IR.Assign) ifNode.elseBody.get(0);
        assertEquals("P_DEPART_GROUP", assign.name);
        assertEquals("NULL", assign.expr);

        // ensure no UNKNOWN nodes anywhere
        assertTrue(ifNode.thenBody.stream().noneMatch(n -> n instanceof IR.UnknownNode));
        assertTrue(ifNode.elseBody.stream().noneMatch(n -> n instanceof IR.UnknownNode));

        String java = new IRToJava().generate(ir, "Demo");
        assertFalse(java.contains("UNKNOWN"));
    }
}

