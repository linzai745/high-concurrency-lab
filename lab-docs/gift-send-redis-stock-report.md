# Gift 高并发送礼实验报告 - V3 Redis Stock Pre-Deduct 库存预扣版

## 1. 实验背景

在 V1 和 V2 的压测中，Gift 送礼链路已经暴露出两个主要问题。

V1 DB Only 同步事务版中，所有请求都直接访问 MySQL，热点 Gift 场景下大量请求集中更新同一行 gift.stock，导致 InnoDB 行锁等待、事务堆积、Hikari 连接池耗尽和大量请求失败。

V2 Cache Preload 缓存前置版中，将 Gift 和 LiveRoom 这类读多写少数据前置到缓存，分散写场景下平均 RT 和 P95 有一定下降，但热点写场景仍然表现较差。原因是缓存前置只能减少读 DB，无法解决 MySQL 单行库存扣减的写锁竞争。

因此，V3 开始从读路径优化转向写路径优化，引入 Redis 库存预扣。
***

## 2. V3 核心目标

V3 的核心目标是验证：
```
1. Redis 预扣库存是否可以降低热点 Gift 对 MySQL 单行库存锁的依赖；
2. 当 MySQL 仍然同步扣库存时，Redis 预扣是否能改善热点写；
3. 当 MySQL 不再同步扣库存时，热点 Gift 性能是否明显改善；
4. Gift 库存瓶颈移除后，系统新的瓶颈会出现在哪里；
5. Redis 预扣库存如何与 MySQL 库存进行最终一致性同步。
```
***

## 3. V3 方案说明

V3 在请求进入 MySQL 主事务之前，先通过 Redis Lua 对库存做原子预扣。

Redis Key：
```
gift:stock:{giftId}
```
例如：
```
gift:stock:1
gift:stock:100
```
V3 预扣逻辑：
```
请求进入
↓
Gift / LiveRoom 缓存读取
↓
Redis Lua 预扣库存
↓
预扣成功，进入 MySQL 事务
↓
预扣失败，直接返回库存不足
```
Redis Lua 逻辑：
```
local stockKey = KEYS[1]
local count = tonumber(ARGV[1])
local stock = tonumber(redis.call('GET', stockKey))
if stock == nil then
return -1
end
if stock < count then
return 0
end
redis.call('DECRBY', stockKey, count)
return 1
```
返回值含义：
```
1  = 预扣成功
0  = Redis 库存不足
-1 = Redis 库存未初始化
```
***

## 4. V3 两种实验模式

V3 分为两个子模式。

|模式| 	Redis 预扣库存 | 	MySQL 同步扣库存	             |说明|
|---|-------------|---------------------------|---|
|V3-A| 	是          |	是	| 保守一致性模式，仍然保留 MySQL 库存同步扣减 |
|V3-B| 	是          |	否	| 性能优先模式，MySQL 不在主链路同步扣减库存  |

***

## 5. V3-A 链路：Redis 预扣 + MySQL 仍扣库存

V3-A 链路如下：
```
请求进入
↓
查订单 DB
↓
查 Gift Cache
↓
查 LiveRoom Cache
↓
Redis 预扣库存
↓
扣余额 DB
↓
扣库存 DB
↓
写订单 DB
↓
写账户流水 DB
↓
返回结果
```
V3-A 保留 MySQL 库存扣减：
```
UPDATE gift
SET stock = stock - #{count},
update_time = NOW()
WHERE id = #{giftId}
AND stock >= #{count}
AND status = 1;
```
所以 V3-A 仍然会在热点 Gift 场景下竞争 gift.id = 1 这一行。

***

## 6. V3-B 链路：Redis 预扣 + MySQL 不扣库存

V3-B 链路如下：
```
请求进入
↓
查订单 DB
↓
查 Gift Cache
↓
查 LiveRoom Cache
↓
Redis 预扣库存
↓
扣余额 DB
↓
跳过 MySQL 库存扣减
↓
写订单 DB
↓
写账户流水 DB
↓
写库存预扣/同步流水
↓
返回结果
```
V3-B 中，MySQL 不再在主链路同步执行：
```
UPDATE gift SET stock = stock - ?
```
因此，热点 Gift 的库存扣减不再竞争 MySQL 单行锁。

但是，V3-B 需要补充 Redis 与 MySQL 库存的最终一致性同步方案，不能只停留在 Redis 预扣。

***

## 7. Redis 预扣库存与 MySQL 库存同步方案

### 7.1 为什么需要同步方案？

V3-B 中，Redis 扣了库存，但 MySQL gift.stock 不在主链路同步扣减。

如果不做同步，就会出现：

Redis 实时库存不断减少；
MySQL gift.stock 长期不变；
Redis 和 MySQL 库存逐渐不一致。

所以 V3-B 必须补充库存同步机制。

***

### 7.2 库存口径

V3-B 中需要区分两个库存口径：

Redis 库存：实时可售库存，用于高并发预扣；
MySQL 库存：持久化库存基准，用于恢复、对账、校准。

因此，MySQL gift.stock 不再表示强实时可售库存，而是持久化基准库存。

***

### 7.3 推荐同步方式：预扣/库存流水 + 异步批量同步

完整方案：
```
Redis 实时预扣库存
↓
订单事务中写库存预扣/同步流水
↓
后台任务按 giftId 聚合待同步流水
↓
批量扣减 MySQL gift.stock
↓
同步成功后更新流水状态
```
核心思想：
```
主链路不再每次 UPDATE gift.stock；
后台任务按 giftId 聚合后批量扣减 MySQL 库存。
```
例如 1 秒内 giftId = 1 被扣了 300 次：

原方案：
```
执行 300 次 UPDATE gift SET stock = stock - 1
```
异步聚合方案：
```
执行 1 次 UPDATE gift SET stock = stock - 300
```
这样可以显著降低热点 Gift 对 MySQL 单行锁的冲击。
***

### 7.4 库存预扣流水表设计

当前实验阶段建议使用一张表统一承载“库存预扣 + MySQL 同步状态”。

推荐表名：
```
gift_stock_reservation
```
表结构：
```
CREATE TABLE gift_stock_reservation (
id BIGINT PRIMARY KEY AUTO_INCREMENT,
reservation_no VARCHAR(64) NOT NULL COMMENT '库存预扣单号',
request_id VARCHAR(128) NOT NULL COMMENT '请求幂等ID',
order_no VARCHAR(64) DEFAULT NULL COMMENT '订单号',
user_id BIGINT NOT NULL,
gift_id BIGINT NOT NULL,
deduct_count INT NOT NULL COMMENT '预扣/扣减数量',
status TINYINT NOT NULL COMMENT '0预扣中 1订单成功待同步 2已同步 3已回补 4失败',
rollback_status TINYINT NOT NULL DEFAULT 0 COMMENT '0无需回补 1待回补 2已回补 3回补失败',
create_time DATETIME NOT NULL,
update_time DATETIME NOT NULL,
UNIQUE KEY uk_reservation_no (reservation_no),
UNIQUE KEY uk_request_id (request_id),
KEY idx_status_gift (status, gift_id),
KEY idx_order_no (order_no),
KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```
***

### 7.5 状态说明
```
0 = 预扣中
1 = 订单成功，待同步 MySQL 库存
2 = MySQL 库存已同步
3 = 已回补 Redis
4 = 失败，需要补偿或人工处理
```
正常状态流转：
```
创建 reservation，status=0
↓
Redis 预扣成功
↓
订单事务成功
↓
更新 status=1，order_no=xxx
↓
后台批量同步 MySQL gift.stock
↓
更新 status=2
```
订单失败时：
```
Redis 预扣成功
↓
MySQL 订单事务失败
↓
Redis 回补库存
↓
更新 status=3
```
***

### 7.6 异步同步任务

后台任务扫描待同步记录：
```
SELECT gift_id, SUM(deduct_count) AS total_count
FROM gift_stock_reservation
WHERE status = 1
GROUP BY gift_id;
```
按 giftId 聚合扣减 MySQL：
```
UPDATE gift
SET stock = stock - #{totalCount},
update_time = NOW()
WHERE id = #{giftId}
AND stock >= #{totalCount}
AND status = 1;
```
同步成功后更新状态：
```
UPDATE gift_stock_reservation
SET status = 2,
update_time = NOW()
WHERE gift_id = #{giftId}
AND status = 1
AND id IN (...);
```
***

### 7.7 Redis 与 MySQL 库存校准

校准公式：
```
expectedRedisStock = mysqlStock - pendingDeductCount
```
其中：
```
mysqlStock = MySQL gift.stock
pendingDeductCount = gift_stock_reservation 中 status=1 的待同步扣减数量
redisStock = Redis gift:stock:{giftId}
```
如果：
```
redisStock != expectedRedisStock
```
说明 Redis 与 MySQL 库存存在不一致，需要触发校准、报警或补偿。

***

## 8. V3 压测场景

V3 共进行了四组基础压测和两组瓶颈定位实验。

8.1 基础压测场景

|场景	|模式	|用户分布|	Gift 分布|	MySQL 是否扣库存|
|---|---|---|---|---|
|V3-A 分散写	|Redis + MySQL 扣库存	|1000 用户	|100 礼物	|是|
|V3-A 热点写	|Redis + MySQL 扣库存	|5 用户	|1 礼物|	是|
|V3-B 分散写	|Redis + MySQL 不扣库存	|1000 用户|	100 礼物|	否|
|V3-B 热点写	|Redis + MySQL 不扣库存	|5 用户	1 |礼物|	否|

## 8.2 瓶颈定位实验

| 实验                   | 	用户分布     |	Gift 分布|	目的|
|----------------------|-----------|---|---|
| 实验 A：热点 Gift + 分散用户  | 	1000 用户	 |1 礼物	|验证热点 Gift 是否仍是瓶颈|
| 实验 B：分散 Gift + 热点用户	 | 5 用户	     | 100 礼物    | 	验证热点用户账户是否是瓶颈 |

***

## 9. V3 基础压测结果汇总

### 9.1 V3-A 分散写：MySQL 仍扣库存
```
用户分布：1000 用户
Gift 分布：100 礼物
VU：100
持续时间：60s
MySQL 是否扣库存：是
```
结果：
```
总请求数：4026
成功请求数：4026
失败请求数：0
成功率：100%
失败率：0%
RPS：65.56/s
平均 RT：1.50s
中位数 RT：1.33s
P90：1.84s
P95：2.47s
最大 RT：6.22s
```
阈值：
```
p95 < 2500ms：通过
失败率 < 5%：通过
```
结论：
```
在分散写场景下，即使 MySQL 仍然同步扣库存，由于 userId 和 giftId 都较分散，行锁竞争较低。Redis 预扣配合缓存前置后，系统表现稳定，P95 达到 2.47s，失败率为 0%。
```
***

### 9.2 V3-A 热点写：MySQL 仍扣库存
```
用户分布：5 用户
Gift 分布：1 礼物
VU：100
持续时间：60s
MySQL 是否扣库存：是
```
结果：
```
总请求数：360
成功请求数：281
失败请求数：79
成功率：78.05%
失败率：21.94%
RPS：4.00/s
平均 RT：17.57s
中位数 RT：16.79s
P90：23.83s
P95：24.80s
最大 RT：60s
```
阈值：
```
p95 < 5000ms：未通过
失败率 < 5%：未通过
```
结论：
```
V3-A 在热点写场景下没有根治问题。虽然引入了 Redis 预扣库存，但后续仍然同步 UPDATE MySQL gift.stock，因此热点 giftId=1 的库存行锁仍然存在。同时 userId 只分布在 1~5，账户余额行也存在竞争。最终导致 RT 和失败率都较高。
```
***

### 9.3 V3-B 分散写：MySQL 不扣库存
```
用户分布：1000 用户
Gift 分布：100 礼物
VU：100
持续时间：60s
MySQL 是否扣库存：否
```
结果：
```
总请求数：3050
成功请求数：3049
失败请求数：1
成功率：99.96%
失败率：0.03%
RPS：49.95/s
平均 RT：1.98s
中位数 RT：1.16s
P90：2.07s
P95：2.91s
最大 RT：23.39s
```
阈值：
```
p95 < 2500ms：未通过
失败率 < 5%：通过
```
结论：
```
V3-B 分散写整体保持稳定，失败率只有 0.03%。但 P95 为 2.91s，高于 2.5s 阈值，且最大 RT 达到 23.39s，说明链路中仍然存在少量长尾请求。相比 V3-A 分散写，这组结果没有明显更优，可能受到库存预扣流水写入、后台同步任务、测试环境状态等影响。
```
***

### 9.4 V3-B 热点写：MySQL 不扣库存
```
用户分布：5 用户
Gift 分布：1 礼物
VU：100
持续时间：60s
MySQL 是否扣库存：否
```
结果：
```
总请求数：595
成功请求数：594
失败请求数：1
成功率：99.83%
失败率：0.16%
RPS：8.93/s
平均 RT：10.55s
中位数 RT：5.56s
P90：35.61s
P95：37.32s
最大 RT：40.08s
```
阈值：
```
p95 < 5000ms：未通过
失败率 < 5%：通过
```
结论：
```
V3-B 热点写相比 V3-A 热点写，失败率从 21.94% 降到 0.16%，说明关闭 MySQL 库存扣减后，热点 Gift 库存行锁不再导致大量失败。
但 P95 仍然高达 37.32s，说明请求虽然大部分最终成功，但仍然存在严重排队。由于该场景只有 5 个 userId，主要瓶颈已经从 gift.stock 行锁转移到 user_account 账户余额行锁。
```
***

## 10. 基础压测结果对比

|场景	|成功率|	失败率|	RPS|	平均 RT|	P90|	P95|	结论|
|---|---|---|---|---|---|---|---|
|V3-A 分散写|	100%|	0%|	65.56/s|	1.50s	|1.84s|	2.47s	|表现稳定，通过阈值
|V3-A 热点写|	78.05%|	21.94%|	4.00/s|	17.57s|	23.83s|	24.80s|	仍受库存行锁和账户行锁影响
|V3-B 分散写|	99.96%|	0.03%|	49.95/s|	1.98s|	2.07s|	2.91s|	稳定但存在长尾
|V3-B 热点写|	99.83%|	0.16%|	8.93/s	|10.55s|	35.61s|	37.32s|	失败率改善，但账户热点导致高延迟

***

## 11. V3-A 与 V3-B 对比分析

### 11.1 V3-A 分散写表现最好

V3-A 分散写达到：
```
成功率：100%
RPS：65.56/s
平均 RT：1.50s
P95：2.47s
```
这说明在 userId 和 giftId 都分散的情况下，Redis 预扣 + MySQL 同步扣库存仍然可以稳定运行。

因为分散写场景下，MySQL 库存行锁和账户行锁都被自然打散。

***

### 11.2 V3-A 热点写没有根治问题

V3-A 热点写失败率为：
```
21.94%
```
P95 为：
```
24.80s
```
原因是 V3-A 虽然增加了 Redis 预扣库存，但仍然执行 MySQL 库存扣减：
```
UPDATE gift
SET stock = stock - ?
WHERE id = ?
```
所以热点 Gift 下，gift.id = 1 仍然是 MySQL 单行写热点。

***

### 11.3 V3-B 热点写失败率明显下降

V3-B 热点写失败率为：
```
0.16%
```
相比 V3-A 热点写：
```
21.94% → 0.16%
```
说明关闭 MySQL 库存扣减后，热点 Gift 不再造成大量失败。

但是 V3-B 热点写 P95 仍然很高：
```
37.32s
```
这说明新的瓶颈不是库存，而是账户余额扣减。

***

## 12. Lock wait timeout 问题分析

在 V3-B 早期测试中，曾出现大量如下错误：
```
MySQLTransactionRollbackException:
Lock wait timeout exceeded; try restarting transaction
```
错误发生在：
```
UserAccountMapper.deductBalance
```
对应 SQL：
```
UPDATE user_account
SET balance = balance - ?,
update_time = NOW()
WHERE user_id = ?
AND balance >= ?
```
这说明请求已经拿到了数据库连接，也已经进入事务，但在执行账户余额扣减时，等待 user_account 行锁超时。

这个错误与 Hikari 连接池超时不同：
```
Lock wait timeout：
请求已经拿到连接，执行 SQL 时等待 InnoDB 行锁超时。
Hikari Connection is not available：
请求在开启事务时拿不到数据库连接，连接池等待超时。
```
在后续测试中，虽然不再大量出现 Lock wait timeout，但 V3-B 热点写 P95 仍然达到 37.32s。这说明账户行锁等待仍然存在，只是大多数请求在 MySQL 锁等待超时前拿到了锁并成功返回。

***

## 13. V3-B 瓶颈定位实验

为了进一步确认 V3-B 的瓶颈，将热点 Gift 和热点用户拆开做了两组对照实验。

***

### 13.1 实验 A：热点 Gift + 分散用户

场景：
```
giftId = 1
userId = 1 ~ 1000
MySQL 不扣库存
Redis 预扣库存
```
结果：
```
总请求数：5004
成功请求数：5004
失败请求数：0
成功率：100%
失败率：0%
RPS：81.96/s
平均 RT：1.21s
中位数 RT：1.08s
P90：1.50s
P95：2.00s
最大 RT：4.31s
```
结论：
```
即使所有请求都集中送同一个 Gift，只要 MySQL 不再同步扣减 gift.stock，并且用户维度分散，系统表现很好。说明 Redis 预扣库存已经有效解决了热点 Gift 的 MySQL 库存行锁问题。
```
***

### 13.2 实验 B：分散 Gift + 热点用户

场景：
```
giftId = 1 ~ 100
userId = 1 ~ 5
MySQL 不扣库存
Redis 预扣库存
```
结果：
```
总请求数：1138
成功请求数：1138
失败请求数：0
成功率：100%
失败率：0%
RPS：16.76/s
平均 RT：5.54s
中位数 RT：5.14s
P90：7.94s
P95：8.58s
最大 RT：14.29s
```
结论：
```
即使 Gift 已经分散，且 MySQL 不再扣库存，只要 userId 仍然集中在 1~5，账户余额扣减仍然会造成明显排队。说明当前主要瓶颈已经转移到 user_account 账户行锁。
```
***

### 13.3 实验 A/B 对比

| 指标         | 	实验 A：热点 Gift + 分散用户 |	实验 B：分散 Gift + 热点用户|
|------------|----------------------|---|
| Gift 是否热点	 | 是                    |	否|
| 用户是否热点	    | 否                    |	是|
| 成功率	       | 100%	                | 100%                 |
|失败率	|0%|	0%|
|总请求数|	5004|	1138|
|RPS	|81.96/s|	16.76/s|
|平均 RT	|1.21s|	5.54s|
|P90	|1.50s|	7.94s|
|P95	|2.00s|	8.58s|
|最大 RT|	4.31s|	14.29s|

核心结论：
```
V3-B 已经解决热点 Gift 库存写锁问题；
V3-B 没有解决热点用户账户扣减问题。
```
***

## 14. V1 / V2 / V3 关键结果对比

### 14.1 分散写场景

|版本|	成功率| 	失败率                                |	RPS	|平均 RT|	P95|	说明|
|---|---|-------------------------------------|---|---|---|---|
|V1 DB Only	|100%	| 0%	                                 |51.65/s|	1.90s	|3.71s	|基准版|
|V2 Cache Preload|	100%| 	0%	|59.38/s|	1.66s|	3.23s|	读路径缓存有效  |   
|V3-A Redis + MySQL 库存	|100%| 	0%	|65.56/s|	1.50s	|2.47s	|分散写下表现最好    |
|V3-B Redis + 不扣 MySQL 库存	|99.96%| 	0.03%|	49.95/s	|1.98s	|2.91s|	稳定但有少量长尾 |

***

14.2 热点写场景

|版本	|成功率	|失败率	|RPS|	平均 RT|	P95	|说明|
|---|---|---|---|---|---|---|
|V1 DB Only	|23.08%|	76.91%	|15.67/s|	5.43s|	17.12s|	连接池耗尽，大量失败|
|V2 Cache Preload	|87.08%|	12.91%	|4.26/s|	19.86s|	28.46s|	失败减少，但锁等待严重|
|V3-A Redis + MySQL 库存	|78.05%|	21.94%	|4.00/s|	17.57s|	24.80s|	仍受库存行锁影响|
|V3-B Redis + 不扣 MySQL 库存|	99.83%|	0.16%|	8.93/s|	10.55s|	37.32s|	库存失败问题改善，但账户热点导致长尾|

*** 

## 15. V3 最终结论

V3 Redis Stock Pre-Deduct 版本的核心结论如下。

### 15.1 Redis 预扣库存可以解决热点 Gift 的 MySQL 库存行锁问题

实验 A 中：
```
giftId = 1
userId = 1 ~ 1000
MySQL 不扣库存
```
结果：
```
成功率：100%
RPS：81.96/s
P95：2.00s
```
这说明热点 Gift 本身已经不再是瓶颈。Redis 预扣库存可以有效替代主链路中的 MySQL 单行库存扣减，避免大量请求竞争 gift.id = 1。

***

### 15.2 如果 MySQL 仍然同步扣库存，热点库存行锁仍然存在

V3-A 热点写中，虽然增加 Redis 预扣，但仍然同步扣 MySQL 库存。

结果：
```
失败率：21.94%
P95：24.80s
```
说明 Redis 预扣本身不能在保留 MySQL 同步库存扣减的情况下根治热点写。只要主链路仍然执行：
```
UPDATE gift SET stock = stock - ?
```
热点 Gift 就仍然可能成为 MySQL 行锁瓶颈。

***

### 15.3 V3-B 关闭 MySQL 库存扣减后，失败率明显改善

V3-B 热点写中，MySQL 不再同步扣 gift.stock。

结果：
```
失败率：0.16%
```
相比 V3-A 热点写：
```
21.94% → 0.16%
```
说明关闭 MySQL 库存扣减后，热点库存行锁导致的大量失败基本消失。

***

### 15.4 新瓶颈转移到账户余额扣减

实验 B 中：
```
giftId = 1 ~ 100
userId = 1 ~ 5
MySQL 不扣库存
```
结果：
```
RPS：16.76/s
平均 RT：5.54s
P95：8.58s
```
这说明即使 Gift 已经分散，只要用户集中在少量账户上，user_account 余额扣减仍然会导致明显排队。

对应 SQL：
```
UPDATE user_account
SET balance = balance - ?,
update_time = NOW()
WHERE user_id = ?
AND balance >= ?
```
因此，V3 的新结论是：
```
库存热点被 Redis 预扣拆掉后，账户余额扣减成为新的写热点瓶颈。
```
***

### 15.5 V3-B 必须配套库存最终一致性方案

V3-B 中，Redis 是实时库存入口，MySQL 不再主链路同步扣库存。

因此必须配套：
```
库存预扣流水
异步批量同步 MySQL gift.stock
同步失败重试
Redis / MySQL 库存校准
异常回补
```
否则 Redis 和 MySQL 库存会逐渐不一致。

***

## 16. 当前版本风险和不足

### 16.1 库存最终一致性复杂度增加

V3-B 提升了热点库存性能，但引入 Redis 与 MySQL 库存一致性问题。

需要处理：
```
Redis 预扣成功但订单失败
Redis 预扣成功但应用宕机
库存同步任务失败
Redis 与 MySQL 库存不一致
库存流水长期未同步
```
***

### 16.2 账户余额仍然是同步 MySQL 扣减

当前账户余额仍然依赖 MySQL 条件更新：
```
UPDATE user_account
SET balance = balance - ?
WHERE user_id = ?
AND balance >= ?
```
热点用户场景下，同一个 userId 的请求仍然会在数据库行锁上排队。

***

### 16.3 后台库存同步任务可能影响主链路

如果库存同步任务和主链路共用同一个 MySQL 实例、连接池和表索引，连续压测时可能出现后台任务影响主链路 RT 的情况。

后续需要：
```
库存同步任务开关
独立线程池
批次大小控制
限速
失败重试
同步积压监控
```
***

## 17. 后续优化方向

### 17.1 V3.1：库存同步闭环完善

在进入 V4 之前，建议先完善 V3.1：
```
V3.1 Redis Stock Pre-Deduct + Stock Reservation Sync
```
主要内容：
```
1. 引入 gift_stock_reservation 表；
2. 订单事务内写库存预扣/同步记录；
3. 后台任务按 giftId 聚合扣减 MySQL gift.stock；
4. 同步成功后更新 reservation 状态；
5. 失败时保留待同步状态并重试；
6. 增加 Redis / MySQL 库存校准任务；
7. 增加 stock-sync.enabled 开关，方便主链路压测和同步任务压测隔离。
```
***

### 17.2 V4：账户扣减热点优化

V3 已经证明新的瓶颈转移到账户余额扣减，因此 V4 应围绕 userId 做优化。

推荐方向：
```
V4 User-Sharded Account Deduct
```
核心思想：
```
按 userId hash 分片；
同一个 userId 的扣减请求进入同一个队列；
同一个 userId 的账户扣减串行执行；
不同 userId 之间并行执行。
```
这样可以把数据库里的不可控行锁等待，前移成应用层可控排队。

可选方案：
```
1. 按 userId 分片队列串行化账户扣减；
2. 用户级限流；
3. 同用户短窗口请求合并；
4. Redis 账户余额预扣 + 账户流水最终一致性；
5. MQ 削峰异步扣减账户。
```
其中更推荐先做：
```
按 userId 分片串行化账户扣减
```
因为它不改变账户余额仍以 MySQL 为准的模型，风险比 Redis 余额预扣更低。

***

## 18. V3 总结

V3 的最终结论可以概括为：
```
V3 Redis 库存预扣证明了热点 Gift 的库存扣减可以从 MySQL 单行更新中拆出来。
在 V3-B 模式下，当 giftId 固定为 1、userId 分散到 1000 个用户时，系统成功率达到 100%，RPS 达到 81.96/s，P95 为 2s。这说明 Redis 预扣库存能够有效承接热点 Gift 的高并发扣减，避免 MySQL gift.stock 单行锁竞争。
但是，当 userId 只集中在 1~5 时，即使 giftId 分散到 100 个，系统 RPS 也只有 16.76/s，P95 达到 8.58s。这说明当前系统的主要瓶颈已经从 Gift 库存行锁转移到账户余额扣减行锁。
因此，V3 的价值不是简单证明 Redis 更快，而是通过实验把瓶颈链路进一步拆清楚：库存热点可以通过 Redis 预扣和异步同步解决；账户热点需要在 V4 中通过 userId 维度串行化、限流、请求合并或账户预扣继续优化。
```