package com.example.agent;

import com.example.agent.model.ir.IR;
import com.example.agent.translate.IRToJava;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IRToJavaTest {

    @Test
    void reassigningExistingVariableUsesSimpleAssignment() {
        IR ir = new IR();
        ir.nodes.add(new IR.Assign("P_ROLLBACK", "0"));
        ir.nodes.add(new IR.Assign("P_ROLLBACK", "NVL(P_ROLLBACK, 1)"));

        String java = new IRToJava().generate(ir, "Example");

        assertTrue(java.contains("var P_ROLLBACK = 0;"));
        assertTrue(java.contains("P_ROLLBACK = NVL(P_ROLLBACK, 1);"));
        assertFalse(java.contains("var P_ROLLBACK = NVL"));
        // ensure main method isn't empty
        assertFalse(java.contains("public static void main(String[] args) {\n  }\n"));
    }
}

