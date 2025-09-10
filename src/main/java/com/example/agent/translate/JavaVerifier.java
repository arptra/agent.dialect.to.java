package com.example.agent.translate;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public class JavaVerifier {

    public static class Result {
        public final boolean ok;
        public final String diagnostics;
        public Result(boolean ok, String diagnostics) {
            this.ok = ok; this.diagnostics = diagnostics;
        }
    }

    public Result compile(String className, String code) {
        try {
            Path tmp = Files.createTempDirectory("java-verify");
            Path file = tmp.resolve(className + ".java");
            Files.writeString(file, code);
            var pb = new ProcessBuilder("javac", file.toString());
            pb.redirectErrorStream(true);
            var proc = pb.start();
            String out = new String(proc.getInputStream().readAllBytes());
            int ec = proc.waitFor();
            return new Result(ec == 0, out);
        } catch (IOException | InterruptedException e) {
            return new Result(false, e.toString());
        }
    }
}
