package org.puti.gift.app.support.account;

import org.puti.gift.app.response.SendGiftResponse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class UserBatchBuffer {

    private final Long userId;
    private final int maxBatchSize;
    private final long maxWaitMillis;
    private final int perUserQueueCapacity;
    private final ExecutorService workerPool;
    private final ScheduledExecutorService scheduler;
    private final UserAccountBatchTransactionService batchTransactionService;
    private final Consumer<PreparedSendGiftCommand> rollbackAction;

    private final Queue<BatchItem> queue = new ArrayDeque<>();

    private boolean flushing;
    private boolean flushScheduled;

    UserBatchBuffer(
            Long userId,
            int maxBatchSize,
            long maxWaitMillis,
            int perUserQueueCapacity,
            ExecutorService workerPool,
            ScheduledExecutorService scheduler,
            UserAccountBatchTransactionService batchTransactionService,
            Consumer<PreparedSendGiftCommand> rollbackAction
    ) {
        this.userId = userId;
        this.maxBatchSize = maxBatchSize;
        this.maxWaitMillis = maxWaitMillis;
        this.perUserQueueCapacity = perUserQueueCapacity;
        this.workerPool = workerPool;
        this.scheduler = scheduler;
        this.batchTransactionService = batchTransactionService;
        this.rollbackAction = rollbackAction;
    }

    synchronized CompletableFuture<SendGiftResponse> add(PreparedSendGiftCommand prepared) {
        if (queue.size() >= perUserQueueCapacity) {
            CompletableFuture<SendGiftResponse> rejected = new CompletableFuture<>();
            try {
                rollbackAction.accept(prepared);
                rejected.completeExceptionally(new IllegalStateException("账户请求过于频繁, userId=" + userId));
            } catch (Throwable ex) {
                rejected.completeExceptionally(ex);
            }
            return rejected;
        }

        BatchItem item = new BatchItem(prepared);
        queue.add(item);

        if (queue.size() >= maxBatchSize) {
            triggerFlush();
        } else {
            scheduleFlushIfNeeded();
        }

        return item.future();
    }

    private void scheduleFlushIfNeeded() {
        if (flushScheduled || flushing) {
            return;
        }

        flushScheduled = true;
        scheduler.schedule(() -> {
            synchronized (UserBatchBuffer.this) {
                flushScheduled = false;
                triggerFlush();
            }
        }, maxWaitMillis, TimeUnit.MILLISECONDS);
    }

    private void triggerFlush() {
        if (flushing || queue.isEmpty()) {
            return;
        }

        flushing = true;
        List<BatchItem> batch = drainQueue();

        workerPool.execute(() -> {
            try {
                List<PreparedSendGiftCommand> preparedList = batch.stream()
                        .map(BatchItem::prepared)
                        .toList();

                BatchExecutionResult result = batchTransactionService.executeBatch(userId, preparedList);
                completeBatch(batch, result);
            } catch (Throwable ex) {
                failWholeBatch(batch, ex);
            } finally {
                onFlushFinished();
            }
        });
    }

    private List<BatchItem> drainQueue() {
        List<BatchItem> batch = new ArrayList<>(queue.size());
        while (!queue.isEmpty()) {
            batch.add(queue.poll());
        }
        return batch;
    }

    private void completeBatch(List<BatchItem> batch, BatchExecutionResult result) {
        Map<String, BatchSuccess> successMap = result.successes().stream()
                .collect(Collectors.toMap(BatchSuccess::requestId, Function.identity()));
        Map<String, BatchFailure> failureMap = result.failures().stream()
                .collect(Collectors.toMap(BatchFailure::requestId, Function.identity()));

        for (BatchItem item : batch) {
            String requestId = item.prepared().requestId();
            BatchSuccess success = successMap.get(requestId);
            if (success != null) {
                item.future().complete(SendGiftResponse.success(success.orderNo(), false));
                continue;
            }

            BatchFailure failure = failureMap.get(requestId);
            Throwable rollbackError = rollbackSafely(item.prepared());
            String reason = failure == null ? "账户扣减批处理结果缺失" : failure.reason();
            RuntimeException resultError = new RuntimeException(reason);
            if (rollbackError != null) {
                resultError.addSuppressed(rollbackError);
            }
            item.future().completeExceptionally(resultError);
        }
    }

    private void failWholeBatch(List<BatchItem> batch, Throwable ex) {
        for (BatchItem item : batch) {
            Throwable rollbackError = rollbackSafely(item.prepared());
            if (rollbackError != null) {
                ex.addSuppressed(rollbackError);
            }
            item.future().completeExceptionally(ex);
        }
    }

    private Throwable rollbackSafely(PreparedSendGiftCommand prepared) {
        try {
            rollbackAction.accept(prepared);
            return null;
        } catch (Throwable ex) {
            return ex;
        }
    }

    private void onFlushFinished() {
        synchronized (this) {
            flushing = false;
            if (!queue.isEmpty()) {
                triggerFlush();
            }
        }
    }
}
