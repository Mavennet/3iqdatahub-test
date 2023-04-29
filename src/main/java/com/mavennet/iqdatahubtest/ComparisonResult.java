package com.mavennet.iqdatahubtest;

import java.util.List;

public class ComparisonResult {
    private String accuracy;
    private List<String> delta;

    public ComparisonResult(String accuracy, List<String> delta) {
        this.accuracy = accuracy;
        this.delta = delta;
    }

    public String getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(String accuracy) {
        this.accuracy = accuracy;
    }

    public List<String> getDelta() {
        return delta;
    }

    public void setDelta(List<String> delta) {
        this.delta = delta;
    }
}
