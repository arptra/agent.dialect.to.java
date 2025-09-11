package com.example.agent;

import com.example.agent.grammar.ManifestDrivenGrammarSeeder;
import com.example.agent.rules.RuleLoaderV2;
import com.example.agent.rules.SegmentEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IfElseSplitTest {

    @Test
    void ifThenAssignmentSplit() throws Exception {
        Path runtime = Files.createTempDirectory("runtime");
        new ManifestDrivenGrammarSeeder(Path.of("spec/plplus_syntax_manifest.json"), runtime).seed();
        RuleLoaderV2 loader = new RuleLoaderV2(runtime);
        String src = "IF P_BRANCH IS NULL THEN P_DEPART_GROUP := NULL; END IF;";
        List<String> tokens = new SegmentEngine().segment(src, loader.ofType("segment"));
        assertEquals("IF P_BRANCH IS NULL THEN", tokens.get(0));
        assertEquals("P_DEPART_GROUP := NULL;", tokens.get(1));
    }

    @Test
    void elseAssignmentSplit() throws Exception {
        Path runtime = Files.createTempDirectory("runtime");
        new ManifestDrivenGrammarSeeder(Path.of("spec/plplus_syntax_manifest.json"), runtime).seed();
        RuleLoaderV2 loader = new RuleLoaderV2(runtime);
        String src = "ELSE P_DEPART_GROUP := NULL;";
        List<String> tokens = new SegmentEngine().segment(src, loader.ofType("segment"));
        assertEquals(List.of("ELSE", "P_DEPART_GROUP := NULL;"), tokens);
    }
}
