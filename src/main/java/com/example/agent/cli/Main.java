package com.example.agent.cli;

import com.example.agent.api.TranslatorEngine;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage:\n  learn <repoRoot> [exts=.dlx,.dsl,.txt] [runtime=runtime]\n  translate <file> [runtime=runtime]\n  fix <dialectFile> <javaFile> <feedback.txt> [runtime=runtime]\n");
            return;
        }
        String cmd = args[0];
        switch (cmd) {
            case "learn" -> {
                Path repo = Path.of(args[1]);
                String exts = args.length >= 3 ? args[2] : ".dlx,.dsl,.txt";
                Path runtime = Path.of(args.length >= 4 ? args[3] : "runtime");
                var engine = TranslatorEngine.fromEnv(runtime);
                engine.learn(repo, Arrays.asList(exts.split(",")));
                System.out.println("Learn OK");
            }
            case "translate" -> {
                Path file = Path.of(args[1]);
                Path runtime = Path.of(args.length >= 3 ? args[2] : "runtime");
                var engine = TranslatorEngine.fromEnv(runtime);
                String src = Files.readString(file);
                String out = engine.translate(src);
                System.out.println(out);
            }
            case "fix" -> {
                Path dialectFile = Path.of(args[1]);
                Path javaFile = Path.of(args[2]);
                Path feedbackFile = Path.of(args[3]);
                Path runtime = Path.of(args.length >= 5 ? args[4] : "runtime");
                var engine = TranslatorEngine.fromEnv(runtime);
                String dialect = Files.readString(dialectFile);
                String java = Files.readString(javaFile);
                String feedback = Files.readString(feedbackFile);
                String out = engine.fix(dialect, java, feedback);
                System.out.println(out);
            }
            default -> System.err.println("Unknown command: " + cmd);
        }
    }
}
