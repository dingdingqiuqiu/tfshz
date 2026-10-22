# 秒杀压测（JMeter）

目标：用 N 个**不同用户**并发抢同一张秒杀券，验证四个不变量：

1. 不超卖：DB/Redis 库存都不会变成负数，订单数 ≤ 库存；
2. 一人一单：同一用户并发多次只成功一次；
3. 不丢单：抢到的用户最终都能在 `tb_voucher_order` 里查到订单（MQ 异步落库）；
4. 可对账：失败原因分布（库存不足 / 不能重复下单）符合预期，死信为空。

## 文件

| 文件 | 作用 |
|---|---|
| `gen-tokens.mjs` | 为每个压测用户生成 JWT（HS256，密钥与后端 `JWT_SECRET` 一致） |
| `seckill.jmx` | JMeter 测试计划：CSV 读 token → 并发 POST `/voucher-order/seckill/{voucherId}` |
| `tokens.csv` | 生成物（已 gitignore，不入库） |
| `result.jtl` / `report/` | 压测结果（已 gitignore） |

## 前置条件

- 后端已启动（默认 `127.0.0.1:8081`）；
- 目标券已预热（应用启动时 `SeckillStockWarmer` 会把 `tb_seckill_voucher` 刷进 Redis）；
- 准备好"可被抢"的测试券：库存要足够，且这些用户之前没抢过（否则会命中"一人一单"）。

## 跑法

```bash
cd loadtest

# 1. 生成 200 个 token（对应 200 个不同 userId）
node gen-tokens.mjs --count 200 --start 940001 --out tokens.csv

# 2. 非 GUI 模式压测（-J 参数覆盖 jmx 里的默认值；每次跑前删掉旧 result.jtl，否则会追加）
rm -f result.jtl
jmeter -n -t seckill.jmx \
       -Jthreads=200 -Jramp=1 -Jloops=1 -Jvoucher=2 \
       -l result.jtl -e -o report

# 3. 看结果
#    控制台会打印 summary 行：样本数 / 吞吐 / 平均 RT / Err%
```

没有本地 JMeter 时可用容器（本仓库已用 `justb4/jmeter:5.5` 验证过）：

```bash
docker run --rm -v "$PWD":/test -w /test justb4/jmeter:5.5 \
  -n -t seckill.jmx -Jthreads=200 -Jhost=host.docker.internal -Jvoucher=2 \
  -l result.jtl -e -o report
```

> Windows 的 Docker Desktop 里 `host.docker.internal` 指向宿主机；Linux 需要 `--add-host host.docker.internal:host-gateway`。

### 两个踩过的坑

1. **`>` 重定向会写出 UTF-16**：Windows PowerShell 的 `node xxx > tokens.csv` 默认写 UTF-16LE，
   JMeter 按 UTF-8 读出来就是乱码 token，现象是**100% 401**。所以脚本提供 `--out` 参数自己写 UTF-8 文件。
2. **result.jtl 是追加写**：重复跑不删旧的 jtl，会把上一轮的失败混进统计里。

## 压测后怎么核对

```sql
-- 订单数、去重用户数（应与"成功抢到"的数量一致）
SELECT COUNT(*) AS orders, COUNT(DISTINCT user_id) AS users
FROM tb_voucher_order WHERE voucher_id = 2;

-- 库存必须为 0（不是负数）
SELECT stock FROM tb_seckill_voucher WHERE voucher_id = 2;
```

```bash
# Redis 侧：库存、已下单集合、死信与 PEL
redis-cli GET seckill:stock:2
redis-cli SCARD seckill:order:2
redis-cli XLEN stream.orders.dlq        # seckill.mq=stream 时
```

Kafka 模式（默认 `seckill.mq=kafka`）：

```bash
docker exec tfshz-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group seckill-order-group
# LAG 应最终为 0；死信 topic 应为空
docker exec tfshz-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic seckill-order-dlq --from-beginning --max-messages 10
```

## 参考结果（本机单实例，2026-10）

| 指标 | 结果 |
|---|---|
| 线程数 / 库存 | 200 / 50 |
| 成功抢到 | 50（其余 150 返回「库存不足」） |
| 总耗时 | ≈ 610 ms |
| DB 订单 / 去重用户 | 50 / 50 |
| DB 库存 / Redis 库存 | 0 / 0 |
| 死信 / PEL 残留 | 0 / 0 |

> 单机 + 本地 mysql/redis，仅用于验证正确性与量级，不作为性能结论。
