package com.ai.agents.sandbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the {@code sandbox.*} block in application.yaml. Every guardrail is a knob here. */
@ConfigurationProperties(prefix = "sandbox")
public class SandboxProperties {

    /** Directory under which temp worktrees are created. Each run's worktree is deleted after. */
    private String workRoot = System.getProperty("java.io.tmpdir");

    /** Hard wall-clock limit per Maven invocation. On expiry the process tree is force-killed. */
    private long timeoutSeconds = 600;

    /** Global cap on concurrent builds, so a busy repo cannot fork many JVMs at once. */
    private int maxConcurrentBuilds = 2;

    /** How long a run waits for a build permit before giving up and escalating. */
    private long queueTimeoutSeconds = 60;

    /** Pre-warmed local Maven repo for offline builds ({@code -Dmaven.repo.local}). Null = default. */
    private String localMavenRepo;

    /** Run Maven offline ({@code -o}) so a build cannot reach the network. */
    private boolean offline = true;

    /** Maven executable. Null prefers the worktree's {@code ./mvnw}, else {@code mvn} on PATH. */
    private String mavenExecutable;

    /** JAVA_HOME handed to the Maven child. Null inherits — which on this box is JDK 11, not 17. */
    private String javaHome;

    public String getWorkRoot() {
        return workRoot;
    }

    public void setWorkRoot(String workRoot) {
        this.workRoot = workRoot;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxConcurrentBuilds() {
        return maxConcurrentBuilds;
    }

    public void setMaxConcurrentBuilds(int maxConcurrentBuilds) {
        this.maxConcurrentBuilds = maxConcurrentBuilds;
    }

    public long getQueueTimeoutSeconds() {
        return queueTimeoutSeconds;
    }

    public void setQueueTimeoutSeconds(long queueTimeoutSeconds) {
        this.queueTimeoutSeconds = queueTimeoutSeconds;
    }

    public String getLocalMavenRepo() {
        return localMavenRepo;
    }

    public void setLocalMavenRepo(String localMavenRepo) {
        this.localMavenRepo = localMavenRepo;
    }

    public boolean isOffline() {
        return offline;
    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    public String getMavenExecutable() {
        return mavenExecutable;
    }

    public void setMavenExecutable(String mavenExecutable) {
        this.mavenExecutable = mavenExecutable;
    }

    public String getJavaHome() {
        return javaHome;
    }

    public void setJavaHome(String javaHome) {
        this.javaHome = javaHome;
    }
}
