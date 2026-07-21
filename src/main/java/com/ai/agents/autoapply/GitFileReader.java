package com.ai.agents.autoapply;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Reads a file's exact bytes at a specific commit via {@code git show <sha>:<path>}.
 *
 * <p>Deliberately a raw byte read rather than the line-buffered process helper: the fix is spliced
 * into and compiled from this exact content, so line endings and the trailing-newline byte must
 * survive verbatim. Reading at the SHA is the "fresh read at the pinned commit" the guardrails
 * require — the driver never trusts caller-supplied file content.
 */
@Component
public class GitFileReader {

    private static final int TIMEOUT_SECONDS = 60;

    public String read(Path repoDir, String sha, String path) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "-C", repoDir.toString(), "show", sha + ":" + path)
                .redirectErrorStream(false)
                .start();

        byte[] stdout = process.getInputStream().readAllBytes();
        byte[] stderr = process.getErrorStream().readAllBytes();

        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("git show timed out for " + sha + ":" + path);
        }
        if (process.exitValue() != 0) {
            throw new IOException("git show failed for " + sha + ":" + path + " -> "
                    + new String(stderr, StandardCharsets.UTF_8).strip());
        }
        return new String(stdout, StandardCharsets.UTF_8);
    }
}
