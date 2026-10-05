package com.kimsooin77.sync.simulation.groupware;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mock.groupware")
public class MockGroupwareProperties {
    private boolean enabled;
    private FailureMode defaultFailureMode = FailureMode.NORMAL;
    private long defaultDelayMs;
    private long timeoutDelayMs = 6_000;
    private long maxDelayMs = 30_000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public FailureMode getDefaultFailureMode() { return defaultFailureMode; }
    public void setDefaultFailureMode(FailureMode mode) { this.defaultFailureMode = mode; }
    public long getDefaultDelayMs() { return defaultDelayMs; }
    public void setDefaultDelayMs(long delay) { this.defaultDelayMs = delay; }
    public long getTimeoutDelayMs() { return timeoutDelayMs; }
    public void setTimeoutDelayMs(long delay) { this.timeoutDelayMs = delay; }
    public long getMaxDelayMs() { return maxDelayMs; }
    public void setMaxDelayMs(long delay) { this.maxDelayMs = delay; }

    public FailureRule defaultRule() {
        return new FailureRule(defaultFailureMode, defaultFailureMode == FailureMode.DELAY ? defaultDelayMs : 0);
    }
}
