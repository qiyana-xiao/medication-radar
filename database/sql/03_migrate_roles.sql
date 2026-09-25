-- 三角色迁移脚本：将旧 'user' 角色改为 'patient'，扩展约束为 patient/doctor/admin
-- 兼容 MySQL 8.0，使用 --force 执行以忽略约束不存在的报错

USE medication_radar;

-- 1. 存量 user → patient
UPDATE users SET role = 'patient' WHERE role = 'user';

-- 2. 尝试删除旧 CHECK 约束（如不存在会报错，用 --force 跳过）
ALTER TABLE users DROP CHECK chk_users_role;

-- 3. 添加新 CHECK 约束
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('patient', 'doctor', 'admin'));

-- 4. 修改默认值
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'patient';
