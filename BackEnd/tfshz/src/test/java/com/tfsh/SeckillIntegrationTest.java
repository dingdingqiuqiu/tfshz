package com.tfsh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfsh.dto.UserDTO;
import com.tfsh.utils.JwtUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 端到端集成测试：真实 MySQL + Redis + Kafka（Testcontainers）。
 *
 * 覆盖单测覆盖不到的部分——**中间的连接**：
 *   HTTP 进入 → Lua 预扣 Redis → 投递 Kafka → 消费组落库 MySQL → 再回头看库存。
 * 单测只能证明"每个零件对"，这里证明"拼起来也对"。
 *
 * 没有 Docker 的环境会自动跳过（{@code disabledWithoutDocker = true}），不会让 `mvn test` 变红。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("it")
// 测试环境默认关闭 observability（注册表退化成 SimpleMeterRegistry），显式打开才会注册 Prometheus 端点
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
// 结束后主动关掉上下文：Kafka 消费者线程是非守护线程，不关会让 surefire 等 30 秒再强杀
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class SeckillIntegrationTest {

    /** 建库脚本在仓库根的 DB/ 下，用 MySQL 官方镜像的初始化目录执行 */
    private static final MountableFile SCHEMA = MountableFile.forHostPath(findSchema());

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("tfshz")
            .withUsername("tfshz")
            .withPassword("tfshz")
            .withCopyFileToContainer(SCHEMA, "/docker-entrypoint-initdb.d/01-schema.sql");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", "itpass");

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    /**
     * Redis 不用 @ServiceConnection：容器带密码，直接把 host/port/password 三件套一起给，
     * 避免连接详情里的空密码把 profile 里的密码覆盖掉（踩过：NOAUTH Authentication required）。
     */
    @org.springframework.test.context.DynamicPropertySource
    static void redisProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "itpass");
    }

    private static final long VOUCHER_ID = 2L;
    private static final int STOCK = 5;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void resetState() {
        // 时间窗放宽，避免用例依赖具体日期
        jdbc.update("UPDATE tb_seckill_voucher SET stock = ?, begin_time = ?, end_time = ? WHERE voucher_id = ?",
                STOCK,
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(1)),
                java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(1)),
                VOUCHER_ID);
        jdbc.update("DELETE FROM tb_voucher_order WHERE user_id >= 900000");
        stringRedisTemplate.opsForValue().set("seckill:stock:" + VOUCHER_ID, String.valueOf(STOCK));
        stringRedisTemplate.delete("seckill:order:" + VOUCHER_ID);
    }

    // ---------------------------------------------------------------- 用例

    @Test
    @DisplayName("抢券：Lua 预扣 → Kafka → 消费组落库，库存与订单一致")
    void seckillPersistsOrderThroughKafka() throws Exception {
        long userId = 900001L;

        JsonNode response = postSeckill(userId);
        assertThat(response.path("success").asBoolean()).isTrue();
        String orderId = response.path("data").asText();
        assertThat(orderId).hasSizeGreaterThan(10);

        // 异步落库：等 Kafka 消费者把订单写进 MySQL
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT id, user_id, status, verify_code FROM tb_voucher_order WHERE id = ?", Long.valueOf(orderId));
            assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(userId);
            assertThat(((Number) row.get("status")).intValue()).isEqualTo(1);
            assertThat((String) row.get("verify_code")).hasSize(10).startsWith("V");
        });

        // DB 库存被条件扣减；Redis 预扣也已发生
        assertThat(jdbc.queryForObject("SELECT stock FROM tb_seckill_voucher WHERE voucher_id = ?", Integer.class, VOUCHER_ID))
                .isEqualTo(STOCK - 1);
        assertThat(stringRedisTemplate.opsForValue().get("seckill:stock:" + VOUCHER_ID))
                .isEqualTo(String.valueOf(STOCK - 1));
    }

    @Test
    @DisplayName("一人一单：同一用户第二次抢券被 Lua 直接拒绝，不会产生第二条订单")
    void secondSeckillFromSameUserIsRejected() throws Exception {
        long userId = 900002L;

        assertThat(postSeckill(userId).path("success").asBoolean()).isTrue();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(countOrders(userId)).isEqualTo(1));

        JsonNode second = postSeckill(userId);
        assertThat(second.path("success").asBoolean()).isFalse();
        assertThat(second.path("errorMsg").asText()).contains("重复");

        Thread.sleep(1500);
        assertThat(countOrders(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("死信：不可解析的消息不重试，直接进 seckill-order-dlq")
    void poisonMessageGoesToDeadLetterTopic() throws Exception {
        kafkaTemplate.send("seckill-order", "this-is-not-json-" + UUID.randomUUID()).get();

        String poisonedPrefix = "this-is-not-json-";
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-dlq-reader-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (Consumer<String, String> consumer =
                     new org.apache.kafka.clients.consumer.KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList("seckill-order-dlq"));
            await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                boolean found = false;
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value() != null && record.value().startsWith(poisonedPrefix)) {
                        found = true;
                        break;
                    }
                }
                assertThat(found).as("坏消息应被转发到死信 topic").isTrue();
            });
        }
    }

    @Test
    @DisplayName("指标：/actuator/prometheus 暴露秒杀计数器")
    void prometheusExposesSeckillMetrics() {
        postSeckill(900003L);

        ResponseEntity<String> index = rest.getForEntity("/actuator", String.class);
        assertThat(index.getBody()).as("/actuator 索引应包含 prometheus").contains("prometheus");

        ResponseEntity<String> response = rest.getForEntity("/actuator/prometheus", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("/actuator/prometheus 返回：%s", response.getBody())
                .isTrue();
        assertThat(response.getBody())
                .contains("seckill_requests_total")
                .contains("result=\"success\"");
    }

    // ---------------------------------------------------------------- 工具方法

    private JsonNode postSeckill(long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken(userId));
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(new HashMap<>(), headers);

        ResponseEntity<String> response = rest.exchange(
                "/voucher-order/seckill/" + VOUCHER_ID, HttpMethod.POST, entity, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + response.getBody(), e);
        }
    }

    private String accessToken(long userId) {
        UserDTO user = new UserDTO();
        user.setId(userId);
        user.setNickName("it-user-" + userId);
        return jwtUtils.createAccessToken(user);
    }

    private int countOrders(long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_voucher_order WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    /** 从 surefire 的工作目录（模块目录）向上找仓库根的 DB/tfshz.sql */
    private static String findSchema() {
        Path cursor = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 4 && cursor != null; i++) {
            Path candidate = cursor.resolve("DB").resolve("tfshz.sql");
            if (Files.exists(candidate)) {
                return candidate.toString();
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("找不到 DB/tfshz.sql（当前目录："
                + Paths.get("").toAbsolutePath() + "）");
    }
}
