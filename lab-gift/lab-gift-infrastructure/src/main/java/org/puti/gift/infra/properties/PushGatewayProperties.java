package org.puti.gift.infra.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 压测监控指标推送到 Prometheus Push Gateway 的配置。
 *
 * @author alin
 */
@ConfigurationProperties(prefix = "lab.monitor.push-gateway")
public class PushGatewayProperties {

    /** 是否开启 Push Gateway 推送 */
    private boolean enabled = false;

    /** Push Gateway 基础地址（不含 /metrics/job/... 后缀） */
    private String baseUrl;

    /** 任务名，作为 Push Gateway 的 job 分组键 */
    private String job = "lab-gift";

    /** 实例标识，作为 Push Gateway 的 instance 分组键；为空时取主机名 */
    private String instance;

    /** 推送间隔（毫秒） */
    private long pushIntervalMs = 15000;

    /** 可选 Basic Auth 用户名 */
    private String username;

    /** 可选 Basic Auth 密码 */
    private String password;

    /** 可选 Authorization 头（如 Bearer token），优先级高于 Basic Auth */
    private String authorization;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public long getPushIntervalMs() {
        return pushIntervalMs;
    }

    public void setPushIntervalMs(long pushIntervalMs) {
        this.pushIntervalMs = pushIntervalMs;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAuthorization() {
        return authorization;
    }

    public void setAuthorization(String authorization) {
        this.authorization = authorization;
    }
}
