# 高并发礼物打赏系统架构设计文档

## 1. 设计背景

本系统用于模拟直播间礼物打赏场景。用户在直播间内给主播送礼，请求链路需要完成礼物校验、直播间校验、库存扣减、余额扣减、订单创建、账户流水记录、库存同步与对账等动作。

该场景的核心特点是：

```text
1. 写多读少，核心压力集中在扣减与写入链路；
2. 礼物库存可能出现热点，例如所有请求都发送同一个 giftId；
3. 用户账户可能出现热点，例如少量用户高频连续打赏；
4. 请求需要保证不超卖、不超扣、幂等、防重复订单；
5. 本地实验需要能够逐步打开或关闭优化开关，形成可对比的性能演进路径。
```

当前项目以 Java 21、Spring Boot 3、COLA、MyBatis-Plus、MySQL、Redis、k6 为主要技术栈，本地单实例优先，后续再扩展到多实例和 MQ 削峰。

***

## 2. 架构目标

### 2.1 功能目标

```text
1. 支持用户向直播间主播发送指定数量的礼物；
2. 支持请求幂等，同一个 requestId 不重复生成订单；
3. 支持礼物库存防超卖；
4. 支持用户余额防超扣；
5. 支持订单、账户流水、库存流水的可追溯；
6. 支持 Redis 实时库存与 MySQL 库存账户的最终一致性；
7. 支持后台库存同步、补偿、校准；
8. 支持压测模式切换，便于本地性能优化演进。
```

### 2.2 性能目标

本地实验不直接追求生产级绝对指标，而是追求瓶颈可解释、优化可对比、数据可复现。

核心目标：

```text
1. 分散写场景：稳定提升 RPS，降低平均 RT 和 P95；
2. 热点 Gift 场景：消除 MySQL gift 单行库存锁竞争；
3. 热点用户场景：识别并缓解 user_account 单行余额锁竞争；
4. 长尾请求：降低锁等待、连接池等待、后台任务抢占造成的 P95/P99；
5. 一致性：优化过程中不牺牲余额、库存、订单幂等的正确性。
```

***

## 3. 总体架构

目标架构分为接入层、应用层、领域层、基础设施层、异步任务层和压测观测层。

```text
k6 / Client
    ↓
GiftController
    ↓
GiftSendAppService
    ↓
SendGift Executor
    ├── DB Only Executor
    ├── Cache Preload Executor
    └── Redis Stock Executor
            ↓
    UserAccountConcurrencyGuard
            ↓
GiftSendDomainService
            ↓
Infrastructure Gateways
    ├── GiftGateway
    ├── LiveRoomGateway
    ├── AccountGateway
    ├── GiftOrderGateway
    ├── StockPreDeductGateway
    ├── GiftStockReservationGateway
    ├── GiftStockAccountGateway
    └── GiftStockSyncBatchGateway
            ↓
MySQL / Redis
            ↑
Background Jobs
    ├── GiftStockSyncJob
    ├── GiftStockSyncRecoverJob
    ├── GiftStockReservationCompensateJob
    └── GiftStockCheckJob
```

***

## 4. 核心领域模型

### 4.1 Gift

礼物基础信息，包含礼物价格、状态、库存等。早期版本中 `gift.stock` 直接作为实时库存；Redis 预扣版本后，建议逐步弱化 `gift.stock` 的实时扣减职责。

### 4.2 LiveRoom

直播间信息，包含主播、房间状态等。属于读多写少数据，适合本地缓存前置。

### 4.3 UserAccount

用户账户余额。当前余额扣减以 MySQL 条件更新为准：

```sql
UPDATE user_account
SET balance = balance - #{amount},
    update_time = NOW()
WHERE user_id = #{userId}
  AND balance >= #{amount};
```

该方案依赖 InnoDB 行锁和条件更新防止超扣。热点用户场景下，同一个 `user_id` 会串行等待同一行锁。

### 4.4 GiftOrder

送礼订单，记录用户、主播、直播间、礼物、数量、金额和 requestId。`request_id` 必须有唯一索引，作为幂等兜底。

### 4.5 AccountFlow

账户流水，记录余额扣减明细，用于账务追溯和后续对账。

### 4.6 GiftStockReservation

库存预占流水，用于承接 Redis 预扣、订单确认、Redis 回补、MySQL 库存账户同步。

推荐状态模型：

```text
INIT       = 已创建预占单，尚未预扣 Redis
RESERVED   = Redis 已预扣
CONFIRMED  = 订单成功，待同步 MySQL 库存账户
SYNCING    = 正在同步 MySQL 库存账户
SYNCED     = 已同步 MySQL 库存账户
RELEASED   = 已回补 Redis
FAILED     = 失败终态
```

### 4.7 GiftStockAccount

礼物库存账户，作为 MySQL 侧库存持久化口径。Redis 是实时可售库存入口，MySQL 库存账户用于恢复、对账和校准。

***

## 5. 送礼主链路设计

### 5.1 V1 DB Only 链路

```text
请求进入
↓
查 requestId 是否已有订单
↓
查 Gift DB
↓
查 LiveRoom DB
↓
领域校验
↓
MySQL 事务
    ├── 扣 user_account.balance
    ├── 扣 gift.stock
    ├── 写 gift_order
    └── 写 account_flow
↓
返回 orderNo
```

特点：

```text
1. 一致性简单；
2. 所有读写都压在 MySQL；
3. 热点 giftId 会竞争同一行 gift.stock；
4. 热点 userId 会竞争同一行 user_account.balance；
5. 适合作为性能基准，不适合作为高并发目标态。
```

### 5.2 V2 Cache Preload 链路

```text
请求进入
↓
查 requestId 是否已有订单
↓
查 Gift Cache
↓
查 LiveRoom Cache
↓
领域校验
↓
MySQL 事务
    ├── 扣 user_account.balance
    ├── 扣 gift.stock
    ├── 写 gift_order
    └── 写 account_flow
↓
返回 orderNo
```

特点：

```text
1. 读路径 DB 压力下降；
2. 分散写场景有收益；
3. 不改变写路径，热点库存行锁仍然存在；
4. 不能作为热点 Gift 问题的最终解法。
```

### 5.3 V3 Redis Stock Pre-Deduct 链路

```text
请求进入
↓
同 userId 并发保护，可选
↓
查 requestId 是否已有订单
↓
查 Gift Cache
↓
查 LiveRoom Cache
↓
创建或复用 GiftStockReservation
↓
Redis Lua 幂等预扣库存
↓
标记 reservation 为 RESERVED
↓
MySQL 事务
    ├── 扣 user_account.balance
    ├── 可选扣 gift.stock
    ├── 写 gift_order
    ├── 写 account_flow
    └── 不扣 MySQL 库存时确认 reservation
↓
事务成功：返回 orderNo
事务失败：回补 Redis 库存，释放 reservation
```

推荐目标态为 V3-B：

```text
Redis 预扣库存：开启
MySQL 主链路同步扣 gift.stock：关闭
MySQL 库存账户异步同步：开启
```

这样可以把热点 Gift 的实时扣减从 MySQL 单行锁中移出。

### 5.4 V4 User Account Keyed Guard + Request Combiner 链路

V3-B 移除热点 Gift 锁后，瓶颈会转移到账户余额扣减。但不能用 `hash(userId) % shardCount` 后每个 shard 单线程执行的方案。该方案会把同一个 shard 上的不同用户也强制串行，锁粒度从“单用户账户行”扩大成“一组用户”，会错误降低正常用户的并发能力。

V4 推荐改为 `UserAccountConcurrencyGuard`：只限制同一个 userId 的并发，不限制不同 userId 之间的并发。

```text
请求进入
↓
按 userId 获取精确并发保护对象
↓
同 userId 只允许 1 个扣减链路进入
↓
不同 userId 继续进入共享工作线程池并行执行
↓
热点 userId 的重复请求在用户维度排队、快速失败或合并
↓
减少 MySQL 同一账户行上的无效并发竞争
```

在热点用户场景下，可以进一步把 guard 演进为 `UserAccountRequestCombiner`。它不是把多个送礼请求合成一个业务订单，而是只合并同一 userId 的账户扣减动作：

```text
同一 userId 的多个送礼请求
↓
每个请求仍然独立完成 Gift / LiveRoom 校验、Redis 库存预扣、requestId 幂等
↓
进入 userId 维度的短时间合并窗口
↓
按到达顺序组成一个 batch
↓
一次性计算 totalDeductAmount = sum(request.amount)
↓
在一个 MySQL 事务内只更新一次 user_account.balance
↓
为 batch 内每个成功请求分别写 gift_order、account_flow、确认 gift_stock_reservation
↓
每个请求分别返回自己的 orderNo
```

这个方案的关键边界：

```text
1. 合并粒度只能是 userId，不能是 shard；
2. 合并对象是账户扣减 SQL，不是订单语义；
3. 每个 requestId 仍然独立幂等；
4. 每个 giftId 仍然独立 Redis 预扣和回补；
5. 每个请求仍然有独立 gift_order 和 account_flow；
6. batch 等待窗口必须很短，例如 5ms ~ 20ms，避免用吞吐换过高 RT。
```

推荐的 V4 合并链路：

```text
请求进入
↓
查 requestId 已有订单，已有则直接返回
↓
Gift / LiveRoom 缓存读取与领域校验
↓
Redis Lua 幂等预扣库存
↓
创建或标记 GiftStockReservation
↓
投递到 userId 对应的 RequestCombiner
↓
Combiner 按 maxBatchSize 或 maxWaitMillis 触发 flush
↓
Batch Transaction
    ├── 锁定 / 更新 user_account 一次
    ├── 为成功项批量写 gift_order
    ├── 为成功项批量写 account_flow
    └── 为成功项确认 gift_stock_reservation
↓
余额不足或失败项回补 Redis 库存，释放 reservation
↓
逐个完成请求响应
```

余额充足时，batch 可以走最快路径：

```sql
UPDATE user_account
SET balance = balance - #{batchTotalAmount},
    update_time = NOW()
WHERE user_id = #{userId}
  AND balance >= #{batchTotalAmount};
```

如果影响行数为 1，说明整个 batch 的账户扣减成功，随后为每个请求分别写订单和流水。

余额不足时，不能简单让整个 batch 全部失败。更合理的策略是按请求到达顺序做前缀成功：

```text
1. 在事务内 SELECT balance FROM user_account WHERE user_id = ? FOR UPDATE；
2. 按请求进入 batch 的顺序累计 amount；
3. 余额足够的前 N 个请求成功；
4. 从第 N+1 个请求开始返回余额不足；
5. 只扣减成功前缀的 totalAmount；
6. 成功前缀写订单、流水、确认库存预占；
7. 失败请求回补 Redis 库存、释放 reservation。
```

该策略保持“先到先得”的用户语义，避免一个大额请求或余额不足请求拖垮同 batch 内已经可以成功的小额请求。

文档级伪代码如下，只表达设计，不对应当前代码实现：

```java
final class UserAccountRequestCombiner {
    private final ConcurrentHashMap<Long, UserBatchBuffer> buffers = new ConcurrentHashMap<>();
    private final int maxBatchSize = 64;
    private final long maxWaitMillis = 10;

    CompletableFuture<SendGiftResponse> submit(SendGiftPreparedCommand command) {
        UserBatchBuffer buffer = buffers.computeIfAbsent(
                command.userId(),
                userId -> new UserBatchBuffer(userId, maxBatchSize, maxWaitMillis)
        );
        return buffer.add(command);
    }
}

final class UserBatchBuffer {
    private final Long userId;
    private final Queue<BatchItem> queue = new ArrayDeque<>();
    private boolean flushing;

    synchronized CompletableFuture<SendGiftResponse> add(SendGiftPreparedCommand command) {
        CompletableFuture<SendGiftResponse> future = new CompletableFuture<>();
        queue.add(new BatchItem(command, future));

        if (queue.size() >= maxBatchSize) {
            triggerFlush();
        } else {
            scheduleFlushIfNeeded();
        }
        return future;
    }

    private void triggerFlush() {
        if (flushing) {
            return;
        }
        flushing = true;
        List<BatchItem> batch = drainQueue();

        sharedWorkerPool.execute(() -> {
            try {
                BatchExecutionResult result = accountBatchService.executeBatch(userId, batch);
                completeFuturesAndRollbackRejectedStock(result);
            } catch (Throwable ex) {
                rollbackAllRedisStockAndFail(batch, ex);
            } finally {
                onFlushFinished();
            }
        });
    }
}
```

批量事务伪代码：

```java
@Transactional(rollbackFor = Exception.class)
public BatchExecutionResult executeBatch(Long userId, List<BatchItem> batch) {
    long batchTotal = batch.stream()
            .mapToLong(item -> item.command().totalAmount())
            .sum();

    boolean allDeducted = accountGateway.deductBalance(userId, batchTotal);
    if (allDeducted) {
        saveOrdersFlowsAndConfirmReservations(batch);
        return BatchExecutionResult.allSuccess(batch);
    }

    long balance = accountGateway.selectBalanceForUpdate(userId);
    List<BatchItem> accepted = new ArrayList<>();
    List<BatchItem> rejected = new ArrayList<>();
    long acceptedTotal = 0;

    for (BatchItem item : batch) {
        long amount = item.command().totalAmount();
        if (acceptedTotal + amount <= balance) {
            accepted.add(item);
            acceptedTotal += amount;
        } else {
            rejected.add(item);
        }
    }

    if (!accepted.isEmpty()) {
        accountGateway.forceDeductLocked(userId, acceptedTotal);
        saveOrdersFlowsAndConfirmReservations(accepted);
    }

    return BatchExecutionResult.partialSuccess(accepted, rejected, "BALANCE_NOT_ENOUGH");
}
```

失败处理要求：

```text
1. batch 事务方法只返回 BatchExecutionResult，不直接 complete future；
2. batch 事务提交成功后，再让成功项返回各自 orderNo；
3. batch 中余额不足的失败项，在事务提交后回补 Redis 库存并释放 reservation；
4. batch 事务整体异常时，DB 自动回滚，所有已 Redis 预扣的请求都要回补；
5. complete future 必须在事务提交或回滚结果明确之后执行；
6. requestId 重试时，先查订单；如果原请求还在 batch 内，可通过本地 inflight map 复用同一个 future。
```

该方案的定位是本地实验阶段的账户热点保护手段：

```text
1. 不改变余额仍以 MySQL 为准的模型；
2. 只对同一个 userId 做串行化，不让不同用户互相阻塞；
3. 支持 per-user 队列容量、等待超时、快速失败；
4. 热点用户吞吐上限仍由单账户扣减串行化决定；
5. 单实例有效，多实例下需要一致性路由、分布式 keyed guard，或支持同 key 有序且不同 key 并行消费的消息队列模型。
```

明确不推荐的 V4 方案：

```text
userId
↓
hash(userId) % shardCount
↓
固定 shard 单线程队列
```

原因：

```text
1. shard 是粗粒度，不是账户行粒度；
2. 不同 userId 会因为 hash 冲突落到同一个 shard；
3. 一个热点用户会拖慢同 shard 下的其他普通用户；
4. shardCount 越小误伤越明显，shardCount 越大线程和队列管理成本越高；
5. 该方案只能作为压测对照或临时保护，不应作为目标架构。
```

***

## 6. Redis 库存设计

### 6.1 Key 设计

```text
gift:stock:{giftId}
gift:stock:reservation:{requestId}
```

`gift:stock:{giftId}` 表示 Redis 实时可售库存。

`gift:stock:reservation:{requestId}` 表示请求维度的预扣标记，用于 Redis Lua 幂等。

### 6.2 预扣语义

预扣需要同时满足：

```text
1. Redis 库存存在；
2. 当前库存 >= 本次扣减数量；
3. 同一个 requestId 重试时不能重复扣减；
4. 预扣成功但后续事务失败时必须回补；
5. 预扣成功但应用宕机时必须由补偿任务处理。
```

### 6.3 库存口径

```text
Redis 实时库存 = 在线请求判断库存是否可售的入口；
MySQL 库存账户 = 持久化库存账本，用于恢复、校准、审计；
GiftStockReservation = Redis 与 MySQL 库存账户之间的扣减流水。
```

校准公式：

```text
expectedRedisStock = mysqlAvailableStock - pendingSyncCount - reservedNotConfirmedCount
diff = redisAvailableStock - expectedRedisStock
```

当 `diff != 0` 时，需要记录告警，并按实验配置决定是否自动修正。

***

## 7. 一致性设计

### 7.1 请求幂等

第一层：`gift_order.request_id` 唯一索引兜底。

第二层：应用层先根据 requestId 查询已存在订单。

第三层：Redis 库存预扣使用 requestId 作为幂等键，避免重试重复扣库存。

### 7.2 余额一致性

余额扣减继续以 MySQL 条件更新为准：

```text
扣减成功：影响行数 = 1；
余额不足：影响行数 = 0；
并发安全：依赖 InnoDB 行锁与 WHERE balance >= amount 条件。
```

账户流水必须与订单在同一个事务内写入。

### 7.3 库存一致性

库存扣减分为两个阶段：

```text
阶段一：Redis 实时预扣，决定请求是否允许进入交易链路；
阶段二：订单成功后，后台聚合 GiftStockReservation 并扣减 MySQL 库存账户。
```

主链路事务失败时，需要回补 Redis 并释放 reservation。

后台同步失败时，需要保留 reservation 状态，等待恢复任务重新处理。

### 7.4 订单与库存预占一致性

当主链路不再同步扣 MySQL 库存时，订单成功必须确认 reservation：

```text
gift_order 写入成功
account_flow 写入成功
gift_stock_reservation 从 RESERVED 更新为 CONFIRMED
```

这三个动作应在同一个 MySQL 事务内完成。

***

## 8. 异步任务设计

### 8.1 GiftStockSyncJob

职责：

```text
1. 扫描 CONFIRMED 状态的库存预占单；
2. 按 giftId 聚合待同步数量；
3. 创建同步批次；
4. 批量扣减 gift_stock_account；
5. 将 reservation 标记为 SYNCED；
6. 将 batch 标记为成功或失败。
```

核心收益：

```text
把 N 次主链路 UPDATE gift.stock 转换为后台少量聚合 UPDATE。
```

### 8.2 GiftStockSyncRecoverJob

职责：

```text
1. 处理长时间停留在 SYNCING 的同步批次；
2. 判断 MySQL 库存账户是否已经扣减；
3. 修复 batch 和 reservation 状态；
4. 避免重复扣减 MySQL 库存账户。
```

### 8.3 GiftStockReservationCompensateJob

职责：

```text
1. 扫描超时但未确认的 RESERVED 预占单；
2. 回补 Redis 库存；
3. 将 reservation 标记为 RELEASED 或 FAILED；
4. 处理应用在 Redis 预扣后、MySQL 事务前宕机的场景。
```

### 8.4 GiftStockCheckJob

职责：

```text
1. 周期性计算 Redis 理论库存；
2. 对比 Redis 实际库存；
3. 输出 diff；
4. 触发告警或人工校准。
```

***

## 9. 本地部署架构

本地实验建议使用以下组件：

```text
lab-gift-start        Spring Boot 应用
MySQL 8.x             订单、账户、库存账户、流水
Redis                 礼物实时库存、预扣幂等键
k6                    压测工具
Actuator / Prometheus 应用指标
```

关键配置开关：

```yaml
lab:
  gift:
    send-mode: redis-stock
    redis-stock:
      mysql-deduct-enabled: false
      init-on-startup: true
      force-init-on-startup: true
    stock-sync:
      enabled: true
    stock-sync-recover:
      enabled: true
    stock-compensate:
      enabled: true
    stock-check:
      enabled: true
    account-concurrency:
      enabled: true
      per-user-max-in-flight: 1
      per-user-queue-capacity: 20
      timeout-millis: 30000
    account-combine:
      enabled: true
      max-batch-size: 32
      max-wait-millis: 10
      per-user-queue-capacity: 100
      request-timeout-millis: 30000
```

本地压测时需要明确每次实验的开关状态，并在报告中记录。

***

## 10. 压测场景设计

### 10.1 分散写场景

```text
用户数量：1000+
礼物数量：100+
并发模型：constant-vus
持续时间：60s 起
目标：验证系统在自然分散写下的吞吐和稳定性。
```

### 10.2 热点 Gift 场景

```text
用户数量：1000+
礼物数量：1
目标：验证 Redis 预扣是否消除 MySQL 库存单行锁。
```

### 10.3 热点用户场景

```text
用户数量：1 ~ 5
礼物数量：100 或 1
目标：验证 user_account 余额扣减是否成为瓶颈，以及同 userId 精确并发保护是否有效。
```

### 10.4 双热点场景

```text
用户数量：1 ~ 5
礼物数量：1
目标：验证极端场景下系统的排队、超时、失败和降级表现。
```

***

## 11. 观测指标

### 11.1 k6 指标

```text
http_reqs
http_req_failed
http_req_duration avg / med / p90 / p95 / p99
RPS
成功请求数
失败请求数
```

### 11.2 应用指标

```text
接口 RT
Hikari 活跃连接数
Hikari 等待连接数
热点 userId in-flight 数
热点 userId 队列长度
per-user 队列拒绝次数
account batch size avg / p95
account batch flush 次数
单请求平均 batch 等待时间
batch 失败后的 Redis 回补次数
Redis 预扣成功/失败次数
库存回补次数
库存同步批次数
库存同步失败次数
库存校准 diff
```

### 11.3 MySQL 指标

```text
慢 SQL
行锁等待
事务等待时间
连接数
TPS
CPU / IO
gift_order 插入耗时
account_flow 插入耗时
user_account 更新耗时
gift_stock_account 更新耗时
```

***

## 12. 风险与边界

```text
1. Redis 预扣提升性能，但引入最终一致性复杂度；
2. 固定 shard 单线程队列会误伤不同用户并发，不应作为目标方案；
3. 本地压测结果受机器 CPU、MySQL 配置、Redis 配置、后台任务频率影响；
4. force-init-on-startup 只能用于实验环境，生产环境不能强制覆盖 Redis 实时库存；
5. 后台库存同步任务如果频率过高，会与主链路争抢 MySQL 资源；
6. 只看成功率不够，必须同时看 P95/P99 和长尾失败原因。
```

***

## 13. 推荐目标态

本地高并发礼物打赏实验的推荐目标态如下：

```text
1. Gift / LiveRoom 使用本地缓存前置；
2. 礼物实时库存使用 Redis Lua 幂等预扣；
3. 主链路不再同步扣 MySQL gift.stock；
4. 订单、账户扣减、账户流水、库存预占确认在 MySQL 事务内完成；
5. 库存通过 GiftStockReservation 异步聚合同步到 GiftStockAccount；
6. 账户余额仍以 MySQL 为准，本地单实例使用同 userId 精确并发保护缓解热点竞争；
7. 后台补偿和校准任务保证 Redis 与 MySQL 库存账户最终一致；
8. 所有优化通过配置开关控制，便于压测对比和回滚。
```
