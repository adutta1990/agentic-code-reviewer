package com.ai.agents.sandbox;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Invokes Maven inside a worktree under the sandbox's guardrails: offline mode against a
 * pre-warmed local repo (no network egress), a JAVA_HOME override so forked builds do not inherit
 * the wrong JDK, and the wall-clock timeout enforced by {@link Processes}. It builds only the
 * module under review via {@code -pl}.
 */
@Component
public class MavenRunner {

    private static final int OUTPUT_CHARS = 16_000;

    private final SandboxProperties props;

    public MavenRunner(SandboxProperties props) {
        this.props = props;
    }

    /** Run the given Maven goals/args in {@code worktree}, scoped to {@code moduleDir} if set. */
    public Processes.Exec invoke(Path worktree, String moduleDir, List<String> goals)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(executable(worktree));
        cmd.add("-B");
        cmd.add("-q");
        if (props.isOffline()) {
            cmd.add("-o");
        }
        if (isSet(props.getLocalMavenRepo())) {
            cmd.add("-Dmaven.repo.local=" + props.getLocalMavenRepo());
        }
        if (moduleDir != null && !moduleDir.isBlank()) {
            cmd.add("-pl");
            cmd.add(moduleDir);
        }
        cmd.addAll(goals);

        Map<String, String> env = new HashMap<>();
        if (isSet(props.getJavaHome())) {
            env.put("JAVA_HOME", props.getJavaHome());
        }
        return Processes.run(cmd, worktree, env, props.getTimeoutSeconds(), OUTPUT_CHARS);
    }

    private String executable(Path worktree) {
        if (isSet(props.getMavenExecutable())) {
            return props.getMavenExecutable();
        }
        Path wrapper = worktree.resolve("mvnw");
        return Files.isRegularFile(wrapper) ? wrapper.toAbsolutePath().toString() : "mvn";
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }
}
