package com.example.agent.rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RewriteEngineTest {
  @Test
  void ampMacroHandlesNestedParens() {
    RuleV2 r = new RuleV2();
    r.id = "rw_amp_macro_strict";
    String out = new RewriteEngine().applyAll("&msg(foo(bar))", List.of(r));
    assertEquals("msg(foo(bar))", out);
  }
}
