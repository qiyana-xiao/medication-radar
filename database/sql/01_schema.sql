-- 用药雷达 MySQL 8.0 初始化脚本
-- 可重复执行，不删除已有表和数据；应用本身不会自动执行本脚本。

CREATE DATABASE IF NOT EXISTS medication_radar
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE medication_radar;

CREATE TABLE IF NOT EXISTS users (
  id INT NOT NULL AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password_hash VARCHAR(256) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'patient',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username),
  CONSTRAINT chk_users_role CHECK (role IN ('patient', 'doctor', 'admin'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reports (
  id INT NOT NULL AUTO_INCREMENT,
  user_id INT NOT NULL,
  patient JSON NOT NULL,
  medications JSON NOT NULL,
  report JSON NOT NULL,
  risk_level VARCHAR(10) NOT NULL DEFAULT '中',
  score INT NOT NULL DEFAULT 0,
  favorite TINYINT(1) NOT NULL DEFAULT 0,
  knowledge_version JSON NULL COMMENT '生成时引用的知识库版本快照（药物id+version）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_reports_user_created (user_id, created_at, id),
  KEY idx_reports_user_risk (user_id, risk_level),
  KEY idx_reports_user_favorite (user_id, favorite),
  CONSTRAINT fk_reports_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT chk_reports_risk CHECK (risk_level IN ('低', '中', '高', '极高')),
  CONSTRAINT chk_reports_score CHECK (score BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS medications (
  id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  aliases JSON NULL,
  category VARCHAR(100) NOT NULL DEFAULT '',
  ingredients TEXT NULL,
  appearance TEXT NULL,
  specification TEXT NULL,
  dosage_form VARCHAR(100) NOT NULL DEFAULT '',
  indications TEXT NULL,
  usage_dosage TEXT NULL,
  adverse_reactions TEXT NULL,
  contraindications TEXT NULL,
  precautions TEXT NULL,
  special_populations TEXT NULL,
  pharmacology TEXT NULL,
  therapeutic_duplication TEXT NULL,
  monitor TEXT NULL,
  notes TEXT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active=上线, inactive=停用',
  version INT NOT NULL DEFAULT 1 COMMENT '知识库版本号，每次编辑+1',
  disabled_reason VARCHAR(500) NULL COMMENT '停用原因',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_medications_name (name),
  KEY idx_medications_category (category),
  KEY idx_medications_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS interactions (
  id INT NOT NULL AUTO_INCREMENT,
  drug_a VARCHAR(100) NOT NULL,
  drug_b VARCHAR(100) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  mechanism TEXT NULL,
  effect TEXT NULL,
  recommendation TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_interactions_pair (drug_a, drug_b),
  KEY idx_interactions_drug_b (drug_b)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

CREATE TABLE IF NOT EXISTS admin_operation_logs (
  id INT NOT NULL AUTO_INCREMENT,
  admin_user_id INT NOT NULL,
  operation VARCHAR(50) NOT NULL,
  target_type VARCHAR(50) NOT NULL,
  target_id VARCHAR(100) NULL,
  summary VARCHAR(500) NULL COMMENT '只保存脱敏摘要，不保存密码、令牌、患者资料、完整提示词或报告',
  before_value TEXT NULL COMMENT '变更前值（仅药物知识库字段，JSON格式）',
  after_value TEXT NULL COMMENT '变更后值（仅药物知识库字段，JSON格式）',
  ip_address VARCHAR(45) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_admin_logs_user_created (admin_user_id, created_at),
  KEY idx_admin_logs_target (target_type, target_id),
  CONSTRAINT fk_admin_logs_user FOREIGN KEY (admin_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
