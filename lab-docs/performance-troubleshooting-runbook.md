# 高并发压测指标与问题排查 Runbook

## 1. 文档目标

本文档用于指导 Gift 高并发送礼系统压测时如何使用 P90、P95、P99、错误率、RPS、资源指标判断系统是否存在问题，并给出可执行的排查步骤。

适用范围：

```text
1. V1 DB Only 同步事务版压测；
2. V2 Cache Preload 缓存前置版压测；
3. V3 Redis Stock Pre-Deduct 库存预扣版压测；
4. 后续账户热点保护、MQ 削峰、多实例压测。
```

核心目标不是只得到“最大 QPS”，而是得到：

```text
在错误率、P95、P99 都可接受的前提下，系统可以稳定支撑的吞吐量。
```

***

## 2. 指标定义和使用方式

### 2.1 P90 / P95 / P99 是什么

以接口响应时间为例：

```text
P90 = 200ms：90% 请求在 200ms 内完成，最慢 10% 请求超过 200ms；
P95 = 500ms：95% 请求在 500ms 内完成，最慢 5% 请求超过 500ms；
P99 = 2s：99% 请求在 2s 内完成，最慢 1% 请求超过 2s。
```

平均 RT 只能看整体趋势，不能代表长尾体验。压测判断必须同时看：

```text
RPS / TPS
平均 RT
P90 / P95 / P99
错误率
资源使用率
队列和等待时间
```

### 2.2 压测判断优先级

压测时优先看以下组合：

```text
第一优先级：错误率
第二优先级：P95 / P99
第三优先级：RPS 是否继续增长
第四优先级：CPU、内存、GC、DB、Redis、线程池、连接池
```

原因：

```text
1. 错误率上升说明系统已经开始不可用；
2. P95 / P99 上升说明大批请求在排队或被阻塞；
3. RPS 不涨但 RT 继续涨，说明系统已到容量拐点；
4. 资源指标用于定位瓶颈在哪一层。
```

***

## 3. 压测前准备清单

每次压测前先固定环境，避免结果不可复现。

### 3.1 记录本次测试信息

在压测报告中记录：

```text
测试时间：
Git commit：
send-mode：
是否开启 Redis 库存预扣：
是否开启 MySQL 库存扣减：
是否开启账户分片队列：
应用实例数：
MySQL 版本：
Redis 版本：
k6 脚本：
VU / duration：
测试数据规模：
```

当前项目关键配置位置：

```text
lab-gift/lab-gift-start/src/main/resources/application.yml
lab-deploy/k6/
```

### 3.2 确认应用观测入口

当前应用端口：

```text
8081
```

Actuator 已暴露：

```text
/actuator/health
/actuator/metrics
/actuator/prometheus
```

检查命令：

```bash
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8081/actuator/metrics
curl -s http://localhost:8081/actuator/prometheus
```

### 3.3 建议每次压测保留的输出

```text
1. k6 终端完整输出；
2. 压测期间应用日志；
3. 压测前后 MySQL 状态；
4. 压测前后 Redis 状态；
5. 如果出现 P99 尖刺，保留线程栈和 GC 信息；
6. 如果出现错误率上升，保留错误响应样本。
```

***

## 4. 压测中如何判断系统有问题

### 4.1 容量拐点判断

出现以下现象时，基本可以判断系统已经接近或超过容量上限：

```text
1. VU 增加后，RPS 不再上升；
2. RPS 基本不变，但平均 RT、P95、P99 持续上升；
3. P99 明显高于 P95，且波动越来越大；
4. 错误率开始上升；
5. 应用线程、DB 连接、Redis 连接、队列开始堆积；
6. CPU 不高但 RT 很高，说明请求大概率在等待锁、IO、连接池或下游服务。
```

容量拐点记录方式：

```text
当 RPS 不再增长，并且 P95/P99 开始快速上升时，记录当前 VU、RPS、P95、P99、错误率。
这个点就是当前配置下的有效容量上限附近。
```

### 4.2 指标现象和初步判断

|现象|优先怀疑方向|下一步动作|
|---|---|---|
|错误率上升，P95/P99 同时上升|系统过载或下游超时|先看错误类型，再看连接池、线程池、DB 锁等待|
|RPS 不涨，RT 持续上涨|请求开始排队|看线程池、Hikari、DB 行锁、Redis 慢命令|
|平均 RT 变化不大，P99 很高|少量请求被长时间阻塞|抓线程栈，查慢 SQL，查 GC，查锁等待|
|CPU 接近 100%，RT 上升|CPU 瓶颈|看热点方法、序列化、日志、加密、循环计算|
|CPU 不高，RT 很高|等待型瓶颈|看 DB、Redis、连接池、锁、队列、网络 IO|
|周期性 RT 尖刺|定时任务或 GC|对齐任务调度时间、GC 日志、库存同步任务|
|热点 Gift 场景 RT 飙升|gift.stock 行锁竞争|查 InnoDB 行锁和 gift 表 UPDATE|
|热点用户场景 RT 飙升|user_account 行锁竞争|查 user_account 条件更新和账户分片队列|

***

## 5. 标准排查流程

### 5.1 第一步：先从 k6 输出定性

重点记录：

```text
http_reqs
http_req_failed
http_req_duration avg
http_req_duration p(90)
http_req_duration p(95)
http_req_duration p(99)
iterations
vus
checks
```

判断顺序：

```text
1. 先看 http_req_failed 是否超过阈值；
2. 再看 P95 是否超过接口可接受阈值；
3. 再看 P99 是否出现明显长尾；
4. 再看 RPS 是否达到平台期；
5. 最后结合资源指标定位原因。
```

建议阈值示例：

```text
分散写场景：
错误率 < 1%
P95 < 3000ms
P99 < 5000ms

热点写实验场景：
错误率 < 5% 或按实验阶段设定
P95 / P99 必须单独分析，不允许只看平均 RT
```

### 5.2 第二步：按错误类型分类

常见错误类型：

```text
HTTP 5xx：服务端异常或下游异常；
HTTP 4xx：参数、库存不足、余额不足、幂等冲突等业务结果；
timeout：服务端处理超时、连接池等待、DB 锁等待；
connection refused/reset：服务不可用、端口异常、连接被关闭；
业务失败码：库存不足、余额不足、Redis 库存未初始化等。
```

排查要求：

```text
1. 不要把业务失败和系统错误混在一起统计；
2. 库存不足、余额不足是可预期业务失败；
3. 超时、5xx、连接失败是系统稳定性问题；
4. 幂等冲突需要结合 requestId 生成规则判断。
```

### 5.3 第三步：看应用进程资源

查 Java 进程：

```bash
jps -l
```

查看 JVM 基础信息：

```bash
jcmd <pid> VM.version
jcmd <pid> VM.flags
jcmd <pid> GC.heap_info
```

查看 GC 概况：

```bash
jstat -gcutil <pid> 1000 10
```

抓线程栈：

```bash
jcmd <pid> Thread.print > thread-dump-$(date +%Y%m%d-%H%M%S).log
```

线程栈重点查：

```text
1. 大量线程是否卡在数据库驱动调用；
2. 是否大量线程等待 Hikari 连接；
3. 是否有 synchronized / ReentrantLock 等锁等待；
4. 是否有 Redis 客户端调用堆积；
5. 是否有业务队列 take / put / offer 阻塞；
6. 是否存在死锁。
```

### 5.4 第四步：看 Actuator 指标

查看 Hikari 指标名：

```bash
curl -s http://localhost:8081/actuator/metrics | grep hikaricp
```

常用指标：

```bash
curl -s http://localhost:8081/actuator/metrics/hikaricp.connections.active
curl -s http://localhost:8081/actuator/metrics/hikaricp.connections.idle
curl -s http://localhost:8081/actuator/metrics/hikaricp.connections.pending
curl -s http://localhost:8081/actuator/metrics/hikaricp.connections.max
curl -s http://localhost:8081/actuator/metrics/jvm.memory.used
curl -s http://localhost:8081/actuator/metrics/jvm.gc.pause
curl -s http://localhost:8081/actuator/metrics/http.server.requests
```

判断方式：

```text
hikaricp.connections.active 接近 maximum-pool-size：连接被打满；
hikaricp.connections.pending > 0：线程正在等连接；
jvm.gc.pause 增大：需要判断是否 GC 导致 RT 尖刺；
http.server.requests 的 max / percentile：和 k6 结果互相校验。
```

### 5.5 第五步：看 MySQL

查看当前连接和正在执行的 SQL：

```sql
SHOW FULL PROCESSLIST;
```

查看 InnoDB 事务和锁等待：

```sql
SELECT * FROM information_schema.innodb_trx\G
SELECT * FROM performance_schema.data_locks\G
SELECT * FROM performance_schema.data_lock_waits\G
```

查看 InnoDB 状态：

```sql
SHOW ENGINE INNODB STATUS\G
```

查看慢 SQL 开关：

```sql
SHOW VARIABLES LIKE 'slow_query_log';
SHOW VARIABLES LIKE 'long_query_time';
```

热点 Gift 排查重点：

```sql
SELECT id, stock, update_time
FROM gift
WHERE id = 1;
```

热点用户排查重点：

```sql
SELECT user_id, balance, update_time
FROM user_account
WHERE user_id IN (1, 2, 3, 4, 5);
```

订单和流水写入量：

```sql
SELECT COUNT(*) FROM gift_order;
SELECT COUNT(*) FROM account_flow;
SELECT COUNT(*) FROM gift_stock_reservation;
```

判断方式：

```text
1. 大量 UPDATE gift 卡住：热点 Gift 库存行锁；
2. 大量 UPDATE user_account 卡住：热点账户余额行锁；
3. 大量 INSERT gift_order / account_flow 慢：写入 IO 或索引压力；
4. Hikari active 满，MySQL processlist 也很多：DB 端慢导致连接被占满；
5. Hikari pending 高，但 MySQL processlist 不高：连接池配置、连接泄漏或应用侧阻塞。
```

### 5.6 第六步：看 Redis

查看 Redis 基础信息：

```bash
redis-cli -h localhost -p 6379 -a '<password>' INFO
```

查看库存 Key：

```bash
redis-cli -h localhost -p 6379 -a '<password>' GET gift:stock:1
```

查看慢命令：

```bash
redis-cli -h localhost -p 6379 -a '<password>' SLOWLOG GET 20
```

查看 Redis 延迟：

```bash
redis-cli -h localhost -p 6379 -a '<password>' --latency
```

判断方式：

```text
1. Redis 慢命令多：Lua、网络、Redis 单线程压力需要排查；
2. gift:stock:{giftId} 不存在：库存未初始化；
3. Redis 库存为 0：库存不足属于业务结果；
4. Redis 正常但 MySQL 慢：瓶颈不在库存预扣，而在余额、订单、流水或同步任务。
```

***

## 6. 常见问题分支排查

### 6.1 P95 / P99 高，但错误率低

含义：

```text
请求大部分能成功，但大量请求在等待。
```

排查顺序：

```text
1. 看 Hikari active / pending；
2. 查 MySQL processlist；
3. 查 InnoDB data_lock_waits；
4. 抓 Java 线程栈；
5. 对比库存同步、补偿、校准任务是否在压测期间运行；
6. 看 Redis slowlog。
```

本项目常见原因：

```text
1. V1 / V2 热点 Gift：gift.stock 单行 UPDATE 串行化；
2. V3-B 之后热点用户：user_account 单行 UPDATE 串行化；
3. 库存同步任务和主链路同时更新 gift 相关数据；
4. 账户分片队列容量过小或处理线程被阻塞。
```

### 6.2 错误率突然上升

先把错误分为两类：

```text
业务预期失败：库存不足、余额不足、重复 requestId；
系统异常失败：5xx、timeout、连接池超时、SQL 异常、Redis 异常。
```

排查顺序：

```text
1. 从 k6 checks 或响应体统计失败原因；
2. 查应用日志的异常堆栈；
3. 查 Hikari pending 和 connection timeout；
4. 查 MySQL 是否出现 lock wait timeout / deadlock；
5. 查 Redis 是否连接失败或库存 Key 缺失；
6. 查是否达到本地机器资源上限。
```

### 6.3 RPS 不上升，CPU 很高

优先怀疑：

```text
1. 应用 CPU 计算瓶颈；
2. 日志输出过多；
3. JSON 序列化 / 反序列化开销；
4. 过多对象创建导致 GC；
5. k6 压测机本身成为瓶颈。
```

动作：

```text
1. 降低日志级别，尤其是 SQL stdout 日志；
2. 用 top / Activity Monitor 看 Java 和 k6 谁占 CPU；
3. 抓 jcmd Thread.print 看热点线程；
4. 观察 jstat -gcutil 是否频繁 GC；
5. 将压测机和被测服务拆开验证。
```

### 6.4 RPS 不上升，CPU 不高

优先怀疑等待型瓶颈：

```text
1. MySQL 行锁等待；
2. MySQL 连接池耗尽；
3. Redis 调用等待；
4. 应用内部队列堆积；
5. 线程池耗尽；
6. 磁盘 IO 或网络 IO。
```

动作：

```text
1. 查 Hikari active / pending；
2. 查 MySQL data_lock_waits；
3. 查 Redis slowlog；
4. 抓线程栈看 WAITING / TIMED_WAITING 位置；
5. 查队列长度和 rejected 数；
6. 对比不同 send-mode 下瓶颈是否迁移。
```

### 6.5 平均 RT 正常，但 P99 很高

含义：

```text
少量请求遇到了异常慢路径。
```

重点排查：

```text
1. GC 暂停；
2. 慢 SQL；
3. 偶发锁等待；
4. 定时任务抢占连接或锁；
5. Redis 偶发慢命令；
6. 本地机器资源抖动。
```

动作：

```text
1. 记录 P99 尖刺发生的时间点；
2. 对齐应用日志、GC、MySQL 慢日志、定时任务日志；
3. 在尖刺期间连续抓 3 次线程栈；
4. 查 MySQL innodb_trx 中长事务；
5. 查 stock-sync / compensate / check 任务是否同时执行。
```

***

## 7. 本项目不同版本的重点排查点

### 7.1 V1 DB Only

核心链路：

```text
查订单 DB
查 Gift DB
查 LiveRoom DB
扣余额 DB
扣库存 DB
写订单 DB
写流水 DB
```

重点看：

```text
1. gift.stock 行锁；
2. user_account.balance 行锁；
3. Hikari 连接池是否耗尽；
4. gift_order / account_flow 写入是否变慢；
5. MyBatis SQL 日志是否拖慢应用。
```

### 7.2 V2 Cache Preload

核心变化：

```text
Gift / LiveRoom 读缓存前置，但余额和库存写仍然走 MySQL。
```

重点看：

```text
1. 分散写 P95 是否下降；
2. 热点写是否仍然卡在 gift.stock；
3. 如果失败率下降但 P95 上升，说明更多请求进入锁等待队列；
4. 缓存命中后 DB 读压力下降，不代表写锁问题解决。
```

### 7.3 V3 Redis Stock Pre-Deduct

核心变化：

```text
Redis 预扣库存；
可配置是否继续 MySQL 同步扣库存。
```

重点看：

```text
1. mysql-deduct-enabled=true 时，热点 Gift 是否仍然锁等待；
2. mysql-deduct-enabled=false 时，热点 Gift 是否明显改善；
3. 新瓶颈是否迁移到 user_account；
4. gift_stock_reservation 是否堆积；
5. stock-sync / recover / compensate / check 任务是否影响主链路；
6. Redis 库存和 MySQL 库存是否能最终对齐。
```

### 7.4 V4 账户热点保护

核心目标：

```text
降低热点用户余额扣减对 user_account 单行锁的直接冲击。
```

重点看：

```text
1. account-shard 队列长度；
2. 队列 offer 超时或拒绝数量；
3. 单用户串行化是否导致 P99 上升；
4. 分散用户场景是否没有明显性能回退；
5. 账户余额一致性和账户流水是否完整。
```

***

## 8. 每次压测后的落地分析模板

复制以下模板到对应实验报告中。

```text
## N. 本次压测排查记录

### 1. 测试配置

测试时间：
Git commit：
send-mode：
mysql-deduct-enabled：
account-shard.enabled：
k6 脚本：
VU：
duration：
测试数据：

### 2. k6 结果

总请求数：
成功请求数：
失败请求数：
失败率：
RPS：
平均 RT：
P90：
P95：
P99：
最大 RT：

### 3. 是否达到容量拐点

结论：
证据：

### 4. 错误分类

业务失败：
系统失败：
超时：
5xx：
其他：

### 5. 应用侧观察

CPU：
内存：
GC：
线程栈结论：
Hikari active：
Hikari pending：

### 6. MySQL 观察

processlist 结论：
data_lock_waits 结论：
慢 SQL：
热点表：
热点行：

### 7. Redis 观察

库存 Key：
slowlog：
latency：

### 8. 根因判断

主要瓶颈：
次要瓶颈：
是否与预期一致：

### 9. 下一步动作

1.
2.
3.
```

***

## 9. 快速判断口诀

```text
平均值看趋势，P95 看大多数用户体验，P99 看长尾风险。
错误率先于吞吐，P95/P99 先于平均 RT。
RPS 不涨但 RT 涨，是排队或过载。
CPU 高查计算，CPU 不高查等待。
热点 Gift 查 gift.stock，热点用户查 user_account。
连接池 pending 大于 0，先查连接为什么回不来。
业务失败要和系统失败分开算。
```

