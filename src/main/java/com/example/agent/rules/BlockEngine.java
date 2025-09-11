package com.example.agent.rules;

import com.example.agent.model.ir.IR;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Data-driven multi-line block parser (no hardcoded keywords). */
public final class BlockEngine {

  private static class CompBlock {
    final RuleV2 r; final Pattern open; final List<Pattern> middle; final Pattern close;
    CompBlock(RuleV2 r) {
      this.r = r;
      this.open = r.open != null ? Pattern.compile(r.open, Pattern.CASE_INSENSITIVE) : null;
      this.close = r.close != null ? Pattern.compile(r.close, Pattern.CASE_INSENSITIVE) : null;
      this.middle = new ArrayList<>();
      if (r.middle != null) for (String m : r.middle) middle.add(Pattern.compile(m, Pattern.CASE_INSENSITIVE));
    }
  }

  public IR parse(List<String> tokens, List<RuleV2> blockRules, List<RuleV2> stmtRules) {
    List<CompBlock> blocks = new ArrayList<>();
    blockRules.stream().sorted((a,b)->Integer.compare(b.priority, a.priority)).forEach(r -> blocks.add(new CompBlock(r)));
    var ir = new IR();
    var stack = new ArrayDeque<Frame>();
    List<IR.Node> current = ir.nodes;
    var stmt = new StmtEngine();

    for (String t : tokens) {
      boolean handled = false;

      // CLOSE
      if (!stack.isEmpty()) {
        var top = stack.peek();
        if (top.b.close != null && top.b.close.matcher(t).matches()) {
          IR.Node node = stack.pop().finalizeNode();
          current = stack.isEmpty() ? ir.nodes : stack.peek().current();
          current.add(node);
          handled = true;
        } else {
          // MIDDLE
          for (Pattern mid : top.b.middle) {
            if (mid.matcher(t).matches()) {
              top.switchToElseLike();
              current = top.current();
              handled = true; break;
            }
          }
        }
      }
      if (handled) continue;

      // OPEN
      for (CompBlock b : blocks) {
        if (b.open != null) {
          Matcher m = b.open.matcher(t);
          if (m.matches()) {
            Frame f = new Frame(b, instantiateIR(b.r, m));
            stack.push(f);
            current = f.current();
            handled = true; break;
          }
        }
      }
      if (handled) continue;

      // STMT
      IR.Node n = stmt.match(t, stmtRules);
      current.add(n);
    }

    while (!stack.isEmpty()) ir.nodes.add(stack.pop().finalizeNode());
    return ir;
  }

  private static class Frame {
    final CompBlock b;
    final IR.Node node;
    boolean elseLike = false;
    final List<IR.Node> thenBody = new ArrayList<>();
    final List<IR.Node> elseBody = new ArrayList<>();
    Frame(CompBlock b, IR.Node node){ this.b=b; this.node=node; }
    void switchToElseLike(){ this.elseLike = true; }
    List<IR.Node> current(){ return elseLike ? elseBody : thenBody; }
    IR.Node finalizeNode() {
      try {
        var cls = node.getClass();
        try { var thenF = cls.getField("thenBody"); thenF.set(node, thenBody); } catch (NoSuchFieldException ignore){}
        try { var elseF = cls.getField("elseBody"); elseF.set(node, elseBody); } catch (NoSuchFieldException ignore){}
        try { var bodyF = cls.getField("body"); bodyF.set(node, thenBody); } catch (NoSuchFieldException ignore){}
      } catch (Throwable ignored) {}
      return node;
    }
  }

  private IR.Node instantiateIR(RuleV2 r, Matcher m) {
    try {
      String fqcn = "com.example.agent.model.ir.IR$"+r.irType;
      Class<?> clazz = Class.forName(fqcn);
      Constructor<?> ctor = clazz.getDeclaredConstructors()[0];
      Class<?>[] ps = ctor.getParameterTypes();
      Object[] args = new Object[ps.length];
      for (int i=0;i<ps.length;i++) args[i] = safe(m, i+1);
      return (IR.Node) ctor.newInstance(args);
    } catch (Throwable t) {
      return new IR.UnknownNode(m.group(0));
    }
  }
  private String safe(Matcher m,int i){ try{return m.group(i);}catch(Exception e){return null;} }
}
