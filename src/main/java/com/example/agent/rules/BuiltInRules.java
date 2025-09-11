package com.example.agent.rules;

import java.util.ArrayList;
import java.util.List;

/** Predefined rule sets used when no learned rules exist. */
public final class BuiltInRules {
  private BuiltInRules() {}

  /** Built-in block rules such as IF/ELSE. */
  public static List<RuleV2> blockRules() {
    RuleV2 ifRule = new RuleV2();
    ifRule.irType = "If";
    ifRule.open = "^\\s*IF\\s+(.+)\\s+THEN\\s*$";
    ifRule.middle = new String[]{"^\\s*ELSE\\s*$"};
    ifRule.close = "^\\s*END\\s*IF\\s*;?\\s*$";
    ifRule.fields = new String[]{"cond"};
    return List.of(ifRule);
  }

  /** Built-in statement rules such as assignments and calls. */
  public static List<RuleV2> stmtRules() {
    List<RuleV2> out = new ArrayList<>();

    RuleV2 assign = new RuleV2();
    assign.irType = "Assign";
    assign.regex = "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*:=\\s*(.+?);?\\s*$";
    assign.fields = new String[]{"name","expr"};
    out.add(assign);

    RuleV2 call = new RuleV2();
    call.irType = "Call";
    call.regex = "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*)\\)\\s*;?\\s*$";
    call.fields = new String[]{"callee","args"};
    call.listFields = new String[]{"args"};
    out.add(call);

    return out;
  }
}
