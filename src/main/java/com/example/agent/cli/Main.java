package com.example.agent.cli;

import com.example.agent.api.TranslatorEngine;
import com.example.agent.grammar.ManifestDrivenGrammarSeeder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage:\n  learn <repoRoot> [exts=.dlx,.dsl,.txt] [runtime=runtime]\n  translate <file> [runtime=runtime]\n  fix <dialectFile> <javaFile> <feedback.txt> [runtime=runtime]\n  collect <path>\n");
            return;
        }
        String cmd = args[0];
        switch (cmd) {
            case "learn-spec" -> {
                // Usage: learn-spec spec/plplus_syntax_manifest.json runtime
                Path spec = Paths.get(args[1]);
                Path runtime = args.length >= 3 ? Paths.get(args[2]) : Paths.get("runtime");
                new ManifestDrivenGrammarSeeder(spec, runtime).seed();
                System.exit(0);
            }
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
            case "collect" -> {
                Path srcRoot = Path.of(args[1]);
                Path samplesDir = Path.of("samples");
                Files.createDirectories(samplesDir);
                try (var stream = Files.walk(srcRoot)) {
                    stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".plp"))
                          .forEach(p -> {
                              Path dest = samplesDir.resolve(srcRoot.relativize(p));
                              try {
                                  Files.createDirectories(dest.getParent());
                                  Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                              } catch (IOException e) {
                                  throw new RuntimeException(e);
                              }
                          });
                }
                System.out.println("Collect OK");
            }
            default -> System.err.println("Unknown command: " + cmd);
        }
    }
}
