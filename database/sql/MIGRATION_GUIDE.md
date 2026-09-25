# 数据库迁移指南

本文档说明从旧版本升级到当前版本需要执行的数据库迁移脚本。

---

## 迁移脚本一览

| 脚本 | 用途 | 影响范围 |
|------|------|----------|
| `03_migrate_roles.sql` | 角色体系迁移 | `users` 表 |
| `04_migrate_knowledge_versioning.sql` | 知识库版本追踪 | `medications`、`reports`、`admin_operation_logs` 表 |
| `05_migrate_drop_salt.sql` | 删除冗余 `salt` 字段 | `users` 表 |
| `06_create_login_logs.sql` | 创建缺失的登录日志表 | 新建 `login_logs` 表 |

---

## 执行顺序

**必须按顺序执行**，不可跳过：

```powershell
# 1. 角色迁移
mysql -u root -p medication_radar < database/sql/03_migrate_roles.sql

# 2. 知识库版本追踪
mysql -u root -p medication_radar < database/sql/04_migrate_knowledge_versioning.sql

# 3. 删除冗余 salt 字段（注册报错修复）
mysql -u root -p medication_radar < database/sql/05_migrate_drop_salt.sql

# 4. 创建缺失的 login_logs 表（登录报错修复）
mysql -u root -p medication_radar < database/sql/06_create_login_logs.sql
```

---

## 脚本 1：角色迁移（03_migrate_roles.sql）

### 改动内容

| 改动 | 说明 |
|------|------|
| `users.role` 约束 | 从 `IN ('admin', 'user')` 改为 `IN ('patient', 'doctor', 'admin')` |
| 存量数据 | 所有 `role = 'user'` 的记录改为 `role = 'patient'` |
| 默认值 | `role` 默认值从 `'user'` 改为 `'patient'` |

### 影响

- **旧 JWT 令牌全部失效**，所有用户需要重新登录
- 新注册用户可选择 `patient` 或 `doctor` 身份
- 管理员权限不变，仍为 `admin`

### 回滚

如需回滚，执行：

```sql
-- 将 patient 改回 user
UPDATE users SET role = 'user' WHERE role = 'patient';

-- 重建约束
ALTER TABLE users DROP CONSTRAINT chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('admin', 'user'));

-- 恢复默认值
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'user';
```

---

## 脚本 2：知识库版本追踪（04_migrate_knowledge_versioning.sql）

### 改动内容

| 表 | 新增字段 | 类型 | 说明 |
|----|----------|------|------|
| `medications` | `status` | VARCHAR(20) | 药物状态：`active`=上线，`inactive`=停用 |
| `medications` | `version` | INT | 知识库版本号，每次编辑 +1 |
| `medications` | `disabled_reason` | VARCHAR(500) | 停用原因 |
| `reports` | `knowledge_version` | JSON | 生成报告时引用的药物 id+version 快照 |
| `admin_operation_logs` | `before_value` | TEXT | 变更前值（仅药物知识库字段） |
| `admin_operation_logs` | `after_value` | TEXT | 变更后值（仅药物知识库字段） |

### 影响

- 药物删除改为**软删除**（`status = 'inactive'`），不再物理删除
- 被历史报告引用的药物**禁止停用**
- 报告生成时自动记录知识库版本快照
- 审计日志记录变更前后值（仅药物字段，不记患者资料）

### 兼容性

- 所有新增字段都有默认值，不影响现有数据
- `status` 默认为 `'active'`，现有药物自动上线
- `version` 默认为 `1`，现有药物版本号为 1
- `knowledge_version` 允许为 NULL，历史报告不受影响

### 回滚

如需回滚，执行：

```sql
-- 删除新增字段
ALTER TABLE medications DROP COLUMN status;
ALTER TABLE medications DROP COLUMN version;
ALTER TABLE medications DROP COLUMN disabled_reason;
ALTER TABLE reports DROP COLUMN knowledge_version;
ALTER TABLE admin_operation_logs DROP COLUMN before_value;
ALTER TABLE admin_operation_logs DROP COLUMN after_value;
```

---

## 脚本 3：删除冗余 salt 字段（05_migrate_drop_salt.sql）

### 背景

旧版 Node 后端使用 `salt + password_hash` 的密码存储方式，`users` 表存在 `salt VARCHAR(64) NOT NULL` 字段。新版 Spring Boot 后端改用 **BCrypt**（自带盐），`salt` 字段已废弃。

由于 `salt` 无默认值，注册时插入用户记录会报错：

```
SQLException: Field 'salt' doesn't have a default value
```

### 改动内容

| 改动 | 说明 |
|------|------|
| `users.salt` | 删除该字段 |

### 影响

- 新注册用户不再需要 `salt`，BCrypt 已内置盐值
- 旧用户的 `password_hash` 为非 BCrypt 格式，无法登录（本就需要重新注册/重置，符合"全员重新登录"策略）
- 无数据丢失风险（仅删除无用的盐值字段）

---

## 脚本 4：创建缺失的 login_logs 表（06_create_login_logs.sql）

### 背景

从旧版本迁移的数据库可能缺少 `login_logs` 表，导致注册/登录写入登录日志时报错：

```
SQLException: Table 'medication_radar.login_logs' doesn't exist
```

### 改动内容

| 改动 | 说明 |
|------|------|
| 新建 `login_logs` 表 | 与 `01_schema.sql` 定义一致，`IF NOT EXISTS` 可重复执行 |

### 影响

- 无数据丢失风险（纯新增表）

---

## 注意事项

1. **备份数据库**：执行迁移前务必备份
   ```powershell
   mysqldump -u root -p medication_radar > backup_before_migration.sql
   ```

2. **执行环境**：确保 MySQL 服务正在运行，且当前用户有 `ALTER TABLE` 权限

3. **执行时间**：两个脚本合计约 1-2 秒（取决于数据量）

4. **执行后**：
   - 所有用户需要重新登录（旧 JWT 失效）
   - 前端需要重新构建（如有缓存）
   - 后端需要重启（加载新字段映射）

---

## 验证迁移成功

```sql
-- 检查 users 表约束
SHOW CREATE TABLE users;
-- 应看到：CONSTRAINT chk_users_role CHECK (role in ('patient','doctor','admin'))

-- 检查 medications 表字段
DESCRIBE medications;
-- 应看到：status, version, disabled_reason

-- 检查 reports 表字段
DESCRIBE reports;
-- 应看到：knowledge_version

-- 检查 admin_operation_logs 表字段
DESCRIBE admin_operation_logs;
-- 应看到：before_value, after_value

-- 检查 users 表不应再有 salt 字段
DESCRIBE users;
-- 应看到：id, username, password_hash, role, created_at（无 salt）

-- 检查 login_logs 表已创建
SHOW TABLES LIKE 'login_logs';
-- 应返回：login_logs
```

---

## 常见问题

**Q: 迁移脚本报错"字段已存在"怎么办？**

A: 脚本使用 `ADD COLUMN IF NOT EXISTS`，理论上不会报错。如果仍报错，可手动检查字段是否存在后跳过。

**Q: 迁移后旧报告还能看吗？**

A: 可以。`knowledge_version` 允许为 NULL，历史报告正常显示，只是没有版本快照信息。

**Q: 迁移后药物还能删除吗？**

A: 可以，但改为软删除（`status = 'inactive'`）。被报告引用的药物禁止停用。

**Q: 如何恢复物理删除功能？**

A: 修改 `MedicationController.java` 的 `delete` 方法，将 `service.disable()` 改为 `medicationMapper.deleteById()`。但不建议，会破坏历史报告一致性。
