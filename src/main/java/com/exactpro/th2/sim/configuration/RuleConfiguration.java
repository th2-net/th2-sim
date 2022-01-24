package com.exactpro.th2.sim.configuration;

public class RuleConfiguration {
    public final static String DEFAULT_RELATION = "default";

    private String sessionAlias = null;
    private String relation = DEFAULT_RELATION;

    public String getSessionAlias() {
        return sessionAlias;
    }

    public void setSessionAlias(String sessionAlias) {
        this.sessionAlias = sessionAlias;
    }

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    @Override
    public String toString() {
        return String.format("Configuration: \n\tsession-alias: %s", sessionAlias);
    }
}
