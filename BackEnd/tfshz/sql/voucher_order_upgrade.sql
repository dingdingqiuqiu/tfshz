-- 把秒杀券订单也升级成「完整的订单」：支付截止、核销码、CAS 版本号
ALTER TABLE `tb_voucher_order`
  ADD COLUMN `verify_code` varchar(32) NULL COMMENT '核销码（二维码内容）' AFTER `status`,
  ADD COLUMN `version` int UNSIGNED NOT NULL DEFAULT 0 COMMENT 'CAS 乐观锁版本号' AFTER `verify_code`,
  ADD COLUMN `expire_time` datetime NULL COMMENT '支付截止时间' AFTER `version`,
  ADD COLUMN `close_time` datetime NULL COMMENT '关闭时间' AFTER `pay_time`;

ALTER TABLE `tb_voucher_order`
  ADD UNIQUE INDEX `uk_voucher_verify_code` (`verify_code`) USING BTREE,
  ADD INDEX `idx_voucher_status_expire` (`status`, `expire_time`) USING BTREE;

-- 历史订单补一个核销码（这些单没有 expire_time，不会被定时任务关掉）
UPDATE `tb_voucher_order`
   SET `verify_code` = CONCAT('V', UPPER(SUBSTRING(MD5(UUID()), 1, 9)))
 WHERE `verify_code` IS NULL;
