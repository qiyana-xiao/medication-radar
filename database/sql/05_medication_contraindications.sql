-- 模块5迁移脚本：禁忌规则表（P0-1 禁忌规则 + 严重程度条件）
-- 设计说明：
--   * 本表为评分规则的唯一权威来源，初始为空表（预期状态，不预填数据）
--   * 规则命中数 = 0 的药物 → 引擎判定"本地知识库暂无该药规则，无法评估"，不进入 LLM 分析、不参与评分
--   * severity_required 支持"重度"（只有明确重度才按禁忌扣分，否则降级"需关注"）与"任何"（匹配即命中）
-- 兼容 MySQL 8.0，使用 --force 执行以忽略表已存在的报错

USE medication_radar;

CREATE TABLE medication_contraindications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    medication_id BIGINT NOT NULL COMMENT '关联 medications.id',
    condition_keyword VARCHAR(100) NOT NULL COMMENT '触发条件关键词，如：冠心病、高血压',
    severity_required VARCHAR(20) NOT NULL DEFAULT '重度' COMMENT '重度=仅明确重度时按禁忌扣分；任何=匹配即扣分',
    score INT NOT NULL DEFAULT 0 COMMENT '命中后累加的风险分（0-100，累加语义，非扣减）',
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'contraindication' COMMENT 'contraindication=禁忌 / caution=需关注',
    note VARCHAR(500) DEFAULT NULL COMMENT '患者可读说明文案，如"若您的冠心病属于重度，则该药禁用，请向医生确认"',
    source VARCHAR(200) DEFAULT NULL COMMENT '依据来源，如：药品说明书（NMPA 备案）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=有效 0=停用（软删除）',
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_contraindication_med (medication_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药物禁忌规则表（确定性评分依据，空表为预期状态）';
