package com.example.agent.translate;

import com.example.agent.model.ir.IR;

public class IRToJava {

    public String generate(IR ir, String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(className).append(" {\n");
        sb.append("  public static void main(String[] args) {\n");
        for (IR.Node n : ir.nodes) {
            sb.append("    ").append(genStmt(n)).append("\n");
        }
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String genStmt(IR.Node n) {
        if (n instanceof IR.Assign a) {
            return "var " + a.name + " = " + sanitize(a.expr) + ";";
        }
        if (n instanceof IR.Call c) {
            String args = String.join(", ", c.args);
            return c.callee + "(" + args + ");";
        }
        if (n instanceof IR.Decl d) {
            return d.type + " " + d.name + ";";
        }
        if (n instanceof IR.If i) {
            return "if (" + sanitize(i.cond) + ") { /* TODO then */ } else { /* TODO else */ }";
        }
        if (n instanceof IR.Loop l) {
            return "// loop " + l.header + "\n    /* TODO convert loop body */";
        }
        if (n instanceof IR.UnknownNode u) {
            return "/* UNKNOWN: " + escape(u.raw) + " */";
        }
        return "/* TODO */";
    }

    private String sanitize(String s) {
        return s.replaceAll(":=|<>", "!=").replaceAll(" and ", " && ").replaceAll(" or ", " || ");
    }

    private String escape(String s) {
        return s.replace("*/", "*_/").replace("\n", " ");
    }
}
