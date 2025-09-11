package com.example.agent;

import com.example.agent.model.ir.IR;
import com.example.agent.rules.BlockEngine;
import com.example.agent.rules.BuiltInRules;
import com.example.agent.translate.IRToJava;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IfBlockTest {

    @Test
    void parsesIfAndGeneratesJava() {
        List<String> tokens = List.of(
                "BEGIN",
                "x := 1;",
                "IF x > 0 THEN",
                "  y := 2;",
                "ELSE",
                "  print(y);",
                "END IF;",
                "END;"
        );
        IR ir = new BlockEngine().parse(tokens, BuiltInRules.blockRules(), BuiltInRules.stmtRules());
        String java = new IRToJava().generate(ir, "Sample");

        assertTrue(java.contains("var x = 1"));
        assertTrue(java.contains("if (x > 0)"));
        assertTrue(java.contains("print(y);"));
    }
}

