# 数据库初始化说明

本目录不是独立数据库服务，也不是第二个数据库工程。项目只使用一个 MySQL 数据库：`medication_radar`。

## 本地初始化

1. 确认本机已安装并启动 MySQL 8.0。
2. 使用有建库权限的 MySQL 账号执行：

   ```powershell
   mysql -u root -p < database/sql/01_schema.sql
   mysql -u root -p medication_radar < database/sql/02_knowledge_seed.sql
   ```

3. 创建应用专用账号并授权。请自行替换用户名、来源主机和密码，不要把真实密码提交到仓库：

   ```sql
   CREATE USER 'medication_app'@'localhost' IDENTIFIED BY '替换为强密码';
   GRANT SELECT, INSERT, UPDATE, DELETE ON medication_radar.* TO 'medication_app'@'localhost';
   FLUSH PRIVILEGES;
   ```

4. 配置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 后启动 Java 后端。

Spring Boot 已关闭 SQL 自动初始化，不会自动建库、建表或修改表结构。将来迁移到云端 MySQL 时执行同一份脚本。

`02_knowledge_seed.sql` 由已备份的 JSON 知识数据生成，包含 283 条药物和 18 条相互作用。
重复执行只会跳过同名药物和同一药物对，不会覆盖数据库中已经人工修订的内容。源 JSON 更新后可运行
`node database/tools/generate-knowledge-sql.js` 重新生成。

用户密码只保存 Spring Security BCrypt 哈希。旧 Node 用户密码和 JWT 不迁移；切换到 Java 版本后需重新注册或由管理员建立新的账号流程。

## 管理员授权

系统不会创建默认管理员，也不会把首个注册用户自动设为管理员。先正常注册，再由数据库管理员执行：

```sql
UPDATE medication_radar.users SET role = 'admin' WHERE username = '实际用户名';
```
