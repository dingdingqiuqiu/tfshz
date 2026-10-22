-- ============================================================
-- 商品 / 商品订单（美团式下单 + 核销）
-- 说明：金额一律用「分」存整数，避免浮点误差
-- ============================================================
CREATE TABLE IF NOT EXISTS `tb_product` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint UNSIGNED NOT NULL COMMENT '所属店铺',
  `name` varchar(64) NOT NULL COMMENT '商品名',
  `description` varchar(255) DEFAULT NULL COMMENT '一句话描述',
  `image` varchar(255) DEFAULT NULL COMMENT '图片',
  `price` int UNSIGNED NOT NULL COMMENT '单价，单位分',
  `stock` int NOT NULL DEFAULT 0 COMMENT '库存',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '1 上架 / 0 下架',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_shop`(`shop_id`, `status`, `sort`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = COMPACT COMMENT='店铺商品';

CREATE TABLE IF NOT EXISTS `tb_product_order` (
  `id` bigint UNSIGNED NOT NULL COMMENT '订单号（RedisIdWorker 生成）',
  `user_id` bigint UNSIGNED NOT NULL,
  `shop_id` bigint UNSIGNED NOT NULL,
  `total_amount` int UNSIGNED NOT NULL COMMENT '订单总额，单位分',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '1 待支付 / 2 已支付 / 3 已核销 / 4 已关闭',
  `verify_code` varchar(32) NOT NULL COMMENT '核销码（二维码内容）',
  `version` int UNSIGNED NOT NULL DEFAULT 0 COMMENT 'CAS 乐观锁版本号',
  `expire_time` datetime NOT NULL COMMENT '支付截止时间',
  `pay_time` datetime DEFAULT NULL,
  `verify_time` datetime DEFAULT NULL,
  `close_time` datetime DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_verify_code`(`verify_code`) USING BTREE,
  KEY `idx_status_expire`(`status`, `expire_time`) USING BTREE,
  KEY `idx_user`(`user_id`, `create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = COMPACT COMMENT='商品订单';

CREATE TABLE IF NOT EXISTS `tb_product_order_item` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id` bigint UNSIGNED NOT NULL,
  `product_id` bigint UNSIGNED NOT NULL,
  `product_name` varchar(64) NOT NULL COMMENT '下单时的商品名快照',
  `price` int UNSIGNED NOT NULL COMMENT '下单时的单价快照，单位分',
  `quantity` int NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_order`(`order_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = COMPACT COMMENT='商品订单明细';

-- ------------------------------------------------------------
-- 103茶餐厅（shop_id = 1）的商品
-- ------------------------------------------------------------
INSERT INTO `tb_product` (`shop_id`, `name`, `description`, `image`, `price`, `stock`, `status`, `sort`)
SELECT 1, '黯然销魂饭', '招牌叉烧 + 两颗溏心蛋', '/assets/food.jpg', 3800, 50, 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tb_product` WHERE `shop_id` = 1 AND `name` = '黯然销魂饭');

INSERT INTO `tb_product` (`shop_id`, `name`, `description`, `image`, `price`, `stock`, `status`, `sort`)
SELECT 1, '铜锣湾漏奶华', '爆浆奶盖吐司', '/assets/food.jpg', 2800, 40, 1, 2 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tb_product` WHERE `shop_id` = 1 AND `name` = '铜锣湾漏奶华');

INSERT INTO `tb_product` (`shop_id`, `name`, `description`, `image`, `price`, `stock`, `status`, `sort`)
SELECT 1, '神仙一口西多士', '外脆内软，炼乳加持', '/assets/food.jpg', 1600, 60, 1, 3 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tb_product` WHERE `shop_id` = 1 AND `name` = '神仙一口西多士');

INSERT INTO `tb_product` (`shop_id`, `name`, `description`, `image`, `price`, `stock`, `status`, `sort`)
SELECT 1, '怀旧五柳炸蛋饭', '四个鸡蛋的快乐', '/assets/food.jpg', 2800, 45, 1, 4 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tb_product` WHERE `shop_id` = 1 AND `name` = '怀旧五柳炸蛋饭');

INSERT INTO `tb_product` (`shop_id`, `name`, `description`, `image`, `price`, `stock`, `status`, `sort`)
SELECT 1, '烧味双拼例牌', '烧鹅 + 叉烧，每日现烧', '/assets/food.jpg', 6600, 20, 1, 5 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tb_product` WHERE `shop_id` = 1 AND `name` = '烧味双拼例牌');

INSERT INTO `tb_product` (`shop_id`, `name`, `description`, `image`, `price`, `stock`, `status`, `sort`)
SELECT 1, '红烧脆皮乳鸽', '皮脆肉嫩，一口爆油', '/assets/food.jpg', 1880, 30, 1, 6 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tb_product` WHERE `shop_id` = 1 AND `name` = '红烧脆皮乳鸽');

INSERT INTO `tb_product` (`shop_id`, `name`, `description`, `image`, `price`, `stock`, `status`, `sort`)
SELECT 1, '冻柠茶', '茶走少甜，解腻首选', '/assets/cafe.jpg', 1200, 100, 1, 7 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tb_product` WHERE `shop_id` = 1 AND `name` = '冻柠茶');
