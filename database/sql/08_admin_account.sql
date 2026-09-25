-- 内置管理员账号（账号密码见 README「快速开始」；重复执行会把该账号密码重置为文档值）
-- 密码哈希由 BCrypt 生成，与后端 PasswordEncoder 校验兼容
INSERT INTO users (username, password_hash, role)
VALUES ('admin', '$2b$10$bMH8y2rzzwlJ7HyO5oe17.eovayyGuQkt25qGBSgWIgTyfbMdAng2', 'admin')
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), role = 'admin';
