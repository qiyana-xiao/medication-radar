-- 模块4迁移脚本：报告快照 + 停用代替删除
-- 兼容 MySQL 8.0，使用 --force 执行以忽略字段已存在的报错

USE medication_radar;

-- 1. medications 表加 status、version、disabled_reason 字段
ALTER TABLE medications ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'active';
ALTER TABLE medications ADD COLUMN version INT NOT NULL DEFAULT 1;
ALTER TABLE medications ADD COLUMN disabled_reason VARCHAR(500) DEFAULT NULL;

-- 2. reports 表加 knowledge_version 字段（JSON）
ALTER TABLE reports ADD COLUMN knowledge_version JSON DEFAULT NULL COMMENT '生成时引用的知识库版本快照（药物id+version）';

-- 3. admin_operation_logs 表加 before_value、after_value 字段
ALTER TABLE admin_operation_logs ADD COLUMN before_value TEXT DEFAULT NULL COMMENT '变更前值（仅药物知识库字段，JSON格式）';
ALTER TABLE admin_operation_logs ADD COLUMN after_value TEXT DEFAULT NULL COMMENT '变更后值（仅药物知识库字段，JSON格式）';

-- 4. 为 medications.status 添加索引
ALTER TABLE medications ADD INDEX idx_medications_status (status);
