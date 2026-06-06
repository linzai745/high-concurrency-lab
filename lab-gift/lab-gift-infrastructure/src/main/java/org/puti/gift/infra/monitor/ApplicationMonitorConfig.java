package org.puti.gift.infra.monitor;

import io.lettuce.core.metrics.MicrometerCommandLatencyRecorder;
import io.lettuce.core.metrics.MicrometerOptions;
import io.lettuce.core.resource.ClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.puti.gift.infra.properties.PushGatewayProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 压测监控配置：开启后将本地 Prometheus 指标通过 Push Gateway 推送到远端。
 *
 * <p>公共标签 application 由 {@code management.metrics.tags} 统一注入。</p>
 *
 * @author alin
 */
@Configuration
@EnableConfigurationProperties(PushGatewayProperties.class)
public class ApplicationMonitorConfig {

    @Bean
    @ConditionalOnProperty(prefix = "lab.monitor.push-gateway", name = "enabled", havingValue = "true")
    PrometheusPushGatewayPusher prometheusPushGatewayPusher(PrometheusMeterRegistry registry,
                                                            PushGatewayProperties properties) {
        return new PrometheusPushGatewayPusher(registry, properties);
    }

    /**
     * 开启 Lettuce 命令级监控：产出 lettuce_command_completion_seconds（命令耗时/吞吐）。
     *
     * <p>Spring Boot 的 Lettuce 自动装配会自动使用容器内的 {@link ClientResources} Bean。
     * histogram=true 输出百分位直方图桶，服务端可聚合 Redis 命令 p95/p99。</p>
     */
    @Bean(destroyMethod = "shutdown")
    ClientResources lettuceClientResources(MeterRegistry meterRegistry) {
        MicrometerOptions options = MicrometerOptions.builder()
                .histogram(true)
                .build();
        return ClientResources.builder()
                .commandLatencyRecorder(new MicrometerCommandLatencyRecorder(meterRegistry, options))
                .build();
    }
}
