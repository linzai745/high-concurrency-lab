package org.puti.gift.app.support.account;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puti.gift.infra.properties.UserAccountShardProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 每个 shard 一个单线程线程池。
 * 同一个 userId 会被路由到固定 shard，从而保证单实例内同 userId 串行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAccountShardDispatcher {
    
    private final UserAccountShardProperties properties;
    
    private final List<ThreadPoolExecutor> shards = new ArrayList<>();
    
    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("UserAccountShard disabled");
            return;
        }
        
        validateProperties();
        
        for (int i = 0; i < properties.getShardCount(); i++) {
            int shardIndex = i;

            ThreadPoolExecutor shard = new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(properties.getQueueCapacity()),
                    runnable -> {
                        Thread thread = new Thread(runnable);
                        thread.setName("account-shard-" + shardIndex);
                        return thread;
                    },
                    new ThreadPoolExecutor.AbortPolicy()
            );
            shards.add(shard);
        }
        log.info("UserAccountShardDispatcher initialized, shardCount={}, queueCapacity={}, timeoutMillis={}",
                properties.getShardCount(),
                properties.getQueueCapacity(),
                properties.getTimeoutMillis());
    }
    
    public <T> T dispatchAndWait(Long userId, Supplier<T> task) {
        if (!properties.isEnabled()) {
            return task.get();
        }
        
        if (userId == null || userId <= 0) {
            throw new RuntimeException("userId非法");
        }
        
        int shardIndex = shardIndex(userId);
        ThreadPoolExecutor shard = shards.get(shardIndex);
        CompletableFuture<T> future = new CompletableFuture<>();
        
        try {
            shard.execute(() -> {
                try {
                    future.complete(task.get());
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException ex) {
            throw new IllegalStateException("账户扣减分片队列已满, userId=" + userId + ", shardIndex=" + shardIndex, ex);
        }

        try {
            return future.get(properties.getTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("账户扣减等待被中断, userId=" + userId + ", shardIndex=" + shardIndex, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("账户扣减执行失败, userId=" + userId + ", shardIndex=" + shardIndex, cause);
        } catch (TimeoutException e) {
            throw new IllegalStateException("账户扣减等待超时, userId=" + userId + ", shardIndex=" + shardIndex, e);
        }
    }

    private int shardIndex(Long userId) {
        return Math.floorMod(userId.hashCode(), properties.getShardCount());
    }
    
    public int queueSize(Long userId) {
        if (!properties.isEnabled()) {
            return 0;
        }
        
        return shards.get(shardIndex(userId)).getQueue().size();
    }
    
    public List<Integer> allQueueSize(Long userId) {
        List<Integer> result = new ArrayList<>();
        
        for (ThreadPoolExecutor shard : shards) {
            result.add(shard.getQueue().size());
        }
        
        return result;
    }
    
    @PreDestroy
    public void shutdown() {
        for (ThreadPoolExecutor shard : shards) {
            shard.shutdown();
        }
    }

    private void validateProperties() {
        if (properties.getShardCount() <= 0) {
            throw new IllegalArgumentException("lab.gift.account-shard.shard-count must be greater than 0");
        }

        if (properties.getQueueCapacity() <= 0) {
            throw new IllegalArgumentException("lab.gift.account-shard.queue-capacity must be greater than 0");
        }

        if (properties.getTimeoutMillis() <= 0) {
            throw new IllegalArgumentException("lab.gift.account-shard.timeout-millis must be greater than 0");
        }
    }
}
