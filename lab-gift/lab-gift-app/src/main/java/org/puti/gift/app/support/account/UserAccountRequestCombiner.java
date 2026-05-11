package org.puti.gift.app.support.account;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puti.gift.app.command.SendGiftCommand;
import org.puti.gift.app.response.SendGiftResponse;
import org.puti.gift.domain.stock.gateway.GiftStockReservationGateway;
import org.puti.gift.domain.stock.gateway.StockPreDeductGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public final class UserAccountRequestCombiner {

    private final UserAccountBatchTransactionService batchTransactionService;
    private final StockPreDeductGateway stockPreDeductGateway;
    private final GiftStockReservationGateway giftStockReservationGateway;

    private final ConcurrentHashMap<Long, UserBatchBuffer> buffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<SendGiftResponse>> inflightRequests = new ConcurrentHashMap<>();
    private final ExecutorService workerPool = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors()),
            namedThreadFactory("account-combiner-worker-")
    );
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            2,
            namedThreadFactory("account-combiner-scheduler-")
    );

    @Value("${lab.gift.account-combiner.enabled:true}")
    private boolean enabled;

    @Value("${lab.gift.account-combiner.max-batch-size:64}")
    private int maxBatchSize;

    @Value("${lab.gift.account-combiner.max-wait-millis:10}")
    private long maxWaitMillis;

    @Value("${lab.gift.account-combiner.per-user-queue-capacity:100}")
    private int perUserQueueCapacity;

    @Value("${lab.gift.account-combiner.request-timeout-millis:30000}")
    private long requestTimeoutMillis;

    @PostConstruct
    public void init() {
        validateProperties();
        log.info("UserAccountRequestCombiner initialized, enabled={}, maxBatchSize={}, maxWaitMillis={}, "
                        + "perUserQueueCapacity={}, requestTimeoutMillis={}",
                enabled,
                maxBatchSize,
                maxWaitMillis,
                perUserQueueCapacity,
                requestTimeoutMillis);
    }

    public SendGiftResponse submitAndWait(PreparedSendGiftCommand prepared) {
        if (!enabled) {
            return executeDirectly(prepared);
        }

        CompletableFuture<SendGiftResponse> marker = new CompletableFuture<>();
        CompletableFuture<SendGiftResponse> existed = inflightRequests.putIfAbsent(prepared.requestId(), marker);
        if (existed != null) {
            return waitFuture(prepared, existed);
        }

        CompletableFuture<SendGiftResponse> future;
        try {
            future = submitToBuffer(prepared);
        } catch (RuntimeException e) {
            inflightRequests.remove(prepared.requestId(), marker);
            throw e;
        }
        future.whenComplete((response, throwable) -> {
            if (throwable == null) {
                marker.complete(response);
            } else {
                marker.completeExceptionally(throwable);
            }
            inflightRequests.remove(prepared.requestId(), marker);
        });

        return waitFuture(prepared, marker);
    }

    private CompletableFuture<SendGiftResponse> submitToBuffer(PreparedSendGiftCommand prepared) {
        UserBatchBuffer buffer = buffers.computeIfAbsent(
                prepared.userId(),
                userId -> new UserBatchBuffer(
                        userId,
                        maxBatchSize,
                        maxWaitMillis,
                        perUserQueueCapacity,
                        workerPool,
                        scheduler,
                        batchTransactionService,
                        this::rollbackRedisStock
                )
        );
        return buffer.add(prepared);
    }

    private SendGiftResponse waitFuture(PreparedSendGiftCommand prepared, CompletableFuture<SendGiftResponse> future) {
        try {
            return future.get(requestTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("账户扣减等待超时, userId=" + prepared.userId(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("账户扣减等待被中断, userId=" + prepared.userId(), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("账户扣减执行失败, userId=" + prepared.userId(), cause);
        }
    }

    private SendGiftResponse executeDirectly(PreparedSendGiftCommand prepared) {
        BatchExecutionResult result;
        try {
            result = batchTransactionService.executeBatch(
                    prepared.userId(),
                    List.of(prepared)
            );
        } catch (RuntimeException e) {
            rollbackRedisStock(prepared);
            throw e;
        }

        if (!result.successes().isEmpty()) {
            BatchSuccess success = result.successes().getFirst();
            return SendGiftResponse.success(success.orderNo(), false);
        }

        rollbackRedisStock(prepared);
        String reason = result.failures().isEmpty() ? "账户扣减失败" : result.failures().getFirst().reason();
        throw new RuntimeException(reason);
    }

    private void rollbackRedisStock(PreparedSendGiftCommand prepared) {
        SendGiftCommand command = prepared.command();
        stockPreDeductGateway.rollback(
                command.getGiftId(),
                command.getRequestId(),
                command.getGiftCount()
        );
        giftStockReservationGateway.release(command.getRequestId());
    }

    private void validateProperties() {
        if (maxBatchSize <= 0) {
            throw new IllegalArgumentException("lab.gift.account-combiner.max-batch-size must be greater than 0");
        }
        if (maxWaitMillis <= 0) {
            throw new IllegalArgumentException("lab.gift.account-combiner.max-wait-millis must be greater than 0");
        }
        if (perUserQueueCapacity <= 0) {
            throw new IllegalArgumentException("lab.gift.account-combiner.per-user-queue-capacity must be greater than 0");
        }
        if (requestTimeoutMillis <= 0) {
            throw new IllegalArgumentException("lab.gift.account-combiner.request-timeout-millis must be greater than 0");
        }
    }

    @PreDestroy
    public void shutdown() {
        workerPool.shutdown();
        scheduler.shutdown();
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger index = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + index.incrementAndGet());
            return thread;
        };
    }
}
