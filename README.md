# 个人日历

面向个人的安卓坚持日历应用。每个日历独立命名、设置重复日期和本地提醒，并按天打卡；第一组示例日历是“吃药”。

## 当前进度

已建立可编译运行的 Android 工程，包含“今天 / 日历 / 我的”三栏导航、统一配色和首次使用时的空状态。创建日历、打卡、登录、提醒与云同步尚未接入；当前界面不会伪造这些功能的结果。

- [原型与开发计划](docs/开发计划.md)
- [核心页面原型](docs/prototype/core-screens.png)
- [登录与“我的”页面原型](docs/prototype/login-and-profile.png)

原型中的日期、完成率和账号均为展示用示例。

## 开发工具与插件

| 项目 | 是否需要 | 说明 |
| --- | --- | --- |
| [Android Studio](https://developer.android.com/studio/install) | 推荐安装 | 用于编辑、Compose 预览、模拟器和真机调试。使用支持 Android API 37.0 / AGP 9.1.1 的 Panda 3 Patch 1 或更新版本。当前开发机未检测到 Android Studio，但命令行构建可用。 |
| Android SDK Platform 37.0、Build Tools 36.0.0、Platform Tools | 必需 | 在 Android Studio 的 SDK Manager 中安装；当前开发机已具备。真机调试需 Platform Tools，模拟器另装系统镜像。 |
| JDK 17 或更新版本 | 必需 | 当前开发机已有 JDK 21；命令行默认仍是 Java 8，构建前必须把 `JAVA_HOME` 指向 JDK 21。 |
| Gradle、Android Gradle Plugin、Compose Compiler Gradle Plugin | 自动安装 | 仓库自带 Gradle Wrapper；构建时按工程文件下载 AGP 9.1.1、Compose Compiler 插件和 Compose 依赖，无需手动安装 IDE 插件。 |
| Android Studio 的 Kotlin / Compose 支持 | 随 Studio 提供 | 无需额外下载第三方 Kotlin、Compose 或 Codex 插件。 |
| Node.js、AWS CLI、AWS CDK | 后端阶段需要 | 当前开发机已有 Node.js 和 AWS CLI；CDK 后续作为项目依赖安装，不要求全局安装。AWS Toolkit IDE 插件可选。 |

工程目前使用 Gradle 9.3.1、AGP 9.1.1、Compose BOM 2026.08.00，最低 Android 8.0（API 26），编译 API 37，目标 API 36。版本依据：[AGP 兼容性](https://developer.android.com/build/releases/agp-9-1-0-release-notes)、[Compose 配置](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)。

## 构建与运行

1. 将 `JAVA_HOME` 设置为 JDK 17 或更新版本，将 `ANDROID_HOME` 设置为 Android SDK 目录；不要把本机 SDK 路径写入仓库。Android Studio 通常会自动识别 SDK。
2. 在仓库根目录运行 `./gradlew.bat :app:assembleDebug`。生成的 APK 在 `app/build/outputs/apk/debug/app-debug.apk`。
3. 在 Android Studio 中打开仓库根目录，选择 `app` 运行配置，在模拟器或 Android 8.0 及以上真机运行。

当前开发机已用 JDK 21 和已安装的 Android SDK 成功执行 `:app:assembleDebug`。首次构建需要联网下载 Gradle 和 AndroidX 依赖。

## 开发流程

按[开发计划](docs/开发计划.md)逐阶段推进：先完成可用的本地日历与打卡，再接入提醒、账号和云同步；每阶段实现后运行对应单元测试、构建与真机验证，并在独立分支提交审查。不得把 `local.properties`、密钥、令牌或签名文件提交到仓库。
