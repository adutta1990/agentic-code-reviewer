package com.ai.agents.sandbox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs an external process with a hard timeout, capturing a bounded tail of its output.
 *
 * <p>Two things here are load-bearing for the sandbox guarantees. First, on timeout the whole
 * process tree is destroyed via {@link Process#descendants()} — Maven forks a JVM for Surefire,
 * and killing only the parent would leave that child running. Second, output is drained on a
 * separate thread so a chatty build cannot deadlock by filling the pipe buffer while we wait.
 */
final class Processes {

    private Processes() {
    }

    /** The outcome of a process run. {@code exitCode} is -1 when the process was killed on timeout. */
    record Exec(int exitCode, boolean timedOut, String output) {
    }

    static Exec run(List<String> command, Path workingDir, Map<String, String> env,
                    long timeoutSeconds, int maxOutputChars) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(workingDir.toFile())
                .redirectErrorStream(true);
        if (env != null) {
            pb.environment().putAll(env);
        }

        Process process = pb.start();
        StringBuilder out = new StringBuilder();
        Thread drainer = new Thread(() -> drain(process, out), "sandbox-proc-drain");
        drainer.setDaemon(true);
        drainer.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        boolean timedOut = false;
        if (!finished) {
            timedOut = true;
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        drainer.join(2_000);

        String output;
        synchronized (out) {
            output = out.toString();
        }
        if (output.length() > maxOutputChars) {
            output = "...(truncated)...\n" + output.substring(output.length() - maxOutputChars);
        }
        int exitCode = timedOut ? -1 : process.exitValue();
        return new Exec(exitCode, timedOut, output);
    }

    private static void drain(Process process, StringBuilder out) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (out) {
                    out.append(line).append('\n');
                }
            }
        } catch (IOException ignored) {
            // Stream closed on process death; whatever we captured is enough for diagnosis.
        }
    }
}
