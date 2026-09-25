-- 模块升级迁移脚本：删除 users 表冗余的 salt 字段
-- 旧版 Node 后端使用 salt + hash 密码存储；新版 Spring Boot 使用 BCrypt（自带盐），
-- salt 字段已废弃且会导致注册报错 "Field 'salt' doesn't have a default value"。
-- 兼容 MySQL 8.0。

USE medication_radar;

ALTER TABLE users DROP COLUMN salt;