package cn.clazs.qratelimiter.spring.fixture;

public class CompatibilityRequest {

    private String userId;
    private String apiType;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getApiType() {
        return apiType;
    }

    public void setApiType(String apiType) {
        this.apiType = apiType;
    }
}
