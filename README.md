# 个人日历

面向个人的安卓坚持日历应用。每个日历独立命名、设置重复日期和本地提醒，并按天打卡；第一组示例日历是“吃药”。

## 当前进度

当前测试版本：**0.2.0（构建号 2）**。已对照原型补充新建日历实时预览、列表月度完成天数、日期状态图例、登录邮箱、账号安全、时区说明、隐私说明和帮助反馈。完整差异见[原型功能核对](docs/原型功能核对.md)。

已建立 Android 工程和“今天 / 日历 / 我的”三栏导航。现在可以在手机本地创建多个独立的坚持日历，选择每天或指定星期，在当天打卡，编辑名称、图标、颜色与今后的重复日期，暂停或恢复日历，并查看各自的月历与完成天数。误打卡可以在当天撤销；建错的日历可在详情页确认后删除，历史打卡会一并删除。“今天”展示进度和任务卡片，“日历”提供横向切换与月历预览。每个日历可以单独设置计划日提醒时间；到点显示本地通知，点击通知进入对应日历。日历和打卡通过 Room 保存在本机，重复点击同一天不会重复计数；编辑和暂停不会改写过去的日期状态。“我的”新增可选登录、自动云备份、立即备份和整份恢复；不强制登录，不做多设备实时同步。

- [原型与开发计划](docs/开发计划.md)
- [本地数据库设计](docs/本地数据库设计.md)
- [AWS 云备份部署与使用](docs/AWS云备份部署.md)
- [原型功能核对与补齐](docs/原型功能核对.md)
- [核心页面原型](docs/prototype/core-screens.png)
- [登录与“我的”页面原型](docs/prototype/login-and-profile.png)

原型中的日期、完成率和账号均为展示用示例。

## 开发工具与插件

| 项目 | 是否需要 | 说明 |
| --- | --- | --- |
| [Android Studio](https://developer.android.com/studio/install) | 推荐安装 | 用于编辑、Compose 预览、模拟器和真机调试。使用支持 Android API 37.0 / AGP 9.1.1 的 Panda 3 Patch 1 或更新版本。当前开发机未检测到 Android Studio，但命令行构建可用。 |
| Android SDK Platform 37.0、Build Tools 36.0.0、Platform Tools | 必需 | 在 Android Studio 的 SDK Manager 中安装；当前开发机已具备。真机调试需 Platform Tools，模拟器另装系统镜像。 |
| JDK 21 或更新版本 | 必需 | SDK 36 的 Robolectric 测试需要 JDK 21；命令行默认仍是 Java 8，构建前必须把 `JAVA_HOME` 指向 JDK 21。 |
| Gradle、Android Gradle Plugin、Compose Compiler Gradle Plugin、KSP | 自动安装 | 仓库自带 Gradle Wrapper；构建时按工程文件下载 AGP 9.1.1、Compose Compiler、KSP 2.3.12、Room 2.8.5 和 Compose 依赖，无需手动安装 IDE 插件。测试还会自动下载 Robolectric 4.17。 |
| Android Studio 的 Kotlin / Compose 支持 | 随 Studio 提供 | 无需额外下载第三方 Kotlin、Compose 或 Codex 插件。 |
| Node.js、AWS CLI、AWS CDK | 云端开发需要 | Node.js 20+、AWS CLI 2.32+；在 `cloud` 中执行 `npm.cmd ci` 安装锁定的 CDK、AWS SDK 和 TypeScript，无需全局安装 CDK。AWS Toolkit IDE 插件可选。 |
| AppAuth、WorkManager | 自动安装 | 安卓构建自动下载，用于浏览器 PKCE 登录和持久化后台备份，不需要手动下载插件。 |

工程目前使用 Gradle 9.3.1、AGP 9.1.1、Compose BOM 2026.08.00，最低 Android 8.0（API 26），编译 API 37，目标 API 36。版本依据：[AGP 兼容性](https://developer.android.com/build/releases/agp-9-1-0-release-notes)、[Compose 配置](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)。

## 构建与运行

1. 将 `JAVA_HOME` 设置为 JDK 21 或更新版本，将 `ANDROID_HOME` 设置为 Android SDK 目录；不要把本机 SDK 路径写入仓库。Android Studio 通常会自动识别 SDK。
2. 在仓库根目录运行 `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`。生成的 APK 在 `app/build/outputs/apk/debug/app-debug.apk`。如果本机内存紧张，可在命令末尾添加 `--max-workers=1 '-Dorg.gradle.jvmargs=-Xms64m -Xmx512m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -Dfile.encoding=UTF-8' '-Dorg.gradle.parallel=false' '-Pkotlin.compiler.execution.strategy=in-process'`。
3. 在 Android Studio 中打开仓库根目录，选择 `app` 运行配置，在模拟器或 Android 8.0 及以上真机运行。当前开发机尚无模拟器或已连接的真机，界面流程已用 Robolectric 本地测试验证，实际屏幕布局和系统提醒仍待设备验收。

首次构建需要联网下载 Gradle 和 AndroidX 依赖。`app/schemas` 中的 Room 数据库结构文件应随模型变更一起提交，以便后续编写数据库迁移。

日历默认仅保存在本机。需要云备份时，先按部署文档生成 `cloud-config.properties` 再构建，在“我的”登录并明确开启备份。换机、卸载或清除数据前检查最近成功备份时间；恢复会替换本机全部记录。退出登录保留本机数据并停止自动备份。

Android 13 及以上需允许通知；Android 12 及以上若未允许“闹钟与提醒”，应用会使用系统非精确闹钟，通知可能延迟。手机重启、系统时间/时区变化后会重新安排提醒。不同厂商的省电策略仍需真机测试。

## 开发流程

按[开发计划](docs/开发计划.md)推进。本地验证运行 Gradle 单元测试、构建与 Lint；云端在 `cloud` 运行 `npm.cmd run check`、`npm.cmd test`、`npx.cmd cdk synth --strict`，部署前查看 `cdk diff`。测试环境可执行 `cloud/scripts/smoke.ps1` 验证真实云资源。实际浏览器登录回跳、换机恢复、后台网络限制和通知需要真机验收。不得把本机配置、密钥、令牌、用户备份或签名文件提交到仓库。
