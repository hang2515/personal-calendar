# AWS 云备份部署与使用

## 服务分工

Room 是日历、打卡和规则历史的工作数据库。AlarmManager 在手机提醒；WorkManager 在联网时执行备份。未登录也可使用全部本地功能。

用户主动登录后，通过 AppAuth 的授权码 + PKCE 流程取得 Cognito 访问令牌。API Gateway 验证签名、有效期、受众和 `personal-calendar/backup` scope，Lambda 再检查访问令牌类型，以令牌 `sub` 构造 S3 对象键。手机不包含 AWS 访问密钥或客户端密钥。

应用服务和备份数据使用香港 `ap-east-1`，包括 Cognito、HTTP API、Lambda、S3 和 CloudWatch。AWS Budgets 单独使用美国东部 `us-east-1` 堆栈管理全账号费用；香港不支持该预算 CloudFormation 资源。无需 EC2、RDS、NAT Gateway 或常驻开发电脑。登录使用 Cognito 托管页面；邮件注册和密码找回沿用其流程。测试阶段使用 Cognito 默认邮件发送配额，小范围正式使用前须验证邮件可达性；达到发送配额时接入 SES。

## 数据与接口

| 接口 | 用途 |
| --- | --- |
| `GET /v1/backup` | 最近备份时间、记录数量、ETag |
| `GET /v1/backup/snapshot` | 下载整份快照及 ETag |
| `PUT /v1/backup/snapshot` | 上传快照，必须带 `If-None-Match: *` 或 `If-Match` |

快照格式版本 1，包含备份 ID、数据集 ID、本地版本、UTC 时间、`Asia/Shanghai` 时区及三张业务表的记录。上限 1 MiB、1000 个日历、10000 条规则、10000 条打卡；超过任一上限明确失败，绝不截断。Room 版本独立升级，备份不携带登录凭据、账号绑定或 WorkManager 数据。

- S3 禁止公开访问、强制 HTTPS、使用 SSE-S3 加密、开启版本控制，非当前版本 30 天后过期。
- 每账号一个当前快照；首次创建和更新均使用条件写入。冲突返回 409，保留本地数据并等待用户选择。
- 手机将待上传快照和原 ETag 原子保存在应用私有目录；响应丢失时重试相同内容。服务器只对完全相同的当前对象视为重复成功。
- 用户预览并确认后才能覆盖云端或恢复本机。恢复完整校验后在单事务中替换，预览后本地变动则拒绝；恢复后自动备份关闭，重新安排提醒。
- 退出账号保留本机数据，停用自动备份并清除本机令牌。重新登录不自动绑定或上传。
- 本期没有逐条实时同步、自动合并、客户端历史版本选择或账号注销入口。若需彻底删除云备份，维护操作必须同时处理所有 S3 版本，不能只创建删除标记。

## 开发环境

需要 Node.js 20 或更新版本、AWS CLI 2.32 或更新版本、JDK 21 和项目要求的 Android SDK。无需安装 AWS Toolkit；CDK、AWS SDK、AppAuth、WorkManager 由项目依赖安装。不要改变其他项目的全局 Java 配置。

```powershell
aws login --profile my-aws
aws sts get-caller-identity --profile my-aws
cd cloud
npm.cmd ci
npm.cmd run check
npm.cmd test
npx.cmd cdk synth --strict --profile my-aws
```

本地 AWS 登录依赖有效的浏览器会话。授权时返回 400 可先选择 `Sign into new session` 重新登录；IAM 身份需具备 `SignInLocalDevelopmentAccess` 及相应部署权限。

## 部署步骤

1. 只读核对账号、额度和区域。需要时启用香港区域；新账号 Free Plan 有期限，额度未耗尽也可能先到期，不自动升级付费计划。
2. 首次执行 `npx.cmd cdk bootstrap aws://<账号ID>/ap-east-1 aws://<账号ID>/us-east-1 --profile my-aws --termination-protection`，分别准备应用和预算区域。CDK 会创建部署角色及构建产物存储。
3. 测试环境执行 `npx.cmd cdk diff --profile my-aws`，审查后执行 `npx.cmd cdk deploy --all --profile my-aws --outputs-file deployment-outputs-dev.json`。
4. 在 PowerShell 运行 `./scripts/smoke.ps1`：验证真实 API 的匿名和错误令牌拒绝，再直接调用已部署 Lambda 验证 S3 读写、账号隔离、重试与冲突。只清理本次随机测试键的版本。这不替代真实账号登录后的端到端验收。
5. 生产部署前在当前进程设置 `ALERT_EMAIL` 为预警接收邮箱，然后依次执行 `npx.cmd cdk synth --strict -c stage=prod --profile my-aws`、`npx.cmd cdk diff -c stage=prod --profile my-aws`、`npx.cmd cdk deploy --all -c stage=prod --profile my-aws --outputs-file deployment-outputs-prod.json`。完成后移除进程中的邮箱变量。
6. 运行 `./scripts/configure-android.ps1 -Stage prod`，从堆栈输出生成被 Git 忽略的 `cloud-config.properties`；重新构建 APK。不提供此文件时仍可构建完整本地模式。

生产预算为整个 AWS 账号每月 5 美元的消费提醒，排除额度抵扣及退款，分别在实际成本超过 80% 和 100% 时通知。预算不是自动停机上限。日志保留 7 天，API 限速 5 次/秒、突发 10 次；Lambda 按需执行，无预置并发。存储历史版本、请求、流量、邮件及监控可能收费。

数据堆栈及用户池启用删除保护，S3 和用户池采用保留策略。维护时先看 `cdk diff`，不得随意更改持久资源逻辑 ID。不要直接销毁 CDKToolkit 或用删除数据库重建的方式解决部署错误。

## 验收

### 中文登录配置（0.3.2）

登录域名使用 Cognito Managed Login v2，现有 Android 客户端通过 `AWS::Cognito::ManagedLoginBranding` 绑定默认样式。授权请求显式携带 `lang=zh-CN`，从登录页进入注册或找回密码页面时继续保留中文。Classic Hosted UI 不支持该本地化能力，不能只给旧版链接增加语言参数。

沿用现有 Essentials 用户池、客户端、PKCE、权限范围与密码策略，不新建账号库或更换用户池。更新登录版本后浏览器会话需要重新登录。邮件模板不由页面语言参数控制，实际收信和手机回跳需另行验收。详见[整月计划与中文登录](整月计划与中文登录.md)。

### 2026-10-03 部署核验

- 测试及生产环境的 Data、Service 堆栈均已部署至香港；生产 Budget 堆栈部署至美国东部。
- 每月 5 USD 预算已读取核对，80% 和 100% 实际费用通知已配置，接收邮箱已核对；邮箱仅通过部署参数传入。
- 生产 API 的匿名与无效令牌请求均返回 401；Cognito 登录页面返回 200，包含登录及注册入口。
- 测试环境已实际验证 Lambda/S3 的账号隔离、幂等上传、条件更新和快照读取，并清理本次测试对象。
- 本机已生成生产环境客户端配置，受 Git 忽略规则保护。

- 安卓：数据库升级、本地变更版本、导出及恢复、日期规则、界面流程、APK 构建和 Lint。
- 云端：格式校验、匿名拒绝、跨账号隔离、条件更新、重复请求、私有存储和 JWT scope。
- 仍需实际设备：注册验证邮件、浏览器登录回跳、后台网络限制、通知权限、断网后重试、两部设备换机恢复与冲突选择。
