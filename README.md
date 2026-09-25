# 用药雷达

用药安全分析辅助平台，面向多重用药患者/家属和医生/药师。帮助患过重病的人在小感冒时判断"这个药我能不能吃"，以及给医生用药提供参考。

**核心定位**：只提供参考，不诊断、不开处方、不替代医生或药师。

## 功能特色

- **三角色差异化**：患者/家属、医生/药师、管理员，权限完全隔离
- **两组用药分析**：区分「长期用药」和「本次新增」，只检查新增药与基础药的冲突
- **商品名识别**：支持输入商品名（如"白加黑"），系统自动识别成分判风险
- **查不到明说**：未收录药物明确提示"无法评估"，严禁假阴性
- **同成分超量提醒**：多种药含同一成分时累计日总量，超上限最高优先级警告
- **报告差异化**：患者看四选一动作结论，医生看可解释评分+机制+证据级别
- **患者视角预览**：医生可切换查看患者会看到的内容
- **健康时间线**：追踪每次分析的风险变化和用药变化
- **知识库版本快照**：报告生成时记录当时知识库版本，历史报告不漂移
- **软删除保护**：被历史报告引用的药物禁止停用
- **NMPA 官方入口**：报告中可一键跳转国家药监局查询官方说明书
- **症状入口**：患者可输入当前症状（头痛、咳嗽等），辅助分析
- **体征通俗化**：血压 150/95 自动显示为"血压偏高（2级高血压）"，患者看得懂

## 项目结构

```text
frontend/       React + Vite 前端
backend-java/   Spring Boot 后端（仓库自带已构建 jar，无需 Maven）
database/       MySQL 建库与知识数据 SQL
docs/           项目介绍、代码架构、重构文档
scripts/        一键启动/停止/建库/环境体检/便携运行时下载脚本
logs/           运行日志：每次启动自动写入 backend.log、frontend.log 等，
                排查问题用；内容只增不改业务数据，可随时整目录删除
backups/        药物知识及旧系统只读归档，不参与运行
runtime/        仅当本机没有 Java 17+/Node 18+ 时脚本自动下载的便携运行时；
                只放在本项目内、不写注册表，删除项目文件夹即彻底清除
```

项目运行时只有一个 MySQL 数据库 `medication_radar`。Spring Boot 不会自动创建数据库、表或导入数据。

## 三类用户

| 角色 | 能做什么 | 不能做什么 |
|------|----------|------------|
| **患者/家属** | 发起分析、看自己报告与历史、查知识库、看健康时间线 | 看不到专业机制内容，进不去后台管理 |
| **医生/药师** | 患者全部功能 + 专业版报告（机制、证据级别、剂量调整、替代方案）+ 患者视角预览 | 进不去后台管理，看不到他人报告 |
| **管理员** | 知识库维护、数据源同步、操作审计、全局统计 | 不能发起分析，看不到任何患者的报告与隐私数据 |

注册时自选身份（患者/医生），默认患者，不做资质审核。

## 默认管理员账号

```text
账号：admin
密码：admin123
```

知识库维护与 AI 服务设置只能由管理员完成，请用以上账号登录后台。
部署给他人使用前，建议登录后尽快修改默认密码。

## AI 密钥配置（DeepSeek，界面内填写）

AI 用药分析报告依赖 DeepSeek 大模型。密钥**不写进任何文件**，由管理员在网页内配置：

1. 用 admin 登录 → 进入「知识库管理」→ 顶部「AI 服务设置」→ 点击「配置 ▼」
2. 填写 DeepSeek API Key（在 [DeepSeek 开放平台](https://platform.deepseek.com/) 申请）→ 保存
3. 保存后**立即生效，无需重启**；密钥经 AES-256-GCM 加密存入数据库，
   任何文件、日志、接口返回中都只出现掩码（如 `sk-****2148`）
4. 「清除」后自动回退到 `backend-java\.env` 中的密钥（如有）；两者都未配置时，
   AI 分析自动降级为本地规则匹配，不影响其他功能

## 快速开始（下载即用）

**唯一前置条件**：本机安装并启动了 MySQL 8.0（[官方安装包](https://dev.mysql.com/downloads/installer/)）。

环境策略：**优先使用系统已安装的 Java 17+ / Node 18+ / Redis**；检测不到或版本过低时，
脚本才会询问是否下载便携版到项目 `runtime\` 文件夹（仅本项目可用、不写注册表；
删除项目文件夹后，下载的运行时随之清除，不会残留或污染本机已有环境）。

Redis 为**可选组件**（限流/令牌注销加速）：启动时若未检测到，脚本会说明降级影响并
询问是否一键安装便携版（约 12MB）——选"是"自动下载启动，选"否"跳过照常运行；
也可随时手动运行 `scripts\setup-redis.ps1 -Start`（`-Uninstall` 可彻底删除）。

1. 双击 `用药雷达.bat`
2. 首次运行会引导填写 MySQL 密码等（自动生成 `backend-java\.env`，该文件已被 Git 忽略）
3. 检测到数据库未初始化时，可一键自动执行 `database\sql\` 下全部建库 SQL
4. 就绪后自动打开浏览器：前端 `http://localhost:5173`，后端 `http://localhost:8080`

停止服务：双击 `停止-用药雷达.bat`，或直接关闭启动窗口。
环境体检（只读诊断）：双击 `体检-用药雷达.bat`。

仓库自带已构建的后端 jar（`backend-java\target\`），**无需安装 Maven**；
只有修改后端代码重新构建时才需要 JDK + Maven：`cd backend-java && mvn -DskipTests package`。
仓库不含 `node_modules`，首次启动会自动 `npm install`。

### 手动初始化（可选，替代一键引导）

```powershell
mysql -u root -p < database/sql/01_schema.sql
mysql -u root -p medication_radar < database/sql/02_knowledge_seed.sql
```

**已有数据库升级**（从旧版本迁移）：

```powershell
mysql -u root -p medication_radar < database/sql/03_migrate_roles.sql
mysql -u root -p medication_radar < database/sql/04_migrate_knowledge_versioning.sql
```

根据 [database/README.md](database/README.md) 创建应用账号，然后在 `backend-java/` 中复制
`.env.example` 为 `.env` 并填写本地配置。`.env` 由一键启动脚本读取，不会提交到 Git。

### 启动方式汇总

- 日常：双击 `用药雷达.bat`（或桌面快捷方式，首次启动后自动创建）
- 命令行：`powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1`
- 其余脚本在 `scripts\` 文件夹：`stop.ps1`（停止）、`init-database.ps1`（建库）、
  `check-env.ps1`（环境体检）、`setup-runtime.ps1`（手动下载便携 Java/Node）

## 验证

```powershell
cd backend-java
mvn clean test package

cd ..\frontend
npm ci
npm run build
```

默认管理员账号 `admin / admin123` 由建库 SQL（`database\sql\08_admin_account.sql`）自动创建；
普通用户注册时选择身份（患者/医生），需要新增管理员时按
[database/README.md](database/README.md) 中的语句授予 `admin` 角色。

## 医学内容红线

1. 相互作用、剂量上限、禁忌、成分映射一律来自本地知识库，代码只读不改
2. AI 只负责组织语言和通俗解释，不得补充或推断医学事实
3. 现有 283 条药品与 18 组相互作用原样保留
4. 数据缺失时用占位 + `TODO: 待医学审核`，不自行编造

## 合规与免责

- 免责声明「本报告仅供参考，不替代医师或药师的诊断与处方建议」紧邻结论区展示
- 三不做：不诊断、不开处方、不推荐具体品牌
- 患者疾病、用药、体征属敏感健康信息：使用前单独授权勾选
- 日志与审计中不得出现患者身份与完整用药清单

## 技术文档

- [项目介绍](docs/项目介绍.md)
- [代码技术架构](docs/代码技术架构.md)
- [项目完整说明](docs/项目完整说明.md)（写给非技术读者的通俗全貌）
