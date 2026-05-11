# 高并发礼物打赏系统性能优化演进规划文档

## 1. 演进原则

本地性能优化不一次性堆功能，而是按瓶颈逐步演进。每个版本只验证一个主要假设，并保留压测数据作为下一阶段判断依据。

核心原则：

```text
1. 先建立基准，再做优化；
2. 先优化读路径，再优化写路径；
3. 先拆 Gift 库存热点，再处理用户账户热点；
4. 先保证一致性闭环，再追求更高吞吐；
5. 每个优化都必须有开关、压测脚本、指标和回滚方式；
6. 不用平均 RT 掩盖长尾，P95/P99 和失败原因必须单独分析。
```

***

## 2. 当前实验结论摘要

已有报告的核心结论：

```text
V1 DB Only：
所有读写都走 MySQL，热点 Gift 和热点用户场景下行锁竞争严重。

V2 Cache Preload：
Gift / LiveRoom 读缓存能改善分散写，但不能解决热点写。

V3 Redis Stock Pre-Deduct：
关闭 MySQL 主链路库存扣减后，热点 Gift 的失败率明显下降；
新的主要瓶颈转移到 user_account 余额扣减。
```

V3 报告中已经证明：

```text
1. Redis 预扣可以替代主链路 MySQL 单行库存扣减；
2. 如果仍然同步 UPDATE gift.stock，热点库存行锁仍然存在；
3. 当 giftId 热点被拆掉后，热点 userId 会成为新的瓶颈；
4. V3-B 必须配套库存预占、同步、补偿、校准闭环。
```

***

## 3. 版本路线图

```text
V0  环境与观测基线
V1  DB Only 同步事务基准版
V2  Cache Preload 读路径优化版
V3  Redis Stock Pre-Deduct 库存预扣版
V3.1 库存最终一致性闭环版
V4  User Account Keyed Guard + Request Combiner 账户热点保护版
V5  Outbox / MQ 削峰异步化版
V6  多实例分区路由与生产化治理版
```

***

## 4. V0：环境与观测基线

### 4.1 目标

建立稳定可复现的本地压测环境，避免后续优化结论被环境噪声污染。

### 4.2 工作项

```text
1. 固定 JDK、MySQL、Redis、k6 版本；
2. 固定 MySQL 连接池参数；
3. 固定初始数据规模；
4. 固定压测脚本参数；
5. 打开 Actuator metrics；
6. 记录每次测试的应用配置开关；
7. 准备数据库重置和 Redis 库存初始化流程。
```

### 4.3 验收标准

```text
1. 同一脚本连续执行 3 次，RPS 和 P95 波动在可解释范围内；
2. 能区分业务失败、超时失败、连接池失败、行锁等待失败；
3. 每次压测后可以恢复到同一份基准数据。
```

***

## 5. V1：DB Only 同步事务基准版

### 5.1 目标

建立最简单的一致性模型和性能基准。

### 5.2 链路

```text
查订单 DB
查 Gift DB
查 LiveRoom DB
扣余额 DB
扣库存 DB
写订单 DB
写账户流水 DB
```

### 5.3 验证点

```text
1. requestId 唯一索引能否防重复订单；
2. user_account 条件更新能否防超扣；
3. gift 条件更新能否防超卖；
4. 热点 Gift 是否出现行锁等待；
5. 热点用户是否出现行锁等待；
6. 连接池是否被事务阻塞耗尽。
```

### 5.4 退出标准

```text
1. 得到 DB Only 的分散写和热点写基准数据；
2. 明确主要瓶颈是 MySQL 写锁和事务堆积；
3. 保留报告：gift-send-db-only-report.md。
```

***

## 6. V2：Cache Preload 读路径优化版

### 6.1 目标

减少 Gift / LiveRoom 这类读多写少数据对 MySQL 的访问。

### 6.2 优化内容

```text
1. Gift 基础信息走本地缓存；
2. LiveRoom 状态走本地缓存；
3. 余额扣减仍然走 MySQL；
4. 库存扣减仍然走 MySQL；
5. 订单和账户流水仍然同步写 MySQL。
```

### 6.3 压测场景

```text
分散写：1000 用户，100 礼物；
热点写：5 用户，1 礼物。
```

### 6.4 预期结论

```text
1. 分散写平均 RT 和 P95 应下降；
2. 热点写仍然会受 gift.stock 和 user_account 行锁影响；
3. 如果热点写失败率下降但 RT 上升，说明更多请求进入了锁等待队列。
```

### 6.5 退出标准

```text
1. 明确缓存前置只能优化读路径；
2. 明确下一阶段必须进入库存写路径优化；
3. 保留报告：gift-send-cache-preload-report.md。
```

***

## 7. V3：Redis Stock Pre-Deduct 库存预扣版

### 7.1 目标

把热点 Gift 库存扣减从 MySQL 主链路中移出。

### 7.2 子模式

|模式|Redis 预扣|MySQL 主链路扣库存|用途|
|---|---|---|---|
|V3-A|是|是|验证只加 Redis 但保留 MySQL 扣库存是否有效|
|V3-B|是|否|验证热点 Gift 行锁移除后的性能变化|

### 7.3 优化内容

```text
1. Redis 使用 Lua 原子预扣库存；
2. Redis 预扣使用 requestId 做幂等；
3. 预扣成功后进入 MySQL 交易事务；
4. 事务失败时回补 Redis；
5. V3-B 中主链路不再 UPDATE gift.stock。
```

### 7.4 压测矩阵

|场景|用户分布|礼物分布|验证目标|
|---|---|---|---|
|分散写|1000 用户|100 礼物|验证正常分散写吞吐|
|热点 Gift|1000 用户|1 礼物|验证库存热点是否消除|
|热点用户|5 用户|100 礼物|验证账户热点是否成为瓶颈|
|双热点|5 用户|1 礼物|验证极端排队与失败表现|

### 7.5 退出标准

```text
1. V3-A 证明保留 MySQL 库存扣减仍会锁竞争；
2. V3-B 证明关闭 MySQL 库存扣减后热点 Gift 明显改善；
3. 明确新瓶颈转移到账户余额扣减；
4. 保留报告：gift-send-redis-stock-report.md。
```

***

## 8. V3.1：库存最终一致性闭环版

### 8.1 目标

补齐 V3-B 的一致性闭环，让 Redis 实时库存和 MySQL 库存账户可以长期运行、可恢复、可校准。

### 8.2 工作项

```text
1. 完善 gift_stock_reservation 状态流转；
2. 完善 gift_stock_account 作为 MySQL 库存账户；
3. 订单事务中确认库存预占；
4. GiftStockSyncJob 按 giftId 聚合同步 MySQL 库存账户；
5. GiftStockSyncRecoverJob 恢复异常同步批次；
6. GiftStockReservationCompensateJob 回补超时预占；
7. GiftStockCheckJob 周期校准 Redis 与 MySQL 库存账户；
8. 压测时区分后台任务开启和关闭两组数据。
```

### 8.3 关键状态流

```text
INIT
↓ Redis 预扣成功
RESERVED
↓ 订单事务成功
CONFIRMED
↓ 后台同步开始
SYNCING
↓ MySQL 库存账户扣减成功
SYNCED
```

失败补偿流：

```text
RESERVED
↓ 订单事务失败或预占超时
RELEASED
↓ Redis 已回补
终态
```

### 8.4 压测重点

```text
1. 后台同步是否影响主链路 P95；
2. reservation 积压数量是否稳定；
3. Redis / MySQL 库存 diff 是否归零或保持可解释；
4. 应用中断后是否能通过补偿任务恢复。
```

### 8.5 退出标准

```text
1. 连续压测后无库存超卖；
2. Redis 与 MySQL 库存账户差异可解释；
3. 超时预占能够自动回补；
4. 同步失败批次能够恢复；
5. 形成 V3.1 报告。
```

***

## 9. V4：User Account Keyed Guard + Request Combiner 账户热点保护版

### 9.1 目标

缓解少量用户高频打赏导致的 `user_account` 单行锁竞争。

### 9.2 优化方案

V4 不再采用 `hash(userId) % shardCount` 后每个 shard 单线程的方案。这个方案会把不同 userId 错误串行化，一个热点用户还会拖慢同 shard 的普通用户。

推荐方案是同 userId 精确并发保护：

```text
userId
↓
获取 userId 维度的并发保护对象
↓
同一个 userId 只允许 1 个余额扣减链路进入
↓
不同 userId 进入共享线程池并行执行
↓
超出 per-user 队列容量后快速失败或返回稍后重试
```

在此基础上，V4 的优化目标可以拆成两个阶段：

```text
V4.1 User Account Keyed Guard：
只保证同 userId 精确串行，不让不同 userId 互相阻塞。

V4.2 User Account Request Combiner：
在同 userId 内做短窗口请求合并，把多次余额扣减合并为一次账户行更新。
```

V4.2 的核心思想是：同一个用户连续发起多次打赏时，不让每个请求都执行一次：

```sql
UPDATE user_account
SET balance = balance - ?
WHERE user_id = ?
  AND balance >= ?;
```

而是在很短的等待窗口内聚合成一个 batch，只对 `user_account` 执行一次余额扣减。订单和流水仍然逐请求写入，不合并业务订单。

```text
请求 A：userId=1, amount=100
请求 B：userId=1, amount=200
请求 C：userId=1, amount=100
↓
合并窗口 10ms
↓
batchTotalAmount = 400
↓
UPDATE user_account SET balance = balance - 400 WHERE user_id = 1 AND balance >= 400
↓
分别写 3 条 gift_order
分别写 3 条 account_flow
分别返回 3 个 orderNo
```

不推荐方案：

```text
userId
↓
hash(userId) % shardCount
↓
固定 shard 单线程队列
```

### 9.3 设计取舍

```text
优点：
1. 不改变余额以 MySQL 为准；
2. 同一 userId 在应用内有序，减少 DB 行锁无效竞争；
3. 不同 userId 不互相阻塞，保留正常并发能力；
4. 可以通过 per-user queueCapacity 和 timeoutMillis 做热点用户保护；
5. 本地实现成本低，适合作为 V4 实验。

限制：
1. 只能解决单实例内的同 userId 并发；
2. 多实例部署时，同一 userId 仍可能落到不同实例；
3. 热点用户吞吐上限仍受单用户余额扣减串行化限制；
4. 队列等待时间可能转化为接口 RT，需要配合限流和超时；
5. per-user 锁或队列对象需要生命周期管理，避免高基数用户造成内存堆积。
```

### 9.4 请求合并设计

#### 9.4.1 合并边界

请求合并只合并账户扣减，不合并订单、不合并库存、不合并 requestId。

```text
可以合并：
1. 同 userId 的余额扣减 SQL；
2. 同 userId 的账户行锁获取；
3. 同事务内的订单批量插入；
4. 同事务内的账户流水批量插入；
5. 同事务内的库存预占确认批量更新。

不能合并：
1. 不同 userId 的请求；
2. 不同 requestId 的幂等语义；
3. 不同 giftId 的 Redis 库存预扣；
4. 不同请求的订单号；
5. 不同请求的成功/失败响应。
```

#### 9.4.2 推荐链路

```text
请求进入
↓
requestId 幂等检查
↓
Gift / LiveRoom 缓存读取
↓
领域校验
↓
Redis 库存预扣
↓
创建或更新 GiftStockReservation
↓
进入 userId 对应的 combiner
↓
达到 maxBatchSize 或 maxWaitMillis
↓
flush batch
↓
MySQL 事务
    ├── 一次性扣减 user_account.balance
    ├── 批量写 gift_order
    ├── 批量写 account_flow
    └── 批量确认 gift_stock_reservation
↓
逐个完成响应
```

#### 9.4.3 触发条件

```text
1. maxBatchSize：例如 32 或 64，达到数量立即 flush；
2. maxWaitMillis：例如 5ms ~ 20ms，避免无限等待；
3. perUserQueueCapacity：例如 100，超过后对该 userId 快速失败；
4. requestTimeoutMillis：请求等待 batch 结果的最大时间。
```

本地实验建议从保守参数开始：

```yaml
lab:
  gift:
    account-combine:
      enabled: true
      max-batch-size: 32
      max-wait-millis: 10
      per-user-queue-capacity: 100
      request-timeout-millis: 30000
```

#### 9.4.4 余额不足策略

余额充足时，直接一次性扣整个 batch：

```sql
UPDATE user_account
SET balance = balance - #{batchTotalAmount},
    update_time = NOW()
WHERE user_id = #{userId}
  AND balance >= #{batchTotalAmount};
```

余额不足时，不建议全 batch 失败。推荐按到达顺序做前缀成功：

```text
1. SELECT balance FROM user_account WHERE user_id = ? FOR UPDATE；
2. 按 batch 内请求到达顺序累计 amount；
3. 余额足够的请求成功；
4. 余额不足后的请求失败；
5. 成功请求写订单、流水、确认库存预占；
6. 失败请求回补 Redis 库存、释放库存预占。
```

这样可以保证先到先得，避免一个后来的大额请求影响前面的小额请求。

#### 9.4.5 幂等与重试

```text
1. 请求进入 combiner 前先查 gift_order.request_id；
2. 同一个 requestId 如果已经在本地 batch 中，复用同一个 future；
3. batch 提交成功后，重试请求通过 gift_order 查询返回已有 orderNo；
4. batch 失败但 Redis 已预扣时，必须回补 Redis 并释放 reservation；
5. 应用宕机导致未完成的 RESERVED reservation，由补偿任务回补。
```

#### 9.4.6 文档级伪代码

以下代码只表达设计，不改动当前项目代码。

```java
public CompletableFuture<SendGiftResponse> submit(SendGiftPreparedCommand command) {
    CompletableFuture<SendGiftResponse> existed = inflightByRequestId.get(command.requestId());
    if (existed != null) {
        return existed;
    }

    CompletableFuture<SendGiftResponse> future = new CompletableFuture<>();
    CompletableFuture<SendGiftResponse> raced = inflightByRequestId.putIfAbsent(command.requestId(), future);
    if (raced != null) {
        return raced;
    }

    UserCombineBuffer buffer = buffers.computeIfAbsent(
            command.userId(),
            UserCombineBuffer::new
    );
    buffer.add(command, future);
    return future.whenComplete((result, error) -> inflightByRequestId.remove(command.requestId()));
}
```

```java
@Transactional(rollbackFor = Exception.class)
public BatchExecutionResult flushInTransaction(Long userId, List<BatchItem> batch) {
    long totalAmount = batch.stream()
            .mapToLong(item -> item.command().totalAmount())
            .sum();

    if (accountGateway.deductBalance(userId, totalAmount)) {
        saveOrdersFlowsAndConfirmReservations(batch);
        return BatchExecutionResult.allSuccess(batch);
    }

    long balance = accountGateway.selectBalanceForUpdate(userId);
    List<BatchItem> accepted = selectAcceptedPrefix(batch, balance);
    List<BatchItem> rejected = selectRejectedSuffix(batch, accepted.size());

    if (!accepted.isEmpty()) {
        long acceptedAmount = sumAmount(accepted);
        accountGateway.forceDeductLocked(userId, acceptedAmount);
        saveOrdersFlowsAndConfirmReservations(accepted);
    }

    return BatchExecutionResult.partialSuccess(accepted, rejected, "BALANCE_NOT_ENOUGH");
}
```

事务外再完成 future 和 Redis 回补：

```java
public void flush(Long userId, List<BatchItem> batch) {
    try {
        BatchExecutionResult result = accountBatchService.flushInTransaction(userId, batch);
        completeSuccessFutures(result.accepted());
        rollbackRedisStockAndCompleteFailedFutures(result.rejected());
    } catch (Throwable ex) {
        rollbackRedisStockAndCompleteFailedFutures(batch);
    }
}
```

### 9.5 压测场景

|场景|配置|目标|
|---|---|---|
|V4-A|account guard 关闭|作为对照组|
|V4-B|per-user-max-in-flight=1|观察热点用户 P95 和失败率|
|V4-C|per-user-queue-capacity 较小|验证快速失败和保护能力|
|V4-D|固定 shard 单线程队列|反例对照，验证不同用户被误串行化|
|V4-E|account combiner 开启，maxWaitMillis=10|验证同用户请求合并收益|
|V4-F|account combiner 开启，不同用户分散|验证不误伤正常并发|
|V4-G|account combiner 开启，余额不足|验证前缀成功和库存回补|

### 9.6 观测指标

```text
1. 热点 userId in-flight 数；
2. 热点 userId 队列长度；
3. per-user 队列等待超时次数；
4. per-user 队列拒绝次数；
5. user_account 更新耗时；
6. Hikari 活跃连接数和等待连接数；
7. 接口 P95/P99；
8. 业务成功率。
9. account batch size avg / p95；
10. account batch flush 次数；
11. 单请求平均 batch 等待时间；
12. batch 一次性扣减成功率；
13. batch 余额不足拆分次数；
14. batch 失败后的 Redis 回补次数。
```

### 9.7 退出标准

```text
1. 热点用户场景下 MySQL 行锁等待下降；
2. Hikari 连接占用更稳定；
3. 失败原因从 DB 锁等待转为可控的同 userId 队列拒绝或超时；
4. 明确单用户串行化吞吐上限；
5. 分散用户场景吞吐不能因为 guard 明显下降；
6. combiner 开启后，热点用户场景下 user_account 更新次数明显少于成功请求数；
7. combiner 开启后，分散用户场景 P95 不能明显恶化；
8. 余额不足场景下没有超扣，失败请求的 Redis 库存能回补；
9. 形成 V4 报告。
```

***

## 10. V5：Outbox / MQ 削峰异步化版

### 10.1 目标

把主链路中非强同步反馈的写入动作异步化，降低接口 RT 和数据库瞬时写入压力。

### 10.2 推荐演进方向

```text
1. 引入 Outbox 表，先不依赖真实 MQ；
2. 订单事务内写业务表和 outbox_event；
3. 本地 job 扫描 outbox_event 并投递或执行异步动作；
4. 后续可替换为 RocketMQ / Kafka / RabbitMQ；
5. 对用户维度使用 key 保证同 userId 有序，同时消费者侧要支持不同 userId 并行处理。
```

### 10.3 可异步化对象

```text
1. 主播收益入账；
2. 礼物榜单统计；
3. 直播间热度增加；
4. 用户行为事件；
5. 账户对账事件；
6. 库存同步事件。
```

账户余额扣减是否异步化需要谨慎。若接口必须实时返回余额是否足够，余额扣减仍应保留同步确认；若产品允许“处理中”，才能改为完全异步扣减。

### 10.4 退出标准

```text
1. 主链路写入表数量下降；
2. 接口平均 RT 和 P95 下降；
3. outbox 积压可观测；
4. 异步失败可重试；
5. 重复消费不会造成重复入账或重复统计。
```

***

## 11. V6：多实例分区路由与生产化治理版

### 11.1 目标

把本地单实例优化方案扩展到多实例部署思路。

### 11.2 重点问题

```text
1. 同 userId 请求如何路由到同一执行单元；
2. 同 giftId 库存预扣是否需要 Redis Cluster hash tag；
3. 同 userId 精确并发保护如何迁移到一致性路由、分布式 keyed guard 或 keyed MQ 消费；
4. 库存同步任务如何避免多实例重复执行；
5. 定时任务如何做分布式锁或任务分片；
6. 幂等键、订单号、批次号如何全局唯一；
7. 限流、熔断、降级、告警如何补齐。
```

### 11.3 推荐方向

```text
1. 接入层按 userId 做一致性路由，或使用支持同 key 有序、不同 key 并行的 MQ 消费模型；
2. 库存预扣继续走 Redis Lua；
3. 账户扣减可以演进为账户事件流，只对同 userId 串行，不把不同 userId 固定串在同一消费线程；
4. 后台任务使用 ShedLock、数据库抢占、任务分片或调度平台；
5. 所有外部副作用使用幂等表或唯一业务键兜底。
```

***

## 12. 每轮压测执行模板

每次版本压测都按同一模板记录，保证报告可对比。

```text
1. 版本号：
2. Git commit / 本地变更说明：
3. 应用配置：
4. MySQL 配置：
5. Redis 配置：
6. 初始数据：
7. k6 脚本：
8. 并发模型：
9. 持续时间：
10. 总请求数：
11. 成功请求数：
12. 失败请求数：
13. RPS：
14. 平均 RT：
15. P90：
16. P95：
17. P99：
18. 最大 RT：
19. 主要错误：
20. MySQL 行锁/慢 SQL：
21. Hikari 指标：
22. Redis 指标：
23. 后台任务指标：
24. 结论：
25. 下一步：
```

***

## 13. 本地执行顺序建议

### 13.1 短期

```text
1. 固化 V3-B 配置：redis-stock + mysql-deduct-enabled=false；
2. 补齐并验证 V3.1 库存闭环；
3. 对后台库存同步任务做开关对照压测；
4. 基于 gift-send-v4-hot-user.js 压测账户热点；
5. 输出 V4 User Account Keyed Guard + Request Combiner 报告。
```

### 13.2 中期

```text
1. 增加热点 userId in-flight、per-user 队列和 account batch 指标；
2. 增加固定 shard 单线程队列反例对照实验；
3. 增加 per-user queueCapacity、maxBatchSize、maxWaitMillis 和 timeoutMillis 调参实验；
4. 增加 DB 连接池参数对照实验；
5. 引入 Outbox 表，为 MQ 化做准备。
```

### 13.3 后期

```text
1. 将库存同步从定时扫描演进为事件驱动；
2. 将账户热点从本地 keyed guard 演进为一致性路由、分布式 keyed guard 或 keyed MQ 消费；
3. 增加多实例压测；
4. 增加限流、降级、熔断和告警；
5. 形成完整高并发打赏实验系列文档。
```

***

## 14. 版本验收总表

|版本|核心瓶颈|优化动作|验收重点|
|---|---|---|---|
|V0|环境不稳定|固定环境和观测|结果可复现|
|V1|MySQL 全链路压力|建立 DB Only 基准|不超卖、不超扣、识别锁竞争|
|V2|读路径 DB 压力|Gift / LiveRoom 缓存|分散写 RT 下降|
|V3|热点 Gift 库存锁|Redis 预扣库存|热点 Gift 成功率提升|
|V3.1|库存最终一致性|同步、补偿、校准|Redis / MySQL 库存可解释|
|V4|热点账户余额锁|同 userId 精确并发保护 + 请求合并|DB 锁等待下降，不误伤不同用户并发，账户更新次数下降|
|V5|主链路写入过重|Outbox / MQ 异步化|RT 下降，异步可重试|
|V6|多实例有序性|分区路由和治理|跨实例一致性与稳定性|

***

## 15. 下一步落地清单

优先执行以下事项：

```text
1. 新增 V3.1 库存闭环压测报告；
2. 对 GiftStockSyncJob 开启/关闭分别压测，确认后台任务对主链路影响；
3. 使用 gift-send-v4-hot-user.js 对 account guard 开关做对照测试；
4. 补充热点 userId in-flight、per-user queue、拒绝和超时 metrics；
5. 输出 V4 热点用户优化报告；
6. 再决定是否进入 Outbox / MQ 演进。
```
