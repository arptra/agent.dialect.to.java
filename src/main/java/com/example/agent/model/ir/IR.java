package com.example.agent.model.ir;

import java.util.ArrayList;
import java.util.List;

public class IR {
    public final List<Node> nodes = new ArrayList<>();

    public sealed interface Node permits Assign, Call, If, Loop, Decl, Block, UnknownNode {}

    public static final class Assign implements Node {
        public final String name;
        public final String expr;
        public Assign(String name, String expr) { this.name = name; this.expr = expr; }
    }

    public static final class Call implements Node {
        public final String callee;
        public final List<String> args;
        public Call(String callee, List<String> args) {
            this.callee = callee; this.args = args;
        }
    }

    public static final class If implements Node {
        public final String cond;
        public final List<Node> thenBody = new ArrayList<>();
        public final List<Node> elseBody = new ArrayList<>();
        public If(String cond) { this.cond = cond; }
    }

    public static final class Loop implements Node {
        public final String header;
        public final List<Node> body = new ArrayList<>();
        public Loop(String header) { this.header = header; }
    }

    public static final class Block implements Node {
        public List<Node> body = new ArrayList<>();
        public Block() {}
    }

    public static final class Decl implements Node {
        public final String name;
        public final String type;
        public Decl(String name, String type) { this.name = name; this.type = type; }
    }

    public static final class UnknownNode implements Node {
        public final String raw;
        public UnknownNode(String raw) { this.raw = raw; }
    }
}
