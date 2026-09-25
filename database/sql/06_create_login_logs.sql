-- 模块升级迁移脚本：创建缺失的 login_logs 表
-- 旧数据库从早期版本迁移而来时可能缺少该表，导致注册/登录报
-- "Table 'medication_radar.login_logs' doesn't exist"。
-- DDL 与 01_schema.sql 保持一致，可重复执行。

USE medication_radar;

CREATE TABLE IF NOT EXISTS login_logs (
  id INT NOT NULL AUTO_INCREMENT,
  user_id INT NULL,
  username VARCHAR(50) NULL,
  success TINYINT(1) NOT NULL,
  failure_reason VARCHAR(50) NULL,
  ip_address VARCHAR(45) NULL,
  user_agent VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_login_logs_user_created (user_id, created_at),
  KEY idx_login_logs_ip_created (ip_address, created_at),
  CONSTRAINT fk_login_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;