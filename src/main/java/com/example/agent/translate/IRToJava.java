package com.example.agent.translate;

import com.example.agent.model.ir.IR;

public class IRToJava {

    public String generate(IR ir, String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(className).append(" {\n");
        sb.append("  public static void main(String[] args) {\n");
        for (IR.Node n : ir.nodes) {
            sb.append(genStmt(n, 1)).append("\n");
        }
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String genStmt(IR.Node n, int depth) {
        String indent = "    ".repeat(depth);
        if (n instanceof IR.Assign a) {
            return indent + "var " + a.name + " = " + sanitize(a.expr) + ";";
        }
        if (n instanceof IR.Call c) {
            String args = String.join(", ", c.args);
            return indent + c.callee + "(" + args + ");";
        }
        if (n instanceof IR.Decl d) {
            return indent + d.type + " " + d.name + ";";
        }
        if (n instanceof IR.Block b) {
            StringBuilder sb = new StringBuilder();
            sb.append(indent).append("{\n");
            for (IR.Node child : b.body) {
                sb.append(genStmt(child, depth + 1)).append("\n");
            }
            sb.append(indent).append("}");
            return sb.toString();
        }
        if (n instanceof IR.If i) {
            StringBuilder sb = new StringBuilder();
            sb.append(indent).append("if (").append(sanitize(i.cond)).append(") {\n");
            for (IR.Node child : i.thenBody) {
                sb.append(genStmt(child, depth + 1)).append("\n");
            }
            sb.append(indent).append("}");
            if (!i.elseBody.isEmpty()) {
                sb.append(" else {\n");
                for (IR.Node child : i.elseBody) {
                    sb.append(genStmt(child, depth + 1)).append("\n");
                }
                sb.append(indent).append("}");
            }
            return sb.toString();
        }
        if (n instanceof IR.Loop l) {
            return indent + "// loop " + l.header + "\n" + indent + "/* TODO convert loop body */";
        }
        if (n instanceof IR.UnknownNode u) {
            return indent + "/* UNKNOWN: " + escape(u.raw) + " */";
        }
        return indent + "/* TODO */";
    }

    private String sanitize(String s) {
        return s
                .replace(":=", "=")
                .replace("<>", "!=")
                .replace(" and ", " && ")
                .replace(" or ", " || ");
    }

    private String escape(String s) {
        return s.replace("*/", "*_/").replace("\n", " ");
    }
}
