package com.exactpro.th2.sim.configuration;

public class RuleConfiguration {
    private String sessionAlias = null;

    public String getSessionAlias() {
        return sessionAlias;
    }

    public void setSessionAlias(String sessionAlias) {
        this.sessionAlias = sessionAlias;
    }

    @Override
    public String toString() {
        return String.format("Configuration: \n\tsession-alias: %s", sessionAlias);
    }
}
