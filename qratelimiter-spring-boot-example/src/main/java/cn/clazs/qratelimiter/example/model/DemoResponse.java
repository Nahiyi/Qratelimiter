package cn.clazs.qratelimiter.example.model;

import cn.clazs.qratelimiter.properties.RateLimiterProperties;

public class DemoResponse {

    private String scenario;
    private String key;
    private String message;
    private String algorithm;
    private String storage;
    private long timestamp;

    public static DemoResponse of(String scenario, String key, String message, RateLimiterProperties properties) {
        DemoResponse response = new DemoResponse();
        response.setScenario(scenario);
        response.setKey(key);
        response.setMessage(message);
        response.setAlgorithm(properties.getAlgorithm().name());
        response.setStorage(properties.getStorage().name());
        response.setTimestamp(System.currentTimeMillis());
        return response;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
