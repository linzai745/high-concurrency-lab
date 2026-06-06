package org.puti.gift.infra.monitor;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.puti.gift.infra.properties.PushGatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 定时把本地 Prometheus 指标抓取后，以 Push Gateway 协议推送到远端。
 *
 * <p>采用 Push Gateway 标准路径：{@code <baseUrl>/metrics/job/<job>/instance/<instance>}，
 * 使用 PUT 方法覆盖同一分组，避免压测过程中残留过期时间序列。</p>
 *
 * @author alin
 */
public class PrometheusPushGatewayPusher {

    private static final Logger log = LoggerFactory.getLogger(PrometheusPushGatewayPusher.class);

    /** Prometheus 文本曝光格式 Content-Type */
    private static final String TEXT_CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    private final PrometheusMeterRegistry registry;
    private final PushGatewayProperties properties;
    private final HttpClient httpClient;
    private final String pushUri;

    public PrometheusPushGatewayPusher(PrometheusMeterRegistry registry, PushGatewayProperties properties) {
        this.registry = registry;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.pushUri = buildPushUri(properties);
        log.info("Prometheus Push Gateway 推送已启用, target={}, intervalMs={}",
                pushUri, properties.getPushIntervalMs());
    }

    @Scheduled(fixedRateString = "${lab.monitor.push-gateway.push-interval-ms:15000}")
    public void push() {
        // 强制使用经典 Prometheus 文本格式(0.0.4)，与 Content-Type 对齐，
        // 避免新版 client 默认输出 OpenMetrics(含 _created / # EOF / native histogram)
        // 导致部分网关只解析了简单 series、丢掉 jvm/tomcat 等指标。
        String body = registry.scrape(TEXT_CONTENT_TYPE);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(pushUri))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", TEXT_CONTENT_TYPE)
                    .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            applyAuth(builder);

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                log.debug("推送指标成功, status={}", status);
            } else {
                log.warn("推送指标失败, status={}, body={}", status, response.body());
            }
        } catch (Exception e) {
            log.warn("推送指标异常, target={}", pushUri, e);
        }
    }

    private void applyAuth(HttpRequest.Builder builder) {
        if (properties.getAuthorization() != null && !properties.getAuthorization().isBlank()) {
            builder.header("Authorization", properties.getAuthorization());
            return;
        }
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            String token = properties.getUsername() + ":"
                    + (properties.getPassword() == null ? "" : properties.getPassword());
            String encoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        }
    }

    private static String buildPushUri(PushGatewayProperties properties) {
        String base = properties.getBaseUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException("lab.monitor.push-gateway.base-url 不能为空");
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base
                + "/metrics/job/" + encode(properties.getJob())
                + "/instance/" + encode(resolveInstance(properties));
    }

    private static String resolveInstance(PushGatewayProperties properties) {
        if (properties.getInstance() != null && !properties.getInstance().isBlank()) {
            return properties.getInstance();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    private static String encode(String value) {
        // Push Gateway 路径分组键不允许包含 '/'，统一做 URL 编码
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
