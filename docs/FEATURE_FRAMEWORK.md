# Hchat 功能框架

## 协作与发布规则

- 提交信息、推送说明、Release notes 和工作流说明统一使用中文。
- 本地默认只负责修改代码、文档和配置；除非明确允许，否则一律不在本地执行 Gradle。
- 明确允许“本地编译”或“编译检查”时，可以执行 `:app:compileReleaseKotlin` / `:app:compileReleaseJavaWithJavac` 这类只编译不出包的任务。
- 每次代码修改完成后都提交并推送到 GitHub，保持远程仓库同步。
- 项目维护两个发布频道：`main` 主线和 `alt-entry` 分支。功能、接口、逆向适配、脚本插件、文档和发布流程改动默认必须同步到 `main` 与 `alt-entry`，但 `alt-entry` 保留频道差异：`消息显示时间`、`历史发言记录`、`红包显示详情`、`跳过网页风险` 与 `视频号媒体下载` 是分支专属入口，分别放在聊天、界面、红包转账和杂项分组，不需要同步到 `main`。
- 普通双分支改动先落到 `main` 并推送，再用 rebase 或 cherry-pick 线性同步到 `alt-entry` 并推送；禁止用默认 merge 生成 `Merge remote-tracking branch ...` 这类提交，保持分支提交列表干净。只有用户明确要求单频道改动，或改动属于上述 `alt-entry` 频道差异时，才只改一个分支。
- 只要改动会影响功能行为、对外接口、脚本插件调用方式、UI 入口、文件路径、构建/发布流程或逆向依据，就必须同步更新相关文档、项目内的 `skills/hchat-project/SKILL.md`，以及 Codex 已安装的 `/data/data/com.termux/files/home/.codex/skills/hchat-project/SKILL.md`。
- 新增脚本插件接口时必须优先对齐 WA 同款调用方式：方法名、参数顺序、参数类型和返回值都按 WA 文档或现有 WA 插件实现；不要额外要求脚本传 `pluginDir`、`context` 等宿主参数。底层暂时拿不到完整返回值时，也要保持 WA 签名，用 `null`、空列表或空字符串等兼容返回兜底。
- 新增脚本插件接口时必须一个接口一个提交；不要把多个新增接口混在同一个 commit 里，方便逐个验证和回滚。
- 只要脚本插件接口发生新增、删除、参数变化、返回值变化或行为变化，必须同步更新面向插件作者的 `docs/SCRIPT_PLUGIN_API.md`。
- 插件 Agent 只能把接口文档、内置开发指南及当前运行时/工具结果明确确认的内容当作事实；能力、可用性或限制没有明确依据时，必须说明未知或需要运行时验证，不得猜测。
- APK 默认交给 GitHub Actions 构建；明确允许“本地构建”时，才可以按本文档的本地 release 构建命令执行并生成 APK。
- 本地项目保持 `compileSdk 34` / `targetSdk 34`；GitHub Actions 构建时临时覆盖为 `compileSdk 37` / `targetSdk 37`。
- 工作流只允许手动触发。代码推送后不会自动构建，需要 APK 时再到 GitHub Actions 手动运行对应工作流。
- 修改代码后先推送；确认需要出包时，再手动运行工作流并检查 Release。
- 工作流每次构建都会同时拉取 `main` 和 `alt-entry` 两个分支，使用同一个 `versionName/versionCode` 分别构建主线包和 `alt-entry` 包，然后按当前最高 `v*` tag 创建下一个 Release。版本名三段都按 0-9 进位，`patch` 超过 9 进到 `minor`，`minor` 超过 9 进到 `major`，避免生成 `1.10.x` 这类未归一化版本。Release 标题只显示模块版本号并标记为 GitHub Latest；资产里同时包含 `Hchat-release-signed.apk` 和 `Hchat-alt-entry-release-signed.apk`。APK `versionCode` 使用 GitHub Actions 构建编号。
- 除了双频道工作流 `.github/workflows/android.yml` 外，还提供单频道工作流 `.github/workflows/android-single.yml`。单频道工作流只拉取当前触发分支并只构建当前频道 APK：`main` 产物是 `Hchat-release-signed.apk`，`alt-entry` 产物是 `Hchat-alt-entry-release-signed.apk`。单频道版本以本地已使用的 `5.5.6 (490)` 和该工作流运行号 `294` 为对齐锚点；计算 `versionName` 时会把 `5.5.6` 与现有 Release/tag 一并取最高版本后递增，`versionCode` 按锚点后的单频道运行次数递增，因此下一次单频道构建为 `5.5.7 (491)`。签名、Release notes 和 Telegram 推送规则保持不变；双频道与无 R8 测试工作流的版本计算不受此锚点影响。
- 日常安装验证使用 `.github/workflows/android-test.yml` 的“安卓测试构建（无 R8）”。它只构建当前触发分支，临时关闭 release build type 的代码压缩混淆和资源压缩，并跳过 release lint，因此不会执行 R8；测试包仍使用正式证书签名，并使用 GitHub Actions 构建编号作为 `versionCode`。`main` 产物是 `Hchat-test-signed.apk`，`alt-entry` 产物是 `Hchat-alt-entry-test-signed.apk`。测试工作流只上传 Actions Artifact，不创建 tag、GitHub Release 或 Telegram 消息；正式发布仍必须使用保留 R8 的单频道或双频道构建工作流。
- GitHub Actions 工作流通过 `gradle/actions/setup-gradle` 复用 Gradle 用户目录与 build cache；手动触发的 `main` 和 `alt-entry` 均允许写入缓存，且因仓库现有 wrapper JAR 不在 action 已知哈希清单中而关闭该 action 的重复 wrapper 校验，并以 `--build-cache --parallel` 执行构建；双频道工作流在同一 runner 中并行执行主线和 `alt-entry` 的 assemble，完成后再统一发布。首次运行需要填充缓存，后续运行通常收益更明显。
- Release notes 只列出上一个发行版之后的提交标题列表，不显示模块版本、版本 Code、构建提交、构建编号或提交哈希；模块关于页和脚本变量 `moduleVer` 只显示 `versionName`，不主动拼接 `versionCode`。
- 首次打开模块设置页必须弹出使用协议与免责声明，确认按钮显示 30 秒倒计时，倒计时结束且用户输入“我同意”后才写入同意状态；取消只关闭配置页，不写入同意状态，下次打开仍需弹出协议。未同意前普通功能不安装，只保留设置入口，避免用户绕过协议直接启用模块。协议状态必须同步提交并立即回读；如果用户在微信进程存活期间从外部删除 FastKV 配置文件，确认时要关闭旧映射、重建配置实例后再写入，不能只更新已脱离磁盘文件的内存实例。协议内容变更需要提升 `TermsGate.TERMS_VERSION`，让旧用户重新确认。
- 设置页关于区域除版本、宿主、作者外，还应在其后显示“配置”分组，提供模块配置导入/导出；再显示“群组”分组，包含可点击跳转的 Telegram Channel `Hchat_ci` 和 Telegram Group `Hchat_Group`；并显示“致谢”分组，只列出实际依赖或明确参考的项目：KavaRef、DexKit、FastKV、WeChat Pad、LSPosed、Miuix、BeanShell-Android、Silk Codec。
- Release notes 在工作流准备构建配置阶段只生成一次，必须先 fetch 全部 `v*` tag，再按当前构建分支 `HEAD` 可达的最近版本 tag 计算 `tag..HEAD`，写入 `HCAT_CHANGE_LOG`。版本号递增可以继续使用全仓库最高版本 tag，但更新说明不能用不可达的其它频道 tag 做范围，否则单频道/双频道交替构建时会把历史提交全列出来。Release 发布和 Telegram 推送共用同一份内容，避免 Release 创建后重新查询 tag 导致更新说明为空。
- GitHub Actions 构建成功后，用 `scripts/download_release_apk.sh` 将当前 Latest Release 的 APK 下载到本地 `dist/` 目录。主线产物文件名是 `Hchat-release-signed.apk`，`alt-entry` 产物文件名是 `Hchat-alt-entry-release-signed.apk`，避免两个频道下载时互相覆盖。
- 仓库配置 `TG_BOT_TOKEN`、`TG_CHAT_ID` 两个 Actions Secret 后，工作流会在发布成功后向该 Telegram 私聊推送一组文件消息：第一个 APK 的 caption 仅展示版本号和更新说明，组内同时附带主线包与 `alt-entry` 包；不发送失败通知。由于 Telegram `caption` 最长 `1024` 字符，更新说明超长时会自动截断。
- 不要把 keystore、`local.properties`、构建缓存、APK 产物或临时文件提交到仓库。
- 启动期不要并发创建或查询共享 `DexKitBridge`，也不要使用 `DexKitBridge.create(classLoader, true)` 初始化微信 ClassLoader；当前通过 `DexInstallScheduler.runDexKitTask(...)` 统一串行执行 bridge 创建、DexKit 查询及关联 Hook 安装，解析出的 Method/Class 再用运行时 ClassLoader 实例化，避免 DexKit native 与 Hook JNI 并发进入 ART 状态切换。
- 普通功能不要为了等 DexKit 全量解析而阻塞微信启动。`DexInstallScheduler` 把 DexKit 可用拆成三段：`Stage.EARLY` 表示共享 `DexKitBridge` 已创建后即可排队，只给必须抢在微信 UI 构建前安装且确认适合调度器的 Hook 使用；`Stage.BRIDGE` 表示共享 `DexKitBridge` 与运行时 ClassLoader 已可用，单功能 DexKit 定位可以立即排队重建缓存；`Stage.WARMUP` 表示 `DexFinder.resolveAll()` 公共预热已完成，并会发布 sticky `Events.DexReady`。依赖自身字符串/方法定位的普通功能默认用 `BRIDGE`，依赖公共 `DexFinder` 全量字段或 `WeChatApis` 观察器完整可用的功能必须显式用 `WARMUP`。
- 依赖 DexKit 的普通功能统一通过 `h.Hchat.hooks.core.DexInstallScheduler` 安装：功能层安装函数返回 `Boolean`，`true` 代表核心 hook 已安装成功，`false` 代表缓存缺失、DexKit 暂未命中或 Hook 安装失败并交给调度器有限重试。微信设置页入口是时序例外：不等待普通调度队列或 DexReady，而是在 `Hchat-Init` 功能安装阶段通过同一 DexKit 串行门直接安装，既抢在设置页 UI 构建前生效，也不再创建独立线程并发查询共享 bridge。除这类明确例外外，功能安装期只登记任务，所有功能登记完成后由调度器按阶段和优先级单后台队列执行；不要在功能内再写 `installWithRetry`、`Thread.sleep` 重试或裸线程抢跑 DexKit。
- 使用 `DexMethodCache` 的功能不能在 hooker 构造时保存固定 `runtimeKey`；每次读取或写入定位缓存前都要实时调用 `DexMethodCache.runtimeKey(context, classLoader)`，让微信升级、降级、热更新和 ClassLoader 指纹变化能立即清空旧 descriptor 并重建当前版本缓存。

## 多版本适配状态

当前分层、缓存完整性约定、证据边界与逐版本验证记录见 [微信多版本适配与验证记录](WECHAT_COMPATIBILITY.md)。DexMethodCache 列表缓存现在要求整组非空描述符全部可解析；同进程失败移除该列表，跨进程失败保留主进程记录。此检查不替代业务签名与多入口安装完整性验证。

## 本机工具说明

当前项目常用工具位于 Termux 环境，默认工作区是：

```sh
# main
/data/data/com.termux/files/home/Hchat

# alt-entry
/data/data/com.termux/files/home/Hchat-alt-entry
```

常用命令路径（Termux 环境）：

- `gh`: `/data/data/com.termux/files/usr/bin/gh`，GitHub CLI，当前版本 `2.94.0`。
- `rg`: `/data/data/com.termux/files/usr/bin/rg`，ripgrep，当前版本 `15.1.0`。
- `git`: `/data/data/com.termux/files/usr/bin/git`，当前版本 `2.54.0`。
- `java`: `/data/data/com.termux/files/usr/bin/java`，OpenJDK `21.0.11`。
- `aapt2`: `/data/data/com.termux/files/usr/bin/aapt2`，Termux 可执行的 Android 资源工具。
- `sh`: `/data/data/com.termux/files/usr/bin/sh`。
- `find`: `/data/data/com.termux/files/usr/bin/find`。
- `sed`: `/data/data/com.termux/files/usr/bin/sed`。
- `awk`: `/data/data/com.termux/files/usr/bin/awk`。
- `termux-open`: `/data/data/com.termux/files/usr/bin/termux-open`。

常用命令路径（proot Ubuntu 环境）：

Codex 有时运行在 proot Ubuntu 环境中，`/data/data/com.termux/` 路径不存在。用 `which gh` 快速判断当前环境。以下为 proot 环境路径：

- `gh`: `/usr/bin/gh`，GitHub CLI `2.95.0`，已登录 `ljh520134`。
- `git`: `/usr/bin/git`，`2.43.0`。
- `rg`: `/usr/bin/rg`，ripgrep `14.1.0`。
- `java`: `/usr/bin/java`。
- `aapt2`: `/usr/local/bin/aapt2`。
- `curl`: `/usr/bin/curl`。
- `python3`: `/usr/bin/python3`。
- `node`: `/usr/bin/node`。
- `sh`: `/usr/bin/sh`。
- `find`: `/usr/bin/find`。
- `sed`: `/usr/bin/sed`。
- `awk`: `/usr/bin/awk`。

proot 环境注意事项：

- `gh` 已通过 PAT 认证，可直接使用 `gh workflow run`、`gh run list`、`gh release` 等命令。
- `git push` 在 proot 环境下可能因文件系统兼容问题卡在 `.git/objects` 写入。备选方案：用 GitHub REST API（`curl` + token）直接创建 blob/tree/commit 并更新 ref。

搜索与查看：

```sh
rg "关键字" app/src/main/java
rg --files
find . -maxdepth 3 -type f
sed -n '1,160p' path/to/file
```

Git 操作：

```sh
git status --short
git diff --check
git add path/to/file
git commit -m "中文提交信息"
git push
```

GitHub Actions：

```sh
gh workflow list --repo ljh520134/Hchat
gh workflow run 295394193 --repo ljh520134/Hchat --ref main
gh run list --repo ljh520134/Hchat --workflow 295394193 --limit 1
gh run view <run_id> --repo ljh520134/Hchat --json status,conclusion,url,headSha
gh run watch <run_id> --repo ljh520134/Hchat --exit-status
```

下载 APK：

```sh
sh scripts/download_release_apk.sh
sh scripts/download_release_apk.sh <tag>
sh scripts/download_release_apk.sh <tag> alt-entry
sh scripts/download_release_apk.sh all
sh scripts/download_release_apk.sh <tag> all
```

下载后的目标路径按频道区分：

```text
dist/Hchat-release-signed.apk
dist/Hchat-alt-entry-release-signed.apk
```

下载脚本默认按当前 Git 分支选择文件名，也可以用第二个参数显式指定 `main` 或 `alt-entry`；传 `all` 时会一次下载同一发行版里的主线和 `alt-entry` 两个 APK。不传 tag 时会查找最新 `v*` Release，并在同一个发行版资产里下载对应 APK。如果使用 `gh run download`，GitHub CLI 会按 artifact 名额外创建子目录；整理产物时也必须保留对应频道文件名，不要让最终 APK 留在 `dist/Hchat-release/` 这类子目录里。

本地编译与构建：

- 默认不要在本地执行 Gradle。
- 明确允许“本地编译”或“编译检查”时，只跑 compile 任务，不生成 APK。
- 明确允许“本地构建”时，才跑 release 构建并生成 APK；本地构建默认跳过 AAR metadata 检测，并使用低内存参数，避免 Termux 环境 R8 被系统杀掉。
- `./gradlew` 在当前环境可能没有执行权限，调用时使用 `sh ./gradlew ...`。
- 不要让 Gradle 临时下载发行版，除非用户明确允许联网下载依赖或构建环境。
- 不要为了本地构建向 `app/build.gradle.kts` 提交 `optimizeReleaseResources` 兜底、SDK 覆盖或其它只服务本机的 Gradle 配置；需要时只在命令行临时处理。

编译检查示例：

```sh
cd /data/data/com.termux/files/home/Hchat
sh ./gradlew :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac

cd /data/data/com.termux/files/home/Hchat-alt-entry
sh ./gradlew :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac
```

本地 release 构建命令仅在用户明确允许“本地构建”时使用：

```sh
cd /data/data/com.termux/files/home/Hchat
git checkout main
git pull --ff-only origin main
sh ./gradlew :app:assembleRelease --no-daemon --no-watch-fs -x lintVitalAnalyzeRelease

cd /data/data/com.termux/files/home/Hchat-alt-entry
git checkout alt-entry
git pull --ff-only origin alt-entry
sh ./gradlew :app:assembleRelease --no-daemon --no-watch-fs -x lintVitalAnalyzeRelease
```

## 依赖与参考项目地址

项目相关依赖和参考项目：

- DexKit: `https://github.com/LuckyPray/DexKit`
- FastKV: `https://github.com/BillyWei01/FastKV`
- KavaRef: `https://github.com/HighCapable/KavaRef`
- Miuix: `https://github.com/compose-miuix-ui/miuix`
- KernelSU: `https://github.com/tiann/KernelSU`
- BeanShell-Android: `https://github.com/CopyLibs/BeanShell-Android`
- Silk-Codec-Android: `https://github.com/YunJavaPro/Silk-Codec-Android`

用途说明：

- DexKit 用于定位微信混淆类、方法和字符串锚点。
- FastKV 用于模块配置存储，统一通过 `HchatStorage.preferences(...)` 使用，不要在功能代码里直接实例化 FastKV。
- KavaRef 是项目反射封装基础，新增反射统一走 `h.Hchat.utils.KavaReflector`。
- Miuix 是设置界面使用的 Compose UI 体系。
- KernelSU 是底部导航、液态玻璃和过渡效果的重要参考项目。
- BeanShell-Android 用于用户自定义脚本插件能力，插件目录采用 WA 风格：`Hchat/脚本插件/<插件名>/main.java`。
- Silk-Codec-Android 代码按 1.0.6 本地接管，提供模块和脚本共用的 Silk/MP3/AAC/M4A 音频转换能力。

当前 Gradle 接入方式：

- `settings.gradle.kts` 需要保留 `mavenCentral()`、`maven { url = uri("https://api.xposed.info/") }`，并额外加入 `maven { url = uri("https://jitpack.io") }`
- `me.yun.silk.SilkCodec`、`me.yun.silk.AacCodec` 放在 `app/src/main/java/me/yun/silk/`
- Native 库只维护 ARM 64/32 两套：`arm64-v8a` 和 `armeabi-v7a`。`libsilk_codec.so`、`libhchat_crash.so` 必须同时放在 `app/src/main/jniLibs/arm64-v8a/` 与 `app/src/main/jniLibs/armeabi-v7a/`。
- `libsilk_codec.so` 使用上游 1.0.6 的 16KB ELF LOAD 段对齐产物，两套 ABI 的段对齐均为 `0x4000`；16KB 兼容由链接器页大小参数保证，不再对普通音频缓冲区强制执行 16KB 堆内存对齐。
- `libhchat_crash.so` 由 `scripts/build_crash_native.sh` 使用 Termux Clang/LLD 生成；脚本会同时生成两套 ABI、去除调试符号，并校验 ELF 架构、`libc.so` 依赖和 JNI 导出。日常 Gradle/GitHub 构建直接打包已提交的 `.so`，不要求额外安装 NDK。
- 当前本地接管版本：`1.0.6`（上游提交 `c09fc1f534c7435c7b4c11a84a4632c6d177e1b9`）

## 微信 APK 横向验证

不确定微信内部逻辑时，优先对多个版本 APK 做横向对比，不要只看单一版本，也不要写死某个版本的混淆类名、字段名或构造签名。凡是涉及微信内部结构、布局、DexKit、反射、数据库、Intent、网络场景或版本兼容的改动，默认尽量覆盖 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`、`8.0.77`；除非对应 APK 缺失或用户明确要求单版本，并且必须在结论里说明未覆盖原因。

APK 的存放目录和文件名由开发者自行决定，不在仓库中预设个人设备路径。可在本机按版本设置绝对路径变量：

```sh
export WECHAT_APK_8049="/你的路径/微信8049.apk"
export WECHAT_APK_8058="/你的路径/微信8058.apk"
export WECHAT_APK_8066="/你的路径/微信8066.apk"
export WECHAT_APK_8068="/你的路径/微信8068.apk"
export WECHAT_APK_8072="/你的路径/微信8072.apk"
export WECHAT_APK_8074="/你的路径/微信8074.apk"
export WECHAT_APK_8076="/你的路径/微信8076.apk"
export WECHAT_APK_8077="/你的路径/微信8077.apk"
```

常用覆盖点必须全部纳入默认横向验证，不要因为 66/68 是中间版本就跳过：

- `8.0.49`: 旧稳定基线，很多入口和字段最容易对照。
- `8.0.58`: 中间版本，常用于确认 49 到 72 之间的签名变化。
- `8.0.66` / `8.0.68`: 支付、媒体、AppMsg 等链路出现过参数差异，适合补兼容。
- `8.0.72` / `8.0.74` / `8.0.76` / `8.0.77`: 新版基线，优先确认当前高版本行为；`8.0.77` 的 versionCode 以实际 APK 为准。

微信热更新相关逆向时，优先先看这些共同入口，不要先猜微信自有混淆类：

- `com.tencent.tinker.loader.app.TinkerApplication#getTinkerFlags`
- `com.tencent.tinker.loader.TinkerLoader#tryLoadPatchFilesInternal(...)`
- `com.tencent.mm.plugin.hp.util.TinkerSyncResponse` 对应的“start to run patch”补丁应用方法
- `com.tencent.mm.hotpatch.LegacyTinkerCore$PatchService`

`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74` 都能对到上面这些共性点；新增“屏蔽热更新”这类功能时优先拦这些入口，不要写死微信业务混淆类名。当前项目里的“屏蔽热更新”除了禁用 Tinker 加载外，也会短路热更新响应处理、`TinkerSyncResponse` 下发响应消费入口和补丁文件应用入口。Tinker 加载拦截必须在 `TinkerApplication.onBaseContextAttached` 原方法执行前安装，不能等普通功能初始化；响应/补丁应用拦截则在 attach 后用最终 ClassLoader 安装。拦截方法时要按目标返回类型给兼容值，避免 `Integer`/`Boolean` 拆箱空值崩溃。已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74` 的热更新网络回调 `onGYNetEnd(...)` 会构造 `TinkerSyncResponse` 并调用响应消费方法，消费方法可用 `before commandNewApkMd5HardCode, response.newApkMd5 = ` / `, response.fileMd5 = ` 锚点定位。

个人状态词长度限制位于 `com.tencent.mm.plugin.textstatus.ui.TextStatusDoWhatActivityV2`。已横向确认 `8.0.49`、`8.0.58`、`8.0.74` 都在该 Activity 构造方法里把限制字段初始化为 `10`，但字段名分别会随版本变化，例如 `Y0` / `l1` / `p1`；解除限制时不要写死字段名，应在构造后只针对该 Activity 中当前值为 `10` 的实例 `int` 字段改成 Hchat 限制值。

模拟相机扫码功能位于实用分类，核心只改写 QBarStringHandler 入参里的来源/场景，把相册扫码 `1/34` 或长按识别 `4/37` 改成相机扫码 `0/4`，不重新识别图片、不改二维码内容，也不改相机真实扫码。最终业务入口都带 `MicroMsg.QBarStringHandler` + `key_offline_scan_show_tips` 字符串锚点，定位必须使用 DexKit `usingEqStrings` 精确字符串匹配：`8.0.49` 是 `l73.l.f`，`8.0.58` 是 `hj3.k.f`，`8.0.66` 是 `gq3.p.g`，`8.0.68` 是 `ht3.p.g`，这四版为 15 参数签名，`source/getA8KeyScene` 位于参数 `2/3`；已确认的 `8.0.70 cw3.p.g`、`8.0.72 ry3.p.g`、`8.0.74 e04.p.g` 是 16 参数签名，新增 `scanUIScene` 后 `source/getA8KeyScene` 位于参数 `3/4`。`8.0.72`、`8.0.74` 的 `wedropf2fhb://` 红包码分支会硬判断 `getA8KeyScene == 4`，否则显示“仅支持扫一扫摄像头识别”类错误。实现只 hook QBarStringHandler，不再 hook BaseScanUI、扫码结果 Bundle 构造/派发方法、相册选择或二维码解码链路；命中方法必须用 `DexMethodCache` 缓存，缓存 key 要带功能 schema，并且每次定位前实时读取 runtime key，不能在 hooker 构造时固定旧版本 key，用于废弃旧定位策略留下的错误缓存。缓存缺失、DexKit 首次空结果或 Hook 安装失败时，必须在当前微信进程内做有限后台重试，不能依赖用户多次强停微信触发重建。

检查本机变量是否指向有效文件：

```sh
for apk in "$WECHAT_APK_8049" "$WECHAT_APK_8058" "$WECHAT_APK_8066" \
  "$WECHAT_APK_8068" "$WECHAT_APK_8072" "$WECHAT_APK_8074" \
  "$WECHAT_APK_8076" "$WECHAT_APK_8077"; do
  test -n "$apk" && test -f "$apk" && ls -lh "$apk"
done
```

## 脚本插件规则

面向插件作者的简明接口文档见 `docs/SCRIPT_PLUGIN_API.md`。该文档用于给用户和插件作者阅读，避免写入项目内部协作规则或逆向细节。

## 日志规则

LSPosed 只有 `XposedBridge.log(Throwable)` 会按错误日志显示。模块自身的失败、异常、未找到、未命中、不可用等错误日志必须统一使用 `h.Hchat.utils.HLog.e(...)`，不要直接 `XposedBridge.log(String)`。普通状态、抓包内容、用户脚本主动 `log()` 仍可使用普通字符串日志，避免把调试信息全部标红。

`捕获异常日志` 位于 `娱乐 > 调试`，默认关闭。开启后，模块在用户同意协议后于微信主进程启动期安装异常记录器；运行中开启也会立即补装。Java 捕获器在 `Application.onCreate` 原方法执行前安装，未捕获异常会写入完整线程堆栈；Native 捕获器覆盖 `SIGSEGV`、`SIGABRT`、`SIGBUS`、`SIGILL`、`SIGFPE`、`SIGSYS`，只用无堆分配的信号现场代码保存信号、可读 `si_code`、发送方、线程名、故障地址、关键寄存器、`pc/lr` 所属 so 与文件偏移、可执行内存映射，并在 ARM64 上保存最多 32 层经过可读栈映射校验的 frame-pointer 回溯；`SIGABRT` 不再把无意义的 `siginfo` 联合字段显示为故障地址。记录完成后继续转交微信原处理器，不能阻断系统 tombstone。Android 11 及以上同时读取 `ApplicationExitInfo`：Native 异常补充退出 PID/UID、内存和系统 tombstone，系统明确记录为 `REASON_ANR` 的异常退出则单独生成 ANR 报告并附带系统 ANR trace；ANR 不使用固定时长的主线程看门狗推断，避免把短暂卡顿、调试暂停或系统负载误报为 ANR。记录保存在微信私有目录 `Hchat/crash/`，下次进入稳定 Activity 后用 Miuix 弹窗展示；点击日志区域或“复制日志”会复制完整内容，用户关闭弹窗后归档为 `last_crash.log` 并避免重复弹出，Activity 切换或写盘失败时保留待展示记录。关闭开关会立即取消待展示弹窗并清理尚未归档的 Java、Native、ANR 记录，同时推进退出记录基线，后续重新开启不会补弹关闭期间的旧异常；`last_crash.log` 继续保留。`OutOfMemoryError` 的 `Failed to allocate` 类型不记录、不弹窗。

脚本插件根目录由 `ScriptPluginRuntime.scriptDir(context)` 管理，通常位于微信媒体目录下的 `Hchat/脚本插件`。每个插件一个独立文件夹，运行时只加载该文件夹内的 `main.java`：

```text
Hchat/脚本插件/
└─ <插件名>/
   ├─ info.prop
   └─ main.java
```

`info.prop` 可选，支持 `name`、`author`、`version`、`updateTime`、`process`。`process` 默认 `main`，可设为 `appbrand`、`all` 或逗号组合的 `main,appbrand`；只有显式包含 `appbrand` 的插件才会进入小程序进程。插件列表主标题显示 `name(version)`；`name` 或 `version` 未配置时显示 `未知`，副标题第一行固定显示插件文件夹名。

插件一级页直接展示总开关和本地插件列表，不再进入脚本插件二级菜单。插件总开关标题右侧、每个插件标题右侧都显示 `单击` 小标记，明确提示左侧标题区域可点。点击插件名称区域打开该插件目录下 `README.md` 的说明弹窗；总开关左侧说明区域点击后弹出脚本根目录路径，并提供复制。说明支持简易 Markdown：标题、列表、引用、代码块、分割线、粗体、行内代码和 `[文字](链接)` 链接，内容过长时必须可继续向下滚动查看。插件定义 `openSettings()` 时，插件行右侧显示设置图标；点击图标只调用已开启插件的 `openSettings()`，未开启时提示先开启插件。说明弹窗使用当前依赖里的 `top.yukonga.miuix.kmp.extra.WindowDialog`，不要手搓 `androidx.compose.ui.window.Dialog` 卡片。说明弹窗只能用于插件 README 这类轻量说明，不要把普通功能设置做成弹窗。因为模块设置页是嵌入微信原 Activity 的 ComposeView，宿主 owner 必须同时提供 lifecycle、savedState、viewModelStore 和 `NavigationEventDispatcherOwner`，并通过 `LocalNavigationEventDispatcherOwner` / `ViewTreeNavigationEventDispatcherOwner` 传给 Miuix；否则 `WindowDialog` 内部返回处理会因找不到 NavigationEvent owner 闪退。插件热重载失败后，当前插件页里的开关状态也要立刻刷新为关闭，不能等切页后才同步。关于页宿主信息显示微信当前 `displayVersion()`，格式为纯版本号样式，例如 `8.0.49.2600(0x2800313D)`，不再显示 `cn.` 前缀，也不再固定写“微信”。

本地插件列表每行提供独立管理菜单，可置顶、重命名展示名称、导出或删除；重命名只更新 `info.prop` 的 `name`，不得修改插件目录名和稳定插件 ID。`本地插件管理` 页面提供搜索、已置顶/其他插件分区、分区内拖拽排序、当前搜索结果全选与反选、批量置顶/取消置顶、导出和删除。显示排序只影响设置界面，不改变脚本回调执行顺序；搜索结果不完整时禁用拖拽，避免覆盖全量顺序。导出使用包含 manifest 的 ZIP，可保留多插件顺序和置顶信息，并执行与导入一致的条目数、单文件和总大小限制；导入必须通过系统文件选择器，先检查插件和同 ID 冲突，再逐项选择跳过或覆盖，所有成功导入或覆盖的插件统一保持关闭。ZIP 解压必须防止路径穿越、符号链接和重复路径；有 manifest 时只按其声明的插件根目录识别入口，插件子目录里的示例 `main.java` 不得被误判成另一插件。删除和覆盖必须先停止插件，与插件 Agent 共用跨进程文件事务锁，并通过持久化事务日志在微信进程中断后恢复未提交的原目录、启用状态和显示排序；导入日志还要记录已安装目录指纹，恢复前发现目录已被外部改动时必须保留当前目录和事务备份，不能直接覆盖或误删。导入检查产生的暂存目录在取消、页面退出和应用导入后立即清理；进程异常退出留下的暂存目录在微信主进程下次启动时清理。

脚本运行时会注入 WA 风格常用变量：`pluginDir`、`cacheDir`、`pluginName`、`pluginId`、`pluginAuthor`、`pluginVersion`、`pluginUpdateTime`、`hostContext`、`hostVerName`、`hostVerCode`、`hostVerClient`、`moduleVer`，以及进程变量 `processName`、`pluginProcess`、`isMainProcess`、`isAppBrandProcess`。`cacheDir` 指向全局 `Hchat/Cache`，不是单个插件目录下的 `cache`，并且运行时只注入路径，不主动创建目录。加载顺序是先执行 `main.java` 顶层代码，再尝试调用 `onLoad()`；插件登记后会再次扫描解释器回调，保证入口顶层异步加载的脚本不会在登记时序窗口内丢失回调。插件关闭时会尝试调用 `onUnload()`，没有定义时直接卸载运行时实例。插件可定义 `openSettings()` 作为设置入口；此回调不会自动执行，只会在用户点击插件行右侧设置图标时执行。已启用插件的 `main.java` 被修改后，运行时会自动卸载旧解释器并重载最新脚本；如果重载失败，会自动关闭该插件并弹 `加载[插件名]失败，已自动关闭`。脚本根目录增删插件文件夹、修改 `info.prop` 或 `README.md` 时，插件页列表会自动刷新，不需要手动切页面。

小程序进程只在 `Application.onCreate` 后创建不带 DexKit 的轻量脚本 Bridge，按插件总开关、单插件开关和 `process` 范围加载插件，不启动完整功能框架、联系人数据库等待、主进程回调或文件监听。其 `dexKit`、`dexKitBridge`、`dexFinder`、`dexBridgeHolder` 为 `null`；插件应使用当前进程 `classLoader`、稳定类名、反射和 Hook API。每个 `:appbrand*` 进程持有彼此隔离的解释器、Hook 和 Dex/SO 代码缓存；共享 `config.prop` 的读写使用跨进程文件锁。单个子进程加载失败只记录日志，不修改共享插件开关。显式非法 `process` 会拒绝加载；配置或代码变更从新启动的小程序进程开始生效。

生成小程序进程 Hook 前必须先通过 Agent 的 APK 逆向工具或已有多版本证据确认稳定完整类名、方法名、参数数量和签名，再在 `onLoad()` 检查 `isAppBrandProcess` 并使用 `findClass`、反射及 `hookBefore/hookAfter/hookReplace`。目标是混淆类且必须运行时定位时，插件应使用 `process=all`：主进程实例通过共享 DexKit 定位并用配置缓存 descriptor，小程序实例只读取缓存；缓存缺失时跳过并提示重新打开小程序，禁止在小程序进程创建 DexKit。

发送按钮会先分发给模块内共享处理器，模块未拦截时再触发脚本的 `onClickSendBtn(String text)`，`text` 是当前输入框的原始文本；任一已启用插件返回 `true` 时会拦截微信原始发送并清空输入框，返回 `false`、`null` 或未定义该回调时放行。长按发送按钮触发 WA 同款 `onLongClickSendBtn(String text)`；返回 `true` 时消费长按、阻止随后单击发送并清空输入框，返回 `false`、`null`、忙碌或异常时继续微信原生长按流程，原流程未消费时松手仍可能触发普通单击回调。模块和脚本均未拦截单击时，已注册的输入装饰器才会修改 ChatFooter 文本并继续微信原生发送；装饰器应优先原位修改 `Editable`，保留艾特等 span，模块处理器和脚本回调始终接收装饰前文本。发送按钮 Hook 必须走 ChatFooter 输入框读取、修改与清空；模块功能和脚本插件共用一个 Hook 与一个 `DexInstallScheduler` 任务，不能分别重复 Hook。长按入口统一 Hook Android `View.dispatchTouchEvent(MotionEvent)`，仅在当前 View 的点击监听器命中 DexKit 动态定位的 ChatFooter 发送监听类时按系统长按时间和 `touchSlop` 建立状态；移动超限、多指、取消或窗口失焦时放弃，返回 `true` 时先向原 View 发送受重入保护的 `ACTION_CANCEL`，再消费剩余事件以阻止松手普通发送。不得替换微信的 `OnTouchListener` 或 `OnLongClickListener`。已横向确认 8.0.49/58/66/68/72/74/76 的发送键字段均为 `android.widget.Button`，发送监听类分别为 `l0/o0/x0/c1/n1/p1/p1`，都位于 `com.tencent.mm.pluginsdk.ui.chat`、实现 `onClick(View)` 并直接持有 `ChatFooter`；运行时仍按字符串与结构定位，不硬编码这些混淆名。所有 BeanShell 入口共用每个解释器自己的可重入锁；为兼容已有 WA 脚本，声明为 `void` 的脚本方法如果误用带值 `return`，解释器会结束该方法并丢弃返回值，只有声明为 `boolean` 的发送按钮回调具有布尔拦截语义。发送按钮回调运行在微信主线程，遇到正被消息、成员变动或其它回调占用的插件时必须跳过该插件并放行原始事件，不能等待解释器锁导致聊天界面卡顿。跳过忙碌插件以及单次 `onClickSendBtn` 或 `onLongClickSendBtn` 超过 `50ms` 都按插件名和回调类型写入限频日志，便于定位脚本自身耗时。触发发送按钮回调前会用 ChatFooter 所在 Activity 回填 `getTopActivity()`，便于聊天输入指令直接打开插件 UI。回调里不要做耗时操作；需要网络或文件操作时脚本自己异步处理，否则仍会造成当前交互延迟。

消息事件会触发 `onHandleMsg(Object msgInfoBean)`。脚本普通消息和媒体自动下载回调统一订阅微信 `message` 及消息分表的数据库变化，不使用 PB/AddMsg 实时层；监听范围要覆盖自己发送、别人发送、手动发送、模块 API 发送以及常见非文本类型。数据库层回调可能比 PB 实时层略晚，但字段更稳定，`getMsgId()` 通常可直接拿到本地消息 ID，媒体回调也能直接使用完整的 `imgPath`、XML 和消息类型，不需要另建阻塞补查队列。去重会先按本地 `msgId` 做稳定 ID 长窗口去重，防止同一条 DB 消息 insert/update 分两次触发；`msgSvrId` 和内容去重仍使用 1 秒窗口；内容窗口会按归一化后的 `talker/sender/type/isSend/content`、`talker/sender/type/content` 和自己发送消息的 `talker/type/content` 去重，用来吃掉 DB insert/update 或群聊 `wxid:\n内容` 前缀不一致导致的重复回调。`onMemberChange(...)`、`onNewFriend(...)` 等其它脚本回调来源不随普通消息监听改变。常用方法包括 `getXml()`、`getMsgId()`、`getSender()`、`getSendTalker()`、`getTalker()`、`getContent()`、`getText()`、`getMsgType()`、`getType()`、`getCreateTime()`、`getCreateTimeSeconds()`、`getMsgSvrId()`、`getMsgSource()`、`getAtUserList()`、`getSelfWxId()`、`getSource()`、`getKind()`、`getNativeUrl()`、`getMessage()`、`getStoredMessage()`、`getImageMsg()`、`getQuoteMsg()`、`getFileMsg()`、`getTransferMsg()`、`getPatMsg()`、`isSend()`、`isSelf()`、`isPrivateChat()`、`isOpenIM()`、`isGroupChat()`、`isChatroom()`、`isImChatroom()`、`isOfficialAccount()`、`isText()`、`isImage()`、`isVoice()`、`isVideo()`、`isAppMsg()`、`isApp()`、`isEmoji()`、`isLocation()`、`isSystem()`、`isRedPacket()`、`isRedBag()`、`isTransfer()`、`isQuote()`、`isFile()`、`isLink()`、`isMusic()`、`isNote()`、`isShareCard()`、`isVoip()`、`isVoipVoice()`、`isVoipVideo()`、`isVideoNumberVideo()`、`isPat()`、`isRecalled()`、`isAnnounceAll()`、`isNotifyAll()`、`isAtMe()`。`getTopActivity()` 返回当前微信顶部 `Activity`，发送按钮回调中会优先用 ChatFooter 所在 Activity 回填；没有可用 Activity 时返回 `null`。

Protobuf 抓包复用 `ProtobufPacketHook` 的唯一网络入口，通过 `onProtobufPacket(Object packet)` 向脚本暴露稳定的方向、URI、CGI ID、原始字节、JSON 和捕获时间，不把微信混淆对象传给脚本。插件监听独立于抓包设置页的日志总开关、方向开关和 CGI 屏蔽列表；Hook 只复制数据，BeanShell 回调统一进入有界单线程队列，不能阻塞微信网络线程。没有已启用插件声明该回调时不注册运行时监听，避免无意义地解析所有网络包。主动发包通过 `sendProtobufPacket(...)` 接受 JSON 文本或 `JSONObject`，复用 `ProtobufPacketRuntime.send(...)` 已有的签名、原生 Scene、通用发包和同类请求重放链路，不新增第二套网络 Hook。

成员变动事件会触发 WA 同款 `onMemberChange(String type, String groupWxid, String userWxid, String userName)`。当前实现是双轨来源：`join` 会优先吃群系统消息/XML 里的被邀请人信息补首进群昵称，同时保留 `WeChatApis.chatroomChanges()` 的 `chatroom.memberlist` 差集兜底；`left` 仍然只依赖 `memberlist` 差集，因为普通成员退群时微信通常没有稳定系统提示。首次看到某个群只建立成员快照，不把已有成员全部当成 `join`。`type` 固定使用 `join` / `left`；`userName` 优先系统消息里的昵称、群昵称、备注、昵称，取不到时回退 `userWxid`。内部会按 `type/group/user` 做短时间去重，避免系统消息和差集同时命中时重复派发。不要新增 Hchat 自定义参数，保持 WA 插件可直接迁移。

好友申请事件会触发 WA 同款 `onNewFriend(String wxid, String ticket, int scene)`。当前实现优先监听微信 `fmessage_msginfo` 好友申请表插入，处理 `isSend=0` 的新申请，并从 `msgContent` 等字段解析 `encryptusername/fromusername`、`ticket/antispamticket` 和 `scene`；消息观察入口仍作为补充来源，但只接受归一化后的好友申请消息类型 `37`，不能仅凭 XML 中的 `antispamticket` 等字段判断，避免联系人名片类型 `42/66` 误触发。回调 `wxid` 优先使用 `fromusername/username` 里的真实联系人 ID，方便同意后直接发送欢迎语；`encryptusername` 这类 `v3_...@stranger` 临时验证用户名会短时缓存，插件调用 `verifyUser(wxid, ticket, scene)` 时自动映射回微信同意请求需要的用户名。申请人 ID 允许 `wxid_xxx`、自定义微信号和兜底临时验证用户名，内部按验证用户名/ticket/scene 做 3 秒去重。插件里要自动通过时直接调用 `verifyUser(wxid, ticket, scene)`；不要额外加 Hchat 自定义参数。

插件列表里的单插件开关必须实时生效：新插件默认关闭，打开时立刻执行对应目录的 `main.java`，关闭时立刻卸载对应插件。加载失败时自动关闭该插件，Toast 文案固定为 `加载[插件名]失败，已自动关闭`，不要在 Toast 里显示完整异常。插件加载失败必须写入当前插件目录的 `log.txt`，内容保留异常类型和简短 message，不写完整堆栈。`log(...)` 必须写入当前插件目录的 `log.txt`，并同步输出到 LSPosed 日志；`toast(...)` 自动带上当前插件名前缀。

脚本运行时默认导入 `XposedBridge`、`XposedHelpers`、`XC_MethodHook`，同时注入 `XposedBridgeClass`、`XposedHelpersClass`、`XC_MethodHookClass`，脚本可以直接使用 LSPosed/Xposed 原生写法。为了贴近 WA 的脚本体验，还提供轻量封装：`findClass(className)`、`hookBefore(member, callback)`、`hookAfter(member, callback)`、`hookReplace(member, callback)`、`unhook(handle)`。通过这些封装注册的 hook 会按插件 ID 记录，插件关闭或加载失败时会自动清理。

脚本支持调用模块共享 DexKit，不要在脚本里自行初始化 DexKit。运行时会注入 `dexKit`、`dexKitBridge`、`dexFinder`、`dexBridgeHolder`，以及 `DexKitBridgeClass`、`DexFinderClass`、`DexBridgeHolderClass`。WA 风格简化函数包括 `findClassList(usingStrings)` 和 `findMemberList(usingStrings)`，参数兼容 WA 常见的 `{"keyword"}` 大括号数组、字符串、数组、`Object[]` 或 `List`；返回值分别是可直接使用的 `Class<?>` 列表和可直接配合 `hookBefore/hookAfter` 的 `Member` 列表。`findMemberList` 会同时收集方法命中和类命中的构造函数/方法，尽量保持旧 WA 插件不改写即可迁移。

脚本配置使用 WA 同款 `config.prop`，文件自动生成在当前插件目录下。可用读取方法：`getString(key, defValue)`、`getStringSet(key, defValue)`、`getBoolean(key, defValue)`、`getInt(key, defValue)`、`getFloat(key, defValue)`、`getLong(key, defValue)`；可用写入方法：`putString(key, value)`、`putStringSet(key, value)`、`putBoolean(key, value)`、`putInt(key, value)`、`putFloat(key, value)`、`putLong(key, value)`。配置只属于当前插件，不写入模块全局 SharedPreferences。

后续新增脚本插件 API 必须继续按 WA 同名同参数暴露。新增前先查 `/data/data/com.termux/files/home/.codex/skills/wa-plugin-dev/references/wa-api/` 和本机 WA 插件样例，不确定时先确认 WA 签名，不要自行发明更方便但不兼容的参数。`getLoginWxid()`、`getGroupMemberList()` 和 `getGroupMemberListInfo()` 在非主线程调用且微信账号/群资料库刚初始化时会短暂重试；群成员读取合并群聊 API、联系人表和 `roomdata` 已解析成员，避免 8.0.58 或部分机型插件加载过早时瞬时返回空列表并触发错误授权判断。主线程调用不等待，不能用兼容重试阻塞微信界面。

已暴露的消息发送全局函数按 WA 签名提供给脚本：`sendText`、`sendQuoteMsg`、`sendPat`、`sendShareCard`、`sendLocation`、`sendImage`、`sendVoice`、`sendVideo`、`sendEmoji`、`sendFile`、`sendMediaMsg`、`shareFile`、`shareMiniProgram`、`shareMusic`、`shareMusicVideo`、`shareText`、`shareVideo`、`shareWebpage`、`sendXmlMsg` 的无回调重载在脚本侧均为 `void`。`sendImage(String talker, String sendPath, String appId)` 会按 WAuxv 语义写入图片消息 `appinfo.appid`：8.0.49/8.0.58 低版本仍把 appInfo XML 放入图片发送 8 参数签名第 6 个参数；8.0.66+ 新版异步图片链路按微信外部 App 发图同款写入 `crossParams.appinfo.appid` 后提交异步图片任务；第 5 个 String 是 `imgsourceurl`，`ImgSourceInfo` 是图片来源 URL，不用于传 appId。`sendVoice(String talker, String sendPath)` 必须由模块接口按文件头识别真实音频类型，非 Silk 的常见音频会先转 Silk 再发送，并自动读取真实时长，不能只按扩展名判断；脚本只有确实要覆盖时长时才使用三参重载，三参单位按 WA 约定为秒。超过 60 秒的语音按真实文件发送，写入微信显示字段时最多 60 秒。`sendXmlMsg` 发送 AppMsg XML 时会透传 XML 自带的 `<msgsource>...</msgsource>` 到微信原生 AppMsgLogic 发送参数，供 WA 插件传递公告、全体通知等特殊来源标记；旧 WA 插件如果通过数据库 Hook 在 `message.lvbuffer` 写入 `<msgsource>...</msgsource>`，且当前消息 `ContentValues` 原本包含 `msgSource` 字段，模块会在入库前同步补写 `msgSource`；旧版消息表或插入参数没有该字段时不主动查表或新增字段，避免干扰微信原生发送。`sendText(String talker, String content, Consumer callback)` 保持 WA 参数顺序，当前底层发送 API 暂不能稳定返回服务端消息 ID，回调值只保证类型为 `Long` 或 `null`。`sendQuoteMsg` 除 WA 文档顺序 `sendQuoteMsg(String talker, long msgId, String content)` 外，也兼容部分旧 WA 插件常见的 `sendQuoteMsg(String talker, String content, long msgId)`；内部会先按本地 `msgId` 查源消息，找不到再按 `msgSvrId` 兜底。`sendPat(String talker, String pattedUser)` 会先调用微信原生 `PatMsgExtension.N6(scene,talker,user)` 校验，再从原生 `sendpat` 请求体字段 2 读取当前账号生成本地 Pair，并以原生点击同款 `scene=0` 通过微信 `sendpat` 网络场景发送，避免只插入本地拍一拍但对方不可见。`sendShareCard(String talker, String wxid)` 通过微信 `SendContactCardHelper` 生成原生名片 XML，普通联系人按消息类型 `42` 发送，OpenIM 名片按消息类型 `66` 发送。`sendLocation(String talker, String poiName, String label, String x, String y, String scale)` 和 `sendLocation(String talker, JSONObject jsonObj)` 使用微信原生位置 XML 格式，按消息类型 `48` 发送；`x` 为经度，`y` 为纬度，`scale` 为空时默认 `16`。

已暴露的消息和杂项全局函数按 WA 签名提供给脚本：`insertSystemMsg(String talker, String content, long createTime)`、`queryHistoryMsg(String talker, long startTime, int count)`、`getUnreadCount(String talker)`、`getAllUnreadCount()`、`clearUnread(String talker)`、`clearAllUnread()`、`delay(long millis, Runnable action)`、`notify(String title, String text)`、`reloadPlugin()`、`compileSnapshot(String path)`、`evalSnapshot(String path)`、`eval(String code)`、`loadJava(String path)`。`queryHistoryMsg` 返回 `List<MsgInfoBean>`；非零 `startTime` 按毫秒时间戳查询该时间之后的消息，`0L` 返回最近消息。消息表查询只选择各支持版本实际存在的稳定字段，`msgSource` 由消息 Bean 从正文或保留字段解析，不能把不存在的数据库列加入 SQL。Native 扩展另提供 `loadSo(String path)` 和 `loadSo(String path, ClassLoader loader)`。当前不向脚本暴露 WA 的转账领取/退回接口。

脚本杂项和音频薄封装仍然保留：`reloadPlugin()`、`compileSnapshot(String path)`、`evalSnapshot(String path)`、`evalSnapshot(InputStream inputStream)`、`evalSnapshot(byte[] data)`、`eval(String code)`、`loadJava(String path)`、`loadDex(String path)`、`loadSo(String path)`、`loadSo(String path, ClassLoader loader)`、`getDuration(String filePath)`。`reloadPlugin()` 异步重载当前插件；`compileSnapshot(String path)` 会把指定脚本编译成同目录下的 `.bshs` 加密快照文件并返回快照绝对路径；`evalSnapshot` 使用模块内置兼容 AES 密钥加载并执行 `.bshs` 快照，支持路径、输入流和字节数组三种来源，快照可在不同插件目录间加载；`compileSnapshot` / `evalSnapshot` 支持在脚本顶层和 `onLoad()` 中调用，不依赖插件完成加载登记；如果 `main.java` 只是入口包装，真正回调定义在 `eval(String code)`、`evalSnapshot()` 或 `loadJava()` 载入的文件里，加载后也必须刷新并识别这些回调；`loadJava` 支持绝对路径，相对路径按当前插件目录解析；`loadDex` 支持 dex/jar/apk，路径规则同 `loadJava`，会复制到微信私有 `code_cache` 并设为只读后用 `DexClassLoader` 加载并返回 `ClassLoader`；`loadSo` 校验 ELF、位数和 ARM ABI 后按内容哈希复制到 `code_cache/hchat_plugin_native/<pluginId>/` 并设为只读，通过 Android Native 加载入口绑定指定 `ClassLoader`，默认绑定微信宿主加载器。BeanShell 动态 JNI 类必须把 `NativeClass.class.getClassLoader()` 传给双参数重载；JNI 类来自 `loadDex` 时必须传其返回值；顶层 `native` 函数没有 JNI 类载体，运行时会明确拒绝调用；同一路径、同内容和同一加载器在进程内复用。替换 SO 后重新加载插件并传入新 JNI 类的加载器时，会通过独立只读副本热加载新版本；固定宿主加载器或旧加载器不能可靠热更新。插件关闭不会卸载 SO，旧版本驻留到微信进程结束；`getDuration` 返回毫秒，失败返回 `0`。

脚本音频转换由本地接管的 `Silk-Codec-Android` 1.0.5 提供。当前脚本层已暴露：

- WA 同款：`mp3ToSilk(String mp3Path, String silkPath)`、`mp3ToSilk(String mp3Path, String silkPath, int hz)`、`silkToMp3(String silkPath, String mp3Path)`、`silkToMp3(String silkPath, String mp3Path, int hz)`
- 基础查询：`getFileType()`、`getAudioInfo()`、`getDuration()`、`getDurationLimited()`、`getErrorMessage()`
- Silk/PCM：`wavToSilk()`、`flacToSilk()`、`oggToSilk()`、`pcmToSilk()`、`autoToSilk()`、`silkToPcm()`、`mp3ToPcm()`、`wavToPcm()`、`flacToPcm()`、`oggToPcm()`、`autoToPcm()`
- AAC/M4A/MP4：`decodeAacFile()`、`encodePcmToAac()`、`encodePcmToM4a()`、`mp4ToSilk()`、`silkToM4a()`、`mp4ToM4a()`、`mp4ToAac()`、`m4aToSilk()`、`aacToSilk()`、`m4aToAac()`、`m4aToM4a()`、`autoToAac()`、`autoToM4a()`、`autoAacToSilk()`、`silkToAac()`、`aacToPcm()`、`pcmToAac()`、`pcmToM4a()`、`m4aToPcm()`、`decodeM4aFile()`
- 通用入口：`startTransform(int type, String inputPath, String outputPath, int sampleRate, Consumer callback)`

脚本运行时、快照和音频方法底层依赖 `h.Hchat.hooks.items.script.ScriptPluginRuntime`、`h.Hchat.hooks.items.script.ScriptAudioBridge` 和 `me.yun.silk.*`。发布构建必须保留这些类的 keep 规则，避免脚本通过方法名调用时被 R8 混淆。收到消息后的媒体下载回调还必须保留 `ScriptMessageHook`、`ScriptPluginRuntime` / `ScriptWaBridge` 内部类、`WeChatMessageChangeApi` 和 `WeChatDatabaseListenerApi`；`alt-entry` 还必须保留 `FinderMediaDownloadSupport` 和 `FinderFeedDetailResolver` 及其内部类。数据库可能先写入 `msgSvrId=0` 的半成品消息，再通过后续写入补齐正文和媒体元数据；普通消息回调与媒体下载分开去重，媒体队列重新查询最新记录后才提交下载。媒体回调声明以 BeanShell 方法表与源码方法声明/显式绑定合并识别，不能只依赖 R8 环境下的解释器方法表。凡是脚本能直接拿到并按方法名调用的 Bean/消息对象也必须 keep，包括 `ScriptMessageBean`、`ScriptQuoteMsgBean`、`WeChatMessage`、`WeChatQuoteMsg`、`WeChatImageMsg`、`WeChatVideoMsg`、`WeChatFileMsg`、`WeChatTransferMsg`、`WeChatPatMsg` 等；否则 release 包里会出现对象类名变成混淆类、`getTitle()` / `getContent()` 等方法取不到值的问题。脚本的普通消息、确认、输入、单选和多选界面通过 `showModule*Dialog` 复用模块 Miuix 弹窗；旧签名默认显示在底部，带 `position` 的重载可指定 `top`、`center` 或 `bottom`，运行时负责切回主线程并校验当前前台 Activity。插件 Agent 默认生成这些接口，不直接生成 Android 原生 `Dialog` / `AlertDialog`，复杂自定义布局除外。

当前 Hchat 音频桥接默认走本地接管后的 `Silk-Codec-Android` native 方法；由于上游 OGG 解码器只支持 Vorbis，首个 Ogg 识别包为 `OpusHead` 时改走 Android `MediaExtractor + MediaCodec` 解码，再复用现有 PCM 重采样和 Silk 编码。普通 Ogg Vorbis 保持 native 路径，Opus 解码失败仍映射为 OGG 错误码；解码过程同时校验有效 PCM 输出并在长时间无进展时中止，失败不保留半成品。Silk 相关接口只接受 `8000/12000/16000/24000`，不支持的采样率按 `24000` 处理；可识别源格式会自动使用真实采样率：MediaCodec 解码以实际输出 PCM 采样率为准，MP3/FLAC/Ogg Vorbis 优先读媒体元数据，WAV 读文件头；转 Silk 时会把中间 PCM 重采样到目标 Silk 采样率，避免容器采样率和解码输出采样率不一致而变速变调；只有裸 PCM 输入因为没有头信息，仍必须依赖脚本传入的 `sampleRate/pcmHz/channels`；`autoToSilk`、`autoToPcm`、`autoToAac`、`autoToM4a`、`autoAacToSilk`、`startTransform` 都必须走 `ScriptAudioBridge` 的统一分发，不要直接绕回未经兼容处理的 native 方法。
公共 `WeChatApis.media().sendVoice(...)` 会统一切到微信主线程执行，再按文件头识别真实音频类型，非 Silk 的常见音频先转成临时 Silk 再走微信语音上传链路，避免 MP3 等文件改成 `.silk` 后按假后缀发送导致无声；自动回复、红包回复、进退群回复、脚本接口和语音转发复用同一公共发送路径。

列表性能接口优先使用：

- `getFriendListInfo()`
- `getGroupListInfo()`
- `getGroupMemberListInfo(String groupWxid)`

这三项返回整理好的 `Map` / `List<Map>`，适合搜索、选择器和批量处理；只有在需要兼容老 WA 对象写法时才用 `getFriendList()`、`getGroupList()`、`getGroupMemberList()`。

脚本 HTTP 使用 WA 风格异步封装：`get(url, headerMap, callback)`、`post(url, paramMap, headerMap, callback)`、`download(url, path, headerMap, callback)`，也可以通过 `http.get(...)`、`http.post(...)`、`http.download(...)` 调用。带 `timeout` 的重载单位是秒；`get/post` 回调返回响应文本，失败时可能为 `null`；`download` 回调返回下载后的 `File`，失败时可能为 `null`。图片下载另提供异步 `downloadImage(s)` 和 WA 兼容 `downloadImg(...)`：前者固定保存到 `Hchat/Image`，后者支持 HTTP URL 与微信图片 CDN fileid；无回调重载等待完整文件落盘，四参数加 `PluginCallBack.DownloadCallback` 的重载和图片对象回调重载均在后台下载。视频下载提供带 `PluginCallBack.DownloadCallback` 的 `downloadVideo(...)`：可传整条消息或直接传 CDN 参数，整条消息优先复用本地完整 MP4，缺失时通过 `imgPath` 查询原生 `VideoInfo`，再以视频文件类型 `4` 下载，不能依赖消息正文一定存在 XML。CDN 下载通过提前捕获的 Mars `CdnManager` 实例提交。底层 HTTP 使用 OkHttp。旧 WA 插件可继续使用 `me.hd.wauxv.plugin.api.callback.PluginCallBack.HttpCallback` 和 `PluginCallBack.DownloadCallback`；成功分别回调 `onSuccess(200,response)` / `onSuccess(File)`，失败回调 `onError(Exception)`。

脚本声明 `onImageDownload(Object msg, String imagePath, String talker, String senderWxid)` 时，普通消息数据库监听在完成消息去重后才提交自动下载；没有启用的订阅插件时不下载。同一条图片只下载一份到 `Hchat/Cache`，然后按插件 ID 顺序分发给所有订阅插件。下载使用 2 个线程、容量 32 的有界队列，队列满时丢弃新事件并限频记录；该回调属于主进程消息能力，不在 `appbrand` 轻量进程触发。

脚本声明 `onVideoDownload(Object msg, String videoPath, String talker, String senderWxid)` 时，普通消息数据库监听只对普通聊天视频提交自动下载。发送消息可复用本地源，接收消息只有取得长度元数据后才物化 VFS 文件，否则从 `imgPath` 等待七个确认版本均已适配的原生 `VideoInfo`，再走聊天视频 CDN 下载。运行时按入队时订阅实例分发，使用 10 分钟任务级去重；没有订阅插件时不下载。视频队列独立使用 2 个线程、容量 32 的有界队列，所有订阅回调结束后清理共享临时文件。该回调不表示微信界面任意下载任务的全局完成事件，也不处理视频号分享。

`alt-entry` 另提供 `onFinderMediaDownload(Object msg, String mediaPath, String talker, String senderWxid)`。运行时只在存在订阅插件时结构化解析聊天消息 XML 的 `objectId`、`objectNonceId`、`sourceCommentScene` 与 `finderFeed/mediaList/media`，不依赖版本混淆类名。聊天分享 XML 通常不包含 `decodeKey`、视频规格和 PCDN 地址；视频下载前统一通过 DexKit 定位并按微信 runtime key 缓存的 `findergetcommentdetail` 原生请求取得完整 `FinderObject`，再复用模块菜单的解密下载链。详情响应按微信动态 protobuf 的零基字段下标读取目标 `feedObject`，并只接受 `objectId` 匹配的 `refObjectList` 作为兜底；协议解析同时兼容旧版 protobuf 公共字段与新版稳定 getter。多媒体动态逐文件下载和回调，同一文件供所有订阅插件共享，回调全部结束后清理临时文件。任务执行状态不按时间过期，完成后保留 10 分钟去重；下载结果只有具备标准 MP4 `ftyp` 文件头时才能成功，否则删除无效文件并按下载失败处理。

脚本运行时内置官方 `com.alibaba.fastjson2:fastjson2` Android 版本，旧插件可以继续导入并使用 `JSON`、`JSONObject`、`JSONArray`、`JSONPath`。`getFriendList()` 和 `getGroupList()` 返回 WA 同包名兼容对象 `me.hd.wauxv.data.bean.info.FriendInfo` / `GroupInfo`，兼容旧插件强转读取 `getWxid()`、`getNickname()`、`getRemark()`、`getRoomId()`、`getName()` 等方法；高性能结构化读取仍优先使用 `getFriendListInfo()`、`getGroupListInfo()`。

脚本可以使用 Java 原生反射，默认导入 `Field`、`Method`、`Constructor`、`Member`。同时注入并默认导入 `KavaReflector`，需要复用项目统一反射封装时使用 `KavaReflectorClass` 或直接调用 `KavaReflector`。WA 风格反射简化函数包括 `firstMethod(instance, methodName)`、`firstMethod(instance, methodName, paramCount)`、`firstConstructor(instance, paramCount)`、`firstField(instance, fieldName)`、`invokeMethod(instance, methodName)`、`invokeMethod(instance, methodName, params)`、`invokeMethod(instance, methodName, paramCount)`、`invokeMethod(instance, methodName, paramCount, params)`、`createInstance(instance, paramCount)`、`createInstance(instance, paramCount, params)`、`getField(instance, fieldName)`、`setField(instance, fieldName, value)`。

### 脚本插件 API 示例

脚本是 BeanShell 语法，文件名固定为 `main.java`。脚本顶层代码会在插件加载时执行；如果定义了 `onLoad()` / `onUnload()`，会分别在开启/关闭插件时调用。

基础变量：

```java
log("插件目录=" + pluginDir);
log("缓存目录=" + cacheDir);
log("宿主版本=" + hostVerName + "(" + hostVerCode + ")");
toast("插件已加载");
```

生命周期和发送按钮：

```java
void onLoad() {
    log("已加载");
}

void onUnload() {
    log("已卸载");
}

void openSettings() {
    toast("这里打开插件自己的设置界面");
}

boolean onClickSendBtn(String text) {
    if ("/id".equals(text)) {
        sendText(getTargetTalker(), "当前账号: " + getLoginWxid());
        return true; // 拦截原发送并清空输入框
    }
    return false; // 不拦截，微信正常发送输入框内容
}

boolean onLongClickSendBtn(String text) {
    if ("/preview".equals(text)) {
        toast("预览: " + text);
        return true; // 消费长按并清空输入框
    }
    return false; // 保留微信原生长按行为
}
```

消息监听：

```java
void onHandleMsg(Object msg) {
    if (msg.isSend() && msg.isText()) {
        log("自己发文本: " + msg.getContent());
    }
    if (msg.isGroupChat() && msg.isAtMe()) {
        sendText(msg.getTalker(), "收到");
    }
    if (msg.isVoice() || msg.isVideo() || msg.isImage()) {
        log("媒体消息 type=" + msg.getMsgType() + " sender=" + msg.getSender());
    }
}
```

账号、会话、联系人：

```java
String self = getLoginWxid();
String alias = getLoginAlias();
String talker = getTargetTalker();
android.app.Activity top = getTopActivity();

Object friends = getFriendList();
Object groups = getGroupList();
Object officials = getOfficialList();
Object labels = getContactLabelList();
Object usersByLabel = getContactByLabelName("重要好友");
Object members = getGroupMemberList("12345@chatroom");
int count = getGroupMemberCount("12345@chatroom");
String name = getFriendName("wxid_xxx", "12345@chatroom");
String avatar = getAvatarUrl("wxid_xxx", true);
```

联系人性别和地区：

- `getFriendGender(wxid)` / `getGroupMemberGender(groupId, memberWxid)` 返回 `0/1/2`，分别表示未知/男/女。
- `getFriendRegion(wxid)` / `getGroupMemberRegion(groupId, memberWxid)` 返回微信本地资料里的原始省市文本拼接，不主动补全成行政区全称。
- 自己账号的性别、城市、省份走 `userinfo` 回退；普通联系人和群成员优先解析 `rcontact.lvbuff`。
- 普通联系人未打开过资料页时，`rcontact.lvbuff` 可能没有性别/地区。需要自动补资料时，优先走微信自己的 `MicroMsg.GetContactService` 业务入口，不要直接写死 `NetSceneGetContact` 类名或构造签名。

资料页 ID：

- `资料页显示ID` 属于实用功能，开启后会在好友资料页和群聊资料页插入一行 `ID: <id>`，点击后复制该好友 wxid/自定义微信号或群聊 ID。
- 好友资料页横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74` 都使用 `com.tencent.mm.plugin.profile.ui.ContactInfoUI#initView()`；ID 优先读取 Intent 的 `Contact_User`，再回退 `Contact_Username` 和 extras 扫描。
- 群聊资料页横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74` 都使用 `com.tencent.mm.chatroom.ui.ChatroomInfoUI`，并保留 `RoomInfo_Id`；ID 优先读取 Intent 的 `RoomInfo_Id`。
- 注入 UI 时不要写死微信 Preference 混淆方法名；好友资料页继续按列表/滚动容器前插入，群聊资料页必须插入微信 `PreferenceScreen`，优先按 `expand_room_member`（更多群员）位置把 ID 行插在其上方，其次回退 `see_room_member` / `room_name`，不能再往普通根布局塞 View，避免固定到页面顶部。

滑动手势：

- `滑动手势` 属于实用功能，设置页包含 `左滑引用回复`、`右滑复读` 和 `长按菜单复读` 三个独立开关，默认都关闭；用户可以只启用其中一种复读触发方式。消息行定位优先 hook `MicroMsg.ChattingDataAdapterV3` 的 `_onBindViewHolder` 绑定方法，保存当前行根 View、talker、本地 msgId 和适配器里的原生消息对象；8.0.74 等版本的内部列表项可能是包装对象，必须递归拆出内部 `com.tencent.mm.storage.*` 原生消息对象。8.0.49 的 `i0`、8.0.58 的 `j`、8.0.66 至 8.0.77 的 `k` 均提供 `getItem(int)`；8.0.77 的 `getItem(int)` 桥接到 `K0(int): com.tencent.mm.storage.e9`，不能把同类的 `J0(int): void` 当作消息读取方法缓存。
- 手势统一由 RecyclerView `onInterceptTouchEvent` 和 `onTouchEvent` 处理，避免消息行和列表两套状态机同时抢事件；交互按 Telegram 风格处理，移动中让命中的整条消息 item 跟手移动，头像、昵称和内容一起移动，松手超过阈值才触发，未触发则弹回；左滑判定成立后持续消费当前触摸流并禁止父级拦截，右滑同样沿用同一状态机；绑定行视图、切换命中行和手势结束时都必须清理 `translationX/alpha`，避免 RecyclerView 复用旧位移导致消息不会回弹。
- `左滑引用回复` 只调用微信原生引用入口，让被引用消息显示到输入框上方；发送动作仍由微信原生输入框处理，不拦发送按钮，不弹 Toast；引用成功后聚焦 ChatFooter 输入框并拉起软键盘。不要自己构造引用 XML 或调用 Hchat 的 `sendQuoteMsg`。
- 横向确认 `8.0.49` 使用 `ChatFooter#setLastQuoteMsgId(long)` / `setLastQuoteMsgInfo(com.tencent.mm.storage.k9)`；`8.0.58` 使用 `ChatFooter#setLastQuoteMsgInfo(com.tencent.mm.storage.q9)`，调用链里会先按 talker/msgId 取原生消息；`8.0.66` 使用 `ChatFooter#S0(String,long,dr4.e)`；`8.0.68` 使用 `ChatFooter#S0(String,long,ou4.e)`；`8.0.72` 使用 `ChatFooter#j1(String,long,f05.f)`；`8.0.74` 使用 `ChatFooter#j1(String,long,r15.g)`；`8.0.76` 使用 `ChatFooter#m1(String,long,a35.g)`；`8.0.77` 使用 `ChatFooter#C1(String,long,y55.i)`。
- 实现时新版优先用 `invalid quote msg id` 字符串锚点定位 ChatFooter 的 `(String,long,Object)->void` 原生引用方法，旧版 8.0.49/58 回退时必须同时调用 `setLastQuoteMsgInfo(nativeMessage)` 和 `setLastQuoteMsgId(long)`，避免只写消息对象或只写 ID 导致引用栏内容为空；不要写死第三参数混淆类名。引用状态写入后要刷新输入区引用栏：`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`、`8.0.77` 横向确认都有 `ChatFooter#setMsgQuoteRlVisibility(int)`；`8.0.49` 没有该方法，回退时还必须通过 `ChatFooter#getChattingContext()` 取旧版 DraftComponent，写入其草稿文本 `g` 和引用消息 ID `i` 后调用 `M0()`，再 requestLayout，否则引用栏可能只在重进聊天后显示或显示为空。
- `右滑复读` 与 `长按菜单复读` 共用同一发送链路：长按菜单通过公共单消息菜单定位缓存注入带代码自绘圆圈 `+1` 图标的 `复读[H]` 项，固定放在菜单首位，并把菜单项绑定到本次长按消息的本地 msgId，不能在点击时遍历处理器对象图猜消息。菜单图标注入复用微信带图标菜单项的五参数入口，再替换为自绘 `Drawable`，不能只调用普通 `Menu.add(...)` 后依赖标准菜单图标渲染。发送通过微信原生 `com.tencent.mm.ui.transmit.MsgRetransmitUI` 的 `Retr_MsgQuickShare=true` 快速转发分支复读到当前聊天，不弹选择联系人页，不显示成功提示。已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74` 都存在 `Retr_MsgQuickShare`、`Select_Conv_User`、`Retr_Msg_Type`、`Retr_Msg_Id`、`Retr_Msg_content` 等稳定 extra；AppMsg 转发分支分别为 49=`w6`、58=`X6`、66=`d7`、68=`h7`、72=`B7`、74=`a7`，都会按 XML `<type>` 解析，引用消息 `<type>57</type>` 走同款引用/接龙分支并写入源消息 ID。复读必须优先按本地消息表读取 `WeChatMessage`，数据库暂不可用时才回退绑定阶段保存的原生消息对象；引用/AppMsg 要传去掉群聊 `sender:\n` 前缀后的 XML body，并保持 `Retr_Msg_Type=2`、`Retr_Msg_Id=源本地 msgId`，不能因高位消息 type 没归一化就退成普通文本；名片消息使用微信原生 `Retr_Msg_Type=8`，该分支在 49/72/74 的 `MsgRetransmitUI#onCreate` 已确认仍按名片选择范围处理。图片复读统一走原生 `MsgRetransmitUI`，不要再优先调用 `WeChatApis.media().images().send(...)`，避免 8.0.49 静默图片发送链路不稳定导致闪退。语音消息不走 `MsgRetransmitUI` 的 `Retr_Msg_Type=34`，因为 49 的 `n6()` 对 34 会直接 finish；复读语音应复用 `WeChatApis.media().voices().send(...)`，文件名参考本地消息表与绑定阶段保存的原生消息对象，时长优先从当前已持有的原生消息对象显式字段（如 `VoiceLength` / `field_voiceLength`）读取并缓存，取不到时再从语音消息 content 的原生 `fileName:duration:flag` 结构解析，新版本仍取不到时按 FileName 读取原生 `voiceinfo.VoiceLength`；触发后的文件路径解析放后台线程，最终语音发送仍切回主线程调用公共发送接口，避免触发时被查库或路径解析卡住。不支持或解析失败的消息类型直接不触发，不要乱发。72/74 等版本即使 `Retr_show_success_tips=false` 仍可能显示完成提示，Hchat 必须只在私有复读标记存在时拦截带 `sendResult` / `SendMsgUsernames` 的完成方法并结束页面，不能影响普通微信转发。
- `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均已确认聊天长按菜单存在 `c(int,int,int,CharSequence,int)` 带图标入口、内部可变 `List` 和 `MenuItem#setIcon(Drawable)`，同时都包含 `raw/icons_filled_edit_photo_pencil` 占位资源；模块应先用宿主资源创建图标槽位，再替换成代码自绘圆圈 `+1`，并移动实际菜单对象到列表首位。

- 单消息长按菜单点击的自定义高位 ID 必须让微信原点击方法继续执行收尾；已确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 会在未知 ID 后清除消息选择态。不要在点击 Hook 中提前设置结果而跳过原方法。

保存表情：

- `保存表情` 属于实用功能，设置页放在 `聊天` 分组，默认关闭。开启后只在 type 47 聊天表情的长按菜单中注入带 `icons_filled_download` 图标的 `保存[H]`，菜单项直接绑定本次长按的原生消息，点击后仍放行微信原菜单收尾，避免残留文字选择态。
- `发送文本格式` 属于实用功能，设置页放在 `聊天` 分组，默认关闭。文本模板支持以中文名称点击插入 `原消息`、`换行`、`发送时间`、`发送耗时`、`发送总数`、`文字消息数`、`文字字数`、`表情消息数`、`转账消息数`、`红包消息数`、`文件消息数`，底层稳定占位符依次为 `${sendText}`、`${line}`、`${sendTime}`、`${sendDuration}`、`${totalMsg}`、`${textMsg}`、`${textWord}`、`${emojiMsg}`、`${transferMsg}`、`${redBagMsg}`、`${fileMsg}`。发送时间格式可单独配置；发送耗时从当前会话首次点击输入框开始计算，到点击发送按钮为止，发送成功后重置，微信拒绝发送且输入框仍保留文本时继续沿用本次计时；没有输入过程的后台发送显示为 `0秒`。模板必须且只能保留一个原消息变量，空模板回退为仅发送原文，导入的非法模板在运行时直接放行原文。用户点击微信原生发送按钮且模块处理器、脚本 `onClickSendBtn` 均未拦截时，模板前后内容会原位插入 ChatFooter 的 `Editable`，保留艾特等 span 并继续使用微信原生文字发送流程；模块和脚本插件通过公共文本接口调用 `sendText`、`sendTextWithAtList`、`sendAt`、`sendAtAll` 或 `sendRaw(type=1)` 时也会经过同一格式化器，因此自动回复等复用公共接口的文本发送同样生效。七个统计变量与输入框提示共用当前本地自然日的真实发送统计，包含手动、模块和脚本插件实际发送的消息。
- `自动勾选原图` 属于实用功能，设置页放在 `聊天` 分组，默认关闭。开启后只处理聊天发图入口：在 `AlbumPreviewUI` 和 `ImagePreviewUI` 的 `onCreate(Bundle)` 之前，为 `query_source_type=3` 且存在 `GalleryUI_ToUser` 的 Intent 同时写入 `key_send_raw_image=true` 和 `send_raw_img=true`，由微信原生页面直接显示原图已选状态，不影响朋友圈或其它图库用途。`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均确认两个页面的类名和 `onCreate(Bundle)` 稳定；`AlbumPreviewUI` 初始化读取两个原图参数，`ImagePreviewUI` 初始化读取 `send_raw_img`，运行时不固定混淆类名或方法名。
- `自动查看原图` 是独立于发送侧“自动勾选原图”的聊天查看功能，默认关闭。开启后在聊天媒体查看页首次恢复以及左右翻页完成后短时等待原生操作完成布局，仅在当前页的“查看原图”或“查看原视频”按钮实际可见、可用且已绑定点击事件时执行一次原生点击；原图已存在、原生入口不提供、页面已退出或开关关闭时不会强制调用内部下载逻辑。待执行任务和成功记录按当前媒体页隔离，翻页时取消上一页重试，销毁页面时清理全部状态。微信 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均确认聊天图片与视频由稳定的 `ImageGalleryUI` 承载，页面持有 `MMViewPager` 和 `ViewPager.OnPageChangeListener`；模块按字段类型取得原生监听器并在 `onPageSelected(int)` 完成后处理，不替换微信监听器，也不固定七版不同的混淆字段名。图片原图按钮资源名为 `cnb`、原视频按钮资源名为 `p1o`，七个版本的 `onClick(View)` 分支分别调用图片原图加载和原视频下载逻辑，运行时不固定随版本变化的资源数值或混淆业务方法。
- `移除转发限制` 属于实用功能，设置页放在 `聊天` 分组，默认关闭。开启后让旧版微信原生 `SelectConversationUI` 的会话数量限制检查返回未超限，并在完整联系人页把转发专用的 `max_limit_num=9` 改为无上限。联系人页只接受旧流程的 `list_type=14`、新版流程的 `ForwardParams_ForwardByUIC=true`，或带 `Retr_Msg_Id` / `Retr_Msg_view_model` 的原生消息转发 Intent，仍不处理其它来源或非默认上限，不影响建群、朋友圈可见范围等其它联系人选择场景；联系人展示、选择状态、确认弹窗和原生转发发送链路保持不变，开关可实时切换。核心限制 Hook 在启动时先按稳定类名和唯一的非静态 `(boolean) -> boolean` 签名直接安装，不等待后台 DexKit 队列；未来版本出现签名歧义时，再用 `max_limit_num` 字符串精确定位并写入 `DexMethodCache`。`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 的旧流程限制方法分别确认为 `B7`、`b8`、`i8`、`m8`、`D8`、`c8`、`Y7`；完整联系人页在 `8.0.49` 为 `MvvmSelectContactUI`，其余确认版本为 `MvvmContactListUI`。`8.0.58` 至 `8.0.76` 的新版 UIC Intent 构造方法分别为 `d60.c0.f8`、`i90.r0.zh`、`fa0.r0.Cg`、`sc0.q0.Qh`、`qd0.q0.Ui`、`pd0.q0.qj`，共同使用 `max_limit_num`、`too_many_member_tip_string` 和 `ForwardParams_ForwardByUIC`。新版随后会在 UIC 配置工厂中把 Intent 限制复制进内部状态，仅 Hook Activity `onCreate` 不能稳定覆盖；`8.0.58` 至 `8.0.76` 对应工厂分别为 `ew4.f4.y2`、`c75.k4.f6`、`ma5.k4.o6`、`sg5.m4.o7`、`cj5.l4.T6`、`jk5.o4.e7`，均同时读取 `min_limit_num`、`max_limit_num` 和 `ForwardParams_ForwardByUIC`。模块通过这三个字符串唯一定位并缓存该无参数对象返回方法，在其读取前改写最终 Intent；定位失败时由统一 DexKit 调度器重试。确认后的完整收件人列表继续交回微信原生流程，大量目标仍受微信发送能力限制。
- `拦截正在输入上报` 属于实用功能，设置页放在 `聊天` 分组，默认关闭。开启后只跳过本机聊天组件向对方发送输入状态的入口，不影响接收对方输入状态、输入框、文字发送或其它网络请求；Hook 安装后实时读取开关，切换不要求重启微信。目标方法通过 `MicroMsg.SignallingComponent` 与 `[doDirectSend] mChattingContext is null!` 两个精确字符串定位，并校验为聊天组件中的非静态、非抽象 `(int) -> void` 方法，定位结果写入 `DexMethodCache`。`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均为唯一命中，分别对应 `ze.M0`、`sg.f0`、`bi.p0`、`si.r0`、`tk.u0`、`gl.n0`、`hl.m0`；在该入口返回可以同时阻断旧版 type `111` 信令和新版 `MMTypingSend` 请求。
- `禁止拍一拍` 属于实用功能，设置页放在 `聊天` 分组，默认关闭。开启后只让聊天头像双击监听直接返回已处理，不再进入 `PatHandler` 创建本地拍一拍消息和发送网络请求；头像单击、长按、接收拍一拍、脚本 `sendPat` 及无障碍入口不受影响。目标方法通过 `MicroMsg.AvatarDoubleClickListener`、`onDoubleClick tag null` 与 `onDoubleClick: %s` 三个精确字符串定位，并校验为聊天包中的非静态、非抽象 `(View) -> boolean` 方法，定位结果写入 `DexMethodCache`。`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均为唯一命中，分别对应 `f7.a`、`o7.a`、`s5.a`、`r5.a`、`c6.a`、`z5.a`、`z5.a`。
- `屏蔽艾特所有人` 属于实用功能，设置页放在 `群组` 分组，默认关闭。开启后使用统一群聊多选器指定需要屏蔽的群聊，未选择群聊时不拦截；旧版本已经开启但尚无群聊范围配置的用户继续按全部群聊生效，重新保存选择后改为只处理所选群聊。功能继续正常接收并保存群消息，复用公共消息模型识别不同版本 `atuserlist` 中的 `notify@all`、`all`、`@all` 和正值 `atall` 标记；服务端把全体名单展开成当前账号 wxid 时，再结合微信真实艾特的 `U+2005` 分隔结构恢复 @ 全体，并排除 `announcement@all` 群公告。命中后记录 talker 与服务端消息 ID，最终在 `NotificationManager.notify()` 前按二者精确匹配并拦截展示，同时保留微信原有的消息通知记账；普通手打“@所有人”不会命中。同一消息在短时内重复提交通知时继续拦截。该功能不修改其他微信原生通知的标题、正文、声音、震动或样式。原生通知处理入口和 `NotificationItem` 链路已横向确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76；其中 `NotificationItem` 的消息 ID 字段均为 `long i`，通知处理器发送前用消息对象的 `msgSvrId` 赋值。
- `隐藏聊天菜单` 属于实用功能，设置页放在 `界面` 分组，默认关闭。开启后按用户填写的菜单显示文字隐藏聊天消息长按菜单项，名称支持逗号、分号和换行分隔，初始值只是可编辑示例，不是固定屏蔽名单。Hook 复用 `SingleMessageMenuLocator.menuCreateMethods`，在菜单创建完成后由最高优先级的 after 回调最后收尾，并通过菜单容器 `removeItem(...)` 删除匹配项，因此也能覆盖其它 Hchat 长按菜单注入项；关闭开关或清空名单后不处理原生菜单。
- `自定义底栏` 与 `悬浮底栏` 属于实用功能，设置页分别放在 `美化` 分组，默认均关闭，两者共用一套微信底栏 Hook 并互斥接管。`自定义底栏` 直接保留微信原生 `LauncherUIBottomTabView`，支持分别修改微信、通讯、发现、我的四个标题与 PNG 图标，也可隐藏标题或隐藏整个底栏；隐藏底栏时不再把整个容器设为 `GONE`，而是在原生页面切换、短暂 detach、回到前台和每次 pre-draw 时持续遍历并隐藏其直接子 View，同步清理微信底部磨砂区域，并把系统导航栏持续设为透明且允许页面内容绘制到其后方，只有设置关闭、功能销毁或确认旧实例失效时才恢复各子项原始可见状态。设置页使用草稿状态，只有保存才生效，取消不改运行配置，重置恢复默认标题和开关并清空图标草稿。图标通过 Android 系统 `image/*` 选择器导入，最大边缩放到 256 像素并原子保存到 Hchat 私有存储目录；替换或取消未保存图片时会清理旧文件。替换图标会复用原 `TabIconView` 的实测宽高和布局槽位，避免上传图片的原始尺寸改变底栏排列。原生标签按根 View 的 `0..3` tag 定位，图标按 `com.tencent.mm.ui.TabIconView` 定位，标题优先按资源名 `icon_tv` 定位，避免固定混淆 holder 类和字段；关闭、页面销毁、配置切换或接管失败时恢复标题、可见性、原始图标和底栏状态。配置保存到 `Hchat_native_bottom_bar_config`，包括 `enable`、`modify_icons`、`modify_titles`、`hide_titles`、`hide_bar`、四个标题和四个图标路径。
- `悬浮底栏` 继续使用原 `Hchat_custom_bottom_bar_config`，升级不会丢失已选择的悬浮样式。启用后保留微信原 `LauncherUIBottomTabView` 容器，持续遍历并隐藏其中四个原生页签子 View，再把悬浮导航作为底栏直接 `FrameLayout` 父容器的底部兄弟 View 挂载，不再占用 Activity 的 `android.R.id.content` 总内容层，避免全屏 overlay 与其它模块的 Launcher View 注入相互覆盖。运行时同时监听底栏构造、attach、Launcher 恢复和设置变化，短暂 detach、重新 attach、冷启动错过普通功能初始化或层级更换时都按当前原生底栏层级补挂。每个悬浮底栏实例使用独立 `Recomposer` 和 Android 主线程帧时钟，ComposeView 通过 `setParentCompositionContext()` 显式绑定，不读写 Launcher 根 View 的 owner，也不创建或复用窗口级 `WindowRecomposer`；关闭时只销毁当前实例的 composition 和 Recomposer，不影响其它模块。四个悬浮按钮复用索引 tag 对应的真实微信 tab View，并直接调用底栏持有的唯一原生 `View.OnClickListener` 执行点击；组件接收当前索引读取器并在长期 `snapshotFlow` 中直接观察真实状态，不依赖父层重组来启动胶囊动画，冷启动和热挂载使用同一条状态链路。点击项只提交外部索引；外部索引是离散切换驱动胶囊位置的唯一入口，负责点击、原生切页、滑动切页和失败回滚。胶囊位置弹簧沿用 KSU 的 `press -> move -> release` 点击反馈，位移启动后立即安排释放，只在接近目标前保持按压形变，不等待位移弹簧完全收敛后才缩回；普通非玻璃胶囊不再额外套用缩放。组件不叠加独立缩放、高光脉冲，避免额外动画与位置弹簧共享取消生命周期。重复点击当前页签保留胶囊自身的按压回弹。拖动保留按压、速度和回弹动画，由 `Animatable` 直接接收每次位移并吸附到最终页签，不在每个触摸帧外层取消、排队和重建互斥任务。运行中找不到已有宿主时会从当前底栏自恢复挂载，热挂载完成后立即及下一帧各同步一次 `getCurIdx()`，因此首次开启、修改设置或切换页签不需要强停微信。图标使用微信原生资源对应的 72x72 outlined/filled 矢量路径。微信、通讯录、发现和我四个页签分别读取原生未读数及红点状态；`显示角标` 开启时四个页签都显示各自未读数字或红点，关闭时全部隐藏，角标始终保持红色。悬浮配置变化延后到下一帧并原位更新 Compose 状态，不销毁光标动画宿主；关闭或销毁时释放 composition、移除 wrapper、清理 owner，并恢复原生子项可见状态。每次 enforce 清零当前 root 中的 `FrostedContentView` 底部磨砂高度，8.0.66 至 8.0.76 还会在其 `dispatchDraw(Canvas)` 真正绘制前再次清零，避免微信在 pre-draw 后重写高度导致通讯录、发现或朋友圈出现白色磨砂底栏；8.0.49 与 8.0.58 没有该类，自动跳过。可单独开启液态玻璃效果，把模糊半径设置为 `0..40dp`，也可隐藏标签文字或关闭全部角标；振动反馈支持独立开关和 `1%-100%` 强度，切换页签时即时读取当前配置，`100%` 沿用新增强度设置前的系统触感，低档位按设备振幅控制能力缩放，不支持幅度控制时按振动时长兼容。新增配置键为 `custom_bottom_bar_vibration_enabled` 和 `custom_bottom_bar_vibration_strength`；其它配置键仍为 `custom_bottom_bar_enable`、`custom_bottom_bar_style`、`custom_bottom_bar_glass`、`custom_bottom_bar_blur_radius`、`custom_bottom_bar_hide_labels` 和 `custom_bottom_bar_show_discovery_badge`，其中原发现角标键为兼容旧配置继续保留，但现在统一控制四个页签角标。`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 已横向确认 `LauncherUIBottomTabView` 的三个构造签名、四个 tab 根 View 的索引 tag、角标 getter、`setTo(int)` 及底栏持有的唯一原生 `View.OnClickListener` 结构稳定。
- 悬浮底栏只在 `composeHost` 实际 attach 后由 `ComposeView` 创建 Composition；冷启动阶段不在 detached View 上强制提前创建，确保重组与 `LaunchedEffect` 使用真实窗口挂载时序。热开关和冷启动共用相同创建流程。
- 胶囊位置不再通过稳定 lambda 内的长期 `snapshotFlow` 间接观察页签；`CustomBottomBarUiState.selectedIndex` 在 Compose 重组时直接传入组件，并作为 `LaunchedEffect` key 启动位移动画，避免冷启动 Composition 对间接状态读取注册不完整时胶囊固定在微信页。
- 悬浮底栏的页签索引与角标变化由原生状态 Hook 立即同步，pre-draw 只保留 500 毫秒低频兜底，不再逐帧执行整组反射 getter。底部磨砂首次压制当前 root 时扫描并清零已有 `FrostedContentView`；Hook 已安装后不再逐帧遍历 Launcher View 树，动态新增或被微信写回的实例继续由 `dispatchDraw(Canvas)` Hook 在绘制前处理。
- 悬浮底栏和内嵌设置页的独立 Recomposer 显式使用 Hchat 私有 lifecycle 创建，沿用 Compose 官方的主线程帧时钟、动画时长状态和生命周期调度，不从 `DecorView` 查找或写入 owner。ComposeView 短暂 detach 时旧 Recomposer 会被取消，重新 attach 后自动重建，避免冷启动阶段留下只能绘制首帧、后续动画与手势协程不再运行的 Composition。
- 悬浮底栏跟随原生底栏层级后，以原生底栏当前窗口可见状态限制为微信首页显示；进入同一 `LauncherUI` 内嵌聊天页时随原生底栏隐藏并恢复该根布局原有磨砂高度，返回首页后由 pre-draw 自动恢复悬浮底栏和磨砂压制。`FrostedContentView.dispatchDraw(Canvas)` Hook 只处理悬浮底栏实际显示且已经被底栏宿主成功接管的 Launcher 根视图，不按全局开关修改其它 Activity、聊天页或挂载失败页面的磨砂区域。
- `快捷设置备注和标签` 属于实用功能，设置页放在 `界面` 分组，默认关闭。开启后在微信首页及聊天分组二级页的会话长按菜单加入快捷入口：好友显示“设置备注和标签”，群聊显示“设置群聊备注”；长按朋友圈时间线每条动态左侧的好友头像时，也会在微信原有“设置权限、投诉”菜单中加入“设置备注和标签”，同时保留个人朋友圈顶部头像入口。好友弹窗可修改或清除备注、勾选/取消/清空现有微信好友标签，也可创建新标签；创建标签后最多等待 15 秒确认微信 `ContactLabel` 表已同步，再把标签分配给好友。群聊入口仅接受联系人表中真实存在的 `@chatroom` 或 `@im.chatroom`，点击后打开微信原生 `ModRemarkRoomNameUI`，使用 `Key_Scenen=2` 和 `Key_Room_Id` 进入群聊备注模式，由微信原生页面负责读取、清空、保存、同步和刷新，不把群聊送入好友标签操作。朋友圈头像入口额外允许联系人表中真实存在的 `@openim` 企业微信联系人，仍不处理自己、群聊、公众号、系统会话或陌生人。朋友圈头像菜单从当前原生菜单回调持有的 `SnsInfo` 或 Improve 条目对象读取作者，不使用整条动态的共享长按菜单，也不按列表位置猜测。会话菜单项只接受本次菜单对象的精确绑定，点击时不重新按列表位置猜联系人。会话定位先用 `headercount:%d, postion:%d` 锚定会话长按方法；朋友圈头像菜单分别覆盖旧时间线和 Improve 的头像菜单创建及点击回调，定位结果写入 `DexMethodCache`。个人主页顶部头像入口使用稳定的 `com.tencent.mm.plugin.sns.ui.SnsHeader#setAvatarOnClickListener(View.OnClickListener)` 和 `getViewHeader()`。`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 已横向确认相关结构；七个版本的群聊设置页均通过 `room_remark` 分支启动同一原生页面并使用相同参数。不能只按 `ConversationLongClickListener` 日志定位会话入口，因为 49/58 仍使用 `ConversationClickListener`。
- `快捷查看朋友圈` 属于实用功能，设置页放在 `界面` 分组，默认关闭。开启后，微信首页和聊天分组二级页的本人、好友及企业微信联系人会话长按菜单都会增加“朋友圈”，点击后使用本次菜单精确绑定的会话 ID 打开微信原生 `com.tencent.mm.plugin.sns.ui.SnsUserUI`，并传入 `sns_userName`。本人通过账号 API 的当前 wxid 确认，企业微信联系人要求 `@openim` 身份且联系人资料有效；群聊、公众号、系统会话、陌生人或聊天分组虚拟会话仍不显示入口。首页复用统一的会话长按菜单定位结果，聊天分组复用原生二级页已经持有的长按目标，并通过公共会话菜单扩展注册表执行同一点击逻辑，不按列表位置重新猜测。`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均已确认 `SnsUserUI.onCreate(Bundle)` 读取同名参数。
- 表情源优先读取消息 `imgPath` 的 md5，再解析 content 中的 `md5`，最后才使用已存在的直接路径。保存时先由公共 `media().emojis().decodeData(...)` 获取 `EmojiInfo`，调用微信 `EmojiFileEncryptMgr` 解密缓存并通过 `MMWXGFJNI.nativeWxamToGif(byte[])` 转为真实图片数据；仅在原生解码不可用时回退 `getAccPath` 与 EmojiLogic 路径解析。结果写入微信外部媒体目录的 `Hchat/Emoji`，按文件头识别 GIF、PNG、JPEG、WebP，未知格式保留为 `.bin`。单消息菜单、解码管理器、WXAM 转换和路径入口均已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`；路径方法依次为 49=`r`、58=`s`、66/68/72/74/76=`p`，运行时不固定混淆方法名。

消息转发：

- `转发` 属于 `增强` 分组，单条转发菜单、多选转发朋友圈、转发收藏和朋友圈转发均为独立开关且默认关闭。开启单条转发后，在可读取的聊天消息长按菜单中显示 `转发[H]`，使用微信 `icons_filled_share` 图标并固定在菜单首位区域；同时启用长按复读和修改聊天记录时，顺序稳定为 `复读[H]`、`转发[H]`、`修改[H]`，不依赖各 Hook 的安装顺序。聊天语音原有的单条 `转发[H]` 会自动让位给该通用入口，避免出现两个同名菜单，收藏语音和多选语音入口不受影响。菜单创建、点击、原生消息绑定和选择态收尾复用公共 `SingleMessageMenuLocator`；普通可重发消息点击后显示转发到朋友圈、转发给好友、系统分享、群发助手和转发至标签，微信本身不能重发的消息只提供转发到朋友圈。
- 转发给好友和转发至标签复用 `SelectedMessageSnapshot`、模块消息发送通道与统一联系人仓库，支持好友、群聊、公众号、好友标签及模块群聊标签；转发至标签会把所选好友标签和群聊标签解析为当前仍存在的真实联系人 ID 并去重。群发助手允许选择模块通道或微信原生群发助手，原生通道继续使用现有消息类型检查、好友范围和分批队列，不绕过限制。模块静默发送按单个收件人串行执行并保留 500ms 内部安全间隔，不能在主线程一次遍历完整名单；文件和聊天图片必须交回微信重发页时，每次最多传入 10 个收件人，当前批次完成或超时后才进入下一批。发送状态按“消息数 × 收件人数”累计，取消后所有已排期回调必须失效，不能恢复旧批次。文件类 AppMsg 不能只把原消息 XML 交给 `sendXml()`，因为 XML 不包含可上传的本地附件路径；转发文件必须按源 `msgId`、会话和 `Retr_Msg_Type=2` 交回 `MsgRetransmitUI`，由微信复制 appattach 并发起上传。聊天图片同样交回 `MsgRetransmitUI`，避免静默图片 API 把实况图片重建成单张静态图。`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均确认该页面读取当前使用的 `Retr_Msg_Id`、`Retr_Msg_Type`、`Retr_MsgTalker` 和 `Retr_File_Name`，七版 AppMsgLogic 发送入口也都保留独立 `attachFilePath` 参数。转发到朋友圈不再限制聊天消息类型：文字保留正文，图片和视频继续作为原媒体，其它类型按语义转换为可编辑摘要，且不会把原始 XML 填入发布内容；随后打开微信原生朋友圈编辑界面供用户修改后自行发送，不直接发布。聊天视频的 `imgPath` 是微信文件令牌时，必须先通过公共视频路径 API 解析成实际 `.mp4`，不能直接用 `File(imgPath)` 判断不存在。朋友圈 `ContentObj.type=54` 的每个媒体项会携带同类型的实况视频子对象，转发时必须同时准备静态封面和视频；`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 的 `SnsUploadUI` 均支持通过 `KMulti_Pic_Item_List` 接收 `GalleryItem$LivePhotoMediaItem`，而 `8.0.49` 不存在该入口，应安全降级为静态图片。聊天实况媒体工厂从 `8.0.74` 开始存在，聊天实况图转朋友圈时优先复用当前选中消息持有的原生消息对象，再按其 talker 和 msgId 查询缓存记录并解析配套视频；构造编辑项时不能硬编码混淆字段名，因为 `8.0.58` 与 `8.0.76` 的时长、解析状态字段名已经变化。
- 开启多选转发朋友圈后，在微信多选分享菜单中显示 `转发到朋友圈[H]`。多条消息按消息时间顺序合并，非媒体消息转换为可编辑摘要，可与最多 9 张图片或单个视频组合；图片和视频仍不能混选，这是微信朋友圈编辑器的媒体载荷约束。媒体文件完整后先退出原生多选状态，再打开同一朋友圈编辑界面，返回聊天时不会残留多选状态。该入口复用 `MultiSelectMessageMenuLocator`、`MultiSelectMessageResolver` 和 `MultiSelectMessageUi`，不新增版本专属定位。
- 聊天实况视频路径可能由微信 VFS 管理，普通 `File` 不可见时必须优先调用当前版本 VFS 的单字符串输入流入口并落地模块缓存；朋友圈编辑项除封面和视频路径外，还要按 `toString()` 语义写入时长、宽高、文件大小、封面时间戳和解析完成状态。解析到实况视频后若原生实况条目构造失败，不得再静默降级为静态图片。
- 聊天视频路径解析在 8.0.49/8.0.58 使用 `MicroMsg.VideoInfoStorage` 路径入口及其静态单例 getter，8.0.66/8.0.68/8.0.72/8.0.74/8.0.76 使用 `MicroMsg.C2CVideoPathFeatureService` 路径入口与服务容器；七版签名都为 `(String token) -> String path`，分别定位到 `zl0.u2.r`、`ms0.u2.r`、`mb0.c.Fh`、`jc0.c.Kg`、`we0.c.Sh`、`vf0.c.bj`、`uf0.d.vj`。路径方法和旧版单例 getter 都随 DexKit 公共缓存保存；返回路径由微信 VFS 管理且普通文件不可见时，先通过微信 VFS 输入流落地到模块缓存。
- 开启转发收藏后，微信收藏主页、独立搜索/类型筛选页及主页顶部筛选结果的外层条目长按菜单显示带微信 `icons_filled_share` 图标的 `转发[H]`，点击后与聊天消息转发一致；收藏语音保存开关开启时，同一外层语音条目同时显示 `保存[H]`。主页顶部筛选由 `FavSearchManager` 的 UIComponent 持有结果适配器，使用微信自定义菜单对象，不走 `onCreateContextMenu`；菜单定位和缓存完整性必须同时覆盖这条创建/点击链路。收藏对象解析优先复用监听器持有的位置、结果适配器及其实际 ListView，不依赖顶部筛选 UIComponent 的混淆类名。聊天页原生收藏选择器及其搜索、语音类型筛选页会在页面初始化前改写 Intent，并在收藏适配器实际重置查询前再次从排除类型集合中移除类型 `3`；同时仅在“收藏语音转发”或通用“转发收藏”任一开关开启时，对这些选择器绑定的原生发送过滤器放行 `field_type=3`，因为微信仍会在查询结果组装阶段过滤收藏语音。该过滤器返回 `true` 表示排除当前收藏项，放行语音必须强制返回 `false`。点击收藏条目时以当前行 `View.tag` 中的原生 holder 收藏对象为权威来源，再用适配器位置兜底；类型筛选后的列表位置可能存在额外偏移，不能让错误位置解析出的非语音项阻断真实语音对象识别。三列表查询方法的第一个参数是用户实际选择的 `searchTypes`，点击语音标签时必须保留其中的类型 `3`，不能把它误当成排除集合删除。类型筛选页不能再按 `key_search_type` 限制，因为原生预设类型页可使用值 `0`。收藏适配器的三列表查询方法在不同版本会混淆为 `c/d/e/f`，必须按参数签名统一 Hook；`onCreate` 完成后调用当前版本真实的无参重置方法重新查询，不能只调用 `notifyDataSetChanged()`。`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均已确认相关收藏列表与选择器结构。
- 开启朋友圈转发后，发现页朋友圈和好友个人主页朋友圈的长按菜单显示带微信 `icons_filled_share` 图标的 `转发[H]`，点击后提供同样的五项转发动作。图片优先复用微信 SNS 缓存里的大图；原图缺失时调用微信 `DownloadManager` 的四参数下载入口，以原图类型 `2` 和 `timeline` 来源场景入队，再等待大图缓存落地。视频只接受微信完整路径或下载完成路径返回且通过时长、尺寸校验的文件；缺失时调用 `SnsVideoService` 七参数任务入口并等待完成，不能把播放中的部分文件当作完整视频。原生下载不可用或未成功落地时才使用当前 `TimeLineObject` 的原图或视频 CDN 地址作最后兜底，不能回退本地缩略图，图片最多 9 张。VFS 文件统一复制到模块缓存；朋友圈视频必须另外读取微信封面或从完整视频生成 JPEG，并分别传入 `KSightPath` 与 `KSightThumbPath`。转发到朋友圈只打开微信原生编辑页，不直接发布，转发给好友、模块群发及标签发送复用现有统一发送队列。链接、小程序等其它朋友圈类型按可读取的文案和链接转为文字转发，不伪造原卡片。菜单创建通过 `MicroMsg.TimelineOnCreateContextMenuListener` / `onMMCreateContextMenu error` 定位，旧时间线点击通过 `delete comment fail!!! snsInfo is null` 等稳定字符串定位，Improve 时间线点击覆盖 `BaseImproveClick` 与 `ImproveMultiPhotoClick` 的三条回调；同一次菜单点击只允许首次成功绑定的回调消费，后续嵌套点击回调必须静默返回。`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均已确认菜单签名、两套点击链路以及上述图片/视频下载入口。七版 `TimeLineObject` 的 `ContentDesc`、`ContentObj`、`Id` 及内容/媒体类型字段 `e` 一致；媒体集合在 `8.0.58` 为 `m`、其余确认版本为 `h`，解析器按集合结构识别，不依赖声明顺序；媒体原图/缩略图字段在 `8.0.58` 为 `n` / `q`，其余确认版本为 `i` / `o`。本地路径定位使用七版均存在的 `getAccSnsPath`、`getMediaFilePath`、`getSnsBigName`、完整视频路径、下载完成路径与视频封面路径语义锚点，所有定位结果写入 `DexMethodCache`；VFS 单参数输入流在 `8.0.49` 位于 `p6`，其余确认版本位于 `w6`。
- 系统分享支持聊天文字、图片、语音、视频、存在本地文件的表情，以及文字、图片和视频收藏、朋友圈。媒体 URI 通过微信宿主的 `android.support.v4.content.FileProvider` 与 authority `com.tencent.mm.external.fileprovider` 生成，并授予临时读取权限，不能暴露 `file://`。`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 的该 Provider 均使用包含空路径 `external-path` 和 `root-path` 的 `@xml/af`，可覆盖微信媒体缓存和数据目录文件。

聊天美化：

- `消息气泡` 属于实用功能，设置页放在 `美化` 分组，默认关闭。浅色模式可分别选择左侧收到消息和右侧自己发送消息的气泡图片；开启 `深色模式单独设置` 后可再配置深色左右气泡，任一深色素材缺失时沿用对应浅色素材。图片统一通过系统 `image/*` 文件选择器读取，经尺寸和格式校验后原子复制到 Hchat 私有目录 `Hchat/message_bubbles`，支持普通 PNG/WebP/JPEG、已编译 NinePatch 以及带 1 像素伸缩标记的原始 `.9.png`。运行时复用 `MicroMsg.MvvmChattingItem` / `[onBindView]` 的统一消息绑定入口：绑定前恢复复用行的原始状态，绑定完成后重新读取 `itemView.tag` 中的当前微信 `BaseViewHolder`，避免高版本系统消息误用绑定前的旧 holder；普通左右气泡优先读取消息方向，取不到时才按当前消息行位置兜底，系统气泡不区分左右且不再被方向解析阻断。实际背景节点按微信未混淆资源语义选择：文字使用 `bkl`，语音使用 `brp/brl/bro/bs0` 状态节点，通话使用 `bs2`，红包和转账优先使用 `clickArea/bkg`；普通系统消息只使用当前 holder 的 `bkl`，高版本无 ID、无原生背景的 `ChattingItemFoldSys$ExpandTextView` 走独立目标；拍一拍类型 `889192497/922746929` 则在稳定容器 `kpw` 内逐个渲染动态创建、无 ID 且持有原生背景的 `MMNeat7extView`，支持同一行聚合的多条拍一拍记录；其它消息才回退 holder 的 `getMainContainerView()`。同一语音行允许同时替换多个状态背景，每次绑定前逐个恢复原生 Drawable 和四向 padding，关闭功能、素材缺失和 RecyclerView 复用都必须恢复原状；文字气泡优先采用自定义 NinePatch 的水平内容边距，普通图片则把微信方向相关的原水平 padding 等分，避免收到消息向右偏、发送消息向左偏。语音、红包和转账等结构化内容继续保留微信原 padding，图片、视频等没有原生气泡背景的媒体不强行添加背景。`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均确认存在该绑定入口及上述稳定资源名；横向确认的系统消息原始类型集合为 `10000`、`10002`、`570425393`、`64`、`603979825`、`889192497`、`922746929`、`268445456`、`268445458`、`285222674`、`-1879048191`、`1077936177`，气泡分类在功能内部按该原生范围识别，避免把高版本系统提示误套普通消息气泡，同时不扩大公共消息类型接口的语义。`8.0.49` 的基础 holder 为 `com.tencent.mm.ui.chatting.viewitems.f0`，其余版本为 `g0`。DexKit 只定位并缓存统一消息绑定方法，不按具体消息 holder 类名逐版硬编码。
- 气泡渲染范围严格限制为普通文本、引用文本、接龙、语音、红包、转账、通话记录、系统消息和拍一拍；名片、合并聊天记录及其它未列出的消息类型保持微信原生背景。红包、转账和系统消息支持独立气泡素材：红包与转账分别提供左右侧浅色/深色素材，居中的系统消息分别提供浅色/深色素材。运行时按原生消息类型和支付 XML 语义分类；同类深色素材缺失时沿用同类浅色素材，同类浅色素材也未设置时保持微信原生气泡，不套用普通消息素材。引用消息根据 `<type>57</type>` / `<refermsg>` 识别，并在同一消息行中按 `<title>` 显示文本锁定回复正文 `bkl`，不修改被引用内容预览。接龙消息根据 AppMsg `<type>53</type>` 或 `solitaire` 语义识别，只替换专用接龙 holder 的正文 `bkl`，不覆盖来源标签或卡片外层；`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均确认存在 `ChattingItemAppMsgGroupSolitatire` 专用渲染链。特殊素材继续按需加载，不在启动时一次解码。自定义气泡会根据实际素材亮度检查气泡范围内普通 `TextView`、`MMTextView`、`MMNeat7extView` 包装文字和链接色，只把对比度不足的基础颜色切换为黑色或白色；语音时长和状态文字即使带有微信原生子背景也参与检查，其余带独立背景的状态标签及文字内原有颜色 Span 保持微信原样。普通文字、引用正文、接龙正文、语音时长和通话记录文字已经启用独立消息文本颜色时，普通气泡不再用自动对比色覆盖用户设置；红包、转账和系统消息仍独立保证可读性。每个被修改的子文字节点都保存完整 `ColorStateList`，只在颜色仍是气泡功能写入值且未被消息文本颜色功能接管时恢复，避免 RecyclerView 复用串色或覆盖后续样式。
- `消息文本颜色` 属于实用功能，设置页放在 `美化` 分组，提供一个总开关和四个颜色配置：左侧浅色、右侧浅色、左侧深色、右侧深色，默认关闭。颜色值支持单色 `#RRGGBB` / `#AARRGGBB`，也支持 `#RRGGBB,#RRGGBB` 或 `#AARRGGBB,#AARRGGBB` 渐变。普通文字、引用消息的当前回复正文、接龙正文、`#` 话题、链接消息和语音时长通过消息行内稳定资源 `bkl` 锁定；接龙按 AppMsg `<type>53</type>`、`solitaire` 或接龙语义识别，只处理接龙卡片正文，不修改来源标签；通话记录按消息类型 `50/1000052/1000053` 识别，只处理稳定资源 `bs3` 标识的可见文字，`bs2` 仅是同级气泡背景；合并聊天记录按 AppMsg `<type>19</type>` 识别，并只处理稳定资源 `bjx` 标识的标题与 `bj2` 标识的摘要，不处理引用预览、昵称、时间、状态或消息详情。上述资源在不同版本中无论是 `MMNeat7extView`、包装 `TextView` 还是普通 `TextView/MMTextView` 都能应用颜色。所有目标统一写入默认文字色和链接色，渐变以及引用、接龙、话题和包含 `ClickableSpan` 的链接富文本额外写入文字 Paint Shader，使可点击 Span / 前景色 Span 不再覆盖用户颜色，同时避免普通纯色消息滚动时重复创建 Shader；RecyclerView 复用前分别恢复完整 `ColorStateList` 和原 Shader，且异步布局回调只允许修改仍绑定本次状态的控件。
- `首页文字颜色` 属于实用功能，设置页放在 `美化` 分组，默认关闭。开启后可分别设置标题和副标题颜色，支持 `#RRGGBB`、`#AARRGGBB` 单色及双端水平渐变，作用于微信首页的微信、通讯录、发现和我四个页签；标题覆盖会话名称、联系人名称、菜单标题等主要文字，副标题覆盖消息摘要、会话时间、账号信息和辅助文字，输入控件、按钮、角标、底栏、聊天页以及微信原生 `SettingsUI`/`setting_new.MainSettingsUI` 设置整页保持微信原样。运行时分别定位并 Hook 带 `MicroMsg.ConversationWithCacheAdapter` 的全部 `getView(int,View,ViewGroup)` 首页入口和带 `MicroMsg.ConversationAdapter` 的分组/旧会话入口，不再因先命中一个入口而漏装其它灰度分支；其中首页入口不限制混淆包名，以覆盖 8.0.66 的 `f45.s0` 和 8.0.68 的 `o75.v0`，分组入口仍限制在 `com.tencent.mm.ui.conversation.*`。8.0.72 及以上存在的独立 MVVM Holder 绑定则通过 `[getView] position=` 与 `handleShowTipCnt` 稳定字符串及 `void(int,holder,item,com.tencent.mm.storage.*)` 方法结构定位内容绑定，再从同一类中按 `void(holder,View)` 结构定位 Holder 初始化，使用弱引用缓存当前 Holder 对应的行 View。各定位结果均以方法列表或独立描述符写入 `DexMethodCache`，不依赖 `f45/o75/sd5/yf5/fh5` 等跨版本变化的混淆包名。通讯录继续使用 `MicroMsg.AddressAdapter`，发现/我共用 `MMPreferenceAdapter`，同时保留 `com.tencent.mm.view.recyclerview.WxRecyclerAdapter` 完整绑定与 payload 局部绑定 Hook；全局 Recycler 入口要求会话行同时命中标题、摘要和时间三个稳定锚点，通讯录行则要求联系人标题和辅助文字锚点，避免按宿主 Activity 粗判而误染小程序等页面。主页及分组页通过 `nickname_tv/kbq`、`last_msg_tv/ht5`、`update_time_tv/otg` 锁定标题、摘要和时间；X2C 未保留 tag 时，资源 ID `kbq` 同时计作会话和通讯录标题，再分别由会话的 `ht5+otg` 与通讯录的 `kjp` 完成行类型判定，时间按副标题色渲染。对旧式 `getView` 返回行和现代 Holder 映射行均增加一次绑定完成后的下一帧回补，不使用持续扫描。通讯录通过资源 entry name `kbq/kjp/cfx` 锁定联系人名称、辅助文字和分段标题，不硬编码跨版本变化的数值 ID。通讯录顶部固定入口通过微信自己的三参数布局 Factory 创建 Hook 单独处理，匹配新的朋友 `obc`、群聊/标签 `n9` 和企业联系人 `dgz`，公众号则按其无 ID 标题所在的 `as2` 容器识别；其中微信复用于加号菜单的 `obc` 还必须确认父链属于 `com.tencent.mm.ui.contact.*`，避免误染首页弹出菜单。固定头部只在创建完成后的单次主线程消息扫描其小型结构，不扫描整棵页面树。发现/我使用的 Preference 绑定只允许 `LauncherUI` 内页，独立 Activity 保持原样。所有绑定入口均在微信绑定前恢复复用状态、绑定完成后只处理当前行；“我”页账号头部继续由 `AccountInfoPreference` 的 Preference 绑定路径处理。会话和通讯录名称使用的微信 `NoMeasuredTextView` 不继承 Android `TextView`，运行时按控件类一次解析并缓存其文字、颜色、字号、Paint、Layout 与 `setTextColor(ColorStateList/int)` 接口，写入后立即校验真实颜色，失败时回退整数颜色接口。滚动期间不使用全局 `TextView` Hook、页面树持续扫描、全局布局或逐帧回调，颜色配置预解析为内存快照，资源 entry name、反射接口、`ColorStateList` 和渐变对象均使用小容量缓存复用；每个目标保存完整原 `ColorStateList` 与 Paint Shader，仅在属性仍保留本功能写入状态时恢复，避免串色和覆盖其它模块。`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均横向确认会话页使用上述旧式、现代或灰度并行绑定链，且标题、摘要和时间资源 entry name 稳定为 `kbq/ht5/otg`；RecyclerView 外层方法名随版本变化，但完整绑定稳定为 `void(androidx.recyclerview.widget.* holder,int)`，payload 稳定为 `void(androidx.recyclerview.widget.* holder,int,List)`，运行时按结构定位并写入 `DexMethodCache`。
- `会话时间样式` 属于实用功能，设置页放在 `美化` 分组，提供 `微信原样`、`自定义` 和 `隐藏` 三种模式，默认保持微信原样。自定义模式按消息 `createTime` 和用户填写的 `SimpleDateFormat` 格式替换微信原生时间文本；隐藏模式把原生时间控件设为 `GONE`，不保留时间控件尺寸或原时间显示间隔。两种模式都只处理微信本次绑定已经决定显示的 `timeTV`，不改变原生时间条出现频率，也不把系统消息当成普通消息详情额外渲染。模式或格式变化时恢复或刷新当前仍附着的时间控件，RecyclerView 复用后以最新原生文本和可见性重新绑定。8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 的 `MicroMsg.MvvmChattingItem` 消息绑定入口均直接读取 `com.tencent.mm.ui.chatting.viewitems.f0/g0.timeTV: TextView`。
- `消息显示时间` 的文本模板变量为 `${time}`（按时间格式生成的时间）、`${relativeTime}`（相对时间）、`${type}`（中文消息类型）、`${typeDec}`（原始消息类型十进制编号）、`${typeHex}`（原始消息类型十六进制编号）、`${msgId}`（本地消息编号）、`${msgSvrId}`（服务端消息编号）、`${atUserList}`（恢复语义后的艾特对象）和 `${mentionedUsers}`（`@我`、`@所有人`、`@N人` 等提及摘要）。艾特信息优先从消息 `msgSource` 读取；部分微信版本把它封装在 `field_lvbuffer` 中，模块按微信原生结构解析后再读取。服务端把 @ 全体展开为当前账号 wxid 时，`${atUserList}` 和点击详情显示“@所有人”，不会列成仅 @ 当前账号；`${rawAtUserList}` 继续保留原始服务器名单用于兼容旧模板，但不显示在变量选择面板。聊天列表绑定前保存 `itemView.tag` 中的微信 `BaseViewHolder`，绑定结束后继续使用该 holder 的 `timeTV`、`clickArea`、头像和主内容容器，不能重新读取已可能被消息点击监听器覆盖的普通 `tag`；消息包装对象的递归解析只按对象身份去重，不调用微信业务对象的 `hashCode()`，避免其内部可变 Map 在消息绑定期间触发并发修改异常；延迟到预绘制阶段的布局重试也沿用同一 holder。列表滚动时复用已注入视图和待执行布局回调，仅解析当前模板实际引用的字段，完整正文在点击详情时再读取，避免重复反射、XML 解析和布局重建。头像上方或下方的标签保持原字号单行显示，按整条消息行的宽度定位，不按头像父容器的内边距截断或显示省略号；头像下方仅在标签超出原消息行时补偿实际溢出高度，避免每条消息固定增加一整行间距；消息行复用时同步恢复临时调整的裁剪状态。
- 消息详情的预绘制任务按当前登记身份执行一次；取消、替换或销毁后，即使旧任务已进入本帧分发快照，也不得继续执行。消息行每次绑定都会使上一条消息的布局重试失效，定位回调同时校验标签仍属于当前消息行及父容器。宿主回收或移除标签后，旧任务不能重试或清理整行；当前标签定位最终失败时只移除该标签。底部与头像位置切换时清除旧的相对布局规则。底部标签始终保持可见，隐藏头像时只异步调整边距，不再等待回调恢复文字；缓存行滑出再滑回但未重新绑定时，通过一次附着预绘制重新校验和补入标签。新绑定、再次脱离和关闭功能会取消旧附着任务，避免旧消息回写。
- 商品和合并转发等 APP 卡片按完整消息行分支挂载纵向容器，将底部类型标签计入行高度；参考 com.qechat.wechat 的卡片容器和布局恢复方式，避免依赖内部 timeTV/clickArea。底部卡片以合并后的 GlobalLayout 回调比对有限子树与标签状态，变化时恢复标签，重新绑定、detach、关闭时取消旧任务。普通消息仍采用一次附着校验。参考包、设备安装版本及验证边界见 MESSAGE_CARD_LABEL_FIX_EVIDENCE.md。
- 商品分享卡片（原始类型 `974127153`、AppMsg 子类型 `82`）本轮仅核验本机微信 `8.0.76 (3141)` APK：DexClub 确认 `gg5.o0 → gg5.n0` 使用 `EcsProductCardViewNewStyle`，`gg5.n8` holder 将该视图放入 `FrameLayout`，并继承 `g0.getMainContainerView()` 的 `clickArea` 返回逻辑。上述修复针对模块附加类型/时间文字的列表复用与延迟定位，不修改商品卡片数据或宿主可见性。其余默认版本未提供 APK，未做本轮横向或真机验证。
- 设备 8.0.76 (3140) 的日志确认小店商品 gg5.n8 与合并转发外层卡片 gg5.t4 都因内容锚点定位失败而被模块清理标签。本轮优先按原生时间条和选择框的相对布局规则识别整行主体内容槽；不依赖 clickArea、卡片内部临时可见性或尺寸评分。标签仅在当前行内清理，避免递归父容器误删其它消息。实际 APK 与布局证据见 docs/MESSAGE_CARD_LABEL_FIX_EVIDENCE.md。
- 预绘制回归使用 `node scripts/run_message_details_predraw_tests.cjs <Kotlin编译器JAR目录>`，直接编译生产队列并模拟 observer 迁移、取消快照、视图回收与重新附着；不执行 Gradle，不替代微信真机滚动验证。
- 消息类型展示以本轮微信 8.0.76 (3141) 的聊天视图工厂及 AppMsg 映射为依据，独立区分 raw type、AppMsg 子类型和合并转发 datatype。补充负数礼物类型、拍一拍、系统通知、商品/订单、视频号等分类；未知编号保留在标签中，避免无依据命名。只解析外层 XML 的直接 type 节点，并限制长度与深度；不修改公共 WeChatMessageTypes.normalize 语义。具体映射依据见 docs/MESSAGE_TYPE_CLASSIFICATION_EVIDENCE.md。系统通知与拍一拍在原生居中内容下方显示详情，不依赖头像锚点。
- 模板包含消息类型变量时，合并转发外层卡片显示“聊天记录”，详情列表中的每一条消息按其原生 datatype 在内容下方显示类型，并继承字号和浅深色配置。详情 Hook 单独由 DexInstallScheduler 有限补装并缓存；收藏详情不注入。转发可能把原始事件转成文字，模块按收到的记录数据标记，不推测已丢失的原始类型。定位与六种行布局依据见 docs/FORWARDED_RECORD_TYPE_EVIDENCE.md。
- “消息显示时间 → 文本 → 时间显示”提供时分秒、月日时分秒、年月日时分秒、中文年月日和仅年月日五种选择，保留自定义格式输入及原有设置，保存后刷新当前消息标签。
- `输入框提示` 属于实用功能，设置页放在 `美化` 分组，默认关闭。开启后可自定义聊天输入框为空时显示的提示模板，关闭功能或模块销毁时恢复微信原提示。设置页复用中文变量编辑器，点击 `发送总数`、`文字消息数`、`文字字数`、`表情消息数`、`转账消息数`、`红包消息数`、`文件消息数` 会在光标处插入稳定占位符 `${totalMsg}`、`${textMsg}`、`${textWord}`、`${emojiMsg}`、`${transferMsg}`、`${redBagMsg}`、`${fileMsg}`，变量按钮只显示中文名称；输入框提示不提供 `发送耗时`。发送统计可单独关闭；功能和统计开关同时开启后，统计当前本地自然日内自己实际发送的完整消息集合，手动发送、模块发送和脚本插件发送均计入，文字字数按 Unicode code point 统计。首次载入会从主消息表及消息分表汇总 `isSend=1` 的当日记录并按 `msgId` 去重，后续新增和状态更新按消息 ID 增量更新内存快照，只有无法确认的删除或状态才重新完整校准，避免每次发送重扫当天全部消息；完整读取失败时保留上一份有效快照，不能把失败误判为零条。该监听限定在统计仓库内部，不扩大共享消息变更 API 的事件语义。输入框提示与发送文本格式复用同一快照。运行时直接 Hook 稳定完整类名 `com.tencent.mm.pluginsdk.ui.chat.ChatFooter#onAttachedToWindow()`，复用共享发送按钮逻辑定位真实输入接口并通过稳定的 `getHint()` / `setHint(CharSequence)` 读写，不扫描全局 `TextView`；恢复时只处理仍保留模块提示的输入接口，不覆盖微信后来写入的新提示。该完整类名、挂载方法和输入接口已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均存在，高版本输入接口无需继承 `TextView`。
- 会话时间复用 `MicroMsg.MvvmChattingItem` / `[onBindView]` 唯一定位的聊天消息绑定入口并写入 `DexMethodCache`。该入口与 `timeTV` 已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`：绑定方法依次为 `ic4.f#h`、`es4.f#h`、`d25.g#h`、`m55.g#h`、`nb5.g#h`、`od5.g#h`、`ve5.g#h`，8.0.49 的时间字段位于 `com.tencent.mm.ui.chatting.viewitems.f0.timeTV`，其余六版位于 `com.tencent.mm.ui.chatting.viewitems.g0.timeTV`。运行时不固定混淆类名，只依赖稳定字符串、方法结构和未混淆字段名。
- `隐藏头像` 属于实用功能，设置页放在 `美化` 分组，提供默认关闭的 `隐藏自己的头像` 和 `隐藏对方的头像` 两个独立开关，均同时作用于群聊和私聊。实现 Hook 微信统一聊天头像绑定方法，并在原生绑定完成后优先比较头像 wxid 与当前登录 wxid，账号信息暂不可用时才回退消息 `isSend`，避免特殊消息方向不准。头像包在 `com.tencent.mm.ui.base.MaskLayout` 内时保持锚点并把外层宽度收缩为 `0`，让文字、图片和语音等气泡向对应屏幕边缘补位；头像直接位于消息行内时使用 `GONE`，适配视频等布局。外层原始宽度按 View 弱引用保存，不隐藏或 RecyclerView 复用时必须恢复，不能直接把 `avatarMask` 设为 `GONE` 导致相对布局锚点失效。该入口已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`，七版均可由 `MicroMsg.ChattingItem` 与 `attachAvatarClickListener: getBizKfWorker:%s` 唯一定位，参数均包含头像 wxid，`avatarIV` 七版存在，`avatarMask` 从 `8.0.72` 起存在。
- `圆角头像` 属于实用功能，设置页放在 `美化` 分组，默认关闭；开启后可用滑块在 `0.10..0.50` 范围调整微信原生头像半径因子，`0.50` 为完整圆形。微信界面覆盖旧式 `MicroMsg.AvatarDrawable` 的四参数头像加载入口，以及新式包含 `workerScope` / `username` 的头像 Drawable 构造和更新入口；构造或更新使用 Kotlin 默认参数掩码时同步清除半径默认位，确保自定义值不会被还原为 `0.10`。上述入口已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`：七版均有旧式加载与新式构造入口，`8.0.49` 没有新式更新入口，`8.0.58` 起均有。通知额外 Hook Android `Notification.Builder#setLargeIcon(Bitmap/Icon)`，并在 `NotificationManager#notify` 提交前处理直接写入的 `Notification.largeIcon`；微信 `8.0.76` 的 `NotificationItem` 已确认读取该 Bitmap。微信原生通知和模块自定义通知使用同一半径处理，模块联系人选择器和语音转发选择器头像也读取同一配置。
- `自定义头像` 属于实用功能，设置页放在 `美化` 分组，默认关闭，支持真实好友、普通群聊和企业微信群聊。图片必须通过系统 `image/*` 文件选择器获取，解码校验后按目标 wxid 的 SHA-256 文件名原子写入 Hchat 私有目录 `Hchat/custom_friend_avatars`；只维护 Hchat 自己的头像映射，不修改微信联系人或群聊头像字段。用户可分别控制聊天消息、会话列表、通讯录、资料页、朋友圈、其它微信界面、桌面快捷方式、消息通知和朋友圈通知，并可从设置页、微信首页或聊天分组二级页的好友及群聊会话长按菜单设置、更换和恢复微信头像；设置页复用好友与群聊通用选择器，点击确定后直接返回功能主页。首页会话菜单复用公共 `ConversationMenuLocator` 且按本次 `MenuItem` 精确绑定真实好友或群聊，聊天分组页通过公共会话菜单扩展注册表复用同一显示条件和点击动作；Hchat 虚拟聊天分组仍使用自身独立头像配置，不进入本功能的已设置列表。头像渲染覆盖旧式四参数 `ImageView` 加载入口和新版头像 Drawable 的最终 `draw(Canvas)`，新版对象按构造/更新时的 username、生命周期页面和原生圆角参数弱绑定，避免异步原生头像覆盖和列表复用串台；群消息行中的成员头像仍按成员 wxid 渲染，设置群聊头像不会把每个群成员的消息头像替换成群头像。桌面快捷方式在微信加密目标用户名之前的 `ShortcutManager` Intent 构建入口中按真实 wxid 替换 `android.intent.extra.shortcut.ICON`，沿用微信原图尺寸和自适应图标标记，好友与群聊走同一路径；定位使用七版共有且唯一的 `MicroMsg.ShortcutManager`、`getScaledBitmap fail, bmp is null` 和 `com.tencent.qlauncher.extra.EXTRA_PUSH_ITEM_UNIQUE_ID`，并继续校验静态 `(Context,String,boolean,String):Intent` 签名，结果写入运行时版本缓存。该入口及其低版本 `INSTALL_SHORTCUT`、高版本 `requestPinShortcut/updateShortcuts` 分发链已横向确认微信 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`；桌面 Hook 使用独立安装任务，定位失败时不影响已有界面头像。已存在的桌面快捷方式由系统启动器持有静态图标，需要重新添加后才会应用当前自定义头像。圆角头像功能关闭时保留微信本次绑定的原生弧度，开启时使用用户设置的弧度覆盖。消息通知同时覆盖 Hchat 通知和微信原生通知：复用原生通知构建链已经写入的 talker，在 `NotificationManager.notify(...)` 提交前替换公开 Bitmap、内部 Icon 与通知 extras 的大图标；`NotificationItem` 标记不能再依赖自定义通知总开关，避免关闭自定义通知后原生通知无法关联目标。无法对应已配置好友或群聊的 talker 保持微信原头像，不按最近消息猜测发送者。原有功能 ID、`configured_friends` 配置键和头像目录继续保留，已有好友头像无需迁移。
- 聊天正文用当前消息正文做匹配后再上色，普通文本必须先走原始正文主路径，目标限定在当前行根 View 内的 `NeatTextView/MMNeat7extView` wrapped `TextView` 或控件自身 `TextView`；引用主正文和 `#` 话题先按候选正文匹配，仍未命中时只在当前消息行内选择可见的主正文 `NeatTextView/MMNeat7extView`。语音仅处理时长 `bkl`，通话记录仅处理文字控件 `bs3`，合并聊天记录仅处理标题 `bjx` 和摘要 `bj2`，不能回退扫描当前消息行的其它普通 `TextView`，避免把群昵称、时间、状态或其它模块插入内容误染色。
- 渲染入口复用聊天消息行 `MicroMsg.ChattingDataAdapterV3` 的 `_onBindViewHolder` 绑定方法并写入 `DexMethodCache`；绑定后递归拆出 `com.tencent.mm.storage.*` 原生消息对象，只处理文本消息、引用消息主正文、语音时长、通话记录文字和 AppMsg 类型 `19` 的合并聊天记录标题与摘要。群聊消息匹配时使用 `WeChatMessage.bodyContent()` 去掉 `sender:\n` 前缀，引用消息匹配主正文 `title`，匹配前会清理话题 `#` 文本常见的不可见字符和占位符，避免误染被引用预览、昵称、时间或其它卡片文本。
- 渐变色通过目标 `TextView` / `TextPaint` shader 渲染，RecyclerView 每次绑定前必须清理本功能设置过的 shader 和纯色标记，避免 View 复用导致颜色残留。不要按固定资源 ID 或固定控件层级找消息正文，微信不同版本文本消息布局会漂移。

删除键清引用：

- `删除键清引用` 属于实用功能，设置页放在 `聊天` 分组，提供一个独立开关，默认关闭；不要把它塞进 `滑动手势` 页面。
- 开启后，优先读取按键来源输入框的真实文本；输入框为空时消费删除键并取消当前引用，输入框有文字时必须放回微信原始删除逻辑。不要把 `ChatFooter#getLastText()` 当作唯一判断来源，否则引用源异常时可能放行微信原逻辑并弹“引用内容已撤回”。
- 清空必须直接调用微信原生关闭引用栏方法，和点击引用栏 `X` 的路径一致，即 `ChatFooter` 的双 boolean 方法传 `false,true`；成功后不要再叠加手动字段清理。运行时要维护当前已附着的 `ChatFooter` 引用，避免软键盘删除时从输入框父级临时找不到 footer。
- 删除键入口必须同时覆盖软键盘和硬键盘：软键盘通常走 `TextView#onCreateInputConnection(EditorInfo)` 返回的 `InputConnection.deleteSurroundingText(...)` / `sendKeyEvent(...)`，只在父级能找到 `ChatFooter` 的输入框上包一层；硬键盘或部分输入法再参考 WeKit 的 `快捷清除引用`，定位微信 `ChatFooterKtHelper` / `supportAutoComplete err` 对应的 `View.OnKeyListener#onKey(View,int,KeyEvent)`。该 `onKey` 入口已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74` 都存在，但不能作为唯一入口。
- 原生清引用方法横向确认：`8.0.49` 是 `ChatFooter.M0(ZZ)`，`8.0.58` 是 `ChatFooter.Y0(ZZ)`，`8.0.66` 是 `ChatFooter.c1(ZZ)`，`8.0.68` 是 `ChatFooter.d1(ZZ)`，`8.0.72` / `8.0.74` 是 `ChatFooter.v1(ZZ)`。`8.0.66+` 可用 `handleQuoteMsgFillingFrom` 定位；`8.0.49` / `8.0.58` 用 `openim_card_type_name` + `err_not_started` 定位。定位不到时不要消费删除键，不要改走手动字段清理。

屏蔽消息：

- `屏蔽消息` 属于实用功能，开启后只按启用的模板规则在 AddMsg 处理方法入库前拦截新收到的消息，命中后直接结束微信原处理流程，使消息不写入 message 表、不显示在聊天/会话列表，也不触发微信新消息通知。不要用消息观察回调后删库代替，否则通知和会话刷新可能已经发生。

- 聊天分组右上角菜单提供 `批量屏蔽消息`，可在当前分组及全部子分组的真实会话中多选并批量屏蔽或解除。该操作为联系人绑定写入独立的“快捷屏蔽全部消息”标记，并自动开启屏蔽消息总开关；已有模板、专属类型、关键词、排除和启停配置继续保留，解除时只移除快捷标记，不覆盖原有规则。快捷屏蔽仍复用 AddMsg 入库前拦截链路，不删除已有聊天记录。
- AddMsg 入口复用公共 `DexFinder.addMsgClasses` 和 `WeChatMessageParseApi`，只 hook 返回 `void` 且参数中包含 AddMsg 对象的方法，并用最高优先级 before hook 在其它 Hchat 消息观察回调前中断；已确认 `processAddMsg` 入库入口为 8.0.49=`com.tencent.mm.plugin.messenger.foundation.u0#c(... )V`、8.0.58=`u0#c(... )V`、8.0.66=`n1#c(... )V`、8.0.68=`u1#c(... )V`、8.0.72=`a2#c(... )V`、8.0.74=`a2#c(... )V`。设置默认关闭；总开关打开但没有启用模板时不会屏蔽任何消息。
- 屏蔽消息命中后，如果同一条消息命中关键词通知的关键词类规则（包含关键词列表或已开启的任意关键词群聊/私聊规则），必须放行微信原处理流程让消息正常入库，由关键词通知后续通过公共消息观察链路提醒；未命中关键词但仍被屏蔽的消息才在结束微信原处理流程前发布模块内部 `MessageBlocked` 事件，只给关键词通知等明确订阅该事件的模块内功能使用。不要把被屏蔽消息重新派发到公共消息观察 API，否则会让自定义通知、脚本插件和其它观察者也收到已屏蔽消息，破坏功能隔离。
- 设置页一级只保留总开关、`默认私聊规则`、`默认群聊规则`、`默认公众号规则`、`模板管理` 和 `名单管理`。默认规则默认关闭，按“名单专属规则优先，未配置名单再走默认规则”的顺序匹配；默认私聊规则覆盖未单独配置的私聊，默认群聊规则覆盖未单独配置的群聊，默认公众号规则覆盖未单独配置的公众号。历史迁移、快捷屏蔽恢复或名单选择产生的空名单记录如果没有模板、专属规则和排除动作，视为未配置并继续跟随对应默认规则；关闭的名单、排除名单和绑定到不存在模板的名单仍作为明确的名单级覆盖，不回落默认规则。删除模板会同步清除名单及三个默认规则中的引用；默认规则因此不再引用任何模板且没有专属规则时同步关闭，避免留下表面启用但永远无法命中的配置。模板管理二级页只配置模板名称、启用状态、消息类型和文字关键词；模板不再保存好友、群聊、公众号或群成员名单。名单管理二级页负责添加好友、群聊、公众号或群成员，并给每个名单项分配一个或多个模板；批量添加多个名单时必须先进入批量配置页选择模板、启用状态和屏蔽/排除动作，再一次性保存，不能直接写入未分配模板的名单。单个名单项也可以开启专属规则，直接覆盖所选模板的消息类型和关键词，避免为少量名单临时新增模板。列表支持按昵称、ID、模板名和专属规则搜索，并支持进入批量删除模式选择部分名单或全选当前筛选结果，确认后才持久化删除；同一个名单项只保留一条记录，重复添加时打开或合并到已有记录，避免同一名单散落到多个模板里难以查找。旧模板里的指定名单、指定群成员、排除名单和排除群成员会在打开设置页时迁移为名单管理记录，并清空模板内旧名单字段。
- 名单匹配支持按会话 talker 或实际发送者 sender 匹配，群聊可选择整个群，也可选择成员全局屏蔽该成员；需要限定某个群时使用群成员名单，内部格式固定为 `群ID/成员wxid`，匹配时要求 talker 和 sender 同时命中，因此不会因为该成员也是好友就屏蔽私聊。群聊发送者优先取本次消息正文的 `wxid:\n` 前缀，前缀缺失时才回退 AddMsg 发送方和 XML，不能让引用内容内部的 `fromusername` 覆盖本次发送者。名单可设为排除名单，排除名单优先级最高。类型判断复用 `WeChatMessage` 分类，支持所有消息、文字、引用消息、图片、小视频、语音、文章/链接、音乐、小程序、名片、动画表情、红包、转账、视频/语音聊天、地图位置、系统消息、拍一拍、视频号链接和未知类型。文字类型同时覆盖带非空外层 `<title>` 的引用回复，不按被引用对象的 `<refermsg><type>` 决定本次回复是否为文字；文字关键词匹配本次引用回复的外层标题，不匹配被引用正文；“引用消息”类型仍覆盖所有引用内容。历史配置如果已经勾选此前全部显式类型，会自动补上未知类型，避免微信新增或未归类的原始类型直接漏过。多个关键词按 `|`、逗号或换行分隔；关键词为空时文字类型命中全部文字及文字引用，开启“所有消息”且关键词非空时，文字消息仍必须命中关键词，非文字继续按所有消息处理。
- 名单管理里的好友、群聊、公众号和标签好友使用共享 `ContactPickerPage`，群成员仍使用群成员选择页。通用好友选择器只保留微信好友和企业微信联系人，不把单向联系人或群成员混入公共候选；所有选择器按微信 `rconversation.conversationTime DESC` 的原生会话新旧顺序排列，没有会话记录的联系人放在末尾，已保存项仍只在进入当前筛选结果时置顶。标签分组只用于先选标签再勾选标签下联系人，最终仍写入联系人 wxid、公众号 ID 或群号，不把标签 ID 写入运行规则。

自动回复：

- `自动回复` 属于实用功能，设置页放在 `聊天` 分组，默认关闭；迁移自脚本插件 `自动回复` 的核心能力，不迁移日志、备份和旧式脚本弹窗。
- 普通消息自动回复通过公共消息观察 API 处理收到的非自己新消息；数据库兜底只消费 `message` 表新增候选，已读、媒体下载和状态变化等旧消息更新不得再次触发，`replace/upsert` 还必须晚于本次观察启动边界并通过五分钟创建时间校验和消息 ID 去重。进入规则前同时检查观察事件方向和当前账号 wxid；非数据库事件在账号 wxid 尚未就绪时直接跳过，避免自己发送或模块发送被误判为入站消息。规则支持模糊、全字、正则和任意消息匹配；模糊、全字和正则只匹配文字，任意消息会匹配图片、语音、视频、表情、文件、位置、卡片等非系统入站消息，排除关键词只对文字内容生效。规则支持私聊、群聊、公众号、指定 wxid/群号/群成员、排除名单、@我、@全体、拍一拍、时间段、最大回复次数和独立回复冷却时间；`仅私聊` 必须排除 `gh_`、`@app`、`newsapp` 等公众号会话，且切换离开“指定名单”后旧名单不再参与匹配，公众号只由全部聊天、仅公众号或明确指定名单规则处理。冷却时间按规则和会话分别计算，从该规则至少成功发送一步回复后开始计时；冷却期间同一会话再次命中该规则不会回复，不同规则或不同会话互不影响，`0` 秒表示不限制，微信进程重启后重新计时。全局“排除指定会话”默认关闭，开启后才显示会话 ID 输入框；支持按逗号、分号或换行配置多个 ID，当前会话精确命中时整条消息不进入好友通过欢迎语和普通自动回复规则。时间段使用统一时、分、秒选择器并按秒匹配，旧 `HH:mm` 配置自动按 `HH:mm:00` 兼容。回复步骤按顺序发送，类型包含文字、图片、语音、随机语音文件夹、表情、视频、文件、收藏、名片、邀请进群、XML、智聊AI回复、小智AI回复和小智语音回复；其中语音回复必须同步切到主线程调用公共语音发送接口。
- 设置页交互对齐脚本的点选习惯：规则指定名单和排除名单支持分别选择好友、群聊和指定群成员，不再暴露 wxid、群号或成员 ID 手动填写框；回复步骤里的文字/XML 提供可点击变量，文字回复支持 `%atSender%` 艾特发送者和 `%atAll%` 艾特所有人，后者使用微信原生 `notify@all` 消息来源发送真正的群聊艾特；图片、语音、表情、视频和文件通过系统文件管理器选择，收藏通过最近收藏选择页选择，名片通过好友选择器选择，邀请进群通过群聊选择器选择。系统文件管理器选出的媒体会复制到模块私有缓存后再发送；随机语音既兼容旧文件夹路径，也支持从已选语音文件里随机发送。
- 最近收藏选择页按微信收藏协议展示各类型的原生标题：文件优先显示 `datatitle`，链接优先显示 `pagetitle`，文字、音乐、位置、文件、小程序、聊天记录、笔记和视频号等类型读取微信对应收藏适配器使用的协议字段，再以 XML 语义标题和正文兜底。收藏标签使用 `tagProto.f` 的实际标签 ID 批量查询 `FavTagInfo` 补齐名称，同时合并微信原生适配器保留在 `tagProto.e` 的用户自定义标签名称；不把 `tagProto.d` 的推荐标签显示为已添加标签，因此顶部搜索可以按真实标签名称命中收藏。
- 自动回复规则管理页只保留规则搜索，不显示会话分类 Tab；支持选择多条规则、全选当前搜索结果并在确认后批量删除。规则编辑、回复步骤编辑和群成员选择这类二级/三级页面返回时要保留原列表滚动位置，不能因为进入选择器后返回就跳回顶部。
- 好友请求处理复用脚本层已经验证过的 `fmessage_msginfo` 好友申请解析与验证用户名映射，原生设置里可开启自动同意、延迟欢迎回复、自动标签和自动备注。自动标签支持固定“新加好友”、按日期格式生成标签以及选择已有微信标签；自动备注支持固定文本、日期、自定义文本和拼到微信昵称后面。好友备注在 8.0.72/8.0.74 优先通过微信 `setcontactproperty` 同步接口发包，旧版本走微信联系人存储原生保存入口，再刷新本地联系人表，不做只改本地数据库的假备注。“对方通过我的好友请求后欢迎语”检测微信固定验证通过文案后发送独立回复步骤，也支持同款自动标签和自动备注配置，配置项与好友请求处理互不覆盖。
- AI 配置二级页只保留 `小智AI配置` 和 `智聊AI配置` 两个入口，分别进入三级页配置对应参数。小智AI配置管理 WebSocket、OTA、控制台地址和设备绑定；也支持登录 `xiaozhi.me` 控制台，短信验证码登录后拉取智能体、模型和语音角色，并可把当前模型/语音角色保存回选中的智能体。小智AI回复固定为文字回复，原“小智语音回复”步骤仍作为独立回复步骤保留。语音角色会随小智 `hello`、`listen/detect` 和语音输入状态请求下发；小智配置支持官方 Music 提示、本地 MCP 桥接、MCP ready 等待时长、空闲断开时长、本地点歌插件 ID/函数调用和运行中上下文清空。智聊AI支持多配置、当前配置切换、新增、复制、删除、模型列表拉取、模型搜索、模型收藏、流式开关、保存后清空上下文和连通测试。智聊的配置列表和模型选择必须使用独立三级页，点选条目只更新待选状态，不自动返回，需由底部按钮确认生效；模型选择页交互按联系人选择器风格组织，搜索框位于模型列表顶部，收藏在每个模型条目内操作。运行时智聊AI回复读取当前智聊配置，并同步兼容旧单配置字段；流式失败会尝试非流式，非流式失败会尝试流式。小智AI回复读取小智 WebSocket 地址，并按服务地址、控制台智能体和微信会话生成并固定复用本地 `session_id`，不再用服务端每次 `hello` 返回的新 `session_id` 覆盖本地会话，让服务端能延续同一聊天的上下文；同一聊天在配置不变且没有未完成回复时复用 WebSocket，空闲约 90 秒后关闭，减少重复握手耗时。按小智设备协议先发送 `hello` 并等待服务端 `hello`，10 字以内短文本直接发送 `listen/detect`，超过 10 字的长文本用 Android TTS 合成临时 WAV、转 16k 单声道 PCM、通过系统 `MediaCodec` 编码为 Opus 帧，再按官方 `listen/start` + 二进制音频帧 + `listen/stop` 音频输入链路发送。文字回复收到 `tts/sentence_start` 后立即发送，避免等待完整 TTS 停止；小智语音回复按 `tts/sentence_start`/`tts/sentence_end` 分段收集二进制 Opus 帧，每段封装为临时 Ogg Opus 后用 Android `MediaExtractor/MediaCodec` 解码为 PCM，再通过 `SilkCodec.pcmToSilk` 转成微信 Silk 并逐条走公共 `sendVoice` 发送；如果设备系统无法解码服务端 Opus 导致没有可发送语音段，但已经收到文字答案，则使用本机 Android TTS 把答案合成 WAV、转 16k PCM/Silk 后发送，兼容 Android 9 等系统 Opus 能力不稳定的设备；`tts/stop` 后会短暂等待后续分段，避免服务端连续多段 TTS 时只发送第一条。`listen/detect.text` 在 ESP32 官方协议和客户端源码中是唤醒词事件，官方 `xiaozhi.me` 服务会拒绝长文本 detect，因此不能作为长文本兜底。

聊天分组：

- 原生二级页右上角设置中的 `分组排序` 调整当前分组内每条直属会话的位置，首次调整会保存当前完整顺序，之后新消息不会改变已固定会话的位置，自动归拢产生的新会话稳定追加到对应区域末尾；`恢复默认排序` 会清除固定顺序并重新跟随微信原生时间排序。固定排序保留置顶、普通、置底三区域语义，各区域内部按用户顺序显示。原来调整同级分组顺序的入口统一改名为 `分组位置`，避免与会话排序混淆。
- `聊天分组` 属于增强功能，设置页放在 `增强` 分组并默认关闭。配置按当前微信账号隔离；每个会话最多直属一个自定义分组，分组可继续选择上级分组形成多级树，但不能选择自己或后代作为上级，同一层级不允许重名。管理页支持搜索、新增、重命名、移动、同级自定义位置、会话多选以及作用于当前搜索结果的全选/取消全选；编辑已有分组时可通过“分组位置”执行移到顶部、上移、下移或移到底部，置顶分组与普通分组分别排序，新建或更换上级的分组默认追加到目标层级末尾。微信首页真实会话和虚拟分组会话的原生长按菜单增加 `聊天分组`：真实会话可直接选择目标分组，选择后自动解除原分组归属；虚拟分组可选择新的上级分组或移回微信首页，候选排除自身及后代以避免循环。该弹窗同时提供新增、全部删除、删除多个、导入和导出，导入导出只处理当前微信账号的聊天分组配置。删除分组不会删除联系人、群聊、聊天记录或微信会话：直属会话移动到上级分组，根分组的直属会话恢复到微信首页，子分组提升到被删分组的上一级。配置读取会修复无效父级、循环父级、重复会话归属和异常排序。
- 聊天分组主页额外提供独立的 `群聊归拢` 三级设置页。用户先选择目标聊天分组，再开启自动归拢规则；规则支持所有群聊、新群聊、指定群聊、免打扰群聊、自己创建的群聊、企业微信群聊、Hchat 群聊标签，以及公众号的包含/排除名单。自动结果只在运行时计算，不写入手动 `conversationIds`，多个规则命中时按分组自定义顺序取第一个归属；公众号排除名单只排除自动候选，不覆盖用户手动放入分组的会话，关闭规则会恢复会话原有 `parentRef`。新群聊规则按当前微信账号记录已见群聊 ID，首次启用只为已经存在 `rconversation` 的群聊建立基线，后续新群的原生父级写入成功后才标记为已处理，避免联系人先于首页会话出现时漏归拢；导入聊天分组配置后会清空当前账号的旧基线并重新建立。群聊和公众号选择器直接读取完整联系人集合，但原生父级批量更新只处理已经存在 `rconversation` 首页会话行的候选，尚无会话的联系人不计为更新失败，后续生成真实会话时再由监听同步归拢。配置变更、真实会话变更、联系人变更或群资料变更后通过单线程同步重新计算原生 `rconversation.parentRef`、未读聚合、最新消息和头像；虚拟联系人、头像和聚合会话行只在目标字段真实变化时写入，并且只对发生变化的分组发送一次微信原生会话刷新，避免自动归拢监听到密集事件后反复触发首页排序而使分组入口闪烁。模块自身写入的虚拟会话与纯父级更新不会再次触发同步。
- 首页分组入口和被归拢会话使用微信原生 `rconversation.parentRef` 关系；点击分组启动微信“折叠群聊”使用的原生 `ConvBoxServiceConversationUI` 二级会话列表，并把 `Contact_User` 设置为对应的 `wxid_hchat_group_*`。仅虚拟分组页面刷新时，模块把折叠适配器内部写死的 `conversationboxservice` 父级替换为当前分组 wxid，并放开原有的群聊类型过滤；微信真实折叠群聊页面保持原样。子分组仍作为虚拟会话显示，点击后继续进入同一原生二级列表；普通会话点击复用微信原生 `startChatting`，列表、头像、未读、时间和返回栈均由微信原生界面处理。
- 开启后，每个非空分组在微信本地建立一个确定性的 `wxid_hchat_group_*` 虚拟联系人和虚拟会话，联系人 `type=0`，不得进入通讯录或普通好友选择器；根分组虚拟会话的 `parentRef` 为空，子分组虚拟会话挂到上级分组 wxid。真实会话的 `rconversation.parentRef` 改成直属分组 wxid，使微信原生首页只显示根分组入口。写入真实会话父级必须复用微信 `ConversationStorage` 的原生批量更新入口，由微信在落库后发送会话变更通知，高版本同时通知父容器刷新，不能裸写数据库后等待首页下次重建。写入前按账号保存每条真实会话原 `parentRef`，关闭功能、移出分组或删除根分组时确认数据库更新成功后恢复原值，不能清空微信已有的折叠等父级状态；模块创建的失效虚拟会话走原生删除入口清理，虚拟联系人同步删除。微信首页原生长按菜单对虚拟分组执行置顶或取消置顶后，模块 Hook `ConversationStorage` 的 `setPlacedTop`/`unSetPlacedTop` 成功入口并同步持久化分组的 `pinned` 配置，后续进入、退出二级页或聚合刷新不会覆盖原生置顶状态；8.0.49 至 8.0.76 的原生置顶位均为 `1L << 62`。
- 微信原生分享页的“最近聊天”和“最近转发”不显示 `wxid_hchat_group_*` 虚拟分组入口；刷新 `NewRecentConversationAdapter` 时保留微信原根会话查询，再在当前线程用同参数执行一次取消根 `parentRef` 限制的展开查询，只把 Hchat 分组内的真实会话按展开 Cursor 的原生顺序并回结果，避免同时暴露微信自己折叠或归拢的其它会话。搜索、完整联系人页、选择状态、确认弹窗和发送链路保持微信原生行为。该适配器刷新入口在 8.0.49 为 `t6#r`，8.0.58 为 `u6#s`，8.0.66/8.0.68 为 `o6#s`，8.0.72/8.0.74 为 `p6#s`，8.0.76 为 `p6#r`；最近转发查询分别为 `v4#u`、`a5#t`、`n3#t`、`m4#t`、`m4#t`、`l4#t`，签名统一为 `(List,int,List,String,boolean,String):Cursor`。运行时使用 `MicroMsg.NewRecentConversationAdapter`、`resetData: recent forward control switch on`、完整会话查询前缀和 `order by case rconversation.username` 定位并按微信运行时键缓存，不硬编码混淆名称。
- 虚拟分组会话聚合所有后代的会话数、实际未读数、免打扰会话未读数、最新消息时间和最新消息预览字段，并使用最新后代的会话状态参与微信原生首页排序；开启最新消息预览时，聚合行使用 `%s` 与 `digestUser` 让微信原生按最新会话名称显示“会话名: 预览内容”，同时保留 `content`、`msgType` 等字段供文字、图片、语音、红包、引用等消息类型渲染。首页和原生二级页在保留真实会话原生位置的前提下，只重排同级虚拟分组占用的位置；已置顶分组优先，同一置顶状态内按自定义顺序显示，因此排序不会改动真实会话的置顶、时间或相对顺序。分组头像在开启最新消息预览时跟随最新会话头像，已设置自定义头像时由自定义头像优先；关闭预览后使用稳定的分组会话数摘要并清除自动头像。自动头像沿用微信 `img_flag.reserved1/reserved2` 的高清、小图 URL 语义，并在头像加载入口把虚拟分组的缓存键临时切换为当前最新真实会话 wxid，避免微信按固定虚拟 wxid 复用首次加载的旧头像；最新会话变化后会使用新的缓存键重新加载。聚合行写入完成后复用微信 `ConversationStorage` 原生父级更新入口，对虚拟 wxid 发送会话变更通知，确保子会话已读、切换未读数模式或分组内真实会话改变免打扰状态后，微信首页外的分组未读数立即刷新。每个分组可独立设置主页置顶、自定义头像、命名、未读数模式、是否预览最新消息和是否显示无消息的空分组；未读数模式提供 `全部未读`、`免打扰不显示未读` 和 `不显示未读`，分别统计全部实际未读、排除微信当前开启免打扰的会话未读或隐藏虚拟分组入口数字，不改真实子会话未读状态。实际未读数兼容微信把免打扰未读写入 `unReadCount` 或 `unReadMuteCount` 的差异；普通联系人免打扰读取原生联系人状态，群聊则解析 `rcontact.lvbuff` 中的 `ChatRoomNotify` 持久状态，值为 `0` 时排除该群聊未读，状态入口暂未就绪时只对当前存在正数 `unReadMuteCount` 的会话作兜底。联系人状态变更会为使用排除免打扰模式的手动分组触发合并重算；无法从更新值直接取得 wxid 时再从带 `username` 条件的 `whereArgs` 恢复，不要求分组同时开启自动归拢。虚拟联系人更新返回零行但目标记录仍存在时按幂等成功处理，不再把未变化的 `rcontact` 行误报为写入失败。旧版“显示未读数字”开启配置迁移为 `免打扰不显示未读`，关闭配置迁移为 `不显示未读`，新建分组默认使用 `全部未读`。单击虚拟分组会话由模块拦截并进入微信原生二级会话列表；首页长按继续显示微信原生会话菜单并追加 `聊天分组`，二级页普通会话保留微信原生长按菜单，并在对应入口定位成功时追加当前分组专属的 `移出`、`移至`、`置顶聊天/取消置顶` 和 `置底聊天/取消置底`；置顶与置底互斥，只改变当前分组的临时 Cursor 顺序，不写回真实会话的全局 `rconversation.flag`，其中置顶沿用微信原生 `1L << 62` 标志显示置顶背景，置底排在当前分组普通会话之后。所有向微信首页会话长按菜单注入功能项的模块功能还必须注册 `ConversationMenuExtension`，聊天分组二级页按同一显示条件、标题和点击动作动态追加已注册项，自动归拢但不在手动 `conversationIds` 中的会话也允许使用这些通用扩展；当前覆盖快捷设置备注和标签、快捷查看朋友圈及自定义头像，不再在聊天分组内手工复制单个功能逻辑。该附加菜单定位失败时保留微信原生长按菜单，不影响聊天分组主体安装。长按子分组则直接打开同一聊天分组管理弹窗。子分组和直属会话保留微信原生 `flag`、会话时间排序，当前分组的置顶会话在最前、置底会话在最后，点击子分组继续进入下一级原生列表，点击真实会话仍走微信原生聊天入口。账号或会话存储尚未就绪时保留变更并在首页查询捕获真实存储实例后重试；收到已分组会话的新消息后重新聚合虚拟会话，密集变更通过单线程脏标记合并。
- 设置页和分组管理弹窗的新建聊天分组界面均提供 `主页置顶` 选项，默认关闭；开启后新分组保存到当前层级的置顶分组区域，之后仍可通过分组菜单取消置顶。
- 使用 `免打扰不显示未读` 的分组同时监听联系人状态和群资料变化；群聊开关写回 `ChatRoomNotify` 后会立即重新聚合虚拟分组，不需要等待下一条消息触发刷新。
- 虚拟分组原生二级页面右上角通过各版本稳定存在的 `MMFragment#addTextOptionMenu(int,String,OnMenuItemClickListener)` 增加 `菜单`。菜单提供 Hchat 模块入口、所有消息标为已读、批量删除消息、开启/解除消息免打扰、批量发送文字、图片、语音、视频、表情、文件、收藏、名片或 XML、发送群聊邀请、添加会话、移出、移至、搜索和分组显示设置；显示设置包含主页置顶、自定义头像、命名、分组内会话固定排序、同级分组位置、三态未读数模式、最新消息预览和空分组显示。操作范围默认使用页面当前生效的会话集合，包含手动加入和自动归拢的会话，并递归包含全部子分组；只有移出和移至处理当前分组直属手动会话。`添加会话` 打开的 `选择分组会话` 选择器使用通用分类，支持好友、群聊、公众号、标签和全部，也可按已有聊天分组及其子分组筛选真实会话；进入标签分类后可按具体好友标签筛选，搜索和全选/取消全选仅作用于当前分类、标签及聊天分组筛选的交集。会话数据在后台读取；读取成功、失败、取消或 Activity 销毁时都先关闭加载层，成功时再通过 decor 的下一帧回调打开选择器，避免透明全屏遮罩残留并吞掉触摸。批量删除消息先多选并二次确认，再提交微信 `MsgInfoStorageLogic` 的 Stage1/Stage2 原生批量清理入口，不直接 SQL 删除消息；该入口在 8.0.49 至 8.0.74 为 `(List,callback,long)`，8.0.76 为 `(List,callback)`，使用 `summerdel deleteMsgByTalker` 与 `AsyncDeleteMessageStage1` 锚点定位并写入运行时版本缓存。私聊免打扰使用微信 `setMute/unSetMute` 原生联系人入口，群聊使用 RoomSDK 的 `notifyMsg=0/1` 操作并调用原生 `factory.a#b()` 提交；运行时只缓存定位结果，不按混淆类名分支。批量发送和邀请在后台单线程按有界小批次执行，目标间保留间隔，不在主线程一次提交全部目标；收藏发送结果表示请求已提交，语音收藏仍可能继续等待微信下载。
- 首页查询入口已横向确认：8.0.49 为 `com.tencent.mm.storage.v4#D`，8.0.58 为 `a5#D`，8.0.66/8.0.68 为 `n3#D`，8.0.72 为 `m4#D`，8.0.74 为 `m4#C`，8.0.76 为 `l4#C`，签名均为 `(int,List,String,boolean,String):Cursor`；查询通过 `rconversation,rcontact` 内连接生成原生会话对象，高版本首页适配器再从内部会话映射取对象，因此不能仅包装 Cursor 注入临时行，虚拟 wxid 必须同时存在于 `rcontact` 与 `rconversation`。查询共同只放行空 `parentRef` 与 `message_fold` 等微信内置父级，因此直属分组 wxid 能稳定隐藏真实子会话。原生父级更新入口在 8.0.49 为 `v4#Q(String[],String)`，8.0.58 为 `a5#S(String[],String,boolean,boolean)`，8.0.66/8.0.68 为 `n3#S(...)`，8.0.72 为 `m4#R(...)`，8.0.74 为 `m4#Q(...)`，8.0.76 为 `l4#Q(...)`；共同使用 `Update rconversation set parentRef = '` 与 `' where 1 != 1 ` 字符串，可稳定定位并由其发送原生会话变更通知。首页点击入口在 8.0.49 为 `i2#onItemClick`，8.0.58/8.0.66/8.0.68 为 `r2#onItemClick`，8.0.72/8.0.74/8.0.76 为 `w2#onItemClick`。运行时不硬编码这些混淆名，而是使用完整首页 SQL、父级更新 SQL 与 `MicroMsg.ConversationClickListener`/`null user at position = ` 锚点定位查询、更新和点击方法，并把描述符写入 `DexMethodCache`；虚拟分组长按不再安装模块拦截，直接由微信原生长按监听生成会话菜单。
- 原生二级列表入口在 8.0.49 至 8.0.76 均为 `ConvBoxServiceConversationUI`，内部 Fragment 为 `ConvBoxServiceConversationFmUI`；其 `onActivityCreated(Bundle)` 通过 `FragmentActivitySupport#getStringExtra(String)` 读取 `Contact_User`，该方法在 Fragment 尚未被标记为当前 Activity 时会忽略 Activity Intent，因此模块仅对虚拟分组 Fragment 的 `Contact_User` 读取回灌所属 Activity Intent 中的虚拟 wxid。折叠适配器刷新方法内部固定查询 `conversationboxservice`；适配器刷新与四参数查询在 8.0.49 为 `u0#H`/`v4#t`，8.0.58 为 `v0#q`/`a5#s`，8.0.66/8.0.68 为 `s0#r`/`n3#s`，8.0.72 为 `t0#r`/`m4#s`，8.0.74 为 `t0#q`/`m4#s`，8.0.76 为 `t0#p`/`l4#s`。运行时不硬编码混淆名，而是使用 `conversationboxservice` 与适配器 `(Context,String,callback)` 构造特征定位并缓存刷新方法，通过弱引用记录虚拟页面与适配器的对应关系；只有该适配器同步刷新期间，才把四参数查询的父级替换为虚拟 wxid 并将类型参数改为 `0`。虚拟页面刷新后读取原生适配器数量，并精确调用 `BaseConversationFmUI#setMMTitle(String)` 显示为 `分组名 (会话数)`，避免误选同名 `CharSequence` 重载而保留微信固定的“折叠的群聊/折叠的聊天”标题。二级列表点击方法使用 `MicroMsg.ConvBoxServiceConversationFmUI`、`user should not be null. position:%d, size:%d`、`specific_chat_from_scene` 与 `chat_from_scene_for_group_chats` 唯一定位，虚拟子分组由模块拦截，普通会话复用各版本稳定存在的 `BaseConversationUI#startChatting(String,Bundle,boolean)`。退出页调用的 `(String):boolean` 会话已读入口通过 `update conversation failed` 与 `updateUnreadByTalker %s` 定位并缓存，仅拒绝虚拟分组 wxid；折叠页对 `@placeholder_foldgroup` 的进入状态上报也仅在虚拟页面生命周期内短路，`onResume()` 完成后再把页面未读数恢复为当前虚拟分组的聚合值，避免显示真实折叠聊天的全局未读数。
- 分组二级页的 `不显示该聊天` 与 `删除该聊天` 保留微信原生菜单、确认弹窗和消息删除流程。微信 8.0.72、8.0.74、8.0.76 的隐藏助手只接受微信内置折叠父级，遇到 `wxid_hchat_group_*` 会仅上报错误码 `3800` 而不再删除 `rconversation` 行；模块通过七版共有的 `hidden_conv_parent` 和静态 `(String):void` 签名定位该助手，仅在目标为虚拟分组下的真实会话时改走公共原生会话删除 API。操作不会移除手动分组配置或自动归拢规则，后续新消息重建会话行后仍按原配置重新归拢；普通会话、微信原生折叠会话和虚拟分组入口保持微信原逻辑。

消息自动转发：

- `消息自动转发` 属于增强功能，设置页放在 `增强` 分组，总开关默认关闭。支持创建多条独立规则，每条规则可单独启停、单独控制是否转发自己发送的消息、是否跟随原消息撤回，并选择多个监听会话、多个转发会话和消息类型；“转发自己发送的消息”和“跟随原消息撤回”均默认关闭，旧规则也保持关闭。监听会话支持好友、群聊、公众号和微信 Clawbot，转发目标支持好友、群聊和微信 Clawbot；所有复用通用联系人选择器的名单页也会显示并保存 Clawbot。每条规则可从已选监听群聊中继续多选指定群成员；未选择成员时监听整个会话，选择后只转发精确匹配“群聊 + 发送者”的消息，不影响私聊或该成员在其它群聊中的消息。取消监听群聊时同步清理该群的成员选择。规则列表支持搜索、单条删除、选择多条规则、全选当前搜索结果并确认后批量删除。
- 可转发类型包括文字、图片、语音、视频、动画表情、引用、已下载文件、链接、音乐、小程序及其它可重发卡片、位置、普通/企业微信名片、聊天记录和视频号。每条规则有独立的延迟发送、包含关键词、排除关键词和替换关键词开关，开关关闭时隐藏对应输入框或规则入口但保留已有内容；延迟按秒设置且不限制上限，关闭或填写 0 时立即发送。包含关键词开启且已填写内容时必须命中任一关键词，排除关键词开启时命中任一内容即跳过，排除优先；筛选始终从原消息正文和 XML 可见内容中匹配。替换只处理普通文字消息的正文，不直接改写引用、链接、音乐、小程序、聊天记录、位置、名片、视频号或其它 AppMsg XML，也不修改文件名和媒体内容。每条替换规则分别填写原关键词和替换内容，替换内容允许留空；匹配不区分大小写，同一次替换按较长关键词优先且结果不再次触发另一条规则。旧规则已经填写筛选关键词但没有开关字段时自动视为开启；旧规则没有替换字段时默认关闭。保存规则时必须至少选择一个监听会话、一个转发会话和一种消息类型。
- 运行时消费公共消息观察 API 的入站消息，并使用观察事件发送者或落库群消息 `sendTalker` 识别实际群成员；规则开启“转发自己发送的消息”后也会消费该监听会话的出站消息，但自动转发会在发送前记录目标会话的消息快照，并按发送后新增的本地消息 ID、服务端消息 ID 及文本兜底正文排除自身出站消息，不能只按会话和类型模糊吞掉用户手动发送的消息，模块转发产生的出站消息不会再次触发。同一条消息会关联登记本地 `msgId` 与服务端 `msgSvrId`，避免数据库事件和协议事件各触发一次；模块发送抑制记录在短生命周期内持续匹配同一消息的后续回调，并把已确认身份写入长期去重记录。多条规则同时命中同一个目标时只发送一次并取最短延迟，替换正文由该最早到期规则决定，同一到期时间按规则保存顺序优先；只要本次实际命中的任一规则开启“跟随原消息撤回”，就登记该目标的跟随撤回关系。替换在目标实际发送前按当前仍有效的规则执行，延迟期间修改、关闭或删除规则会实时生效；替换后正文为空时跳过该目标，不发送空消息。转发消息 ID 必须经过两次稳定扫描且发送前基线之外只能存在一个候选；媒体正文为空或并发发送导致候选不唯一时不登记，不能冒险撤回用户手动发送的消息。撤回识别优先复用防撤回已经横向适配的原生六参数入口，并以公共消息观察到的撤回 XML 作为独立兜底，不能使用撤回系统消息自身的 `msgSvrId` 代替原消息 ID，本地 `msgId` 与服务端 `msgSvrId` 必须分命名空间匹配。关联记录按账号持久化、最多保留 512 条并在 24 小时后清理；原消息撤回时取消仍在延迟队列中的对应目标，只尝试撤回模块自己自动转发且当前仍处于微信可撤回时限内的消息，不处理用户手动发送或已经不可撤回的消息。延迟从收到消息时开始计算，目标与当前监听会话相同时自动跳过，防止回环。消息在最多保留 128 条待处理消息的单线程限速队列中按计划发送时间和目标逐个发送，目标间保留 500ms 间隔；关闭、删除或修改规则后，尚未发送的目标会按最新规则重新校验，包括自己消息开关与群成员范围，失配目标立即跳过。图片优先读取微信本地完整原图或高清父图；本地只有未完成文件时继续进入 CDN 准备，不得提前停在等待状态。CDN 下载按 `cdnbigimgurl` 原图、`cdnmidimgurl` 中图顺序选择可用项并通过原图发送模式转发，不使用缩略图。聊天视频通常只有 `imgPath` 而没有消息正文 XML；本地完整视频缺失时优先以 `imgPath` 查询微信原生 `VideoInfo`，从其 `reserved4` 与 `totallen/videomd5` 读取 `cdnvideourl/aeskey/md5` 和完整长度，再以视频文件类型 `4` 主动下载 MP4，消息正文、保留字段、转译字段和消息来源中的 XML 只作为兼容兜底。下载通过 Mars 完成回调立即恢复转发，只保留一次 60 秒无回调超时保护，不再每 500ms 重跑整条媒体准备。模块只清理自己写入 `cacheDir/Hchat_auto_message_forward` 的独立临时媒体，不删除微信原始文件；任务完成、失败、取消或超时后延迟 1 小时删除，给微信异步上传与弱网重试留出时间，运行时启动及之后每 6 小时清理超过 24 小时且不属于活动任务的残留，晚到的 CDN 成功回调只会删除本任务的确切缓存。`VideoInfo` 查询、视频 CDN 字段和文件类型已横向确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76。普通文字、名片、位置和 AppMsg 复用公共静默消息 API，媒体复用图片、语音、视频、表情和文件公共发送 API。

快捷已读：

- 最新收到语音的已播放位优先通过微信原生消息存储更新；语音类型统一经过 `WeChatMessageTypes` 标准化，兼容高位携带状态标志但低 16 位仍为 `34` 的消息类型。定位时先在当前会话最近 8 条消息中按 `rconversation.content` 精确匹配当前语音摘要，只有绝对最新消息本身仍是未播放语音时才保留原有兜底，不能因隐藏消息或分表查询顺序误标历史语音。原生入口在当前版本无法构造消息对象、无法定位更新方法或返回未更新时，按当前 talker 解析实际消息分表并以 `msgId + 原 content` 条件回退更新，同时同步 `rconversation.content`。会话摘要更新也带原 content 条件，若期间已经收到新消息则不覆盖新摘要；最终失败日志必须包含消息更新或会话摘要更新阶段以及分表更新结果。
- `快捷已读` 属于实用功能，设置页放在 `聊天` 分组，提供 `拖拽已读` 和 `注入加号菜单已读` 两个独立开关，默认都关闭。启用悬浮底栏时，拖拽已读优先使用悬浮底栏宿主的实际屏幕区域识别微信页签，不依赖被隐藏的原生页签文字或液态 Compose 节点，保证两种底栏同时开启时仍能收到完整拖拽手势。
- `拖拽已读` 开启后，先定位微信底部 tab 容器，再只在该容器内命中未读红点/角标，不处理聊天列表里的会话未读数；数字角标和无数字纯红点都要支持，不依赖红点 View 自身可触摸。按下时只记录候选红点，不创建拖拽层、不吞底部导航点击，只有明显向上拖动超过启动阈值后未读角标才跟随手指移动。松手时只有向上拖动达到已读阈值才触发已读。触发时从 `rconversation` 查询普通未读、免打扰未读、`@我`/`@所有人`、红包、群收款及最新未播放语音对应的会话：普通未读和 `@` 提醒逐个调用微信 `ConversationStorage` 原生 `updateUnreadByTalker(String): boolean` 入口；红包清除 `hbMarkRed`，群收款只清除 `attrflag` 的 `0x01000000` 位，并紧接原生已读入口通知会话观察者刷新，不能只改数据库后隐藏红字；最新收到语音的红色 `[语音]` 按微信 `VoiceContent` 的稳定序列化格式把已播放位从 `0` 更新为 `1`，再通过原生消息存储更新入口提交，不能误用 `UnReadInvite`。拖回原位或未达阈值只复位角标，不触发已读；触发成功后取消微信当前通知。Hchat 设置页覆盖在 `LauncherUI` 上时不处理红点拖拽。
- `注入加号菜单已读` 开启后，右上角加号菜单会增加 `全部已读` 入口；`界面 -> 插件 Agent 入口` 可独立控制菜单中的 `插件 Agent`，点击后直接进入 Agent 对话页。两者都复用设置入口已确认的 `PlusSubMenuHelper` 动态注入链路，不新增另一套菜单 DexKit 定位。加号菜单 Hook 只在任一加号菜单入口开关开启时解析并安装，开关从关闭改为开启后需要重启微信才会出现菜单项。

快捷终止：

- `快捷终止` 属于实用功能，设置页放在 `界面` 分组，默认关闭。开启后在 LauncherUI 右上角加号菜单末尾添加 `快捷终止`，点击后关闭菜单并立即结束微信当前主进程，不弹二次确认；重新打开微信即可恢复。
- 菜单项复用 `SettingsInjector` 已有的 `PlusSubMenuHelper` 动态注入、点击分发和 adapter 行渲染，不新增 DexKit 定位，不写死混淆类或字段。图标使用脚本原有的白色 Android 电源图标 `android.R.drawable.ic_lock_power_off`；其它 Hchat 加号菜单项继续使用模块 `H` 自绘图标。

自定义通知：

- `自定义通知` 设置页放在 `实用` 页签内的 `增强` 分组，默认关闭；开启后按“会话专属规则优先，未配置会话再走默认规则”的顺序接管微信通知。默认规则分为 `默认私聊通知`、`默认群聊通知` 和 `默认公众号通知`，三者默认关闭，用户开启后分别覆盖未单独配置的私聊、群聊和公众号；`weixin`、`fmessage`、`filehelper` 等微信内部系统 talker 不继承默认会话规则，避免启动恢复或内部状态通知被当成普通新消息。`忽略微信自带的消息免打扰` 属于默认规则和单个会话规则的独立选项，默认关闭；关闭时读取微信原生 Contact 的免打扰判断，普通 `@chatroom` 群聊在原生入口尚未就绪时回退读取同一 Contact 的持久化 `type & 0x200` 标志，不使用 `rconversation.unReadMuteCount` 猜开关。状态未知时消息观察链路不补发 Hchat 通知，已经存在的微信原生通知保持原处理；开启后仅对应规则忽略该状态并继续提醒。规则自己的 `免打扰` 和静默时段仍独立生效。旧版全局开关只用于迁移缺少该字段的旧规则，新保存的规则会显式记录各自设置。
- 命中规则的新消息由模块发送自定义系统通知，并拦截对应微信原生通知，避免同一条消息重复提醒；原生通知已经出现但缺少可靠 `msgSvrId`、无法安全接管时保留原通知，同时标记本轮已由原生链路处理，消息观察链路不得再补发第二条 Hchat 通知。消息观察兜底和原生通知接管优先按 `talker + msgSvrId` 原子认领同一消息；原生通知缺少服务端 ID 时，按通知事件与同会话观察事件的发生时间配对，匹配结果不再依赖头像加载队列真正开始执行时的 1.5 秒有效期。微信原生通知构建较慢、兜底已经先发出时，后到链路不得再次发布、震动或播放铃声，首次发布失败则释放认领允许另一条链路补发。没有服务端 ID 时仍不得用正文合并两条真实消息。当前聊天窗口可见、自己发送、系统消息、拍一拍和撤回类消息不弹自定义通知。好友申请等特殊通知只走微信原生通知拦截路径，不走消息观察兜底。通知标题始终按当前 `talker` 解析，私聊和群聊有备注时只显示备注、没有备注时分别显示微信昵称或原群名称，不再拼接两个名称。正文详情优先沿用同一条微信原生通知内容，不能使用隐私模式下可能复用其它会话信息的原生汇总标题；消息观察兜底链路无法识别具体类型时只从 XML 标题/描述或原文取展示内容，不再追加模块自定义类型前缀或“非文本消息”占位。自定义通知同时显示会话未读数：优先读取微信 `NotificationItem` 已计算的 `unreadCount`，观察兜底按通知栏内同会话现存的 Hchat 通知计数；多于 1 条时在正文前显示 `[N条]`，并同步写入 Android `Notification.number`。免打扰和静默时段会在微信原生通知处理器播放首页提示音之前结束本轮处理，同时阻止自定义通知与系统原生通知；普通通知规则关闭声音时也会拦截同一处理栈内的微信提示音，不能只在 `NotificationManager.notify(...)` 阶段静音。
- 通知头像必须使用对应会话头像：私聊使用好友头像，群聊使用群头像。通知点击打开对应聊天；接管微信原生通知时必须复用原通知的 `contentIntent`，避免重新拼装 `LauncherUI + ChattingUI` 双 Activity 点击栈被部分系统识别成额外小窗，只有消息观察兜底没有原生通知对象时才自行构建聊天返回栈。默认规则和会话规则可分别控制是否显示 `已读` 动作，旧配置默认开启。点击后复用快捷已读已经定位并缓存的微信原生单会话已读入口，只更新当前 talker，并只清理通知栏里同一 talker 的 Hchat 自定义通知，不能调用 `NotificationManager.cancelAll()` 影响其它会话；开启快捷回复后可在通知栏直接回复，独立的 `引用消息回复` 开关默认关闭，只有快捷回复和该开关同时开启时才引用触发通知的原消息。消息观察路径同时携带本地 `msgId` 和服务端 `msgSvrId`，原生通知接管路径按 `talker + msgSvrId` 解析本地消息；旧通知缺少消息标识、源消息已被删除或引用发送失败时回退普通文本回复。快捷回复广播必须使用异步广播保活并走独立执行队列，不能被通知头像加载阻塞；每次发布通知都写入实例令牌，回复完成前先确认同一通知 ID 仍属于原实例，再回写成功或失败状态以结束系统内联回复进度，成功时延迟清理并继续校验完成令牌，避免覆盖或误删同 ID 的后续新通知。
- 默认规则和会话规则都支持配置启用状态、免打扰、忽略微信自带的消息免打扰、震动、铃声、已读按钮、快捷回复、引用消息回复、同会话通知合并、消息详情、静默时段；静默时段使用统一时、分、秒选择器并按秒匹配，批量配置使用同一交互。`合并同会话通知` 默认关闭，开启后同一 talker/wxid 使用稳定通知 ID 更新同一条通知，折叠状态显示最新一条消息并累计未读数，展开后通过 Android `InboxStyle` 显示最近最多 7 条消息；用户清除该通知后历史随活动通知清空，关闭时每条消息继续独立显示。群聊默认规则支持屏蔽 `@所有人` 和屏蔽 `@我`，单个群聊规则额外支持仅显示指定成员、屏蔽指定成员。艾特屏蔽必须同时作用于消息观察兜底和微信原生通知接管路径；原生路径按同一条消息的 `talker + msgSvrId` 关联统一艾特分类，不能从通知展示文字猜测，也不能在接管后绕过规则重新发送。规则列表支持添加好友/群聊/公众号/标签好友、搜索、单条规则快捷开关、批量删除和批量配置；保存、添加、删除和批量应用这类写入操作必须给出轻量 Toast 反馈。
- 原生通知拦截优先 hook 微信生成 Android `Notification` 的原生构建方法，把微信已计算好的标题、正文、摘要和 talker 写入 Hchat 私有 `Notification.extras`，再拦截 `NotificationManager.notify(...)` 复用这些原生字段；同时保留 `NotificationItem` 标记兜底，固定类名不可用时按 `id: `、`userName: `、`unreadCount:` 字符串锚点查找通知项类。只有通知携带可靠的正值 `msgSvrId` 时才接管普通消息通知，无法证明对应本轮真实消息的启动恢复或内部通知保留微信原处理；Hchat 通知沿用原消息或原生通知的时间，不把历史恢复通知重写为当前时间，并在最终 `Notification` 上显式保留时间与显示标记，避免 Builder extras 合并或 ROM 模板处理后隐藏时间。已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68` 的旧构建方法签名为 `Notification,int,int,PendingIntent,String,String,String,Bitmap,...,String`，其中第 5/6/7 个字符串分别进入原生标题、正文和摘要；`8.0.72`、`8.0.74`、`8.0.76` 切换为单个参数对象，字段 `e/f/g/o` 分别对应标题、正文、摘要和 talker。微信关闭 `settings_show_detail` 后会在每次新通知构建前调用通知清理方法：`8.0.49` 为 `com.tencent.mm.booter.notification.a0.b()`，`8.0.58` 至 `8.0.76` 为 `com.tencent.mm.booter.notification.i0.b()`；处理入口在 `8.0.49` 为 `q.d(...)`、`8.0.58` 至 `8.0.68` 为 `x.d(...)`、`8.0.72` 至 `8.0.76` 为 `m0.a(...)`。模块按日志字符串锚点定位并缓存这两类方法，仅当当前处理栈持有正值 `msgSvrId`、消息不是发出或系统消息、对应规则确实启用且不在免打扰状态时跳过批量清理；不能仅凭自定义通知总开关阻止启动期过期通知清理，也不能拦截聊天已读等场景对同一清理方法的正常调用。微信首页前台反馈另按 `notification.playSound: is mainUItalker: %s` 定位外层通知处理器，并按 `playSound playHandler == null` 定位三参数提示音调度方法；`8.0.49` 的外层处理器为 `q.d(owner,String,String,int,int,boolean)`，`8.0.58` 至 `8.0.76` 均为同签名的 `x.d(...)`，七版提示音调度方法均为 `void(String,boolean,boolean)` 且各有两个实现。定位结果写入 `DexMethodCache`，缓存 key 必须使用当前运行时 key。Android 8+ 通知通道声音不可变，模块自定义通知通道保持静音，铃声由模块按规则手动播放。自定义通知拦截器必须跳过 Hchat 自定义通知和关键词通知自身的通知 marker/channel，避免群聊免打扰规则误吞关键词通知。
- `NotificationItem.toString()` 的 `unreadCount` 读取字段已横向确认：8.0.49 为整数字段 `j`，8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 为整数字段 `m`；运行时只按这两个已验证字段名读取，不把通知 ID 等其他整数误当未读数。
- 原生已读入口必须通过 DexKit 精确字符串 `updateUnreadByTalker %s`、`unReadMuteCount`、`atCount` 定位并写入 `DexMethodCache`；已横向确认 `8.0.49`=`com.tencent.mm.storage.v4#b0(String)`，`8.0.58`=`a5#d0(String)`，`8.0.66/8.0.68`=`n3#d0(String)`，`8.0.72`=`m4#c0(String)`，`8.0.74`=`m4#b0(String)`，类名和方法名会变，不能写死。启动时要在公共 API 预热完成后主动准备 `ConversationStorage` 实例并缓存，触发拖拽或菜单已读时不能再依赖首次深度遍历 CoreStorage，也不能把正常未就绪状态作为 LSPosed 错误栈输出。
- `8.0.49` 至 `8.0.76` 的会话实体均持久化 `hbMarkRed`、`attrflag`、`msgType`、`isSend` 与 `content`；`@` 红字除 `atCount` 外还要覆盖微信原生已读入口清除的 `attrflag & 0x00100000`，群收款红字使用 `attrflag & 0x01000000`。同一版本范围内语音内容均按 `文件标识:时长:已播放位:` 或带群发送者前缀的四段形式解析，最后的已播放位为 `1` 时会话列表不再把 `[语音]` 标红。快捷已读只更新当前最新收到且未播放的语音，不能把历史语音批量改成已播放，也不能清除 `attrflag` 的其它业务位。

关键词通知：

- `关键词通知` 设置页放在 `实用` 页签内的 `增强` 分组，默认关闭；迁移自脚本插件 `杂项/关键词通知`，通过公共消息观察 API 处理收到的非自己消息。
- 触发条件包括关键词列表、任意关键词群聊/私聊、`@我`、`@所有人` 和群公告；任意关键词只处理普通文字和引用回复，不处理图片、语音、表情、文件、链接、系统消息、拍一拍、通话等非文本消息。
- 关键词通知除公共消息观察 API 外，还订阅屏蔽消息的内部 `MessageBlocked` 事件。屏蔽消息在拦截入库前会先调用关键词通知匹配；命中关键词列表、已开启的任意关键词规则、`@我` 或 `@所有人/群公告` 时直接放行入库，并由公共消息观察链路触发关键词通知。未命中这些规则但被屏蔽的消息才通过内部事件兜底给关键词通知；该通道不派发给自定义通知，保持关键词通知和自定义通知隔离。
- 生效范围支持排除模式和仅生效模式，名单通过联系人选择器选择好友、群聊或标签好友，不再提供手动填写 wxid 或群号入口。关键词、`@我`、`@所有人/群公告` 三类系统通知可分别控制铃声、震动和自定义铃声文件，Android 8+ 通知通道保持静音并由模块按设置播放铃声，避免系统通道缓存导致修改不生效。关键词通知使用独立于自定义通知的 channel 前缀和通知 marker，不跟随自定义通知的群聊免打扰。通知标题私聊和群聊有备注时只显示备注，没有备注时分别显示微信昵称或原群名称。通知头像私聊使用私聊者头像，群聊使用群头像。通知和 Toast 支持 `%keyword%`、`%sender%`、`%wxid%`、`%content%`、`%type%` 变量，三类模板留空时使用内置默认模板。

定时任务：

- `定时任务` 设置页放在 `实用` 页签内的 `增强` 分组，默认关闭；迁移自脚本插件里的定时发送能力，但收敛到模块统一的 Miuix 设置页。
- 当前模块版支持创建多条独立任务，任务目标可选 `聊天` 或 `朋友圈`。聊天目标可选择 `模块通道` 或 `微信原生群发助手`：模块通道通过统一联系人选择器选择好友、群聊、公众号或标签好友，可按顺序发送文本、图片、视频、文件、表情、语音、XML 或收藏；原生通道只选择好友，支持文字、图片、视频、语音、表情和视频号，并等待原生群发队列真实完成后再记录任务结果。从聊天多选菜单创建的任务会按原顺序保存消息重发快照和所选通道，并作为不可手工伪造的 `聊天记录` 内容展示。旧任务没有通道字段时默认模块通道。朋友圈目标固定使用模块通道，不要求选择联系人，明确提供 `文字`、`图文`、`视文`、`图片`、`视频` 五种类型；图文最多 9 张图片，视文只允许 1 个视频，发布统一复用 `WeChatSnsApi` 的纯文字、图片列表或视频接口。
- 媒体文件必须通过系统文档选择器挑选并复制到模块私有目录，不提供手填路径入口；收藏内容通过最近收藏选择页选取。一个任务可添加、修改或删除多个计划时间，同一份内容和目标会在各时间分别执行；任务支持单次、每天和每周循环，周循环只在用户勾选的星期触发，每个计划时间独立推进到下一次有效日期。计划时间依次使用日期、时分和秒数选择流程，最终执行时间精确到秒；旧配置中的单个 `planTime` 自动迁移为时间列表。
- 调度器始终只为一个任务挂载最近的计划时间；每次回调必须携带排期时保存的 `planTime`，执行前重新读取当前任务并确认它仍是列表中的最近时间且当前墙上时钟已经到点。远期计划通过公共 `WeChatTaskApi` 的 `RTC_WAKEUP` 精确闹钟在 Doze 或锁屏冻结时唤醒仍存活的微信进程，并保留 Handler 同刻兜底；闹钟触发后持有最长 30 分钟的局部 WakeLock，避免广播结束后发送线程立刻再次休眠。执行完成后，单次任务只移除刚执行的时间，循环任务只推进刚执行的时间，再调度列表中的下一项；编辑任务后残留的旧回调、旧闹钟或系统时间回拨都不得提前触发新计划。微信进程被系统杀死或被用户强停后无法直接执行微信内部发送 API，只能在微信进程恢复时按超时补发规则处理。
- 微信进程重启后会从本地配置恢复待执行任务；任务回调真正发送前还会统一复核迟到时间，关闭“超时补发”时超过 1 分钟执行宽限的迟到实例直接跳过，避免进程冻结后旧 Handler 回调恢复便无条件发送。单次任务只有开启“超时补发”且超时未超过 10 分钟才补发；每天和每周任务开启时补发当前错过的计划实例，关闭时直接顺延到下一轮。每周任务首次排期和后续循环都必须落在用户已勾选的星期。任务列表不保留单次任务历史，循环任务记录最近执行结果并继续排期。
- 编辑页的“立即执行”只临时发送一次并记录结果，不移除或推进任务中已经配置的任何计划时间。

群发助手：

- `群发助手` 设置页放在 `实用` 页签内的 `增强` 分组，默认开启；关闭后不再向微信多选消息菜单注入 `群发助手[H]` 和 `定时转发[H]`，已经保存的定时任务仍按原计划执行。设置页提供全局 `群发助手间隔延迟` 和 `群发间隔延迟`：前者以分钟配置微信原生群发超过单批人数上限后的联系人批次间隔，后者以秒配置多条内容或模块通道多个目标之间的间隔；两项默认均为 `0`，运行时仍保留原有最短安全间隔。自定义群发和定时任务已有的单次间隔大于全局值时，以较大值为准。两个菜单相互独立：`群发助手[H]` 和 `定时转发[H]` 都先选择 `模块通道` 或 `微信原生群发助手`；定时转发随后选择聊天、单次/每天/每周重复方式及计划日期、时、分、秒，每周模式继续选择一个或多个星期。重复任务所选时间已经过去时复用 `ScheduledTaskSettings` 的重复计算排到下一次，每周任务的首次执行也必须落在所选星期。秒数输入、重复方式、通道选择和星期多选统一使用模块 Miuix 弹窗，不使用 Android 原生 AlertDialog；日期与时分继续使用系统选择器。模块通道可选择好友、群聊、公众号或标签好友，微信原生群发助手只允许选择好友并可按微信好友标签筛选。Compose 选择弹窗必须先移除旧遮罩，再通过 decor 的下一帧回调打开联系人、时间或进度窗口，不能在关闭加载窗口的同一消息循环里立即创建新窗口；用户主动关闭加载窗口时还必须取消后续展示，避免透明根 View 残留后吞掉屏幕点击或联系人列表无法显示。
- 设置页提供独立的 `自定义群发` 编辑入口，内容编辑器与定时任务共用同一套有序内容模型。模块通道支持与定时任务聊天发送相同的文字、图片、视频、文件、表情、语音、XML 和收藏类型，可选择好友、群聊、公众号、好友标签或群聊标签，并支持聊天间隔和多条内容间隔；微信原生群发助手支持文字、图片、聊天视频、语音、表情和视频号，只选择好友，其中视频号沿用 XML 内容存储，并必须通过 `<appmsg><type>51</type>` 或明确的 `finderFeed/finderObject/finderUsername/objectId+objectNonceId` 结构校验。聊天视频必须持有微信原生视频文件令牌；自定义编辑器直接选择的外部视频尚未导入微信视频存储时应在启动前明确判为不支持，不能把绝对路径作为原生令牌提交后长期停留在发送中。聊天多选菜单先选择通道，再只对微信原生通道执行类型限制；定时转发按定时任务支持范围保存。两条群发通道默认显示可取消的发送进度，结果按“内容数 × 联系人数”统计；开启 `后台静默发送` 后，多选消息、自定义群发，以及转发菜单里的好友、标签和群发助手发送都直接启动原有发送队列，不显示进度窗口，完成、部分失败和取消结果仍正常提示。
- 模块通道把选中的原生消息转换为公共 `WeChatRetransmitPayload`，并优先复用模块现有的静默媒体或 XML API；微信原生群发助手接受文字、图片、语音、视频、表情和视频号 AppMsg。定时转发保存公共重发工厂生成的消息快照及通道，到点后通过对应发送器执行。只有模块通道无法从本地快照静默重建的消息才回退微信 `MsgRetransmitUI` 快速转发链路。多条消息和收件人严格串行；静默 API 的每次提交保留 500ms 安全间隔，自定义群发配置为 0 秒时只表示没有额外用户延迟，仍保留该内部间隔。微信重发页按最多 10 个收件人分批，当前批次完成或明确失败后才处理下一批。发送进度窗口的完成回调统一回主线程关闭，程序完成关闭不能再次触发用户取消回调。`MsgRetransmitUI.onCreate` 读取的 `Retr_MsgQuickShare`、`Select_Conv_User`、`Retr_Msg_Id`、`Retr_Msg_Type`，以及完成方法使用的 `sendResult`、`SendMsgUsernames`，已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`；完成方法名逐版变化，必须按字符串与所属类定位并写入运行时缓存。
- 群发图片会同时收集微信 `ImgInfoStorage` 按原生图片记录返回的大图/高清父记录路径、自发消息仍保留的绝对源文件，以及通过 `THUMBNAIL://`/`THUMBNAIL_DIRPATH://`/`read img buf failed:` 定位入口还原的 `imgPath`。选中消息的 UI 对象不保证是原生消息实体，解析前还会按 `msgId` 补取数据库原生消息对象。大图路径可能由微信 VFS 引用文件映射到 `image2/.ref` 数据，普通 `File.isFile()` 会误判为不存在；此时必须通过 `com.tencent.mm.vfs.w6/p6` 的静态 `InputStream(String)` 入口读取到模块缓存。大图和直接源文件属于主候选并按文件大小选择，只有二者都不存在时才允许 token 解析结果兜底；不能用 Android `BitmapFactory` 是否能解码来比较微信私有图片文件，否则私有大图可能得到 0 尺寸并被普通 JPEG 缩略图错误覆盖。模块通道和自定义群发使用图片发送 API 的原图模式；微信原生群发的图片构造器与网络场景必须同时传入原图模式 `1`，不能只修改其中一处。模式 `0` 会把图片压缩为最长边 960、JPEG 70%；模式 `1` 会直接复制选中的图片文件。大图、VFS 读取与 token 路径入口已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`，均通过 `DexFinder` 定位和缓存或按稳定签名动态解析；8.0.49 与 8.0.76 的原生 `MassSendMsgUI` 均明确把同一个原图模式同时传给图片构造器和 `NetSceneMasSend`。
- 微信原生群发助手通道按 `MicroMsg.NetSceneMasSend` 动态定位官方群发网络类，再从其稳定构造 `(<同包 MassSendInfo>, boolean, int)` 取得信息类；文字、图片、语音、视频、表情和视频号 AppMsg 按官方群发队列逐条发送，其它消息类型在进入联系人选择前拦截。`MassSendInfo` 必须对齐微信新建群发的最小写入方式，只设置联系人、人数、消息类型、正文或必要媒体字段，不能预填自定义 `clientid` 或其它原生保持默认的状态字段；网络场景构造器会自行生成客户端时间标识，高版本对自定义请求 clientid 的处理可能只生成本地 `masssendapp` 记录而不完成实际投递。提交网络场景时不能只交给模块通用网络发包器：通过 `MicroMsg.MassSendFooterEventImpl` 回调的实际调用关系同时定位微信官方群发使用的静态网络队列 getter 和单参数 boolean 提交方法，优先沿用该精确入口，调用异常时才回退通用网络 API。该 getter、提交方法和最小字段写入已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`，定位结果按微信运行时 key 写入 `DexMethodCache`。表情按原生 `msgtype=47` 构造：通过同一回调从其实际写字段顺序解析并缓存 protobuf 类、md5、大小、类型、内容和空 buffer 字段，序列化后写入 `MassSendInfo` 的 byte[]；不能把文件路径或 md5 直接当正文发送。语音按原生 `msgtype=34` 构造：聊天中已存在的语音直接复用原消息的微信文件名与 `voiceinfo`，不能再次调用录音完成入口创建 `masssendapp` 中转消息；只有外部音频才复用公共语音 API 转换格式、创建 `voiceinfo` 并复制到微信语音存储路径。两种情况最终都把微信文件名和毫秒时长写入 `MassSendInfo`，不能把外部绝对路径直接写入内容字段，否则原生网络场景按文件名查询得到长度 0，发包和本地群发记录都会失败。视频按原生 `msgtype=43` 构造时同样必须把消息数据库的 `imgPath` 原始令牌写入内容字段，把播放时长写入 `mediatime`，并把原生视频保留字段设为 `2`；模块重发载荷为发送 API 解析出的绝对 `.mp4` 路径，两者语义不同，不能互相替代。`NetSceneMasSend.onGYNetEnd(...)` 会在视频每个上传分片返回后执行，模块只能在明确错误或同一 `MassSendInfo.status` 已变为 `199` 时结束当前任务，不能把首个成功分片误判为整条视频完成。视频令牌查询、分片状态推进和最终状态在上述七版保持一致。联系人选择器不限制总人数，模块读取微信远端配置的当前单批上限并自动拆分，读取失败使用微信七版一致的默认值 500；超过单批上限直接提交会收到 `errCode=-71`。图片使用同包 storage 中唯一的 `(String path, String contacts, int count, int scene) -> MassSendInfo` 构造器，不能手工伪造图片记录。`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 都保持同包 `g0/y/z/k0/a0` 结构和 `a0.c(): int` 上限方法，仅 `k0` 的服务 getter 随版本混淆；`8.0.49` 的联系人列表字段为 `j`，`8.0.58` 至 `8.0.76` 为 `m`，人数、类型、内容、时长字段稳定为 `n/o/h/p`，且 type 34 都会用内容字段返回的文件名查询微信语音存储长度。表情 protobuf 的语义和写入顺序七版一致，但 `8.0.58` 的混淆字段名与其它版本不同，运行时不得硬编码类名或字段名。联系人数据在功能加载后后台预热并缓存 5 分钟；原生通道读取好友及好友标签，不等待群聊或公众号，模块通道继续加载好友、群聊、公众号和标签。
- 多选入口与多选撤回、多选语音转发共用 `MultiSelectMessageMenuLocator`、`MultiSelectMessageResolver` 和 `MultiSelectMessageUi`；只有保存任务或确认群发成功入队后才退出微信多选状态，取消任一选择窗口时保持原多选。群发过程显示可取消窗口，取消只停止当前批次及其后续消息；模块通道会关闭当前重发页，原生通道会调用当前 `NetSceneMasSend.cancel()`。系统消息、通话、撤回提示、红包和转账等不可重发消息不生成发送快照。

僵尸粉检测：

- `僵尸粉检测` 设置页放在 `实用` 页签内的 `增强` 分组，总开关默认关闭。检测范围为空时使用全部当前好友，也可通过统一联系人选择器自选好友和配置排除名单；队列支持暂停、断点继续和重置，固定只保留一个在途请求，并提供随机请求间隔、超时和最多 0-5 次重试。进度通过设置页和低优先级通知显示；可选 WakeLock 默认关闭并限制单次最长持有一小时。异常结果使用昵称与原因上下排列的自适应布局，避免长昵称或大字体挤压结果说明。
- 检测使用微信 `NetSceneTenpayRemittanceGen` 只生成转账下单请求，不发送确认付款请求。构造参数必须按逆向语义填写：`fee=1.0`、`fee_type=1`、`receiver_name=目标 wxid`、`scene=31`、`transfer_scene=2`、`channel=11`，`placeorder_reserves` 和 `placeorder_attach` 保持空值；不能照搬脚本把时间戳写入 `placeorder_reserves`。回包只有在错误文案明确包含“不是收款方好友”“拒绝接收”或完整好友关系异常语义时才判为异常好友；`errCode=0/2` 且响应带非空 `req_key` 才判为正常，其它支付风控、实名校验、服务端文案或超时统一记为检测失败，不能误判为正常或僵尸粉。
- 异常好友可自动追加自定义标签，默认标签为 `僵尸粉`。追加前读取该好友已有标签并把新标签合并后调用公共联系人标签 API，不能用单标签调用覆盖原标签。自动删除默认关闭，开启时必须再次确认；暂停或重置任务会取消尚未执行的删除。设置页另提供独立的批量删除入口，使用统一好友选择器并支持按微信标签筛选、全选当前筛选结果，默认预选本次检测出的异常好友；提交前必须重新确认所有目标仍是当前好友并二次确认，删除任务按配置间隔逐个执行且可中途停止。8.0.49/58 的删除服务使用 `void(String)`，8.0.66/68/72/74/76 使用 `void(String, boolean)`，第二个参数才表示是否同时清理聊天记录。高版本保留已有聊天记录时会主动插入“已删除该好友”系统消息；删除前没有任何聊天消息的联系人自动按清理记录调用，避免删除操作创建新会话，已有聊天记录仍遵守用户设置。调用时必须通过微信服务容器按删除服务实现的接口取得官方单例并保留强引用，不能直接无参构造临时实例；只有服务容器不可用时才允许构造兜底。
- 逆向依据：`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均由 `Micromsg.NetSceneTenpayRemittanceGen`、`receiver_openid`、`placeorder_attach` 唯一定位到 `com.tencent.mm.plugin.remittance.model.q0`；8.0.49/58 构造器为 29 参数，8.0.66-76 末尾增加 `has_try_hkpay` 后为 30 参数，回调均为 `onGYNetEnd(int,String,JSONObject)`，且都读取 `req_key`、`tansfering_num` 与 `receiver_open_id`。同组版本均存在 `/cgi-bin/micromsg-bin/addcontactlabel` 与 `/cgi-bin/micromsg-bin/modifycontactlabellist`；8.0.66/68/72/74/76 的核心删除服务使用 `MicroMsg.DeleteContactService` 与 `delete contact %s isClearRecord:%s` 定位，依次为 `ek1.n.a`、`hl1.n.a`、`xo1.n.a`、`aq1.n.a`、`lq1.n.a`，签名均为 `void(String,boolean)`，不能误选依赖个人资料页实例的单参数 UI 包装器。8.0.49/58 继续使用 `delete contact %s` 单参数入口。探测构造器、回调和删除方法都按完整微信运行环境写入 `DexMethodCache`。

音频转换：

- `音频转换` 设置页放在 `实用` 页签内的 `语音` 分组，名称固定为 `音频转换`。当前模块版提供 `任意音频转silk保存`、`任意音频转silk发送`、`Silk 转 MP3 保存`、`Silk 转 M4A 保存` 四种模式；输入文件统一使用系统文档选择器，保存模式统一使用系统创建文档选择器，发送目标统一使用现有联系人选择器，不提供手填路径、手填 wxid 或脚本式弹窗流程。
- `音频转换` 模块页与脚本 API 共用 `h.Hchat.media.AudioTransformBridge`。`任意音频转silk保存` 优先走 `autoToSilk(...)`，输入本身已是 Silk 时直接复制；OGG 会按识别包区分 Vorbis 与 Opus，Ogg Opus 通过系统媒体解码器转为 PCM 后再编码 Silk。`任意音频转silk发送` 复用同一兼容入口及 `WeChatApis.media().voices().send(...)` 公共发送链路；`Silk 转 MP3/M4A 保存` 分别复用 `silkToMp3(...)`、`silkToM4a(...)`。保存结果写入用户在系统文档选择器里确认的目标 URI。
- `音频转换` 选择输入文件后提供播放/暂停和可拖动进度条；普通音频直接使用系统播放器异步准备，Silk 输入先在后台转换为唯一命名的临时 M4A 试听文件。切换文件、进入名单页、开始处理或离开页面时停止并释放播放器，临时试听文件随页面或文件切换删除。`任意音频转 Silk 发送` 模式可启用“裁剪与自动分割”，把当前播放位置分别设为开始时间或结束时间，也可重新设置为完整音频区间；播放到选定终点后自动暂停并回到起点。自动分割秒数最少 1 秒且不设置人为上限，发送前把源音频解码为单声道 PCM，只读取选定起止区间并按设置时长编码为一段或多段 Silk，再按原顺序逐段发送。处理和发送过程支持取消，任一分段失败后停止发送余下分段；任务完成、取消或失败后自动清理临时 PCM/Silk 文件。所有保存模式仍输出单个完整文件，不执行裁剪或分段。

微信强保活：

- `微信强保活` 设置页放在 `实用` 页签内的 `杂项` 分组，默认关闭。开启后可按开关组合使用模块前台服务、微信进程内 `PARTIAL_WAKE_LOCK`、Root `cmd deviceidle whitelist +com.tencent.mm`、Root AppOps 后台限制放行、前台服务看门狗和网络心跳，用于提高微信息屏后的后台存活率。
- 前台服务使用模块自身进程启动并显示常驻通知；WakeLock 在微信进程内持有，关闭总开关或卸载功能时必须释放。Root Doze 白名单、Root AppOps 放行、看门狗拉起微信和网络心跳都只在用户单独开启后执行，执行失败只记录 Hchat 错误日志，不影响普通保活。网络心跳由前台服务定时发起短超时轻量请求。设置页提供电池优化白名单状态查看和跳转系统电池优化设置。

朋友圈：

- `朋友圈自动点赞`、`朋友圈自动评论`、`朋友圈自动转发`、`朋友圈自动刷新` 和 `朋友圈发布通知` 是朋友圈分组内五个独立功能，开关、设置和运行生命周期互不绑定，均默认关闭。五者复用公共 `WeChatSnsApi`；自动点赞、自动评论、自动转发和发布通知共同订阅 SNS 时间线写入使用的 `(long snsId, SnsInfo) -> boolean` `replaceUserBySnsId` 入口，直接消费微信已经解析好的 `SnsInfo`，不再依赖高版本可能绕开的通用 SQLite `ContentValues` Hook。朋友圈协议类型统一由公共分类器解释：`1` 为图片、`2` 为文字、`3` 为网页/链接、`4` 为音乐、`5` 为视频链接、`15` 为视频、`54` 为实况照片，当前版本已确认的其它协议值归为其他类型，未确认或缺失的数值归为未知类型；媒体解析、脚本记录、自动互动、自动转发、发布通知和底部类型变量不再各自维护数字判断。
- `朋友圈自动点赞` 订阅公共朋友圈入库事件，支持白名单/黑名单、同一人每天点赞数量限制、固定/随机延迟、运行时段和发布时间限制。单人每日限制按发布者分别统计成功提交的点赞，`0` 表示不限制，计数跨微信重启持久化并在本地日期变化时清零。`点赞自己的朋友圈` 是独立开关且默认关闭；开启后自己的朋友圈不受白名单或黑名单过滤，仍遵守单人每日数量、时段、发布时间、类型、关键词和去重设置。内容类型提供文字、图片/图文、视频/视文、网页/链接、音乐、其他卡片和未知类型开关；实况照片跟随图片开关，视频链接跟随网页/链接开关。新增的网页/链接、音乐、其他卡片和未知类型开关默认关闭，避免升级后扩大已有自动点赞范围。文字、图片正文、视频正文及链接/卡片正文分别保存独立的关键词过滤开关与关键词列表；纯图片和纯视频因正文为空自然不会命中关键词。固定/随机延迟与发布时间限制不设人为最大值，秒数参数最少 0 秒，发布时间范围仍最少 1 小时。每次开启时记录本次开启时间，只处理发布时间不早于该时间的朋友圈；关闭后取消全部待点赞任务，再次开启时重新计算。待执行任务按 `snsId` 唯一占用；实际提交成功的点赞 `snsId` 永久持久化，不参与临时状态过期或容量淘汰，避免后续数据库更新再次发送点赞请求。失败进入短冷却后可重试，普通过滤只做短期内存抑制。帖子删除时能从更新条件解析 `snsId` 的，会取消对应待点赞任务。
- `朋友圈自动评论` 与自动点赞使用等量但完全独立的设置和状态：评论正文、评论自己的朋友圈、白名单/黑名单、同一人每天评论数量、固定/随机延迟、运行时段、发布时间限制、七类朋友圈开关、四组排除关键词和运行日志。新增类型开关同样默认关闭，实况照片与视频链接分别跟随图片和网页/链接设置。评论正文支持点击中文“时间”变量插入 `${time}`，仅在模板包含该变量时显示时间格式输入；格式使用 Java `SimpleDateFormat` 规则，默认 `yyyy-MM-dd HH:mm:ss`，非法输入不保存，运行时异常格式回退默认值，并在延迟结束后真正提交评论时按设备当前时区生成时间。评论正文为空时不提交；每次开启只处理本次开启后发布的朋友圈，关闭后取消待评论任务。待执行、成功去重、每日计数和日志都保存在自动评论自己的配置中，不与自动点赞共享；成功提交的 `snsId` 永久持久化，失败短暂冷却后允许重试，帖子删除时取消对应待评论任务。最终通过公共 `WeChatSnsApi.comment(...)` 走微信原生文字评论包装方法静默提交。
- `朋友圈自动转发` 只处理统一好友选择器中选中的好友，好友为空时不执行，始终跳过当前账号自己的朋友圈以避免自动发布再次触发。每次开启记录新的启用时间，只处理发布时间不早于本次开启时间的内容；已确定成功入队、过滤、达到上限或最终失败的源 `snsId` 持久化保存，微信后续重复更新同一条记录时不会再次转发，关闭会取消待执行和正在准备媒体的任务，再次开启时清空旧去重并建立新基线。内容类型可独立控制文字、图片/图文、视频/视文、实况照片、网页/链接、音乐、其他卡片和未知类型，新增四类开关默认关闭；视频链接继续按可下载视频处理，网页链接、音乐、其他卡片和未知类型则把解析到的正文与链接写入文案模板后发布为文字朋友圈，没有可解析内容时跳过。支持固定或随机秒数延迟、每日提交上限、正文包含关键词、正文排除关键词、多条关键词替换规则、运行日志和文案模板；包含、排除和替换分别使用独立开关，开启后才显示对应输入框或规则入口。包含和排除可同时生效且排除优先，筛选始终匹配原朋友圈正文；包含关键词开启且填写多个关键词时，正文命中任意一个才转发。替换规则在原正文写入 `%content%` 模板变量前执行，每条规则分别设置原关键词和替换内容，替换内容允许留空以删除关键词，不会改动发布者、ID 或模板固定文字；匹配不区分大小写，同一次替换按较长关键词优先且替换结果不会再次触发另一条规则。旧配置已填写筛选关键词但没有开关字段时自动视为开启。模板变量包含原文、发布者、发布者 ID、类型及源朋友圈 ID，默认模板只保留原文。每日上限默认 `20`，`0` 表示不限制，并只按成功提交到微信发布队列的次数计数。
- `朋友圈关键词屏蔽` 是朋友圈分组内的独立界面功能，默认关闭。开启后，发现页时间线和好友个人主页中正文命中任意屏蔽关键词的动态会被压缩隐藏；多个关键词支持逗号、分号、竖线或换行分隔，匹配不区分大小写，正文为空时不隐藏。功能只处理列表绑定结果，不删除或修改 `SnsInfo` 数据，也不影响插件读取、自动点赞、自动评论、自动转发和发布通知。发现页以 Improve 完整条目 `measure(holder,item,int):boolean` 为主入口，并把旧 `SnsTimeLineBaseAdapter#createView(SnsInfo,int,int,View):View` 作为可选兼容入口；运行时缺少旧入口不会影响 Improve 屏蔽，也不会作为安装错误重试。个人主页覆盖 `SnsSelfAdapter#getView(int,View,ViewGroup):View`；绑定前恢复复用 View 的原始可见性和高度，绑定后再按当前真实 `SnsInfo.getTimeLine().ContentDesc` 判断，避免留下空白或污染下一行。Improve 的 `item` 只沿逆向确认的唯一两级无参访问链取得真实 `SnsInfo`，不读取或猜测正文控件；8.0.68 及以上在关键词配置有效时使用原生 `SnsUserUI`，关闭功能或清空关键词后恢复微信默认页面选择。四类定位结果分别写入 `DexMethodCache`，并通过 `DexInstallScheduler` 独立安装。
- `朋友圈关键词屏蔽` 逆向依据：8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 的旧时间线入口依次为 `ep.d`、`vp.d`、`sp.d`、`yp.d`、`wr.d`、`bs.a`、`rs.a`，签名均为 `(SnsInfo,int,int,View):View`；Improve 入口依次为 `qh3.x1.g(cj4.n0,vh3.a,int)`、`au3.y1.g(b05.t0,gu3.a,int)`、`e24.z1.c(cb5.s0,k24.b,int)`、`t54.z1.d(me5.s0,z54.b,int)`、`kb4.z1.d(uk5.s0,qb4.b,int)`、`tc4.i2.f(in5.s0,zc4.b,int)`、`wd4.i2.d(po5.s0,ce4.b,int)`，返回值均为 `boolean`。个人主页完整条目入口依次位于 `yl`、`qm`、`pm`、`um`、`so`、`so`、`ip`，均为 `SnsSelfAdapter#getView(int,View,ViewGroup):View`；Flutter 页面开关仅存在于 8.0.68、8.0.72、8.0.74、8.0.76，依次为 `t34.b.a()`、`k94.b.a()`、`va4.b.a()`、`yb4.b.a()`。
- `朋友圈过滤` 是朋友圈分组内的独立功能，默认关闭，支持“过滤所选好友”和“只看所选好友”两种模式。名单选择页提供好友、好友标签和聊天分组三个范围，标签或聊天分组可直接整组勾选，也可搜索后全选；聊天分组包含其全部子分组，提交时统一展开并只持久化当前选中的真实好友 wxid。企业微信联系人保留在通用好友候选中，群聊、公众号、单向联系人、群成员和 `wxid_hchat_group_*` 虚拟会话不会写入过滤名单。名单为空时不筛选，自己的朋友圈始终显示。配置变化在 `120ms` 防抖后刷新当前朋友圈本地列表：Improve 页面从当前时间线 `RecyclerView` 获取 Adapter，先调用其 `(int,int,int)` 归位入口，再读取 Adapter 的 `MvvmList` 字段并调用缓存的 `MvvmList.submitRefreshAll`；旧版页面调用 `SnsTimeLineVendingAdapter` 本地 Cursor 重载入口，本地入口不可用时才回退网络刷新。作者名单只改写微信首页首屏固定的 `SELECT rowid, * FROM SnsInfo WHERE (SnsInfo.sourceType & 2) <> 0 ORDER BY SnsInfo.createTime DESC LIMIT 10 OFFSET 0`：过滤模式使用 `NOT IN`，只看模式把所选好友与当前账号合并后使用 `IN`，并一次读取 `LIMIT 1000`，避免隐藏首屏后反复触发分页加载。View 过滤只保留为配置即时生效和兼容兜底。
- 时间线过滤只匹配首页首屏固定 SQL，不再对个人主页、后续分页或其它 `SnsInfo` 查询做宽泛插入；朋友圈专用 `SnsSqliteDB` 查询包装方法仍在 `(String,String[]) -> Cursor` 执行前完成改写，并按完整运行时 key 缓存。已确认的入口映射为 `8.0.49=k2.j`、`8.0.58=k2.k`、`8.0.66=m2.j`、`8.0.68=n2.j`、`8.0.72=p2.j`、`8.0.74=p2.B`、`8.0.76=p2.B`。公共数据库查询拦截器继续作为兼容兜底，不包装或缩减查询后的 Cursor。
- `朋友圈过滤` 仅在总开关开启时于主时间线相机按钮左侧注入文字入口“过滤”，点击后打开快捷弹窗，可直接关闭过滤、切换“过滤所选好友/只看所选好友”模式并修改过滤对象；开关在朋友圈页面存活期间发生变化时，通过 `MMActivity#removeOptionMenu(int)` 实时移除或重新添加入口。名单弹窗支持好友、企业微信联系人、好友标签和聊天分组筛选，保存后通过配置监听立即重算当前时间线，不再跳转模块设置页。8.0.49、8.0.58、8.0.66、8.0.68、8.0.72 使用 `SnsTimeLineUI#onCreate(Bundle)`；8.0.74、8.0.76 使用 `ImproveSnsTimelineUI#onCreate(Bundle)`；两类页面均通过继承自 `MMActivity` 的 `addTextOptionMenu(int,String,MenuItem.OnMenuItemClickListener)` 添加入口，七版均保留 `(int):boolean` 的菜单移除入口。8.0.68 同时安装旧版与 Improve 页面入口，以覆盖微信运行时页面切换。
- 自动转发复用朋友圈手动转发已经验证的 `TimeLineObject` 结构和原媒体准备器：图片优先读取最多 9 张微信本地原图，缺失时触发微信原生下载并等待落地；视频只接受下载完成且通过媒体校验的完整文件；VFS 媒体先复制到模块缓存。准备完成后分别调用 `WeChatSnsApi.uploadText`、`uploadTextAndPicList`、`uploadTextAndVideo` 或 `uploadTextAndLivePhotoList`，不启动 `SnsUploadUI`，因此不会出现编辑界面。API 返回 `true` 只表示新朋友圈已经写入微信本地发布队列并已尝试触发上传，不等同于服务端最终发布成功；微信已返回有效本地 ID 后不因触发上传结果重试，避免重复写入朋友圈。首次准备或入队失败会在 300 秒后重试一次，第二次失败后停止。普通图片不得因文件缺失退化成纯文字；多张实况和实况/静态封面混排按原顺序逐项发布，只有配套视频下载失败或校验无效的项降级为静态封面，其它项继续保留动态效果。`8.0.49` 没有实况静默上传入口，多项实况全部按静态图文发布；`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 的原生 `setUploadList(List)` 均逐项处理实况子元素，支持最多 9 项多实况和混排。媒体定位首次初始化通过统一 `DexInstallScheduler` 串行门执行，手动转发和自动转发不会并发访问共享 `DexKitBridge`。
- 公共 `WeChatSnsApi` 和脚本插件额外提供朋友圈缓存读取与原样转发链路。读取通过 `SnsCore` 的朋友圈专用 `SnsInfoStorage` 原生 getter，再分别调用七版稳定的 `(long) -> SnsInfo`、`(boolean,String,int,boolean,String,int,int) -> Cursor` 和 `(String,int,int) -> Cursor` 完成按 ID、按发布者和首页时间线查询；不访问 CoreStorage，也不向插件暴露微信原生对象。对外 Bean 包含无符号 `snsId`、发布者、显示名、发布时间、存储类型、内容类型、正文和媒体元数据；列表最多读取 200 条并按发布时间倒序返回。媒体准备与手动/自动转发共用 `SnsForwardContentResolver`，插件可把成功准备结果直接传给现有朋友圈发布 API；多实况列表接口同时接受单文件 Motion Photo、公开实况 Bean 和包含封面/视频路径的 JSON 项。该能力只覆盖当前账号本机已经缓存的内容，不承诺服务端完整历史。
- 逆向依据：8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 的 `replaceUserBySnsId` 方法依次为 `d2.K5`、`b2.b4`、`c2.y3`、`d2.u3`、`f2.c5`、`f2.Y2`、`f2.C3`，签名均为 `(long, SnsInfo) -> boolean`，并由时间线网络响应调用。七版时间线 ViewType 映射依次位于 `ep.q`、`vp.q`、`sp.r`、`yp.r`、`wr.r`、`bs.k`、`rs.k`，签名均为 `(SnsInfo, boolean) -> int`；文字内容类型为 `2`，视频为 `15`，图片为 `1/54`。
- `朋友圈自动刷新` 使用微信原生时间线下拉刷新请求，刷新间隔不设人为最大值且最少可设为 0 秒；调度器仍以 1 秒为实际轮询精度，并支持可跨零点的刷新时段。公共 API 在 DexKit 单线程预热阶段提前解析点赞和刷新入口，刷新调度器只在 `WARMUP` 就绪后启动，不能和公共 DexKit 解析并发。网络队列 Hook 会在预热后补装所有已确认候选类，并只接受首参数具有 `getType(): int` 的网络请求队列方法，不能因启动期先命中非执行类而停止补装。通过配置监听只在开关开启时创建调度器，关闭或功能销毁时立即释放调度线程。
- `朋友圈发布通知` 订阅与自动点赞相同的公共朋友圈入库事件，只提醒统一好友选择器中选中的好友。系统通知与 Toast 默认开启且可独立关闭，模板变量通过输入框下方的可点击项插入。模板留空时按发布者、类型和从微信对象直接取得的完整正文生成默认文案，不清洗或截断正文；系统通知使用高优先级消息样式、振动、朋友圈页跳转和发布者头像大图标。头像在通知后台线程中优先读取微信本地头像缓存，再尝试高清与普通头像 URL；头像不可用时仍正常发送通知。每次开启时记录本次开启时间，只提醒发布时间不早于该时间的朋友圈；发布时间未知的更新不会提醒。当前启用周期内已通知的 `snsId` 持久化保存，重启微信后继续去重；用户关闭开关时才同时清空启用时间和通知记录。旧版本首次升级到持久化去重实现时，如果开关已开启但没有通知记录字段，以本次启动时间建立新基线，不能重放升级前已经提醒过的朋友圈。
- `朋友圈伪集赞` 和 `朋友圈伪评论` 作为两个独立功能项放在 `朋友圈` 分组，均默认关闭并各自提供开关、清除数据和恢复默认操作；两项只在设置层拆分，运行时仍共享朋友圈记录、节点合并与长按菜单 Hook。伪互动作用于用户明确选择的现有朋友圈，不按朋友圈内容类型过滤，网页、音乐、视频号和其它扩展类型与普通文字、图片、视频一样可配置。两个长按菜单默认显示 `伪集赞[H]`、`伪评论[H]`，设置页可完整替换菜单文字，`[H]` 不是强制后缀；两项各自提供默认关闭的“隐藏长按菜单”开关，开启后只隐藏对应菜单入口，已经保存的伪点赞或伪评论仍继续在朋友圈显示。实际显示的点赞人、评论人和评论正文均不附加该标记。伪集赞和伪评论分别提供互不联动的“使用非好友”开关；关闭时只使用通用好友选择器中的微信好友和企业微信联系人，开启时只在对应伪互动选择器的当前加载请求中额外加入群聊成员。群成员候选不缓存、不写入或复用模块通用联系人集合，关闭任一开关后，该伪互动选择器下一次打开立即恢复普通候选，也不会影响另一个伪互动或其它功能的联系人选择器。伪集赞支持手动选择、按数量随机选择和凭空生成三种方式，自动勾选或随机选择人数不足时生成带稳定本地身份和随机昵称的虚拟点赞人；“凭空生成点赞”不依赖任何真实联系人，输入多少就生成多少，自动勾选、随机选择和凭空生成均不设置人为人数上限。排除名单只作用于自动和随机选择的真实候选人，随机结果在选择器中置顶且保存前可调整；“随机排序”只在用户保存时打乱一次，刷新和重新绑定不会再次变化。微信朋友圈实际按 `LikeUserList` 渲染可见点赞人，因此数量伪造使用真实或虚拟点赞节点，不只修改无法稳定显示的 `LikeCount`。伪评论联系人选择支持多选，并复用统一选择器对当前筛选或搜索结果执行全选和取消全选；设置页可维护不限固定条数的随机评论池，随机评论开启且评论池非空时为每位选中好友独立随机抽取内容，否则保留手动输入并可一次应用到多位好友。每位好友生成独立评论记录并使用本次统一选择的评论时间，后续仍可单独修改正文、时间、顺序或删除。朋友圈长按菜单只显示 `伪评论[H]`；进入伪评论列表后，仅在当前朋友圈已有伪评论时于列表底部显示 `清空伪评论`，没有伪评论时不显示。评论时间使用模块通用数字输入弹窗样式，分两排直接输入年、月、日、时、分、秒，并校验实际日期。
- `朋友圈伪转发` 作为独立功能项放在 `朋友圈` 分组并默认关闭。开启后在普通朋友圈长按菜单显示 `伪转发[H]`，菜单文字可自定义或单独隐藏；选择后复制源 `SnsInfo` 的完整时间线、媒体、卡片和 protobuf 数据，只把作者改为当前账号，并允许修改正文及显示日期时间。普通文字、图文、视频、实况、音乐、网页、小程序、视频号和其它原生卡片都保留原结构；卡片正文为空时不会把媒体 URL 填入编辑框。记录只写入本机朋友圈缓存，不上传服务器，允许重复创建，创建后仍可继续使用伪集赞和伪评论。
- 伪转发创建时从最多 2000 条真实时间线记录中按目标日期寻找相邻无符号序列区间，只生成数据库未占用且登记未使用的 ID，并显式写入和回读校验 `sourceType=6`，使记录同时进入发现页和自己主页的原生查询。克隆时移除源 `rowid`，清空源点赞、评论、我已点赞、私密状态和可见范围名单；任一关键字段、登记、插入或回查失败都会停止创建。公共朋友圈入库观察器同时过滤旧本地前缀和持久化登记 ID，避免伪记录触发自动点赞、自动评论、自动转发或发布通知。
- 伪转发删除复用微信原生朋友圈删除入口，但已登记记录只走本地死亡标记和缓存删除，不创建服务端删除请求；本地标记失败时会截断原删除流程，避免误删真实服务端内容。设置页提供 `清空伪转发`，只遍历持久化登记，删除失败的登记会保留供重试，不扫描真实朋友圈。保存和清理后优先让旧 Vending 时间线重新查询本地 Cursor；新版 Improve 时间线直接刷新当前列表，不发送 `NetSceneSnsTimeLine` 网络刷新，也不会为让历史日期立即出现而破坏原生时间顺序。
- 本地插入、删除、删除确认、来源位保护、旧时间线重载和新版 Improve 刷新已按 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 的实际结构定位并使用完整运行环境 key 缓存。微信后台清除来源位时，只保护已登记伪记录的 `sourceType` 位 `2/4`，真实朋友圈和其它来源位不受影响。设置页另提供默认关闭的 `调试日志`；关闭时不执行日志专用的 Cursor 扫描和延迟回查，真实创建失败和保护安装失败仍保留错误日志。
- 每条朋友圈可单独清空伪集赞或伪评论，单条伪评论也可独立删除；两个设置页的 `恢复默认` 分别关闭当前功能、在后台清理当前类型已保存的伪互动并恢复真实显示，清理失败时保留对应待清理标记并在下次微信启动后重试。该功能严格只修改本机展示和本地朋友圈缓存，不调用真实点赞、评论网络接口，也不会让对方或其他用户看到这些伪互动。配置只按从原生 `SnsInfo` 取得的真实无符号 `snsId` 存储，不接受对象身份哈希生成的临时 ID。伪评论是本机节点，点击回复时会在真实评论请求构造前终止并提示不能回复。
- 伪互动与朋友圈转发复用共享的朋友圈长按菜单定位和分发入口，避免重复 Hook 同一菜单。运行时解析 `SnsObject`，使用稳定的 `LikeUserList`、`CommentUserList` 以及彼此独立的总数和可见列表计数字段构造点赞、评论节点；模块为节点生成带保留命名空间的合成 `CommentId`，并同时校验互动类型。该标识由伪互动清理和朋友圈评论防撤回共享：重建时移除命名空间内的旧模块节点再添加当前配置，防撤回则把这些节点排除在真实评论删除判定之外，正文被旧版本加过删除标签也能正确清理。修改或删除伪互动后，旧节点身份会作为待清理状态持久化到原生缓存写回成功为止，期间评论发送保护仍同时识别当前节点与待清理节点。修改通过 `SnsInfo.setAttrBuf(byte[])` 更新对象，再调用原生 `SnsInfoStorage.update(long,SnsInfo)` 写回本地缓存；普通记录加载和网络刷新后的统一朋友圈入库事件都会按已保存配置重新合并，避免下拉刷新后恢复原显示。刷新回调先同步修正本次入库对象的展示数据，再把缓存查询、持久化写回、普通修改和批量恢复放入单线程后台队列，记录加载 Hook 先按 `snsId` 判断是否存在配置，不对无关朋友圈重复解析 protobuf。
- 普通朋友圈使用微信原生上下文菜单回调；朋友圈首页按日折叠的聚合卡片不经过该回调，因此共享菜单层另外定位 `FoldImproveTimelineItem` 的绑定完成方法，并从折叠 protobuf 的有序重复子项 `snsId` 列表解析真实朋友圈。长按聚合卡片后先选择具体的真实朋友圈，再显示共享入口当前启用且适用于该条内容的全部操作，包括朋友圈转发、伪集赞、伪评论、伪转发及后续注册到共享菜单的功能；不按父项、首条内容或列表位置猜测。折叠详情页中的单条朋友圈继续使用原生长按菜单。该绑定和子项结构已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`。
- 朋友圈评论服务存在普通文字评论入口和带评论节点对象的 UI 回复入口，伪评论保护同时覆盖两者。`8.0.49`、`8.0.58` 的节点入口为 8 个参数，`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 为 7 个参数；七版均在第 4 个参数携带被回复节点，返回对应版本互动节点类型。共享长按菜单定位、朋友圈记录读取、互动 protobuf 结构、缓存更新和两类评论入口的适配依据均已覆盖上述七个版本。
- `朋友圈原图上传` 属于实用功能，设置页放在 `朋友圈` 分组，提供一个总开关，默认关闭。开启后 hook 图库预览页向朋友圈返回媒体结果的方法，把朋友圈强制压缩标记改为不压缩，并避免视频因朋友圈入口进入编辑/转码路径；同时 hook 朋友圈 `SnsMediaStorage` 的上传图创建方法，定位锚点为 `MicroMsg.snsMediaStorage` 与 `SnsCompressResolutionFor2G/3G/4G/Wifi`，并优先调用微信内部 `convertImg2WxamWithoutZip(origPath, targetPath)` 将原图无压缩写入上传目标。无压缩转换不可用或失败时，才回退到微信 VFS 复制兜底；兜底仍失败则放行微信原逻辑，避免发布失败。
- 逆向依据：`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74` 的图库返回方法均在 `com.tencent.mm.plugin.gallery.ui.ImagePreviewUI`，签名稳定为 `(Intent, boolean, boolean) -> void`，方法名分别为 `q6` / `R6` / `Z6` / `f7` / `w7` / `V6`，共同写入 `CropImage_OutputPath_List`、`key_select_video_list` 和 `CropImage_Compress_Img`。朋友圈图片上传图创建入口均位于 `com.tencent.mm.plugin.sns.storage.*`，共同带 `MicroMsg.snsMediaStorage` 与 `SnsCompressResolutionFor2G/3G/4G/Wifi`，前 4 个参数稳定为 `(String dir, String src, String name, boolean isUpload)`，后续还有压缩尺寸参数；已确认 8.0.49=`i2.b(String,String,String,boolean,int,int)`、8.0.74=`m2.P0(String,String,String,boolean,int,int)`。无压缩转换入口同样位于 `com.tencent.mm.plugin.sns.storage.*`，共同带 `convertImg2WxamWithoutZip origPath:%s OutOfMemoryError! rollback`，签名为 `(String origPath, String targetPath) -> boolean`。相关定位都走 `DexMethodCache`，缓存 key 带微信版本、热更新和 ClassLoader 指纹。
- `朋友圈上传尾巴` 属于实用功能，设置页放在 `朋友圈` 分组，提供总开关、`SDK ID` 和 `SDK 名称`。开启且两项都非空时，在微信原生编辑页、模块或脚本共用的朋友圈提交入口覆盖 SDK 来源；只填写其中一项时不注入，避免生成不完整来源信息。提交入口和两个 SDK 设置方法复用公共 `DexFinder` 的朋友圈发布 API 定位与运行时缓存。
- `朋友圈防撤回` 属于实用功能，设置页放在 `朋友圈` 分组，提供 `朋友圈防撤回`、`朋友圈评论防撤回` 与 `强制旧版个人主页朋友圈` 三个开关，均默认关闭。动态防撤回开启后拦截 SQLite/WCDB 对 `SnsInfo` 的写入与查询：删除态更新携带 `sourceType=0` 时移除该字段，并向朋友圈文案写入默认或自定义删除标签；增量更新缺少 `type/field_type` 时按更新条件回查旧记录类型。评论防撤回恢复到首次加入自定义标签时的实现：拦截 `SnsInfo.attrBuf/field_attrBuf` 写入，解析新旧 `CommentUserList`，把旧列表中未出现在新列表的真实评论直接恢复，更新 `CommentCount/CommentUserListCount`，并写入默认或自定义删除标签。伪评论使用 protobuf 类型和保留 ID 命名空间识别，不参与删除判定；伪互动重建时也按该 ID 清理旧节点，因此被旧版本误加删除标签的伪评论副本会在重新应用后移除。该实现不再依赖 `processCommentDelAction`、`NetSceneSnsObjectOp` 操作码或评论总数变化。查询增强和强制旧版个人主页逻辑保持不变。模块只保护本机已缓存内容，不重新拉取服务端已经不可见的数据。
- 逆向依据：WeKit 的 `拦截朋友圈删除` 和 `朋友圈查询增强` 使用同一数据库保留思路。评论数据使用微信运行时 `com.tencent.mm.protocal.protobuf.SnsObject` 解析；`CommentCount`、`CommentUserListCount` 与 `CommentUserList` 字段名稳定，评论身份按服务端 ID、本地 ID，或作者、时间和正文生成去重键。评论内容字段兼容 `h/m`，时间字段兼容 `i/n`，本地评论 ID 字段兼容 `j/m/o/n/p`，服务端 ID 字段兼容 `r/u/q/t`。实现不依赖朋友圈 UI 绑定方法、评论删除方法混淆名或瞬时调用栈。
- `去除朋友圈广告` 属于实用功能，设置页放在 `朋友圈` 分组，提供一个总开关，默认关闭。开启后 hook `com.tencent.mm.plugin.sns.storage.ADInfo(String)` 构造方法，在广告信息 XML 解析前直接返回，阻止朋友圈广告信息解析和展示。
- 已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74` 均存在同名同签名 `ADInfo(String)` 构造，且 8.0.74 构造末尾调用 `feed(str)` 解析广告信息。该功能使用稳定类名和构造签名直接反射安装，不引入 DexKit 缓存。

群聊标签：

- `群聊标签` 是实用页签 `群组` 分组里的共享分类功能，配置保存在独立的 `Hchat_group_chat_labels` 存储中。设置页支持新增、改名、删除多个标签，并复用统一群聊多选器维护每个标签包含的群聊；标签名称不能为空且不允许重复，标签之间允许包含相同群聊。
- 所有包含群聊且支持多选的统一 `ContactPickerPage` 都能使用群聊标签，但标签区域只在群聊分组中显示：纯群聊选择器直接显示，好友/群聊/公众号混合选择器只有切换到 `群聊` 时显示，`全部`、`好友`、`公众号` 和好友标签分组不显示。点击标签会一次选中标签内当前仍存在的全部群聊，再次点击已全选标签会取消这些群聊；多个标签或手工选择命中的相同群聊按 wxid 去重，部分选中时显示 `已选 x / y`。单选联系人入口不显示群聊标签，编辑标签自身成员时也不递归显示其它标签。
- 标签选择最终向原功能写入真实群聊 wxid，不把模块私有标签 ID 写进任务、模板或名单配置，因此现有运行时无需增加标签解析分支。标签成员后续变化不会静默改变已经保存的旧任务；需要让旧任务使用新成员时，应重新进入该任务的群聊选择器并保存。标签删除同样不移除各功能已经保存的群聊。

进退群监控：

- `进退群监控` 属于实用功能，设置页放在 `群组` 分组。原有 `退群系统消息` 开关和行为保持不变，默认关闭；开启后监听 `chatroom.memberlist` 变化，用上一份成员快照和当前成员列表做差集，成员减少时插入本地系统消息；首次看到某个群只建立快照，不把已有成员当成退群。退群系统消息和邀请详情共用一套适用群聊范围，默认 `全部群聊`；切换为 `指定群聊` 后只对群聊多选器中保存的群插入系统消息，未选择群时不插入。该范围独立于进退群回复的监听群，不影响成员快照、邀请次数累计和主动回复。
- 退群命中后往对应群聊插入一条本地系统消息，模板可自定义，默认 `%displayName%(%userWxid%) 退出了群聊`。支持 `%displayName%`、`%groupNickname%`、`%userName%`、`%remarkName%`、`%userWxid%`、`%groupName%`、`%time%`；其中 `%displayName%` 保持原有 `群内昵称(微信昵称)[备注]` 的组合规则，没有群内昵称时显示 `微信昵称[备注]`，备注为空或与前面名称重复时不单独显示。普通变量会做 XML 转义，`%userWxid%` 使用微信系统消息支持的 `_wc_custom_link_` 标签生成 Hchat 专属资料页链接；wxid 高亮颜色可配置，默认 `#576B95`，当前处于群聊时打开资料页会带上群聊上下文。
- `邀请详情` 开关默认关闭，开启后在群成员新增时读取 `chatroom.roomdata` 里的邀请者字段，能确认邀请者和被邀请者后插入本地系统消息。模板可自定义，默认显示邀请者、被邀请者和累计邀请次数，支持 `%inviterName%`、`%inviterGroupNickname%`、`%inviterWxid%`、`%inviteeName%`、`%inviteeGroupNickname%`、`%inviteeWxid%`、`%inviteCount%`、`%groupName%`、`%time%`；两个 wxid 变量使用同一套可点击资料页链接，其余变量做 XML 转义。邀请者和被邀请者名称优先显示联系人备注，没有备注时回退群内昵称，最后回退微信昵称。邀请次数按 `memberlist` 从无到有的成功进群事件累计，即使 `邀请详情` 开关关闭也继续记录；同一成员退群后再次被邀请进群要再次计数，不按时间窗口去重。首次看到新群后 15 秒内只把成员变化当作微信同步已有成员处理；一次性新增 10 人及以上也当作批量同步，不插邀请详情、不累计、不触发进群回复，避免新进已有群时历史成员邀请记录刷屏。`memberlist` 与 `roomdata` 分次写入时只在后续 `roomdata` 更新事件到来后补插，不做定时重试；确认不到邀请者时不插入，避免误判普通扫码进群或退群提示。
- 退群检测只依赖本地 `chatroom` 表成员列表差集，因为普通成员主动退群时微信通常没有稳定系统提示。群内昵称优先用快照里的群昵称缓存；联系人备注 `remarkName` 和微信昵称 `nickname` 分开处理，备注只作为独立补充显示，不再替代微信昵称导致 `备注(备注)`。
- `进退群回复` 是脚本“进退群”功能的模块内置迁移，单独总开关默认关闭，只对设置页里选择的监听群生效。成员增加时按进群模板回复，成员减少时按退群模板回复；支持文本、卡片、文本+卡片、整体延迟、提示/媒体精细延迟、媒体发送顺序，以及图片、语音、表情、视频、文件和收藏附件，整体延迟默认 `0` 秒。模块发送出去的进群文字回复会独立读取微信进群提示中的成员名称并短期缓存，不依赖 `邀请详情` 开关；`chatroom.memberlist` 已先变化但联系人和群资料尚未落库时，发送线程最多等待 2 秒补齐名称，期间不允许空的 `displayname/roomdata` 覆盖已有有效名称，随后 `%userName%`、`%groupNickname%` 和 `[AtWx]` 共用该名称回退，只有微信确实未提供任何名称时才显示 wxid。文本和卡片模板兼容脚本变量 `%userName%`、`%groupNickname%`、`%userWxid%`、`%realNameTail%`、`%gender%`、`%region%`、`%groupName%`、`%time%`，多个模板用 `||` 分隔时随机选择；文本模板里的 `[AtWx=%userWxid%]` 和 `[AtWx=wxid]` 会解析为真正的群聊 @。
- `监听群与专属设置` 二级页负责选择监听群并按群编辑专属规则。监听群列表支持选择部分群或全选当前搜索结果并确认批量删除；群专属设置页顶部提供模板选择项，可直接绑定、切换模板或选择“不使用模板”；每个群可以单独关闭进群回复或退群回复；提示模板、媒体和精细延迟分别支持“跟随全局”或“单独设置”，媒体额外支持“不发媒体”。提示模板单独设置时使用该群自己的提示类型、文本、卡片和文本/卡片顺序，并提供随机填充和恢复全局入口；文本模板为空表示该群不发送文本，卡片标题或描述为空时回退全局对应字段。媒体单独设置时使用该群自己的媒体顺序、发送序列和各类附件路径，发送序列可包含 `favorite`，附件路径为空表示该群不发送对应媒体；延迟单独设置时使用该群自己的提示、图片、语音、表情、视频、文件和收藏延迟。群专属设置页底部提供删除当前群操作，即使群已经不在微信联系人列表中，也能从保留的监听群条目进入并删除；单个或批量删除都必须同时清理监听列表、进退群禁用列表、模板绑定及对应群全部专属配置键。相关选项页选中后不自动返回，需点击返回回到原页面，并保持群专属设置页滚动位置。旧的退群系统消息开关不受这些专属回复规则影响。
- `回复模板管理` 保存一整套进退群回复配置，包括模板启用状态、进群/退群回复开关、提示类型、文本/卡片、媒体顺序与附件路径、精细延迟。`批量套用模板` 只面向已选择的监听群，进入页面时默认全选，并可对当前搜索结果一键全选或取消全选；每个群最多绑定一个模板，绑定后该群按模板配置执行，群专属编辑页显示模板选择与摘要。选择“不使用模板”后恢复该群原有专属设置；未绑定模板的群继续按原有“群专属优先、未配置则跟随全局”的规则执行。

改名监控：

- `改名监控` 是 `群组` 分组里的独立功能，使用独立的 Feature、运行时和 `Hchat_group_rename_monitor_config` 配置，不依赖进退群监控的总开关、监听群、模板或配置文件，也不迁移此前临时放在进退群监控里的改名配置。
- 运行时监听 `chatroom.roomdata` 与 `memberlist`，只比较前后都存在的稳定成员。首次快照、成员增删、15 秒预热、一次新增 10 人及以上和一次变化 10 个及以上昵称均不提醒；群昵称数据没有完整解析时不覆盖有效快照，避免把数据库同步误判成改名。
- 群内昵称为空表示该成员没有设置独立群昵称，微信实际显示其微信昵称。改名模板中的 `%groupNickname%`、`%oldGroupNickname%`、`%newGroupNickname%` 遇到空值时必须回退微信昵称，不显示“未设置”；微信昵称缺失时再回退备注和 wxid。检测快照仍保留原始空值，以便正确识别设置或取消群昵称。
- `插入系统消息` 和 `发送改名提醒` 是两个默认关闭的独立开关。系统消息适用范围默认 `全部群聊`，可切换为 `指定群聊` 并使用群聊多选器独立选择；指定模式未选择群时不插入。该范围不复用发送提醒的监听群，也不影响改名快照检测。系统消息模板可自定义，默认 `%oldGroupNickname% 改名为 %newGroupNickname%(%userWxid%)`，例如 `测试4 改名为 测试(wxid)`；支持 `%userName%`、`%oldGroupNickname%`、`%newGroupNickname%`、`%userWxid%`、`%realNameTail%`、`%gender%`、`%region%`、`%groupName%`、`%time%`，普通变量做 XML 转义，`%userWxid%` 生成可点击资料页链接。发送提醒只作用于选择的监听群，支持整体延迟、文本、卡片、文本+卡片及顺序，卡片使用改名成员头像，发送失败时回退普通文本。
- 文本、卡片标题和描述支持 `%userName%`、`%oldGroupNickname%`、`%newGroupNickname%`、`%userWxid%`、`%realNameTail%`、`%gender%`、`%region%`、`%groupName%`、`%time%`，多个候选用 `||` 分隔随机选择，文本中的 `[AtWx=wxid]` 会转换为真正的群聊 @。旧变量 `%groupNickname%` 与 `%newGroupNickname%` 含义相同，仅保留运行时兼容，不再显示在变量选择面板。
- 监听群可进入独立专属设置，覆盖提示类型、文本/卡片顺序、文本、卡片标题和描述；已失效群仍保留在监听群列表，可进入专属页删除并同步清理该群全部配置。
- `提醒模板管理` 支持保存多套完整改名提醒配置，包括模板启用状态、整体延迟、提示类型、文本/卡片顺序、文本、卡片标题和描述。每个监听群最多绑定一个模板，绑定后模板配置优先于该群专属设置；选择“不使用模板”恢复本群专属设置，停用模板后已绑定群不发送改名提醒，删除模板会同步解除所有群绑定。
- `批量套用模板` 只面向已选择的改名监听群，进入页面默认全选全部监听群，并可对当前搜索结果一键全选或取消全选；再次套用会替换同一群原有模板绑定，不会产生重复绑定。改名监听群管理页也支持选择部分群或全选当前搜索结果并确认批量删除，删除时同步清理对应群的模板绑定和专属配置。

多选撤回：

- `多选撤回` 属于实用功能的 `聊天` 分组，总开关默认关闭。开启后，在聊天页多选消息并打开微信原生分享菜单时注入 `批量撤回[H]`；只有选中消息全部能解析出有效本地 `msgId` 且全部由当前账号发送时才显示，混入他人消息或无法确认归属时不显示。
- 点击 `批量撤回[H]` 后直接遍历当前菜单处理器持有的原生消息对象，立即构造并提交 `NetSceneRevokeMsg(原生消息对象, "你撤回了一条消息", "")`，不再按 `msgId` 查库、重建消息对象或每条等待 `250ms`。全部请求入队后通过共享 `MultiSelectMessageUi` 调用微信原生多选组件的完整退出方法，由微信关闭底栏、清空选择并刷新聊天页；不能调用 `Activity.onBackPressed()`，否则部分微信聊天容器会直接结束并返回桌面。退出方法按“被真实多选菜单点击处理器调用的无参聊天组件方法”动态定位，从当前菜单处理器对象图解析对应组件实例并按运行环境缓存，不写死各版本混淆名。第三个字符串必须为空；传入消息正文会让构造器先改本地状态、服务端再拒绝错误请求，表现为仅本地撤回并提示系统繁忙。菜单创建方法只按调用 `WWAPIFactory` 的关系精确定位，菜单点击方法通过 `FinalShareCountByType` 精确定位，不再 Hook 全部同名回调，也不再包装底部分享按钮或使用选中项缓存。选中消息在菜单创建和点击时直接从当前处理器对象解析；`8.0.66` 以上优先读取处理器的 `List` 字段，`8.0.49/58` 沿聊天组件引用找到无参返回 `List` 的选中消息方法。菜单定位、原生退出调用和 `NetSceneRevokeMsg` 三参数构造已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`，结果按运行环境写入独立 DexKit 缓存。

语音转发保存：

- `伪造语音时长` 属于 `语音` 分组，设置单位为秒，范围 1-60；输入框只在开关开启后显示。运行时通过 `MicroMsg.SceneVoice.Recorder` 与 `Stop file success: ` 两个精确字符串定位微信原生录音器类，再从该类中选择唯一的非静态、无参、返回 `long` 的录音长度方法，并在方法执行前直接返回设置的毫秒数；不固定混淆类名、方法名或字段名。已横向确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 的录音器类均只有一个符合条件的方法，且 `Recorder.stop()` 会调用它作为语音长度；8.0.49 与 8.0.76 导出实现均为当前 `elapsedRealtime` 减去录音开始时间。定位结果写入独立 DexKit 缓存。开关开启后，Hchat 公共语音发送 API 在完成 `voiceinfo` 时也统一写入设置值，因此右滑复读、收藏语音、自动回复、红包回复、定时任务和脚本发送等模块语音均使用伪造显示时长；文件真实时长或调用方传入时长仍用于长语音/CDN发送判断，不改变实际音频内容。
- `兼容低版本小程序` 属于 `杂项` 分组，默认关闭，只保留两条已确认有效的路径。第一条与 WeKit 一致：主进程创建小程序启动请求时，把 `CgiLaunchWxaApp` 的整数 `libVersion` 参数固定替换为 `9999`，使服务端按兼容的基础库版本继续启动；通过 `MicroMsg.AppBrand.CgiLaunchWxaApp|func:1122` 与完整构造日志精确定位 17 参数构造器。第二条处理小程序自己的升级判断：adidas 阿迪达斯 397 版会读取 `wx.getAppBaseInfo().SDKVersion`，低于 `3.5.5` 时调用 `wx.updateWeChatApp()`；当前 3.4.10 基础库会转换为 `private_openUrl` 打开微信官方升级页。功能通过 `private_openUrl`、`rawUrl`、`geta8key_open_webview_appid` 定位该 JSAPI，只在目标为 `support.weixin.qq.com/update` 或 `szsupport.weixin.qq.com/update` 时清空 URL，让原方法返回失败回调并释放跳转锁。公共 WebView 入口不再拦截，因为直接跳过 WebView 只能隐藏升级页，无法恢复已被中止的小程序启动流程。主进程完成两个入口的 DexKit 定位与缓存，`:appbrand*` 子进程从跨进程缓存安装 JSAPI Hook，不额外创建 DexKit。两个入口已横向确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76，每版均唯一命中。此前未解决复现样本的 Page/Service 初始化 JSON、`getPublicLibVersion.clientVersion` 和 `SDKVersion` 写入保持删除。功能不修改 `WAVersion.json`、不替换真实基础库，也不补充低版本微信缺失的 JS API；越过版本判断后仍可能因真实能力缺失而无法使用。修改设置后需强制停止并重启微信。
- `跳过小程序视频广告` 属于 `杂项` 分组，默认关闭。功能在主进程和 `:appbrand*` 子进程精确 Hook `com.tencent.mm.appbrand.commonjni.AppBrandJsBridgeBinding.subscribeHandler(String,String,int,String)`；收到 `onVideoTimeUpdate` 时解析第二个字符串参数的 JSON，把 `position` 改为 `60`、`duration` 改为 `1` 后交给原生 JS 分发，使视频广告满足播放完成条件。该非混淆类名、方法名、四参数签名、`mJsRuntimeInst` 字段读取和 `nativeSubscribeHandler` 调用已横向确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76，均保持一致；入口使用精确反射定位，不运行 DexKit。修改设置后需强制停止并重启微信。
- `跳过全局小程序开屏广告` 属于 `杂项` 分组，默认关闭。功能拦截微信 `AppBrandSplashAd` 的全局广告资格判断，开关开启时直接返回 `false`，使小程序不进入开屏广告创建和展示链路。主进程通过 `MicroMsg.AppBrandAdUtils[AppBrandSplashAd]` 与 `isAdContact, appId:%s, canShowAd:%s` 两个精确字符串唯一定位单参数布尔方法，并写入运行环境隔离的 DexKit 缓存；`:appbrand*` 子进程从跨进程缓存重新解析并安装同一 Hook，缓存尚未生成时通过统一 `DexInstallScheduler` 有限补装，不在子进程创建 DexKit。该入口已横向确认微信 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均唯一命中；8.0.49 参数为小程序运行时，后续版本参数为 `AppBrandInitConfigWC`，运行时不固定混淆类名或参数类型。修改设置后需强制停止并重启微信。
- `虚拟定位` 同步自下游 `liuyuehua666/Hchat2`，位于 `杂项` 分组并默认关闭。设置页可通过微信原生 `com.tencent.mm.plugin.location.ui.RedirectUI` 和 `map_view_type=8` 搜索选点，也可手动输入纬度 `-90~90`、经度 `-180~180`。选点结果优先读取结构化的 `kwebmap_slat` / `kwebmap_lng`，再读取 `KLocationIntent` 中七版稳定的纬度 `d`、经度 `e` 字段，并保留下游字符串格式解析作为末级兼容。运行时用 `MicroMsg.SLocationListener`、`MicroMsg.SLocationListenerWgs84`、`MicroMsg.DefaultTencentLocationManager` 三组精确字符串锚点定位 `onLocationChanged(TencentLocation,int,String)`，把描述符写入 `DexMethodCache`；收到首个定位对象时只 Hook 其具体非抽象 `getLatitude()` / `getLongitude()`，关闭功能时保持原值。主进程负责 DexKit 定位，`:appbrand*` 子进程通过跨进程缓存重新解析同一批方法，不在子进程创建 DexKit，因此同时覆盖微信普通页面和小程序定位。回调结构、`RedirectUI`、`map_view_type`、`KLocationIntent` 及结构化经纬度返回字段已横向确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76。
- `语音转发保存` 属于实用功能，设置页拆成 `聊天语音转发`、`聊天语音保存`、`多选语音转发`、`多选语音合并`、`收藏语音转发`、`收藏语音保存` 六个开关；旧版总开关只继续作为原有五项的默认值，新增加的多选语音合并默认关闭。开启后普通聊天语音和收藏语音长按菜单按开关显示 `转发[H]`、`保存[H]`；多选至少两条语音时显示 `合并语音[H]`，点击后通过模块弹窗选择转发或保存。合并过程在后台按多选顺序把每条语音统一解码为 24 kHz 单声道 PCM，再重新编码为一条 Silk 语音，不直接拼接原始 Silk 或 MP3 文件；选择转发时先显示联系人选择器，用户确认目标后再后台合并，并通过 `WeChatApis.media().voices().send(...)` 作为一条微信语音消息发送，避免转码期间阻塞联系人选择；选择保存时才转换为 MP3 并写入 `/storage/emulated/0/Android/media/com.tencent.mm/Hchat/Voice/Hchat_merged_voice_时间.mp3`。转发使用的临时 Silk 在发送或取消后清理，合并加载窗关闭后必须等下一帧再执行发送或后续界面操作，弹窗根视图的清理失败不能遗留全屏触摸遮罩。通用转发功能的 `转发收藏` 开启时，收藏语音长按菜单只保留通用 `转发[H]` 和原有保存项，避免重复。单条和多选语音转发都在独立单线程队列中逐条执行，每次发送之间保留 500ms 间隔；公共语音 API 只把微信要求的最终发送步骤切回主线程，不再在一个主线程消息中遍历完整收件人列表。聊天语音时长与右滑复读共用 `VoiceMessageDurationResolver`，优先读取当前原生消息对象字段，再解析 `fileName:duration:flag` content，均无时长时按 FileName 读取原生 `voiceinfo.VoiceLength`；联系人选择用 Miuix `WindowDialog`，列表需要有头像、搜索以及 `全部 / 好友 / 群聊 / 标签` 分组筛选，标签分组可先选某个好友标签，再勾选该标签里的一个或多个好友发送；底部全选/取消全选只作用于当前筛选和搜索结果，底栏按钮不能使用过低的固定高度导致系统字体缩放时裁字；单条保存仍在后台转成 MP3 并写入同一 `Voice` 目录。

文本转语音：

- `文本转语音` 位于实用功能的 `语音` 分组，`文本转换语音模式` 与 `文字转换语音播放` 默认关闭。前者开启后，在当前聊天输入 `#tts` 可开启或关闭普通转换模式，输入 `#tts e` 可切换或关闭英文模式；模式按会话独立保存于当前进程，关闭功能时立即清空，普通输入由有界单线程队列合成后通过 `WeChatApis.media().voices().send(...)` 作为微信语音发送。后者开启后，普通文字消息长按菜单置顶显示 `转语音播放[H]`，点击后在后台合成并使用本地播放器播放，同时继续执行微信原生菜单点击收尾，避免消息保持选中状态。
- 转换引擎可选择在线语音、系统默认 TTS 或任一已安装 TTS 引擎。在线语音保留 64 个可见角色选项，设置值使用 `序号:voiceId` 区分重复名称或重复 voiceId；英文模式使用专用英文角色。TTS 引擎和角色选择复用文字转语音播报的引擎目录、候选回退顺序与 Android `Voice` 读取逻辑；跟随系统默认时，角色读取和实际文件合成都会在默认入口初始化失败后临时尝试系统默认包及其它可用引擎，手动指定引擎不回退。发送时先通过所选 TTS 引擎生成本地音频，再交给公共语音 API 转换发送；播放和发送始终使用同一引擎配置。统一语速范围为 `0.1x` 至 `3.0x`，以 `0.1x` 为步进并默认 `1.0x`；系统 TTS 调用 `setSpeechRate`，在线语音把倍率映射到接口的 `speech_rate=-9..20`，发送和长按播放共用同一语速。
- 网络请求、TTS 合成、下载和音频转换不得阻塞微信主线程。队列最多保留 8 个等待任务，网络音频上限为 16 MiB；功能销毁时取消网络请求、停止 TTS、清空队列并释放播放器。缓存文件只写入微信缓存目录，发送完成、播放完成、失败或下次初始化时清理。发送失败时，仅当用户仍停留在原会话且输入框仍为空时恢复原文字，不能覆盖用户后来输入的内容。
- 多选语音相关入口只在选中消息全部都是语音时注入微信原生多选分享菜单；`逐条转发语音[H]` 由多选转发开关控制，`合并语音[H]` 由多选合并开关控制且至少需要两条语音。微信原生的逐条/合并/企业微信/元宝等菜单项保持不变；逐条转发复用 Hchat 语音发送接口依次发给选中的好友、群聊或标签联系人，合并语音先弹出模块的转发/保存选项。用户确认发送或保存成功后通过 `MultiSelectMessageUi` 调用原生多选组件退出方法，取消操作时保持多选。两项功能与多选撤回共用 `MultiSelectMessageMenuLocator`、`MultiSelectMessageResolver` 和原生退出逻辑，在菜单创建和点击时直接从精确菜单处理器解析选中消息及退出组件，不再包装底部转发按钮，也不维护短时语音源缓存。菜单创建只按调用 `com.tencent.wework.api.WWAPIFactory` 的关系精确定位，不能给被调用方法附加 `com.tencent.mm`、`com.tencent.wemeet.app` 字符串条件；菜单点击通过 `FinalShareCountByType` 精确定位，退出方法通过该菜单点击处理器的调用关系唯一定位。`8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均唯一命中。微信已占用 item id `0..5`，Hchat 自定义项使用高位独立 id 并按运行环境缓存定位结果。

QQ 点歌：

- `QQ点歌` 属于娱乐功能的独立 `点歌` 分组，总开关默认关闭。默认触发词是 `点歌`，发送 `点歌 歌名` 后搜索 QQ 音乐；`点歌发送卡片` 默认开启，`点歌发送语音` 默认关闭，两个开关彼此独立，同时开启时按先卡片后语音的顺序发送。只有开启卡片时才下载封面并发送音乐卡片，开启语音时下载 QQ 音乐播放地址并发送歌曲语音；提交发送或失败后立即清理本次下载的缓存文件。两个开关都关闭时不发送内容并提示至少开启一种发送方式。搜索不到、播放地址不可用、音频下载失败或消息发送失败时，收到的指令优先引用原消息回复失败原因。普通指令只订阅 `message` 表数据库变化并只处理 `INSERT`，不再同时接收 `local_send`、PB 服务端回包和数据库更新，也不使用时间窗口去重；公共数据库监听器会把 wrapper 到 WCDB/Android SQLite 以及 `insert` 到 `insertWithOnConflict` 的同线程嵌套调用收敛为一次最外层写入事件，避免别人发送一条点歌指令触发两次。只处理 30 秒内的文字消息。网络搜索、封面和歌曲下载统一在单线程后台队列执行，不阻塞微信数据库回调或发送按钮。
- 自己发送的指令始终可以点歌；开启 `拦截自己的点歌指令` 后，点击发送按钮会清空输入框并直接执行点歌，不把指令发进聊天，关闭时则等待自己发出的文字消息入库后执行。发送按钮与脚本插件共用 `ScriptSendButtonHook`，模块点歌命中后不再向脚本派发同一点击，避免同时启用原 QQ 点歌脚本时重复发送。
- `允许他人点歌的聊天` 使用好友/群聊多选器；未选择时只有当前账号的指令生效，选择后对应私聊好友或群成员也可触发。聊天内发送 `开启点歌`、`关闭点歌` 可快速增删当前会话并插入本地系统提示。设置还支持多个逗号分隔触发词、自定义 AppID、使用点歌人头像替换封面、使用点歌人群昵称/微信昵称替换歌手，以及开启自定义 singer 后用 `歌名&显示名` 临时指定或填写默认 singer。
- QQ 音乐搜索先调用 `music.search.SearchCgiService/DoSearchForQQMusicDesktop`，失败时回退 `smartbox_new` 与歌曲详情接口；播放地址沿用脚本的 `MtLimitFreeSvr.Obtain` 临时 vkey 路径，再回退 `music.vkey.GetVkey/UrlGetVkey`。音乐卡片通过公共 `WeChatMediaApi.shareMusicWithMetadata(...)` 发送，分别设置歌曲落地页、播放地址、歌词、专辑图和缩略图，默认 AppID 为 `wx485a97c844086dc9`。
- 普通聊天语音仍通过 `ChattingItemVoice`、`Retr_Msg_content`、`Retr_Msg_Type` 锚点定位菜单创建/点击；`8.0.49/58/66/68` 的菜单点击参数通常直接带 `com.tencent.mm.storage.*` 消息，`8.0.72/74` 会传 MVVM 包装对象（如 `rd5.d -> we5.a -> storage.f9`），解析消息时必须递归拆字段，不要只读固定参数位。收藏语音主收藏页通过 `OnCreateContextMMMenu` 定位微信列表菜单，搜索/筛选页通过 `onCreateContextMenu(ContextMenu, View, ContextMenuInfo)` 定位 Android 菜单；模块只处理收藏列表的外层条目长按，不注入 `FavoriteVoiceDetailUI`。新增定位必须写入 DexKit 缓存，缓存失效后再重新定位。
- 普通聊天语音转发与右滑复读统一使用当前已持有原生消息对象读取时长，优先调用显式 `getVoiceLength/getVoiceLen/getDuration` 或读取 `field_voiceLength/voiceLength/VoiceLength/duration/field_duration`，失败后解析语音 content 的 `fileName:duration:flag` 结构；新版本两处均不携带时长时，再按 FileName 调用微信原生语音信息查询包装方法，并从返回对象的 `ContentValues["VoiceLength"]` 读取毫秒值，最后才使用默认时长。两条链路共用文件名和消息 ID 时长缓存，收藏语音仍按收藏数据项自身时长字段解析。已横向确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 都存在唯一的静态 `(String FileName) -> VoiceInfo` 包装方法，其内部调用语音存储的 `SELECT ... VoiceLength ... FROM voiceinfo WHERE FileName=?` 查询；运行时通过该查询方法的调用关系定位并缓存包装方法，不硬编码各版本的 `cm0/ps0/ox0/py0/u11/w21/y21` 混淆包名、方法名或时长字段名。
- 收藏语音已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74` 均有 `FavoriteVoiceDetailUI`，收藏项类型为 `field_type=3`；本地文件路径要按微信详情页播放逻辑从收藏 data item 的动态 `(gp0)->String` helper 取得，不要写死 `o72.x1` / `om1.o1` 这类版本相关类名。该 helper 返回值可能是微信 VFS 路径，不一定是普通 `File`；选择 helper 时不能先要求 `pathExists` 成功，也不能只缓存和尝试一个同参 `String(data)` 方法，否则 72/74 可能选到非播放路径 helper。应优先按微信详情页同款 helper 排序并保留多个候选路径，逐个尝试普通文件或 VFS 输入流 materialize，失败再递归扫描字段。已确认 58/66 的播放路径 helper 名为 `x`，68 为 `w`，72/74 为 `x`；VFS 读取 58/72/74 为 `com.tencent.mm.vfs.w6.E(String)`，66/68 为 `com.tencent.mm.vfs.w6.F(String)`。存在判断要兼容 `com.tencent.mm.vfs.w6/p6` 的 `j/k(String)` 布尔或长度返回值，读取时优先用 `E/F(String)` 打开流并复制到模块缓存，递归扫描字段只能做最后兜底。
- 从聊天加号面板进入微信原生收藏选择页时，`FavSelectUI#onItemClick(AdapterView, View, int, long)` 会对 `field_type=3` 的收藏语音直接弹出“不支持发送”提示。模块在 `FavSelectUI`、`FavSearchUI`、`FavFilterUI` 初始化前后从 Intent、页面和适配器的排除类型集合中移除 `3`，搜索/筛选页重新查询列表，并在点击时读取行 View 或适配器中的收藏对象，复用收藏语音路径解析后弹出 Miuix 确认框。主页顶部的语音筛选结果不走 `FavSelectUI#onItemClick`，而是由 `FavTopSearchUIC` 的独立 `OnItemClickListener` 发送；运行时通过 `FavTopSearchUIC$initOnItemClickListener$1` 唯一字符串定位并缓存该回调，不能复用普通收藏列表的点击定位。该入口已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`。确认框内置预览播放器，支持播放/暂停、前后 5 秒跳转和点击进度条跳转，确认后通过 `WeChatApis.media().voices().send(...)` 发送到当前目标会话。目标会话优先读取当前行 View 的 Activity 及其 `key_to_user`，再按 58 的 `P`、72/74 的 `T`、其它版本的 `T/P/S/Q` 和当前聊天 talker 兜底，不写死单一字段名。

语音消息预览：

- `语音消息预览` 属于 `语音` 分组并默认关闭。开启后，长按已下载的聊天语音会显示 `预览语音[H]`；点击后直接打开 Miuix 预览窗口，显示消息记录的真实时长，并提供播放、暂停、前后跳转 5 秒和进度跳转。菜单创建与点击复用公共 `SingleMessageMenuLocator`，消息文件和时长复用 `SelectedMessageSnapshot` 与 `VoiceMessageDurationResolver`，不增加单独的微信内部方法定位；点击自定义菜单后继续执行微信原生菜单收尾逻辑，避免消息保持选中状态。
- 微信聊天语音先在后台转换为唯一命名的临时 MP3，再复用收藏语音的 `MediaPlayer` 预览控件；关闭窗口、转换失败、功能销毁或转换完成时窗口已经关闭都会删除临时文件，不能使用固定文件名造成并发覆盖或缓存堆积。语音文件尚未下载时不启动播放器，并提示当前文件不可用。

修改聊天记录：

- `修改聊天记录` 属于娱乐功能，设置页放在 `聊天` 分组，总开关默认关闭。开启后在微信聊天消息长按菜单里给可修改消息追加 `修改[H]` 菜单项，图标优先使用微信资源 `icons_filled_edit_photo_pencil`，找不到时保留文字菜单。
- 菜单创建按 `MicroMsg.ChattingItem` + `msg is null!` 精确定位 `void(menu, View, ContextMenuInfo)`，菜单点击按 `MicroMsg.ChattingItem` + `context item select failed, null dataTag` 精确定位 `void(MenuItem, int)`，两者都用 `DexMethodCache` 缓存。已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 均保持这两组字符串和方法结构。创建菜单时只读取当前 `View.tag` 的直接消息字段或直接消息返回方法，不再递归遍历对象图；当前原生消息对象和 `msgId` 同时绑定到本次新增的 `MenuItem` 及其菜单分组，点击时只消费本次绑定，绑定缺失则拒绝修改，避免弹窗读到上一次长按的消息。菜单分组取当前原生菜单项的 `groupId`，不再从 `View` 内部数值字段猜测。

- 点击 `修改[H]` 后由模块显示编辑弹窗，但仍让微信原菜单点击方法继续完成选择态清理；不能提前截断该方法。
- `修改[H]` 菜单项必须主动重排到菜单首位区域：存在 Hchat `转发[H]` 时紧随 `转发[H]`，否则存在 `复读[H]` 时紧随 `复读[H]`，都不存在时排第一；不能依赖各功能的 Hook 安装顺序。
- 当前支持普通文字、引用消息和转账消息。普通文字直接修改 `message.content`；群聊收到的消息保留原始 `sender:\n` 前缀。引用消息会修改当前 AppMsg 的 `<title>` 和 `<refermsg>` 里的引用文本，并按 `<svrid>` 尽量级联修改被引用的原消息。转账消息只修改本地 XML 展示字段和本地记录金额字段：金额按元输入，保存时写回 `total_fee`、`feederval`、`fee` 等分值字段，并同步 `feedesc` 以及含金额的 `title/desc/payerdes/receiverdes` 展示文本；展示金额保留用户输入的小数位，例如 `10.00` 不会被压缩成 `10`；不改变服务端真实转账金额、收款状态或支付请求。
- 修改前会把原始 `content` 按 `msgId` 备份到模块 FastKV `Hchat_edit_message_backup`；弹窗内有备份时显示 `恢复`。保存和恢复优先把新内容写入当前原生消息对象，再调用微信 `MsgInfoStorage` 的按本地 `msgId` 更新入口，使 WCDB ORM、消息缓存和 `doNotify()` 保持一致；已确认该入口在 8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 均为 `(long, MsgInfo) -> int`，8.0.49 只有 `void` 旧签名，因此该版本失败时回退按实际消息表执行 SQL 并提示退出重进。目标消息仍是会话最新消息时继续同步更新 `rconversation.content/msgType/digest/isSend`。

`getGroupList()` 返回的是群对象列表，每个对象都支持常见的 WA 风格读取方式：

```java
for (var group : getGroupList()) {
    log(group.getRoomId() + " => " + group.getName());
    log("备注: " + group.getRemarkName() + ", 展示名: " + group.getDisplayName());
}
```

发送消息：

```java
sendText(talker, "普通文本");
sendText(talker, "[AtWx=wxid_xxx] 你好"); // 群聊 @ 某人
sendText(talker, "[AtWx=notify@all] 大家好"); // 群聊 @ 全体
sendPat(talker, "wxid_xxx");
sendShareCard(talker, "wxid_xxx");
sendLocation(talker, "测试地点", "测试描述", "113.3245", "23.0999", "16");
sendImage(talker, "/sdcard/a.jpg");
sendVoice(talker, "/sdcard/a.amr", 3); // 第三个参数是秒
sendVideo(talker, "/sdcard/a.mp4");
sendEmoji(talker, "/sdcard/a.gif");
sendFile(talker, "/sdcard/a.zip", "压缩包.zip");
sendXmlMsg(talker, "<msg><appmsg appid=\"\" sdkver=\"0\"><title>测试</title></appmsg></msg>");

sendText(talker, "异步文本", new java.util.function.Consumer() {
    public void accept(Object ok) {
        log("发送结果=" + ok);
    }
});
```

系统消息、历史消息、延迟和通知：

```java
long msgId = insertSystemMsg(talker, "处理中...", System.currentTimeMillis());
Object history = queryHistoryMsg(talker, 0L, 10);
int unreadCount = getUnreadCount(talker);
int allUnreadCount = getAllUnreadCount();
boolean cleared = clearUnread(talker);
boolean allCleared = clearAllUnread();

delay(1000, new Runnable() {
    public void run() {
        notify("Hchat", "延迟任务已执行");
    }
});
```

HTTP 和下载：

```java
java.util.HashMap headers = new java.util.HashMap();
headers.put("User-Agent", "Hchat");

get("https://example.com", headers, new java.util.function.Consumer() {
    public void accept(Object body) {
        log("GET=" + body);
    }
});

java.util.HashMap params = new java.util.HashMap();
params.put("a", "1");
post("https://example.com/api", params, headers, 30, new java.util.function.Consumer() {
    public void accept(Object body) {
        log("POST=" + body);
    }
});

download("https://example.com/a.zip", cacheDir + "/", headers, new java.util.function.Consumer() {
    public void accept(Object file) {
        log("下载完成=" + file);
    }
});
```

HTTP 规则：`Content-Type: application/json` 时 `post` 会把参数 Map 转成 JSON；否则按 `application/x-www-form-urlencoded` 拼接。`download` 的路径如果是目录或以 `/` 结尾，会按 URL 自动推断文件名。

杂项和音频：

```java
eval("log(\"来自 eval\")");
loadJava("extra.java");
loadSo("libs/arm64-v8a/libdemo.so");
reloadPlugin();

long duration = getDuration(cacheDir + "/voice.mp3");
log("音频时长=" + duration);
```

Hook、DexKit 和反射：

```java
Class clazz = findClass("com.tencent.mm.ui.LauncherUI");
Method method = clazz.getDeclaredMethod("onResume", new Class[0]);
Object handle = hookAfter(method, new java.util.function.Consumer() {
    public void accept(Object param) {
        log("LauncherUI onResume");
    }
});
unhook(handle);

Object classes = findClassList({"MicroMsg"});
Object members = findMemberList({"sendAppMsg", "attachFilePath"});

Method substring = firstMethod(String.class, "substring", 2);
Object text = invokeMethod("hello", "substring", 2, new Object[]{1, 3});
Object builder = createInstance(StringBuilder.class, 1, new Object[]{"Hchat"});
```

通过 `hookBefore`、`hookAfter`、`hookReplace` 注册的 Hook 会在插件关闭或加载失败时自动清理。脚本也可以直接使用原生 `XposedBridge` / `XposedHelpers` / `XC_MethodHook`、Java 反射，以及 `KavaReflector`。DexKit 只能使用模块共享实例，不要在脚本里自行初始化。

### 脚本插件 API 范围

插件页在 `插件 Agent` 之前提供 `在线插件` 入口。在线插件页从 `https://hchat.103.97.179.142.sslip.io` 读取社区插件，支持搜索、按最新或下载量排序、刷新、查看插件版本/作者/更新时间/下载量和详情。打开当前版本或历史版本详情只读取内容，不增加下载量；只有插件文件通过校验并成功写入本地插件目录后，客户端才调用独立统计接口记录一次下载。每次成功安装使用随机事件 ID 上报，同一事件的网络重试幂等；统计上报失败不回滚已经成功的本地安装。旧版累计值全部来自混合的详情浏览口径，首次应用新统计迁移时统一清零，无法反推出历史真实下载量。详情页展示当前版本文件以及全部历史版本，`README.md` 使用与本地插件说明一致的 Markdown 样式渲染；每个历史版本显示作者上传时填写的更新说明，并可选择任一历史版本安装。安装前必须明确提示社区代码风险；下载内容逐文件校验编码、大小和 SHA-256，默认托管文件为 `main.java`、`main.java.bshs`、`info.prop`、`README.md`，其中 BeanShell 加密文件 `main.java.bshs` 通过 Base64 传输并按原始二进制字节安装。额外依赖文件只能是安全的单层文件名，文本使用 UTF-8，二进制使用 Base64；每个额外文件不超过 16 MiB，单个版本最多 32 个额外文件，全部文件总量不超过 32 MiB。安装目录优先沿用上传插件的 `sourcePluginId`，服务端旧数据缺少该字段时才回退远程插件 ID；旧版本以远程 ID 创建的目录会在下次更新时迁移回 `sourcePluginId`。同名目录已存在时必须再次确认覆盖。目录更新采用同目录暂存、备份和原子替换，默认托管文件按版本清理，额外依赖文件写入插件根目录并保留本地其它文件，不会因版本缺少额外文件而误删 `config.prop` 等运行数据。新安装或更新后的插件统一保持关闭，必须由用户回到本地插件列表手动启用。

在线插件列表和详情展示服务端持久化的点赞数与评论数。详情页支持当前微信账号点赞、取消点赞、发表评论和删除自己的评论；评论正文最多 1000 个字符。点击任意评论行会在保留插件详情上下文的情况下展开详情弹窗内部的底部回复区，提示为“回复 昵称”且不添加 `@`，不能再向 Activity decor 叠加第二个全屏 Compose 输入弹窗；根评论按最新在前显示，回复按父评论 ID 归入根评论下方的浅色讨论串并按时间正序显示，回复已有回复时显示“回复人 回复 被回复人：正文”，关系不依赖昵称文本解析。服务端限制最新评论基础窗口时必须额外返回完整回复链的祖先评论，客户端不能因固定条数裁剪而把回复错误显示成根评论。在线插件页右上角提供回复通知铃铛和统一未读角标，通知中心显示回复者、插件名、回复正文、原评论摘要和时间，只把本次实际展示的未读通知标记为已读，点击通知会先关闭通知层再进入对应插件详情。互动身份直接读取当前微信账号资料，不提供手工填写 wxid 的入口；所有互动请求携带模块安装标识，服务端只保存 `安装标识 + wxid` 的 SHA-256 派生哈希，不保存原始安装标识。重复点赞不重复计数，取消点赞、删除评论和回复通知访问都校验 wxid 与安装身份；评论和通知公开响应不公开 wxid、微信号或身份哈希，删除按钮仅按服务端返回的 `canDelete` 显示。管理员会话仍可删除任意评论，但不能读取普通用户通知；删除根评论会级联清除其回复和通知，整插件删除会级联清除全部点赞、评论与通知。

在线上传只列出本地存在 `main.java` 的可运行插件，支持多选、全选/取消全选、逐项修改在线名称和填写本次更新说明。批量上传按选择顺序执行并分别显示进度与结果，单个失败不会终止其它插件；服务端返回 `pending` 时模块必须把该项和汇总明确显示为“待审核”，不能算作失败或已发布。每次上传同时提交当前微信账号的 wxid、微信号和微信昵称，wxid 无法读取时不允许产生无法追溯的新上传；服务端按 wxid 执行上传黑名单，命中时返回稳定的 `UPLOADER_BLACKLISTED`。每个插件默认上传 `main.java`，以及存在时的 `main.java.bshs`、`info.prop`、`README.md`；每个本地插件行都可以通过系统文件选择器多选插件目录外的附加依赖文件，附加文件按文件名上传并在详情、历史版本和安装时一并保留。加密文件和二进制依赖保持原始字节不变，不上传配置、缓存、日志或未选择的其它文件。首次上传返回的 `ownerToken` 只保存在当前安装的模块私有配置中，服务端只保存令牌哈希；以后从同一本地插件更新或删除在线条目必须携带该令牌。在线插件详情仅在本机保存的所有权记录与远程插件 ID 匹配时显示删除入口，删除前使用模块确认弹窗，并明确只删除线上插件及全部历史版本、不删除本地插件；服务端仍按 `ownerToken` 做最终鉴权，错误或缺失令牌不能删除他人的插件。若管理员已删除远端插件而本地仍保存旧归属，更新请求明确返回 `PLUGIN_NOT_FOUND` 时，模块清除该条旧归属并自动按新插件重试一次；权限、审核和网络错误不得触发该恢复。相同版本名与文件内容不会重复创建历史版本，但允许更新该版本的更新说明。服务端实现位于 `server/plugin-market`，生产进程为 `hchat-plugin-market.service`，监听 `127.0.0.1:8765`；Nginx 负责公网 HTTPS，SQLite 位于 `/var/lib/hchat-plugin-market/plugin-market.db`，程序位于 `/opt/hchat-plugin-market`，证书由 Certbot 定时续期。

在线插件列表、详情和历史版本中的服务端 UTC 时间统一按 `Asia/Shanghai` 显示为 `yyyy-MM-dd HH:mm:ss`。服务端 `/admin/` 提供网页管理端，管理员密码登录成功后换取最长 8 小时的 HttpOnly 签名会话，前端不保存或读取密码。登录后的右上角使用原生折叠账户菜单统一承载修改密码和退出登录，修改密码表单在菜单内二次展开，关闭菜单、退出登录或认证失效时必须清空密码输入。管理页支持搜索、刷新、在插件主行查看最近一次提交者并在历史版本中查看每个版本上传者的 wxid/微信号/微信昵称、按 wxid 拉黑或解除拉黑、插件多选和当前筛选全选、批量删除、整插件双重确认删除、展开历史版本和删除非当前单个版本；当前公开版本必须先由其它版本替代，不能直接单删。旧版本没有上传者身份时明确显示未知，不据此生成黑名单。管理员可持久化开启“插件上传审核”：开启后新插件在通过前不进入公开列表，已有插件的待审核新版本不替换当前公开版本；关闭审核时无需逐项通过。新服务器部署、SQLite/密码迁移、HTTPS、回滚与验收统一按 `server/plugin-market/DEPLOYMENT.md` 和幂等 `deploy.sh` 执行，Nginx 与后端请求体上限均为 48 MiB。

插件页提供 `插件 Agent` 入口。Agent 的配置页和聊天页相互独立；聊天页每次打开都从临时新对话开始，发送首条用户消息后才写入本地历史，未产生用户对话就退出、切换或再次新建时不生成历史记录。旧会话需要从历史列表主动打开，会话保存在脚本根目录同级的 `Agent/sessions`。历史列表支持左右滑动编辑或删除，长按可编辑标题、上下移动、置顶、锁定或删除；排序、置顶和锁定状态会随会话保存，锁定会话必须先解锁才能删除。代码草稿和目标插件严格归属各自会话；运行中的会话允许新建或切换到其它会话，返回设置页、微信收到新消息和页面销毁都不会取消后台任务，停止按钮只作用于当前会话。多个会话可以同时运行，消息、工具事件、压缩状态和待确认的插件变更按会话独立保存；正在运行的会话不能直接删除。重新进入 Agent 时优先恢复离开前查看的会话，后台完成或等待确认的结果可以继续查看。Agent 使用独立的 OpenAI 兼容配置，把多轮对话、本地插件清单和内置脚本开发指南发送给模型，并根据用户描述自动判断新建、修改或删除插件，不要求用户预选任务类型。`alt-entry` 构建内置分支自己的脚本 API 指南，包含分支专属的原图发送、图片下载、图片消息字段和媒体发送返回值，不再复用主线指南。插件文件的列出、按行读取、正则/glob 搜索、新建、统一补丁修改、移动、恢复和删除全部通过可见的 `hchat.workspace.*` 工具调用完成，不再把完整源码藏在最终控制响应中。统一补丁采用 Codex 的 `*** Begin Patch` 结构并支持单次多文件 Add/Update/Move/Delete；差异通过独立 `show_diff` 工具和最终消息显示标准统一 diff。生成结果会显示可核验的工具调用和接口实际返回的 `reasoning_content` 思考过程，不展示客户端伪造的思考摘要；普通 Agent 回复不重复显示“插件 Agent”标题，工具消息仍保留“工具调用”标题。聊天页通过左上角返回箭头退出，不再提供代码预览页或底部返回按钮，输入框的空白区域也可直接点击唤起输入法。

Agent 可通过 `界面 -> 悬浮快捷菜单` 中的 `插件 Agent` 快捷项展开或收起；快捷按钮会保持在 Agent 页面上层，收起和 Activity 迁移复用原会话保存与恢复逻辑，不取消请求、上下文压缩或等待 Diff 确认的任务。

插件工作区额外提供 `check_access` 权限预检工具，可在暂存工作区创建前检查单个路径或递归检查插件目录，返回读写、父目录替换、POSIX mode、owner UID/GID 和当前微信进程 UID；设置 `repair=true` 时只尝试补齐文件所有者读写位及目录进入位，不设置全局可写。创建工作区、事务提交和旧草稿写入也会自动执行相同的可恢复权限处理；若文件属于其它 UID 或受系统存储策略限制，工具返回无法修复的准确路径和原因。最终写入按插件串行执行，优先使用同目录移动备份；外部存储不支持目录移动时，会在修改原目录前创建并校验完整复制备份，再只同步本次变化路径。备份完整性和提交完成状态会持久化，微信进程中断后只从校验通过且尚未提交完成的备份恢复，恢复失败时保留备份和详细错误。

历史列表会在每个正在生成回复、压缩上下文或执行文件落盘的会话右侧显示旋转圆环，当前打开的会话也按同一状态显示；只等待用户确认时不显示工作中状态。写入插件和修改插件工具的代码差异默认收起，可点击 `代码变更` 展开或再次收起。

`界面 -> 悬浮快捷菜单` 默认关闭，不申请系统悬浮窗权限。开启后可选择仅在 `LauncherUI` 主页显示或覆盖全部微信 Activity；主按钮可自由拖动到屏幕内任意位置并记忆归一化坐标，不再自动吸边，切换 Activity 时会在挂载前恢复位置，窗口尚未完成布局时先隐藏再定位，避免左上角闪烁。点击主按钮会按设置在其正上方或正下方通过对应方向的位移缩放动画展开纵向快捷项，默认向上展开，并支持仅图标、仅文字、图标和文字三种样式；菜单高度、屏幕边界、拖动范围、动画起点和缩放锚点都按当前展开方向计算，切换选项后当前页面实时重新挂载。副图标默认使用 `44dp` 正圆按钮，图标和文字模式把文字标签与圆形图标分离显示，不再共用胶囊背景。圆形图标列锚定主按钮，主按钮位于左侧时文字向右展开、位于右侧时文字向左展开。快捷项可启停、排序、新增、编辑和删除，动作可打开填写的微信 Activity 完整类名、Hchat 设置或展开/收起插件 Agent。首次升级时，仅在悬浮快捷菜单尚未显式配置对应字段时迁移旧 Agent 悬浮窗的启用状态、位置和全页面范围；已有快捷项配置一次性补入插件 Agent，用户手动删除后不再自动补回。内置扫一扫、朋友圈、视频号、收藏和钱包 Activity 已通过 Manifest 横向确认存在于微信 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`，仍作为可编辑默认值，未来版本页面不存在时明确提示打开失败。主按钮默认使用 `44dp` 白色圆形底和深色四宫格，朋友圈默认图标使用与其它副图标一致的单色相机光圈样式；拖动主按钮时已展开菜单保持显示并同步移动，拖动结束后保留当前位置并重新贴靠主按钮，再次单击主按钮或点击菜单外区域会播放反向动画并收起；菜单在完成当前位置计算前保持隐藏，避免左上角闪烁。默认快捷项使用按最终控件尺寸实时绘制的设置、扫一扫、朋友圈、视频号、收藏、钱包等对应矢量图标，不再放大 Android 旧位图资源。主按钮和每个快捷项仍可通过系统图片选择器分别设置浅色、深色图标，深色图标未配置时回退浅色图标；图标副本存放在 `Hchat/FloatingShortcut/icons`，删除或替换配置时同步清理对应文件。

主设置页只显示一行“按钮外观”入口，二级页分别配置主按钮与副按钮的 `36-64dp` 尺寸和背景颜色；颜色继续复用模块统一取色器，单色保持旧格式，设置起始色和结束色时使用横向渐变。菜单名称支持 `10-24sp` 字号，并使用同一取色器设置单色或按文字实际宽度显示的横向渐变，名称颜色留空时跟随系统深浅模式；页面可一次恢复全部默认外观。默认矢量图标根据渐变两端的平均亮度自动选择深浅前景，自定义图片保持原色。副按钮颜色留空时继续跟随系统深浅模式。

“仅微信主页”范围不再只按 Activity 类名判断；新版微信在 `LauncherUI` 内嵌聊天 Fragment 时，运行时通过公共聊天页 API 的进入/退出事件立即移除或恢复悬浮入口。“所有微信页面”范围不受聊天页事件影响。

脚本插件运行时在微信冷启动阶段只先初始化 Bridge、插件目录和文件监听；已启用插件会等公共 Dex 定位完成并确认联系人数据库包含 `rcontact`、`chatroom` 表后，再由独立线程加载。等待采用有限退避且不在 DexKit 调度锁内执行插件；某个插件加载失败不会阻断后续插件。手动启用和重载仍同步返回真实结果，数据库尚未就绪时直接返回可重试错误，不能把尚未执行的任务伪报为加载成功；关闭插件会同时取消已经排队的文件重载，避免插件被重新加载。

Agent 配置支持多份模型配置档案，可切换、新建、重命名和删除，并可对当前聊天接口执行最小请求测试连接。聊天输入区显示当前配置和模型，可直接切换；运行中切换只影响下一次请求，不改变已经启动的请求。输入区快捷选项可单独开关联网搜索和每个已配置的 MCP 服务器，关闭联网搜索后 Agent 不会发起搜索请求；“插件文件修改确认”可选择“每次询问”或“始终允许”，前者确认每次工具写入和最终提交，后者跳过全部确认并自动提交；“提示缓存”可选择“自动”“强制”或“关闭”，新配置和缺少该字段的旧配置默认使用“强制”，并按模型配置档案独立保存；用户已明确保存的选择不被覆盖。新配置的 API 地址默认为空。接口类型按配置档案独立保存：`OpenAI 兼容`、OpenAI、OpenRouter 和硅基流动会补全 `/v1/chat/completions`，DeepSeek 会补全 `/chat/completions`，Anthropic 会补全 `/v1/messages`，Gemini 会按当前模型和流式状态生成 `/v1beta/models/{model}:generateContent` 或 `:streamGenerateContent?alt=sse`。地址规范化会识别尚未输入完整的结尾并保留反向代理路径前缀，例如 `https://xapi.deuo.top//v1/chat/completi` 会解析为 `https://xapi.deuo.top/v1/chat/completions`；选择具体平台时会填入官方地址，之后也可改为平台代理地址并按该平台规则补全。Anthropic 使用 Messages API 原生消息与工具协议，Gemini 使用 generateContent 原生内容与函数调用协议，其余平台使用 Chat Completions 协议。只有 `自定义请求链接` 要求用户填写完整 HTTP(S) URL，客户端仅去除首尾空白，不补全或改写路径与查询参数。页面实时显示最终请求地址。旧版单配置中的 `apiBaseUrl` 和 `apiPath` 首次读取时自动合并到“默认配置”，并继续回写旧键以兼容降级读取。

Agent 的内置开发指南位于固定 system 前缀，且不嵌入构建版本、联网开关或当前工具协议。每个会话持久化独立的 provider-neutral 协议转录，用户消息及其运行上下文、模型原始控制响应、原生函数调用、工具结果和客户端状态更新按实际发生顺序只追加；网络重试不新增协议记录。原生工具结果保持模型调用顺序，兼容 JSON 工具状态保留模型原始回复后再追加客户端结果，本地图片在首次出现的位置固化。提示缓存“自动”模式只向 OpenAI 与 Anthropic 官方接口发送各自显式缓存字段，其它接口使用服务端原生策略；“强制”模式也向 OpenAI 兼容、自定义 Chat Completions 和 Anthropic 兼容地址发送稳定缓存标识；“关闭”模式不发送显式缓存字段。工具续轮复用相同的稳定 system、工具定义和缓存标识，只在尾部追加工具调用与结果。兼容接口以 `400/422` 拒绝显式缓存字段时，客户端先保持当前工具协议无缓存重试，成功后在本次进程内记住该地址不支持，不能误切到兼容 JSON 工具协议。Gemini 回放原始 `functionCall` part，并为缺少 ID 的流式调用生成稳定指纹。手动/自动压缩、编辑重发、重新生成、删除或回滚历史以及新建分支会显式建立新的缓存周期，普通会话切换、退出和进程恢复不会重建前缀。自动压缩的摘要、压缩边界和转录通过同一 checkpoint 恢复；未闭合的函数调用在下一次请求前补充中断结果，不自动重放可能带副作用的工具。旧会话首次使用时从现有消息迁移一次。

Agent 默认内置当前微信 APK 的本地逆向工具，不需要用户配置额外服务。当前运行微信复用模块启动时已经创建的共享 `DexKitBridge`；用户明确提供其它微信 APK 绝对路径时，`open_target_session` 只通过 `input` 注册持久化目标并返回稳定 `session_id`，该 ID 仅传给后续查询工具。旧客户端若把同一 APK 路径同时放进 `input` 和 `session_id`，运行时按 `input` 兼容处理。为控制微信进程内存，外部 APK 只保留一个 DexKit 实例处于打开状态，切换目标时释放上一个；`compare_methods_using_strings` 会串行使用同一字符串锚点横向返回最多 12 个微信版本的候选。工具还提供结构化 Manifest、DEX 字符串定位类/方法、资源 ID 使用方法定位、按类名/方法名/descriptor 筛选、类结构检查、方法调用者/调用目标/字段/opcode/注解证据，以及 resources.arsc 资源值检索、资源解析和二进制 XML 解码。find/list 查询支持 `brief` 和显式 `fields` 投影，分页统一返回 `offset/limit/hasMore`；候选的 `sourceEntry` 通过一次批量 Dex 扫描取得并写入既有轻量缓存。资源层使用 ARSCLib 按目标 APK 按需加载，并可返回文件路径和 `table-backed` / `table-value` 等解析状态；Manifest 可按分区展开，XML 可按 `nextOffset` 连续读取。界面文案优先走资源检索并按资源 ID 继续定位，代码常量才走 DEX 字符串检索。工具结果带目标 `session_id` 和 `sourcePath`，Agent 不能把一个版本的 descriptor 视为其它版本证据。类和方法支持 Java 语义视图与 Smali 原始证据视图，并按偏移连续读取完整内容；已定位类与 Dex 条目的映射按 APK 状态轻量持久化，微信进程重启后可以继续复用。Java 导出只把目标类所在的单个 Dex 临时交给 JADX，完成后立即释放，不复制 Dex，也不缓存导出正文。

Agent 也可主动请求联网搜索或连接用户配置的多个远程 Streamable HTTP MCP 服务器。原生函数协议把联网拆成 `hchat_web_search` 和 `hchat_web_fetch`：前者按关键词或 `owner/repo` 查找公开资料，后者读取已经给出的完整 HTTP(S) 网页、README、GitHub 文件或目录正文；兼容 JSON 协议仍通过 `searchQuery` 自动区分关键词和 URL。工具结果带回实际来源并进入下一轮上下文，HTTP 状态、限流和不可读取的响应类型会明确反馈，不把失败伪装成“没有该资料”。GitHub 的仓库 URL、README、`blob` 文件和 `tree` 目录分别读取仓库元数据、README、文件内容或目录树。Codex 的服务端 `web_search` 工具不能直接嵌入 Android APK，因此模块端使用同样的工具调用和结果回灌流程实现等价能力，并限制响应大小和内网地址访问。联网请求优先使用系统返回的真实公网地址；系统只返回已知的公网代理合成地址或本地解析失败时，通过固定公网引导的 DoH 取得并短时缓存真实公网 IPv4，DoH 不可用时才回退该已知系统代理地址，普通内网地址和 IP 直连仍然拒绝。网页正文按响应声明字符集解码，直读握手失败时回退到可核验搜索结果。

兼容接口优先收到标准 OpenAI `tools` 定义；插件工作区、本地逆向、联网、文件和 MCP 调用使用 `assistant.tool_calls` / `tool` 角色回传并保存 `tool_call_id`。服务端不支持原生工具时自动退回 JSON 控制协议，已知的 `hchat.workspace.*` 与 `hchat.reverse.*` 调用仍会作为结构化工具事件执行，控制字段不会显示为聊天正文。详细的回合边界、思考、Working、中断和压缩见本文档后面的“Agent 交互架构”章节。

每轮聊天和工具事件都保存 `messageId`、`turnId`、父消息 ID、执行阶段和完成时间；同一模型响应返回的多个工具调用会完整进入队列，联网搜索和外部文件读取最多并行 3 个，插件工作区、DexKit、MCP 和未知副作用工具串行。工具结果超过单页大小时写入会话结果仓库，并通过 `hchat.reverse.read_tool_result` 返回 `handle` / `nextOffset` 续读；结果预览和完整详情分开保存，分支复制和删除消息会同步处理结果文件。
插件工作区的 `write_file` 和 `apply_patch` 工具在聊天记录中只显示工具状态和本次彩色 Diff，不显示调用参数、结果摘要或结果详情；其他只读、逆向、联网和 MCP 工具继续保留参数与结果详情。

每个 MCP 服务器可独立启用、关闭或删除；只有已启用的服务器会连接并向 Agent 提供工具。MCP 客户端按标准 JSON-RPC 流程执行 `initialize`、`notifications/initialized`、分页 `tools/list` 和 `tools/call`，支持 `Mcp-Session-Id`、JSON/SSE 响应、服务端 `instructions` 和可选 `Authorization` 请求头；不同服务器的工具按独立命名空间合并，同名工具不会互相覆盖，工具名、参数及结果会记录到本轮工具调用并继续交给模型处理。旧版单 MCP 配置首次读取时自动迁移为一个服务器条目。远程 MCP 是内置逆向工具之外的可选扩展，Android 端不启动本地 STDIO MCP 进程。本地工具优先解析标准的 `status: local_tool` 控制 JSON，同时兼容模型把 `local_tool` 作为顶层控制字段或对象；若兼容模型返回的其它字段破坏了整份 JSON，但响应中仍能可靠提取已知 `hchat.reverse.*` 或 `hchat.workspace.*` 工具名及完整参数对象，客户端只恢复这次本地调用。工作区恢复调用仍只修改暂存副本，最终校验和确认不放宽；远程 MCP 不使用这种恢复。

Agent 生成请求使用 OpenAI 兼容的 SSE 流式响应，聊天页会实时显示已收到的可核验回复片段；尚未收到正文时显示独立的 `Working (耗时)` 请求状态，一轮请求首次出现正文后不再闪回 Working，正文直接保留在对应 Agent 消息中，也不展示客户端伪造的思考摘要。若接口返回 `reasoning_content`，聊天会展示其实际内容。客户端把兼容接口返回的累计字段转换成只追加的流式增量，已显示正文不会被后续片段覆盖。JSON 校正或工具后的下一轮模型请求会创建新的 Agent 消息，旧消息正文不会被清空；每次模型请求都会分配并持久化独立的流消息 ID，正文、思考和状态增量只更新对应 ID 的 Agent 消息；工具调用使用自己的事件 ID 单独显示，迟到的流片段不能覆盖已经显示的其它消息。SSE 必须收到 `[DONE]` 或明确的 `finish_reason` 才视为完整结束；完整控制响应形成前不会执行新工具，因此短暂连接错误可安全撤下不完整响应并复用同一请求退避重试，最多 6 次，数字秒格式的 `Retry-After` 优先。已经完成的工具调用和协议记录不会因模型重试而重放。同一次生成中，模型第一次确定具体插件目标后，客户端会独立锁定该目标并在每次工具调用后继续注入；后续响应必须原样返回目标，偏离目标的结果不会执行，避免开放需求在多轮逆向过程中自行换题。工具名称、参数摘要、执行状态和结果摘要以结构化事件写入会话；每次生成使用独立的工具事件 ID，结束时会补回未成功实时渲染的已执行事件；同一工具和参数允许按需再次执行，每次结果独立记录；后续参数错误也会保留此前已经完成的工具记录；流式执行时自动展开，结束后自动收起，点击工具行可查看和复制详细记录，代码 diff 独立折叠。思考过程始终默认折叠，流式追加思考内容时不会自动展开；只有用户点击“思考中”或“思考过程”后才渲染完整内容，避免长思考持续触发 Markdown 排版造成界面卡顿。生成期间仍可编辑下一条消息并加入待发送队列，队列支持附件、编辑、删除和立即发送；当前回复以及待确认或正在执行的插件文件操作结束后，会按顺序自动发送。发送状态只显示在聊天记录中；生成期间输入框同时保留停止按钮和加入队列按钮，不显示“回复中”状态文字。发送键始终使用模块主色和白色图标，空输入时仅禁用点击，不改变视觉颜色。会话列表按配置栏和输入区的实际高度动态保留底部空间，输入区高度变化时最后一条消息和回到底部按钮不会被遮挡。键盘出现时聊天根布局使用 IME 内边距；如果打开键盘前正在底部则继续跟随底部，否则保留当前阅读位置，让消息列表和输入框一起位于键盘上方。停止会中断当前模型、联网搜索或 MCP HTTP 请求，并把已经收到的内容保存为“已中断”消息；再次发送时会把这个中断状态作为上下文的一部分交给模型。兼容忽略流式参数并直接返回完整 JSON 的服务器。

单轮任务不设置固定的工具调用总次数上限，多文件操作、分页读取和多版本逆向可持续到任务完成或用户主动停止；同一工具和参数可为复核或刷新再次执行，只有控制响应格式校正次数保留有限保护。

用户只询问当前脚本 API、接口用法或开发指南时，Agent 使用独立的 `answer` 状态直接依据当前构建内置指南回答，不要求生成插件草稿；指南明确列出的接口必须视为当前构建可用能力。`alt-entry` 会优先从当前模块 ClassLoader 读取指南，包上下文读取失败时再使用包含分支图片下载接口的内置兜底，避免退回缺少分支 API 的主线式清单。中文对话会要求可见思考内容使用简体中文，英文标题式摘要不应作为客户端伪造的工作状态。客户端完整合并兼容接口实际返回的 `reasoning_content`、`reasoning_details`、`thinking`、`thinking_blocks` 和 `analysis` 等思考字段，不主动截断或生成摘要；服务端未下发的隐藏思考链无法由客户端恢复。

聊天记录支持长按消息操作：复制、系统 TTS 朗读、引用、重新生成、回滚到所选消息、删除、从所选位置创建独立会话分支，以及查看角色、时间、状态、字符数、Token 估算和附件数量。当前任务会持久化目标、工作上下文、原生工具历史、插件工作区恢复点和单调检查点序号；流式正文按约 600ms 节流落盘，工具完成和任务阶段切换立即排队保存。微信进程意外结束或请求断流后，遗留运行状态会转成“已中断”，再次进入 Agent 时优先打开意外中断的会话；用户主动停止的会话不会强制抢占入口。中断卡片提供“继续任务”和“重新开始”：继续任务沿用原 `turnId`、原用户消息、协议结果和经校验的暂存工作区，不新增一条伪用户消息；重新开始会删除对应用户消息之后的分支、工具结果和工作区恢复点，再从原用户请求执行。引用任意用户或 Agent 消息后，输入区显示可取消的引用预览；发送后的用户消息会持久化引用角色和正文快照，并以明确的引用上下文交给模型。运行中加入待发送队列时会一并携带引用，发送后重新打开会话也不会丢失。用户消息还支持编辑并重发：该操作直接删除当前会话中所选消息及其后续记录，把原正文、引用和附件放回输入框，重新发送后覆盖原分支。插件工作区的“已暂存”和“已写入/已删除”状态各自追加为独立会话消息，不覆盖模型正文；成功写入或删除后，会在对应成功状态消息保存代码状态，使回滚和分支恢复到对应状态，旧历史没有快照时不猜测旧代码。

聊天输入支持通过系统文件管理器添加多个文件或图片，选择和复制附件不设置文件大小上限；同一次选择结果只消费一次，避免宿主 Activity 和基类重复回调后产生多个相同附件。文本、代码和日志按上下文容量分段读取，图片按 OpenAI 兼容 Chat Completions 的 `image_url` 数据发送，避免把超大文件一次性读入内存或请求正文。附件处理期间的副本放在当前会话的 `Agent/attachments/<sessionId>`；系统授予持久读取权限时，整轮模型请求、工具回灌和协议重试结束后删除模块副本，重新生成或编辑重发时再从原始 URI 临时恢复；无法持久读取的来源保留唯一副本，避免重试时附件丢失。用户也可以直接在消息中给出 Android 绝对路径，Agent 会自动读取文件或目录；模型后续请求读取文件时，只允许读取用户消息明确给出的路径及其子项，不会开放任意路径读取。Agent 回复支持标题、列表、引用、链接、行内代码和围栏代码块，代码块提供复制按钮；流式输出按约 80ms 合并界面更新。聊天会在用户位于底部时跟随新内容，用户主动上翻后停止强制跳转，并提供回到底部按钮。配置页支持从当前 API 地址拉取 `/models` 并搜索选择模型。

会话保留完整本地消息、代码草稿、Diff 和工具结果文件，但发送给模型时可按 Token 估算压缩较早上下文。压缩摘要按 Codex 式交接状态固定整理当前目标、用户约束、决策、插件与工作区状态、完成项、验证结果、关键 descriptor/路径/工具结果 handle、失败尝试、待办和最近上下文，不保存思维链。压缩成功后，模型请求用这份摘要替换压缩边界之前的消息和原生工具协议历史；后续工具历史只从边界后的消息重建，不能把旧工具输出重新带回请求。顶部提供手动压缩，配置页可关闭自动压缩或调整 2000 至 1000000 的阈值；Token 估算包含原生工具历史。手动和自动压缩都会显示独立的带耗时 `Working` 状态，成功后显示估算的压缩前后 Token，失败不移动边界，停止按钮可取消正在进行的压缩请求。

插件工作区限制在单个安全插件目录内，拒绝路径穿越、符号链接和超限文本、补丁或目录。首次修改前先检查真实插件根目录能否提交；写入工具只修改缓存暂存区，结果和界面统一标记“已暂存、尚未提交”，不能把工具成功表述成真实插件已经落盘。Agent 完成最后一次写操作后必须调用 `workspace_status`，并对同一 revision 调用 `show_diff(path=".")`；客户端以工作区创建时的稳定文件快照计算变更和标准统一 diff，运行中插件后来写入的日志、缓存或配置不会被误算成 Agent 修改。最终提交只对 Agent 实际新增、修改或删除的路径做冲突检查；其它路径的最新内容先复制进同目录准备区，再叠加 Agent 的文件变更，因此不会因无关运行文件变化误报“目标插件已发生变化”，也不会覆盖这些新内容。同一个被修改文件确实同时被外部改动时仍拒绝提交。当前 revision 校验通过后，模型返回 `workspace_done`、`answer`、`ready` 或 `delete` 都由客户端统一收束为待应用变更，避免因控制状态或 `taskGoal` 表述偏差反复生成最终说明；被拒绝并重试的控制响应会从聊天记录中撤掉。diff 超过单页时可按子路径继续查看，最终提交仍使用客户端重新计算的完整目录状态。“每次询问”模式会在 `write_file` / `apply_patch` 后显示工具级 Diff，并在整轮结束时对新建、修改、路径删除、整插件删除和高风险代码显示完整 Diff；关闭弹窗不会丢弃状态，聊天页保留可重新打开的待确认入口，明确取消才丢弃暂存副本。工具级和最终确认弹窗都可勾选“始终允许”，确认后立即保存设置并放行本轮剩余写入与最终提交。“始终允许”模式跳过工具级与最终确认，在相同校验通过后直接提交。目录提交使用同目录准备区和备份交换；成功修改或创建后插件保持禁用，提交中途失败则恢复旧目录及其原启用状态。最终提交失败或模型请求中断时保留当前暂存副本和恢复点，只有新任务、重新开始、明确取消、提交成功或恢复点超过 24 小时才清理；续跑前重新校验缓存路径、插件身份、基线和 revision。若恢复点已完成当前 revision 的状态检查及完整 Diff，直接重建待提交变更，无需再调用模型。下次打开工作区会检查同目录的中断备份和准备目录，目标目录缺失时优先恢复原插件，确认目标已存在后再清理残留；超时工作区由缓存清理。内置指南只引导使用公开 WA 风格 API，不要求模型猜测混淆类名。

暂存工作区按任务隔离。普通新用户消息不会继承上一任务的暂存状态，并会异步清理旧恢复点；“继续任务”是例外，它沿用原任务 ID、协议边界和经校验的工作区 checkpoint。恢复后旧工作区工具结果继续有效，不会被模型重新执行；恢复失败时才剔除旧工作区状态并重新读取真实插件。其它逆向、联网和 MCP 证据始终保留且不会自动重放。同一会话可在每次确认提交后继续多次修改同一插件，新任务以真实插件最新文件为基线。无有效工作区或缺少当前 revision 校验时，结束状态最多自动校正两次，之后以明确错误结束，不允许无限重试。

当前脚本插件已经可以稳定使用的能力分三层：

1. 基础运行时
   - `pluginDir` / `pluginDirFile`
   - `cacheDir` / `cacheDirFile`
   - `scriptDir` / `scriptDirFile`
   - `pluginId` / `pluginName` / `pluginAuthor` / `pluginVersion` / `pluginUpdateTime`
   - `hostContext` / `hostVerName` / `hostVerCode` / `hostVerClient` / `moduleVer`
   - `log(...)` / `toast(...)`

2. WA 风格高频接口
   - 联系人和会话：`getLoginWxid()`、`getLoginAlias()`、`getTargetTalker()`、`getTopActivity()`、`getOfficialList()`、`getFriendList()`、`getFriendListInfo()`、`getGroupList()`、`getGroupListInfo()`、`getContactLabelList()`、`getContactLabelListInfo()`、`getContactByLabelId()`、`getContactByLabelName()`、`verifyUser()`、`addChatroomMember()`、`inviteChatroomMember()`、`delChatroomMember()`、`getGroupMemberList()`、`getGroupMemberCount()`、`getGroupName()`、`getChatroomName()`、`getGroupRemarkName()`、`getGroupMemberName()`、`getGroupNickName()`、`getFriendNickName()`、`getFriendRemarkName()`、`getFriendGender()`、`getFriendProvince()`、`getFriendCity()`、`getFriendRegion()`、`getGroupMemberGender()`、`getGroupMemberProvince()`、`getGroupMemberCity()`、`getGroupMemberRegion()`、`getFriendDisplayName()`、`getFriendName()`、`getAvatarUrl()`
   - `getFriendList()` / `getOfficialList()` 返回对象兼容 `getWxid()`、`getNickname()`、`getRemarkName()`、`getName()`；`getGroupList()` 返回对象兼容 `getRoomId()`、`getName()`、`getRemarkName()`、`getDisplayName()`、`getMemberList()`、`getMemberCount()`；`getContactLabelList()` 返回对象兼容 `getLabelId()`、`getLabelName()`、`getName()`、`getUserNameList()`
   - 推荐高性能列表接口：`getFriendListInfo()`、`getGroupListInfo()`、`getContactLabelListInfo()`，直接返回已整理好的 `Map` 数据，脚本侧无需再逐项反射对象字段
   - `verifyUser(String wxid, String ticket, int scene)` / `verifyUser(String wxid, String ticket, int scene, int privacy)` 按 WA 同款签名通过好友申请；来自 `onNewFriend(...)` 的真实联系人 ID 会自动映射回微信验证用户名；底层定位微信 `NetSceneVerifyUser`，8.0.49 为 6 参构造，8.0.58/66/68/72/74 为 8 参构造，统一用 `/cgi-bin/micromsg-bin/verifyuser` 和 `MicroMsg.NetSceneVerifyUser.dkverify` 锚点解析，不写死混淆类名
   - `addChatroomMember(String chatroomId, String addMember)` / `addChatroomMember(String chatroomId, List<String> addMemberList)` 按 WA 同款签名添加群成员；底层定位微信 `NetSceneAddChatRoomMember`，URI 为 `/cgi-bin/micromsg-bin/addchatroommember`，type 为 `120`，构造形态为 `String,List,String,Object`，已用 DexClub 确认 8.0.49 和 8.0.74，不写死混淆类名
   - `inviteChatroomMember(String chatroomId, String inviteMember)` / `inviteChatroomMember(String chatroomId, List<String> inviteMemberList)` 按 WA 同款签名邀请群成员；底层定位微信 `NetSceneInviteChatRoomMember`，URI 为 `/cgi-bin/micromsg-bin/invitechatroommember`，type 为 `610`，构造形态为 `String,List,int,Object`，不要写死混淆类名
   - `delChatroomMember(String chatroomId, String delMember)` / `delChatroomMember(String chatroomId, List<String> delMemberList)` 按 WA 同款签名移除群成员；底层定位微信 `NetSceneDelChatRoomMember`，URI 为 `/cgi-bin/micromsg-bin/delchatroommember`，type 为 `179`，构造形态为 `String,List,int`，不要写死混淆类名
   - 好友申请回调：`onNewFriend(String wxid, String ticket, int scene)`，优先从 `fmessage_msginfo` 好友申请表解析参数，消息观察入口补充，优先给脚本真实联系人 ID，并和 `verifyUser(...)` 的验证用户名映射配套使用
   - 消息发送：`sendText()`、`sendPat()`、`sendShareCard()`、`sendLocation()`、`sendImage()`、`sendVoice()`、`sendVideo()`、`sendEmoji()`、`sendFile()`、`sendXmlMsg()`
   - `sendShareCard(String talker, String wxid)` 按 WA 同款签名发送联系人名片；底层定位微信 `SendContactCardHelper` 的名片 XML 构造方法，普通联系人按消息类型 `42` 发送，OpenIM 名片按消息类型 `66` 发送，不手写联系人字段
   - 消息和杂项：`insertSystemMsg()`、`queryHistoryMsg()`、`getUnreadCount()`、`getAllUnreadCount()`、`clearUnread()`、`clearAllUnread()`、`delay()`、`notify()`、`reloadPlugin()`、`eval()`、`loadJava()`、`loadDex()`、`loadSo()`、`getDuration()`
   - HTTP：`get()`、`post()`、`download()`
   - Hook：`hookBefore()`、`hookAfter()`、`hookReplace()`、`unhook()`
   - DexKit：`findClassList()`、`findMemberList()`
   - 反射：`firstMethod()`、`firstConstructor()`、`firstField()`、`invokeMethod()`、`createInstance()`、`getField()`、`setField()`

3. 脚本消息对象
   - `getXml()`、`getSender()`、`getTalker()`、`getContent()`、`getText()`、`getMsgType()`、`getMsgSvrId()`、`getMsgSource()`、`getSelfWxId()`、`getSource()`、`getKind()`、`getNativeUrl()`
   - `isSend()`、`isSelf()`、`isGroupChat()`、`isChatroom()`、`isImChatroom()`、`isPrivateChat()`、`isOpenIM()`、`isOfficialAccount()`
   - `isText()`、`isImage()`、`isVoice()`、`isVideo()`、`isAppMsg()`、`isEmoji()`、`isLocation()`、`isSystem()`
   - `isRedPacket()`、`isTransfer()`、`isQuote()`、`isFile()`、`isLink()`、`isMusic()`、`isNote()`、`isShareCard()`、`isVoip()`、`isVoipVoice()`、`isVoipVideo()`、`isVideoNumberVideo()`、`isPat()`、`isRecalled()`、`isAtMe()`

下一步如果继续扩展，优先补薄封装，不要直接把 `database()` / `network()` / `databaseChanges()` 这种低层入口原样暴露给脚本。更适合补的是：

- `WeChatMessageStoreApi` 的消息历史查询
- `WeChatConversationApi` 的会话列表、未读数、打开聊天
- `WeChatTaskApi` 的主线程/异步/节流
- `WeChatNotifyApi` 的通知和跳转

## 分层规则

- `hooks/api/**`: 只放公共微信 API，不放功能私有业务逻辑。
- `hooks/items/<feature>/**`: 每个功能一个独立功能包。
- `hooks/core/**`: 功能生命周期、Hook 注册和共享框架辅助逻辑。
- `ui/**`: 共享设置 UI 框架、Provider 接口和内嵌 Miuix 设置页。
- `event/**`: 跨功能事件。
- `dexkit/**`: DexKit 定位和已缓存的反射目标。
- `utils/KavaReflector.kt`: 项目统一反射入口，基于 KavaRef 封装。新增反射代码必须走这里；即使 Xposed/DexKit 需要原始 `Method`、`Field` 或 `Constructor`，也先由这里获取。
- `compat/kavaref/**`: KavaRef 运行时本地兼容层。文件目录放在 `h/Hchat/compat/...` 是为了项目结构好看，但文件内部包名必须保持 `com.highcapable.kavaref...`，因为 KavaRef core 会按这个完整类名查找。

## 开发规则

当某个行为依赖微信内部逻辑且答案不确定时，先逆向微信。宁愿多花时间确认，也不要猜。

硬规则：

- 不要编造类名、方法名、字段、构造签名、数据库表、Intent 参数或网络请求结构。
- 修改触碰微信内部逻辑的代码前，必须先有 DexKit、DexClub 或 APK 逆向证据。
- 证据不完整时要说明未知点，并保留受保护的 `isAvailable()` / `supports...()` 路径，不要写假实现。
- 不要把不确定的修复当成事实。
- 代码必须放在正确分层：
  - 功能行为放在 `hooks/items/<feature>/**`
  - 可复用且已验证的微信能力放在 `hooks/api/**`
  - DexKit 目标发现和缓存放在 `dexkit/**`
  - 框架生命周期和 Hook 清理放在 `hooks/core/**`
  - UI Provider 和共享 UI 辅助逻辑放在 `ui/**`
- 不要因为方便就把功能私有逻辑塞进 `hooks/api/**`。
- 不要在功能内重复实现公共 API。先使用 `WeChatApis`，只有验证目标版本后才新增 DexKit 逻辑。
- 新增或修改功能只要使用 DexKit 定位微信类、方法、构造器或字段，就必须缓存定位结果。缓存 key 必须包含微信版本、versionCode、clientVersion、Tinker/热更新标识、APK 时间戳和 ClassLoader 指纹。读取缓存时如果已存 key 和当前 runtime key 不一致，必须立即清空旧 descriptor 并写入当前 key，避免微信升级/降级后界面或文件仍显示旧版本缓存。缓存命中时不得每次启动微信都重新跑 DexKit；只有缓存缺失、缓存目标无法解析、微信升级/降级或热更新标识变化时才重新定位；不要跨版本或跨热更新复用旧 descriptor。
- DexKit 较重的定位和相关 Hook 优先放到后台线程安装，避免拖慢微信启动。必须在微信 attach、登录或消息初始化前生效的功能可以同步早期安装，但必须先尝试有效缓存路径，并在代码或功能文档里说明原因。
- 不要在功能代码里散落原始 Java 反射。字段、方法、构造器查找、实例创建、类加载和 `Method.invoke` 统一使用 `KavaReflector`。

## 反射规则

项目代码默认使用 KavaRef 作为反射封装。

依赖：

- `com.highcapable.kavaref:kavaref-core:1.1.0`

为什么有本地兼容层：

- 官方 `kavaref-android:1.1.0` 要求 `compileSdk 37`。
- 本地项目使用 `compileSdk 34` / `targetSdk 34` 和 AGP `9.0.1`，GitHub Actions 构建时临时覆盖为 `compileSdk 37` / `targetSdk 37`。
- 为了保持 release 构建稳定，项目在 `h/Hchat/compat/kavaref/**` 提供 `kavaref-core` 需要的少量 platform/runtime 类。
- 不要修改这些文件里的 `package com.highcapable.kavaref...`。目录是为了项目结构好看，包名是 KavaRef 的运行时约定。

推荐写法：

```kotlin
val ctor = KavaReflector.findConstructor(clazz, String::class.java, Int::class.javaPrimitiveType)
val request = KavaReflector.newInstance(ctor, talker, 0)

val fields = KavaReflector.declaredFields(clazz)
val value = KavaReflector.readField(instance, "fieldName")
```

Java 调用：

```java
Object value = KavaReflector.readField(target, "fieldName");
Object request = KavaReflector.newInstanceByArgs(clazz, args);
```

规则：

- 新业务/新功能不要直接调用 `getDeclaredFields`、`getDeclaredMethods`、`getDeclaredConstructors`、`getDeclaredField` 或 `Constructor.newInstance`，除非有明确互调原因。
- 如果 Xposed、DexKit 或缓存需要原始 `Method` / `Field` / `Constructor`，必须通过 `KavaReflector` 获取。
- 项目没有主动升级到兼容的 compile SDK 前，不要添加 `kavaref-android`。
- 不要把 `kavaref-jvm` 和 `kavaref-core` 一起加进依赖；`1.1.0` 版本会出现重复的 `KavaRefProperties` 类。

## Kotlin / Java 规则

新增或修改代码时，只要实际可行就优先使用 Kotlin。如果某段 Xposed、反射、DexKit、R8 或 Java 互调路径用 Kotlin 会变得别扭或有风险，就改用 Java。

现存 Java 文件属于当前稳定代码，不是必须迁移的待办事项。

新增会被 Java 调用的 Kotlin 代码时，保持 Java 可调用名称稳定：

- Java 需要静态风格调用时使用 `@JvmStatic`
- 只有 Java 代码需要直接字段访问时才使用 `@JvmField`
- Kotlin 的 `val isAvailable` 或显式函数要保持 `isAvailable()` 兼容
- 公共 API、生命周期组件和反射边界保留类级注释
- 不要把语言迁移和行为变更混在一起
- 默认不在本地执行 Gradle；明确允许“本地编译”时，只跑 compile 任务；明确允许“本地构建”时，验证 release 构建使用 `:app:assembleRelease`。

## 功能包结构

较大的功能要保持入口类精简，并按职责拆分实现。

当前红包功能结构：

- `hooks/items/payment/core`: 功能入口、设置、状态和流程编排。
- `hooks/items/payment/detect`: 旧消息/数据库兜底识别、XML/nativeUrl 解析、反射辅助和过滤。
- `hooks/items/payment/grab`: 静默网络领取流程和 UI 领取兜底。
- `hooks/items/payment/reply`: 领取成功后的自动文本回复。
- `hooks/items/payment/notify`: Toast/系统通知输出和模板变量格式化。
- `hooks/items/payment/fake`: 假红包/拆包兼容。

当前转账功能结构：

- `hooks/items/payment/transfer`: 自动收转账功能、设置、XML 解析和规则。
- `hooks/items/payment/fakebalance`: 伪造零钱显示功能，只替换钱包、零钱、零钱通和经营账户相关页面的本地 UI 显示，不修改真实余额、数据库、支付请求或网络返回。
- 伪造零钱显示采用 WeKit 同款思路：只 hook `com.tencent.mm.plugin.wallet_core.ui.view.WcPayMoneyLoadingView` 的金额设置入口，并补 hook `com.robinhood.ticker.TickerView` 文本设置入口；余额、零钱通和经营账户分别提供独立开关、显示方式和金额，设置页只在对应开关开启时显示配置，关闭时保留已保存内容。显示方式可选固定金额、增加金额或减少金额，金额输入只保存非负数；增加或减少模式基于微信本次传入的真实金额计算，例如真实余额 `12.63` 选择增加金额并填写 `100` 后显示 `112.63`，减少后的结果最低为 `0.00`。旧版没有显示方式字段时默认使用固定金额，曾保存的带符号金额仍会识别为对应增减模式。旧版总开关仅作为三个新开关尚未保存时的兼容默认值，不再显示或写入。启用对应账户后不替换构造阶段的无数字空文本，零钱通和经营账户等待微信设置最终字号后再一次性替换金额；服务页 cell、金额加载控件与 TickerView 的嵌套调用共用一次动态计算结果，同一控件重绘时也会识别上次渲染值并继续以缓存的微信原始金额为基准，不能把已经增加或减少过的显示值再次参与计算。模块不修改微信传入的字号，只在字号变更后刷新 TickerView 每个字符的滚动高度和最终位置，避免小字号创建的字符列在放大后显示重影。服务页钱包列表按 `MallWalletSectionCellView.c(..., String, ...)` 的 cell 绑定参数区分 `balance_cell` 和 `lqt_cell`。余额、零钱通和经营账户页面优先用金额控件局部父层级文案区分金额类型，经营账户识别服务端动态下发的经营账户、经营账号、商户账户、商户余额和商家账户文案；局部文案未命中时先读 Activity 页面标题，再回退类名或调用栈，避免经营账户复用零钱通页面类时继续误用零钱通金额。经营账户金额旧配置未保存该值时先沿用零钱通金额，用户保存后独立生效。8.0.66/68/72/74 额外修改 Kinda 零钱通详情入口传入的分单位余额；8.0.49/58 没有该入口，仍走旧 UI 金额控件链路。不劫持配置存储、不做页面生命周期补刷、不扫描整页普通 `TextView`，避免多链路补丁互相覆盖导致金额跳回。
- 自动收款只对微信转账状态机里的待领取状态发起操作：空 `paysubtype` 兼容旧消息，明确状态只接受 `1`、`7`、`21`、`27`；`21/27` 覆盖高版本延迟到账转账。其它状态视为已结束或不可领取，不自动收款/退回。
- 自动收款支持选择零钱、零钱通、经营账户和服务端下发的其它具体账户。零钱直接发起 confirm，不增加查询等待；选择非默认位置时先按当前登录 wxid 复用已缓存的 `recv_account_type`、`bind_serial`、`sub_recv_channel_id`，仅在缓存缺失时主动发送微信原生 remittance query，从 `recv_account_info.recv_channel` 按账户名称匹配真实账户并持久化，后续转账直接复用。查询类通过 `Micromsg.NetSceneTenpayRemittanceQuery`、`recv_account_info`、`recv_channel` 和构造/响应签名动态定位并缓存，不固定混淆类名、方法名、字段名或账户类型数值；8.0.49、8.0.58 使用五参数构造，8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 使用包含 `transfer_attach` 的六参数构造。领取/退回的 `transferoperation` 请求同样按完整类型签名映射：8.0.49 为 10/9 参数、8.0.58 为 12/11 参数、8.0.66 为 13/12 参数、8.0.68/72/74/76 为 14/13 参数，后一项是省略 `left_button_continue` 的委托构造；群聊请求传真实群 wxid，构造或发包失败日志包含微信版本、失败阶段、请求类和构造签名。查询失败、超时、账号切换、选项失效或服务端未提供对应账户时回退零钱，详情页账户项 Hook 仅保留为缓存补充。
- 自动收款规则与红包助手对齐为独立的模板、默认模板和适用聊天模型；旧版全局设置继续作为无模板时的兜底。每个好友或群聊最多绑定一个模板，绑定关闭时该聊天不自动收款，模板删除会解除相关绑定，批量套用会覆盖同一聊天旧绑定；适用聊天列表支持选择部分条目或全选当前筛选结果并确认批量删除。模板整体保存收款位置、固定/随机延迟、白名单/黑名单、金额条件、关键词、禁收时段、拒收退回、回复步骤和成功提醒。
- 全局设置和模板都支持多步骤收款后回复，私聊与群聊分别配置，回复类型包括文字、图片、语音、视频、表情、文件、收藏和 XML；每一步可设置发送前延迟并随机追加 `0-2` 秒，按列表顺序发送。文字、XML、通知、Toast 和播报支持 `{amount}`、`{talker}`、`{sender}`、`{time}`，文字回复额外支持 `{@转账的人}` 生成真正的群聊 @。旧版只有一套回复配置时，私聊和群聊继续共用旧配置；保存群聊配置后可留空，以关闭群聊自动回复而不影响私聊。`{time}` 表示本次收款成功时的本机时间，格式由自动收款全局设置单独配置，默认 `yyyy-MM-dd HH:mm:ss`；保存时会校验 `SimpleDateFormat` 格式，多步骤延迟回复仍使用同一次收款时间。
- 自动收款成功提醒支持通知栏、Toast、系统/自定义铃声、震动和 TTS 播报，使用独立通知通道；不提供失败通知，收款请求无法提交时只记录模块错误日志。
- 转账领取/退回本身不在功能包里实现，必须调用 `WeChatApis.payment().transfers()`，让 DexKit 定位集中在 `hooks/api/payment` 和 `dexkit/DexFinder`。

红包识别顺序：

1. 优先使用 `WeChatApis.message().observe()` 作为统一消息源。
2. 只有 `observe()` 不可用时才使用 `RedPacketMessageHook` 和 `RedPacketDatabaseHook`。
3. 去重逻辑保留在 `RedPacketState.markDetected(...)`。
4. 红包过滤时按 XML `fromusername` -> nativeUrl `sendusername` -> 消息前缀 -> observe sender 解析发送者，让“跳过自己的红包”基于真实红包发送者判断。

红包规则配置：

- 红包助手支持“红包模板”和“适用聊天”两级配置。模板只定义抢包策略，适用聊天只负责把好友或群聊分配到某个模板，同一个聊天只保留一条记录，避免同一对象被多个规则重复命中；适用聊天不包含公众号。
- 适用聊天列表支持批量删除。批量模式下可按当前搜索结果全选或取消全选，删除前必须通过模块弹窗确认；删除按绑定 ID 过滤并立即持久化，不会误删其它模板或规则。
- 规则匹配优先级固定为：适用聊天优先；命中的适用聊天如果开关关闭则直接不抢，开关开启时按绑定模板执行，没有绑定模板时跟随默认规则或旧版全局设置；未命中适用聊天时使用默认规则；没有默认模板或没有任何规则时回到旧版全局设置。绑定到不存在模板的聊天视为规则关闭，不再意外回落到其它模板。
- 当前红包模板覆盖抢包模式、无延迟/自定义延迟/随机延迟、跳过自己、关键词过滤、禁抢时段、私聊红包回复步骤、群红包回复步骤，以及通知栏、浮窗、铃声、震动、失败提醒和抢到后播报。全局旧版设置也分别保存私聊和群聊回复；升级后尚未保存群聊回复字段的旧配置继续让群聊沿用原回复步骤，用户保存后两套步骤才独立生效，任一列表为空只关闭对应场景的回复。禁抢时段通过时、分、秒选择器配置并按当天秒数匹配；新 JSON 使用 `quietStartSecond` / `quietEndSecond`，读取旧分钟字段时自动乘以 60。通知栏、浮窗、失败提醒、播报和文字/XML 回复支持 `{time}`，表示本次红包处理时的本机时间；时间格式由红包全局设置单独配置，默认 `yyyy-MM-dd HH:mm:ss`，保存时校验 `SimpleDateFormat` 格式。通知栏和浮窗提醒文案分别配置，对应输入框只在开启对应提醒后显示。Android 8+ 红包通知通道保持静音，铃声由模块按模板或全局设置手动播放，避免通知通道声音创建后不可更新导致换铃声不生效；从文件选择的铃声会复制到模块外部文件目录，避免原始文档 URI 权限失效。两类回复步骤都在二级页管理，每条步骤可单独设置文字、图片、语音、视频、表情、文件、收藏或 XML、发送前延迟和随机追加延迟，运行时按列表顺序发送。文字回复支持 `{@发红包的人}` 变量，命中时会用微信原生 @ 参数发送红包发送者。媒体/文件类回复在设置页通过系统文件管理器选择文件，不要求用户手填路径；收藏回复通过收藏选择页选择一个或多个收藏 `localId`，选择页只走微信原生收藏存储接口，并按微信原生游标推进方式连续分页加载全部收藏，不使用数据库兜底，也不固定混淆类名、方法名或字段名。旧版白名单/黑名单和自动屏蔽新进群仍作为全局兼容设置保留。
- 旧版全局抢包延迟设置同样收敛为 `无延迟`、`自定义延迟` 和 `随机延迟` 三个选项；只有选中 `自定义延迟` 时显示单个延迟输入框，选中 `随机延迟` 时显示最小/最大延迟输入框。
- `自动屏蔽新进群` 是自动抢红包的全局保护开关，默认关闭。模块启动后会把当前群聊记录为基线；开关开启期间新加入或新出现的群聊会自动加入 `适用聊天`，并把该群开关设为关闭。用户可以在 `适用聊天` 里手动打开某个新群，打开后按该群绑定模板、默认规则或旧版全局设置正常抢红包。
- 红包关键词过滤只匹配红包祝福语、发送标题、描述、备注等用户可见文案，不直接匹配完整 XML、`nativeurl`、发送者、接收者或其它固定协议字段；发送标题、描述里如果是“给你发了一个红包”等微信固定文案会被排除，避免单字关键词误伤所有红包。关键词支持用 `|`、中英文逗号或换行分隔；屏蔽关键词模式命中任一关键词即跳过，指定关键词模式要求命中至少一个关键词才抢。
- 抢到红包后可选系统语音播报，播报文案复用红包模板变量，例如 `{amount}`、`{talker}`、`{sender}`、`{time}`。播报开关默认关闭；命中模板时按模板通知配置执行，未配置通知字段的旧模板会继续沿用旧版全局通知设置。播报使用系统 TTS 的媒体音频通道，只有成功提交 TTS 后才写入去重记录；首次初始化失败会保留待播报内容并重建一次 TTS，避免首条红包播报直接丢失。

依赖关系要通过清晰 API 向内收敛：

- 功能入口负责创建和组装组件
- detect/grab/reply/notify/fake 不要自己注册到 `FeatureRegistry`
- 共享微信行为放到 `hooks/api/**`，不要放进单个功能包
- 仅该功能使用的辅助逻辑留在功能包内部
- 操作微信转账请求的支付功能必须使用 `TransferOperationParams` 和 `WeChatApis.payment().transfers()`

## 新增功能

1. 创建 `hooks/items/<feature>/<FeatureName>Feature.kt`。
2. 继承 `BaseFeature`。
3. 在 `onFeatureInit` 注册 settings provider。
4. 在 `onFeatureInstall` 安装 Hook 或 API 观察者。
5. 在 `FeatureRegistry` 注册功能。

```kotlin
class ExampleFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "示例功能"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(ExampleSettingsProvider())
        subscribe(Events.MessageReceived::class.java) {
            // 轻量事件处理
        }
    }

    @Throws(Throwable::class)
    override fun onFeatureInstall(context: FeatureContext) {
        // 安装 Hook 或使用 WeChatApis
    }

    companion object {
        const val ID = "example"
    }
}
```

## 新增设置卡片

Settings provider 只提供元数据，不能打开弹窗、Activity 或自定义 View。

设置入口只需要固定元数据时，继承 `SimpleFeatureSettingsProvider`。

```kotlin
class ExampleSettingsProvider : SimpleFeatureSettingsProvider(
    ExampleFeature.ID,
    "示例功能",
    "功能说明",
    FeatureSettingsProvider.CATEGORY_ENHANCE
)
```

Provider 分类决定主页面所在页签：

- `FeatureSettingsProvider.CATEGORY_PRACTICAL`: 实用
- `FeatureSettingsProvider.CATEGORY_ENTERTAINMENT`: 娱乐
- `FeatureSettingsProvider.CATEGORY_ENHANCE`: 插件

`SimpleFeatureSettingsProvider(featureId, title, subtitle)` 默认放到 `插件`。需要放到 `实用` 或 `娱乐` 时，使用四参数构造。主页面先按分类进入页签，页签内可在 `MiuixSettingsPage.kt` 里按功能 ID 做固定分组和排序。

然后在 `ui/miuix/MiuixSettingsPage.kt` 添加实际页面：

```kotlin
@Composable
private fun FeatureSettingsPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    when (provider.featureId()) {
        ExampleFeature.ID -> ExampleMiuixPage(context, provider, onBack)
        else -> UnsupportedFeaturePage(provider, onBack)
    }
}
```

## 设置 UI 规则

所有设置 UI 都由 `MiuixSettingsPage.kt` 里的内嵌 Miuix 框架渲染。

硬规则：

- 功能设置不要使用 `AlertDialog`、`Dialog`、模块 `Activity` 或功能私有设置 View。
- 当前唯一例外是脚本插件 README 查看器，它使用内嵌 Compose `Dialog` 作为轻量说明弹窗。不要把这个例外用于功能配置流程。
- 不要在 `FeatureSettingsProvider` 上重新引入 `showDetail(Context)`。
- `FeatureSettingsProvider` 只暴露 `featureId`、`title` 和 `subtitle`，不要把图标/颜色字段加回来。
- 功能所在主页签由 `FeatureSettingsProvider.category()` 控制。
- `SettingsUI.show(context)` 保持模块设置入口并调用 `MiuixSettingsPage.show(context)`；`SettingsUI.showScriptPluginAgent(context)` 只用于直接进入插件 Agent 对话页。当前启动面是微信设置项，以及按各自入口开关启用的 LauncherUI 右上角加号菜单、长按右上角加号入口和微信进程内悬浮快捷菜单；插件 Agent、快捷已读和快捷终止只复用同一加号菜单注入链路，不新增 DexKit 定位，悬浮快捷菜单在 Application 初始化阶段注册原生 Activity 生命周期回调，并可承载插件 Agent 动作。
- 新版微信的模块设置项保持在 `SettingAdditionHeaderSearch` 与 `SettingGroupPersonalInfo` 之间。微信按 `SettingLocation.frontItem -> 当前项` 构造单链；WeKit 会在 `SettingGroupPersonalInfo` 的位置方法上用 `beforeHook` 直接返回其代理项，默认优先级下可能让后注册模块的 `afterHook` 根本不进入回调队列。Hchat 对该位置方法使用最高优先级 Hook，先进入回调队列并在最后执行 `afterHook`：读取 WeKit 或其它入口已经返回的 `frontItem` 作为 Hchat 前置项，再把 `SettingGroupPersonalInfo` 接到 Hchat 后面，保持原位置且不覆盖微信原生设置。
- 右上角加号菜单入口必须通过结构校验动态定位 `MicroMsg.PlusSubMenuHelper`。不要写死 `of/rg` 这类特定版本混淆类、`lf/og` 这类 wrapper 类或 `mf/pg` 这类 item 类。
- Hchat 右上角加号菜单图标从菜单 adapter 行 View 上按模块菜单 ID 设置，不能把模块 `R.drawable` ID 传进微信菜单 item。设置入口、插件 Agent 和全部已读使用透明底白色线性 `H` 自绘图标；快捷终止使用脚本同款白色 `android.R.drawable.ic_lock_power_off` 电源图标。
- 右上角加号菜单的 `SparseArray` key 必须严格保持为从 `0` 开始的连续 adapter position key。已横向确认微信 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 的 adapter 都通过 `items.get(position)` 读取 wrapper；负 key、跳号或移除后遗留的空洞都会让 `getCount()` 覆盖到空 wrapper。前置、后置和移除菜单项时必须按最终显示顺序整体重排并连续写回，点击仍通过注入 item id 识别。
- 长按右上角加号入口只通过 `com.tencent.mm.ui.HomeUI$PlusActionView` 构造函数绑定，并调用其 `h()` 方法获取真实按钮 `View` 后设置 `OnLongClickListener`；不要再安装 `View.setOnClickListener`、`dispatchTouchEvent` 或 `performLongClick` 兜底 Hook。
- 插入 Hchat 菜单行不能只依赖加号菜单 adapter 工厂 Hook。微信 8.0.58/66/68 会在子类无参 `boolean d()` 里填充菜单 `SparseArray` 后再委托父类显示逻辑，8.0.74 可能在父类构造阶段提前创建并缓存 adapter。插入逻辑必须在子类填充方法后和父类显示方法前都执行。
- 平板模式必须在主进程过滤前安装早期 Hook，确保微信 main、push、tools 和迁移相关进程在升级或降级后都返回一致的平板状态。其它 Hchat 功能仍然只能保持主进程生效。
- 平板模式围绕 `TinkerApplication.onBaseContextAttached` 安装：`before` 先使用当前 loader 已有效的缓存。开启屏蔽热更新时，`before` 也可以立即定位并 Hook base APK loader，因为 Tinker 补丁会被拦截，base loader 就是预期运行时；这样能防止降级后的首次启动在平板模式生效前进入微信 attach/登录检查。未开启屏蔽热更新时，完整 DexKit 定位必须在 `after` 用 post-Tinker class loader 执行。Hook 状态必须按 class loader 跟踪，不能用单个全局 boolean。
- 平板模式只能 Hook 通过 `Lenovo TB-9707F` 锚点定位到的微信内部平板检测方法，以及通过 `loginAsOtherDeviceBtn` 定位到的登录按钮可见性方法。不要全局伪造 `Build.*` 或设备指纹。方法 descriptor 缓存必须按当前微信版本、clientVersion 和 Tinker 标识区分，升级或降级后不能回退使用其它版本的 descriptor。
- 设置页嵌入当前微信 `Activity` 的 decor view，不能启动模块 app。
- 保持 page/ComposeView 上安装 `EmbeddedComposeOwner`，并给 ComposeView 绑定当前页面独立的 `Recomposer`；不在 decor 上安装 owner，不复用窗口级 `WindowRecomposer`，页面关闭时同步销毁自己的 composition、Recomposer、lifecycle、saved-state、ViewModel 和 navigation owner。
- 没有右上角关闭按钮。返回行为是：详情页返回主列表，主列表关闭 overlay。

`MiuixSettingsPage.kt` 已经统一处理的可复用 UI 行为：

- `LazyColumn` 页面使用 `rememberLazyListState()` 并传入 `state = listState`，从二级选择页返回时保持滚动位置。
- 会跳转或选择的可点击行使用 `responsiveTap(...)`，不要直接用 `Modifier.clickable(...)`，避免滚动后的第一次点击被吞。
- 少量互斥枚举/int 选项优先使用类似系统设置的页内弹出选择菜单，即 `PopupChoiceRow` / `PopupChoice`；不要做点击一行循环切换值。
- 只有选项很多、需要多选、需要搜索或需要复杂配置时，才使用 `OptionPickerPage` 或单独二/三级页面。
- 新增功能的二级/三级菜单切换必须走 `SettingsRouteTransition` 或同等过渡动画；三级菜单返回二级菜单必须保留二级列表原滚动位置，不允许返回后跳到顶部；系统返回、返回手势和模块返回按钮必须执行当前可见页面的同一返回动作，四级页面必须逐级返回三级页面，不能由外层宿主直接跳回二级页面。
- 二级选择菜单选中条目后不要自动返回上一级，必须等用户手动点击返回，避免连续配置多个选项时反复进入菜单。
- 选择器里的筛选分组、标签筛选、搜索筛选属于同层状态切换，不做左右横向页面进出动画；只有进入标签成员、群成员、编辑页这类真实下一层页面时才做横向过渡。
- 联系人选择器每个联系人使用独立 lazy list item，不要把大联系人列表塞进一个 `SettingsCard`。
- 大型联系人名单必须使用批量标签关联、轻量联系人记录和仅含会话 ID 的排序查询；不能按标签逐个扫描 `rcontact`，也不能只为排序读取完整会话或解析无关的 `lvbuff`。筛选结果在一次重组内复用，已选置顶保持线性稳定分区，联系人 lazy item 使用稳定 ID key。
- 好友、群聊、群成员、标签好友等名单选择器进入页面时，必须在当前筛选和搜索结果内把已保存的已选项置顶，未选择项保持原有排序；本次进入后新增勾选或取消勾选不要立刻触发列表重新排序。通用会话/联系人名单选择器支持按聊天分组及其全部子分组筛选候选项，提交时只持久化真实会话 ID，不保存 `wxid_hchat_group_*` 虚拟分组 wxid。通用好友选择只包含微信好友和企业微信联系人，不包含单向联系人或群成员；所有联系人按微信原生会话的新旧顺序排列，无会话记录的项目放在末尾。群聊存在备注时统一显示为 `群备注(群名称)`，搜索同时匹配群备注、群名称和群号；弹窗或页内弹出菜单如果承载名单/多选，也遵守相同显示、搜索和置顶规则。普通少量单选枚举弹窗保持业务顺序即可。
- 涉及好友、群聊、公众号、标签好友或群成员名单的设置必须使用现有选择器，不要额外暴露 wxid、群号或成员 ID 手动填写框。
- 涉及文件、媒体或路径选择的设置必须使用 Android 系统文件管理器或系统选择器，不要要求用户手动输入绝对路径作为唯一入口。
- 联系人和选项的选中标记保持小尺寸并放在右侧。
- 主功能行保持普通设置行样式，不显示 provider 图标。图标只保留给底部导航。
- 联系人头像由 `AvatarMemoryCache` 做进程内缓存，只在微信进程重启后重新加载。
- 开关使用 Miuix 默认主题色，不要写死红色开关。
- `PageScaffold` 统一处理系统导航栏避让：三键/虚拟按键模式下，底部导航、底部操作栏和内容底部 padding 要整体避开虚拟按键，并保留一层间距；如果内嵌微信 decor 后 `WindowInsets.navigationBars` 取不到高度，要用系统 `navigation_bar_height` 兜底。全面屏手势模式保持原贴底观感。
- 多级设置页不要用早退直接替换 Composable。普通选择器、模板/名单管理、标签成员和群成员这类子页面切换统一走 `SettingsRouteTransition`，按页面深度做横向进出动画。
- 通知会话规则和屏蔽名单列表顶部提供 `公众号`、`群聊`、`好友`、`全部` 四个分类 Tab，顺序固定为公众号、群聊、好友、全部；红包适用聊天和收款适用聊天不显示公众号，只提供群聊、好友、全部；自动回复规则不显示分类 Tab。上述五类规则或会话列表以及改名、进退群监听群管理页都支持批量删除，分类页的全选只作用于当前分类和搜索结果，其余页面只作用于当前搜索结果，删除前统一二次确认。
- 主设置页使用本地 KSU 风格 `FloatingBottomBar`，四个页签为：实用、娱乐、插件、设置。`设置` 保持最右侧。实用和娱乐页签首页只展示大分组入口，点击分组后进入二级菜单展示该分组内的功能项；页签顶部提供系统设置风格的全局搜索框，可按功能名、描述、分组、插件名或插件目录搜索模块功能和本地脚本插件；搜索页顶部固定输入框和取消按钮，未输入时展示真实搜索记录，点击结果后记录当前关键词；从功能详情返回时先回到所属分组页或搜索页，再返回页签首页。
- 主页签归类当前固定为：
  - `实用`: 返回 `CATEGORY_PRACTICAL` 的 provider；返回 `CATEGORY_ENHANCE` 且不是脚本插件总开关的 provider 显示在页内 `增强` 分组。
  - `娱乐`: 返回 `CATEGORY_ENTERTAINMENT` 的 provider。
  - `插件`: 固定显示脚本插件入口。
  - `设置`: 全局 UI 设置和关于信息。
- 实用页签内二级分组顺序固定为 `聊天`、`语音`、`红包转账`、`增强`、`群组`、`朋友圈`、`美化`、`界面`、`杂项`。`聊天` 分组包含防撤回、多选撤回、自动回复、拦截正在输入上报、禁止拍一拍、自动勾选原图、自动查看原图、移除转发限制、屏蔽消息、快捷已读、删除键清引用和滑动手势；`语音` 分组包含音频转换、文本转语音、伪造语音时长、语音消息预览和语音转发保存；`红包转账` 分组包含自动抢红包、自动收款和伪造零钱；`增强` 分组包含聊天分组、消息自动转发、自定义通知、关键词通知、文字转语音播报、僵尸粉检测和定时任务等增强类功能；`群组` 分组包含群聊标签、屏蔽艾特所有人、进退群监控和改名监控；`朋友圈` 分组包含朋友圈自动点赞、朋友圈自动评论、朋友圈自动转发、朋友圈自动刷新、朋友圈过滤、朋友圈关键词屏蔽、朋友圈伪集赞、朋友圈伪评论、朋友圈伪转发、朋友圈底部详情、朋友圈发布通知、朋友圈原图上传、朋友圈上传尾巴、朋友圈防撤回和去除朋友圈广告；`美化` 分组包含消息气泡、消息文本颜色、首页文字颜色、会话时间样式、输入框提示、隐藏头像、圆角头像、自定义头像、自定义底栏和悬浮底栏；`界面` 分组包含设置入口、插件 Agent 入口、悬浮快捷菜单、资料页 ID、快捷设置备注和标签、快捷设置群聊标签、快捷查看朋友圈、快捷终止及隐藏聊天菜单；`杂项` 分组包含兼容低版本小程序、跳过小程序视频广告、跳过全局小程序开屏广告、虚拟定位和指定骰子猜拳。`alt-entry` 额外在聊天、界面、红包转账和杂项分组分别显示消息显示时间、历史发言记录、红包显示详情、跳过网页风险与视频号媒体下载，不再显示统一的“实用功能集合”。
- “快捷设置群聊标签”默认关闭。开启后，微信首页和聊天分组二级页的真实群聊长按菜单都会显示“群聊标签”，可多选已有模块群聊标签、移出全部标签，或新建标签并立即加入当前群聊；修改后会触发聊天分组同步，使按群聊标签配置的自动归拢及时更新。普通联系人、虚拟分组会话和其它非群聊条目不显示该菜单。
- `文字转语音播报` 位于 `增强` 分组，处理允许名单内收到的普通文字消息，并可单独开启默认关闭的 `播放语音消息`；允许名单支持好友和群聊且为空时不播报。文字消息可选择跟随系统默认或指定任一已安装的 TTS 引擎；引擎目录按实际声明 `android.intent.action.TTS_SERVICE` 的可用服务枚举，不强制厂商引擎提供额外元数据。跟随系统默认时，空包名初始化失败会依次尝试当前 Android 用户内可见的系统默认包名和其它已安装引擎，回退只在当前进程临时生效并在设置页显示实际引擎，不改写用户配置；手动指定引擎时不会静默切换。每次初始化都有超时保护，切换候选前会关闭旧实例并忽略迟到回调；微信分身所在 Android 用户看不到任何 TTS 服务时停止无效重试并记录明确原因，模块不能绕过 Android 跨用户权限绑定主用户的 TTS 服务。设置页通过所选引擎的 Android `Voice` API 读取播报角色，优先展示声明支持中文的角色，没有中文元数据时展示该引擎全部角色，并按稳定的 `Voice.name` 保存选择。角色影响所有 TTS 播报，切换引擎时会恢复为该引擎默认角色，旧角色不再提供时明确标为不可用且不会擅自改选。切换引擎时销毁旧实例并使用所选包名重新初始化，保留当前待播报文字，已卸载的历史选择在设置页明确标为不可用。微信 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 的清单均声明了 `android.intent.action.TTS_SERVICE` 包可见性查询。语音消息不交给 TTS：收到 PB 消息后按 `talker + msgSvrId` 异步等待消息入库和本地语音文件落地，重试期间会周期性按本地消息 ID、会话内 `msgSvrId` 和必要时的全局 `msgSvrId` 刷新已落库消息，逐个验证去重后的候选文件名与实际路径，避免 PB 临时文件名阻止后续落库结果；确认文件存在后再通过微信原生 `SceneVoicePlayer` 播放 Silk/AMR 原语音；等待最多 60 秒，功能、语音开关、允许名单或免打扰状态失效时立即取消。原生播放器在七个版本中均以稳定日志和实例 `(String,boolean,boolean,int)->boolean` 播放签名定位，暂停、继续和停止入口一并缓存，不固定混淆类名。文字和原语音进入同一串行队列，不会同时发声；原生完成或错误回调缺失时按语音时长加宽限超时跳过，避免阻塞后续任务。`自定义播报内容` 沿用旧 `播报发送人` 配置键，使用单一模板和中文可点击变量，支持微信昵称、备注、群内昵称、群聊名称、会话名称、播报来源、消息正文、消息类型、语音时长、消息时间和发送者 ID；微信昵称读取原始 `nickname`，不使用会拼接备注的完整显示名。默认模板为 `{播报来源}{消息正文}`，播报来源在私聊生成“昵称发了一条消息说”，在群聊生成“群聊名称里的群内昵称说”；语音消息的正文变量为空，模板前导语与原语音连续入队，用户可用消息类型或语音时长变量自行补充。消息时间按独立格式设置渲染，格式无效时不保存并回退默认格式。默认开启的 `免打扰不播报` 同时读取私聊和群聊的微信原生会话免打扰状态；独立的定时免打扰支持跨午夜。音键控制仅在存在播报任务时接管按键：播放中按音量减暂停，暂停后再按音量减跳过当前消息，按音量加从暂停位置继续，空闲时保留微信原有音量行为；原语音使用微信播放器自身的暂停、继续和停止。微信前台通过按键分发 Hook 处理，切到后台后在播报和暂停期间通过临时 `MediaSession` 接收音量调节，并以系统媒体音量变化监听兼容不分发远程音量事件的系统；队列结束或功能关闭时立即释放会话和监听。功能关闭或模块销毁时停止 TTS 与原生语音并清空队列。
- 自定义播报模板的“发送者昵称”按钮插入 `{发送者昵称}`，并兼容手动填写的 `{微信昵称}`；两者都只读取原始微信昵称。模板还提供发送者微信号和会话 ID 变量，备注、群内昵称、群聊名称始终独立渲染。
- 模块内所有系统文件输入选择器统一使用 `*/*`，不按图片、视频、音频或其它 MIME 类型过滤文件列表；具体发送方式仍由用户在功能中选择的内容类型决定。系统分享 Intent 和创建输出文件使用的 MIME 保留实际业务类型，它们不属于输入文件列表过滤。
- `alt-entry` 的视频号媒体菜单兼容标准 `ContextMenu` 和微信自定义菜单对象，覆盖普通视频流、分享面板及作者主页二级视频菜单。菜单项优先调用微信原生带图标添加方法；`8.0.49` 的五参数入口最后一个状态参数必须传 `false`，避免新增项显示为灰色禁用，找不到原生入口时才回退模块位图图标或普通菜单添加方法。作者主页旧入口额外用 `ref_eid`、`tridot`、`delete`、`forward` 锚定 `onCreateMMMenu`；新版继续使用 `getCreateSecondMoreMenuListener: username=` 等现有锚点。定位缓存升级到新槽位后只重建一次，后续仍按微信 runtime key 复用。
- 点击视频号菜单时只从当前回调参数、点击监听对象的直属字段和一层 holder 字段提取媒体，不做无界对象图扫描，避免误复制或下载相邻视频。当前 `FinderItem` 的普通媒体 JSON 缺少 `media_cdn_info.pcdn_url` 时，会按相同下标读取 `getMediaExtList()` 并合并扩展媒体里的 PCDN 直链；扩展数据仍无直链时继续使用现有密链、密钥和详情补全流程。
- `alt-entry` 的视频号视频下载会从媒体 `spec` 中优先选择 `codingFormat=h265` 的规格，并把对应 `fileFormat` 作为 `X-snsvideoflag` 请求参数写入原始 CDN 密链后下载解密，避免 PCDN 或默认空规格返回 H.266/VVC 视频。没有 H.265 规格或原始密链不可用时保留原下载流程；规格字段已横向确认微信 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`。
- 视频号聊天分享 XML 先隔离完整 `finderFeed` 节，再用 PullParser `nextText()` 直接读取 `mediaList/media` 叶子字段。ARSCLib 会把 `org.xmlpull.v1` 实现带入最终 APK，R8 full mode 如果混淆这套公共接口，会把 Android `Xml.newPullParser()` 返回的系统解析器错误强转为 APK 内置实现，运行时触发 `ClassCastException`；发布规则必须完整 keep `org.xmlpull.v1.**`，让视频号和普通视频 XML 在 R8 与无 R8 下都通过系统接口调用。解析异常按 10 秒限频记录原始 Throwable、输入长度和内容哈希，不能再静默转换成 `null`；媒体入口、补齐成功、普通未命中和回调检测的临时诊断日志不保留。
- 娱乐页签内二级 `群聊` 分组包含实名尾巴、群昵称自定义颜色和群成员头衔；`状态` 分组包含解除状态词长度限制；`聊天` 分组包含修改聊天记录；`抓包` 分组包含 Protobuf 抓包/发包。
- `设置` 页签把全局 UI 选项存到 `Hchat_miuix_ui`：`floating_nav` 和 `glass_nav`。
- 模块自己的设置使用 `HchatStorage.preferences(context, name)`。日常读写由 FastKV 处理，路径在微信私有 `Hchat/` 目录，不在 Android `shared_prefs/`。
- FastKV 可能为同一个配置名创建 `.kva/.kvb` 文件。`HchatStorage` 不再迁移旧存储数据。功能代码不要直接实例化 FastKV。
- `glass_nav` 使用 compose-miuix-ui 的 `miuix-blur`（`rememberLayerBackdrop` / `layerBackdrop` / `drawBackdrop`），并配合本地 `lens(...)` / `vibrancy(...)` 辅助实现底栏液态玻璃效果。
- 脚本插件可通过 `applyModuleFloatingGlassBar(View[, Map])` 把已经定位到的 Activity 原生底栏交给模块托管。适配器复用同一液态玻璃 surface，保留目标 View 的点击和选中逻辑，使用占位 View 保存原布局，并在句柄恢复、插件关闭/重载、原父容器离开窗口或 Activity 销毁时还原父容器、索引、布局参数和背景；同一个 Activity 内容根同时只允许一个托管宿主，Android 13 以下自动回退普通悬浮样式。接口不负责微信底栏定位，也不能接管 `SurfaceView`、`TextureView` 或非 Activity 内容树中的 View。
- `glass_nav` 只能在 Android 13 及以上启用；低版本没有 `android.graphics.RuntimeShader`，设置页必须禁用该开关并回退普通底栏。
- 重要：backdrop 只能从页面内容层记录。底栏必须作为独立 overlay 画在 `layerBackdrop(...)` 容器外，否则 blur 目标可能记录自身并在微信宿主进程触发 native `libhwui` 崩溃。
- 底栏是从 KernelSU 的 KSU 风格液态底栏复制/适配而来，不是 Miuix `NavigationBar`。相关文件：
  - `ui/miuix/FloatingBottomBar.kt`
  - `ui/miuix/LiquidGlass.kt`
  - `ui/miuix/animation/DampedDragAnimation.kt`
  - `ui/miuix/animation/InteractiveHighlight.kt`
  - `ui/miuix/animation/DragGestureInspector.kt`
  - `ui/miuix/liquid/CombinedBackdrop.kt`
  - `ui/miuix/liquid/InnerShadow.kt`
- 不要把底栏替换成简单的 `textureBlur` 背景或手写白色半透明胶囊。KSU 风格行为依赖三层：普通内容、由 `tabsBackdrop` 捕获的隐藏主色 tint 内容、以及移动选中液态胶囊绘制的 `combinedBackdrop`。
- 可见底栏图标/文字保持 `onSurface`。主色/蓝色选中效果来自液态胶囊下方移动的隐藏 tint 内容层。如果单独按 `selectedIndex` 给可见 item 着色，慢速拖动时会因为颜色只在选择完成后变化而显得不对。
- 点击项必须调用 `FloatingBottomBar` 内容作用域提供的选择回调，并且只更新外部 `selectedTab`；外部选中索引是离散切换驱动胶囊位置的唯一入口，离散位置弹簧沿用 KSU 的 `press -> move -> release` 反馈；拖动中的连续位置与最终吸附也必须通过同一个互斥动画通道同步，不能让点击回调和外部同步同时启动位置动画，也不能用待确认标记跳过外部同步。用于 `tabsBackdrop` 的透明主色内容层只参与绘制，不能重复注册点击事件。不要绕过组件直接更新页签，也不要只把页签状态保存在底栏内部。
- 开启 `floating_nav` 时，底栏使用 intrinsic width 并居中悬浮。关闭时底栏必须 `fillMaxWidth()`，让四个 item 分散到整个底部。
- 除非有明确测量原因，否则保持 KSU blur 强度：外层/tint backdrop 使用 `blur(4.dp.toPx(), 4.dp.toPx())`；选中胶囊使用 `lens(...)`，不要叠加强模糊。更大的 blur 会冲淡图标和文字。
- 底栏图标是本地 `ImageVector` 定义，路径数据来自 AndroidX Material Rounded。微信内嵌 UI 不要使用 `vectorResource(R.drawable...)`；模块 drawable 资源可能按微信资源表解析并触发 `Resources$NotFoundException`。

新增设置页时：

1. 用 `registerSettingsProvider(...)` 注册元数据 provider。
2. 在 `FeatureSettingsPage(...)` 添加分支。
3. 使用 `PageScaffold`、`SettingsCard`、`SwitchRow`、`PopupChoiceRow`、`ActionRow`、`InputRow`、`NumberInputRow`、`ContactPickerPage` 和 `OptionPickerPage` 构建页面。
   `PageScaffold` 负责 Miuix 顶栏、内嵌 backdrop 和底栏管线。
4. 在任何 `picker` / `optionPicker` 分支前保存页面滚动状态：

```kotlin
val listState = rememberLazyListState()
val scrollBehavior = MiuixScrollBehavior()

picker?.let {
    ContactPickerPage(...)
    return
}

PageScaffold(...) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(...)
    ) {
        // 设置项
    }
}
```

5. 打开联系人选择或其它子页面的行使用 `ActionRow`。
6. 少量枚举/int 选项使用 `PopupChoiceRow` / `PopupChoice` 作为页内弹出选择菜单；大量、可搜索、多选或复杂选项才使用 `OptionPickerPage`，不要点击行后直接改值。
7. 好友/群聊选择使用 `ContactPickerPage`。除非共享选择器无法覆盖需求，否则不要在功能 UI 页面里直接查询联系人。
8. 文件/媒体选择使用 Android 系统文件管理器；选择结果需要普通文件路径时，应复制到模块私有缓存后再交给发送或运行时接口。

## 朋友圈底部详情

`朋友圈底部详情` 的入口是 `设置` -> `实用` -> `朋友圈` -> `朋友圈底部详情`，默认关闭。开启后，在发现页朋友圈的底部时间和好友个人主页的原生日期区域按同一自定义模板显示；功能只改时间详情展示，不改变朋友圈发布内容或原有可见范围设置。个人主页通过七个支持版本共有的 `SnsSelfAdapter` 条目读取和日期绑定入口取得当前 `SnsInfo`，避免按位置猜测或查询数据库；同一次绑定可能连续读取前一条、当前条和同组图片条目，因此运行时按 `createTime` 保存最近条目并与日期绑定参数精确匹配，不能依赖最后一次 `getItem()` 的返回值。新版本的 Flutter 朋友圈页无法承载该模板，因此功能开启时使用微信原生页面渲染，关闭后不改变微信原有页面选择。

格式设置包括：

- `文本格式` 默认值为 `${originalText} | ${time}`。留空保存时恢复该默认值；模板会替换已知变量，普通文本原样保留。
- `时间格式` 默认值为 `yyyy-MM-dd HH:mm:ss`，使用 Java `SimpleDateFormat` 规则，按设备当前时区格式化朋友圈创建时间。格式无效时不能保存；创建时间缺失或格式化失败时回退为微信原时间文本。
- 支持的变量为 `${originalText}`（微信原时间）、`${time}`（按时间格式生成的时间）、`${type}`（朋友圈中文类型）、`${snsId}`（朋友圈 ID，无符号十进制）和 `${userName}`（发布者 ID）。

文本格式输入框下方提供中文变量按钮：`微信原时间`、`自定义时间`、`朋友圈类型`、`朋友圈ID`、`发布者ID`。点击按钮会把对应的实际占位符插入当前光标位置，并替换当前选中文本，用户不需要手动记忆变量名。

`隐藏可见范围` 默认关闭。开启后只隐藏朋友圈底部的可见范围图标，不修改朋友圈的真实可见范围或权限判断。

多版本定位和缓存遵循统一规则：发现页时间文本方法通过 `getTimeString` 字符串锚点定位，可见范围方法通过 `getShowGroupEnable` 字符串锚点定位；个人主页日期绑定方法通过 `cerateTimeView` 与 `formatTimeInGrid` 定位，当前条目读取方法通过 `getItem` 与 `com.tencent.mm.plugin.sns.ui.SnsSelfAdapter` 定位，新版页面选择开关通过 `enableFlutterSNSPage` 与 `com.tencent.mm.plugin.sns.router.SnsRouter` 定位。每个候选都继续校验实例方法、参数、返回类型、继承关系和朋友圈数据字段等结构条件，不能写死任一微信版本的混淆类名或方法名。所有 descriptor 都必须通过 `DexMethodCache` 缓存，且每次读写缓存前实时生成 runtime key，至少区分微信版本、`versionCode`、`clientVersion`、热更新标识、APK 时间戳和 ClassLoader 指纹。发现页时间、可见范围和个人主页 Hook 使用独立的 `DexInstallScheduler` 任务，任一定位失败不会重复挂载或阻断其它 Hook。缓存命中时直接复用，缓存缺失、解析失败、微信升级/降级、热更新变化或 ClassLoader 变化时清理旧 descriptor 并按当前版本重新定位；定位和安装走后台流程，不阻塞微信启动。个人主页的日期绑定和条目读取入口已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`。

## 移除通话媒体限制

`call_media_limit` 默认关闭。开启后把微信 `DeviceOccupy` 的通话、语音、相机和小程序音视频占用检查结果改为 `false`，允许聊天中播放语音条、查看视频和打开拍摄。关闭后 Hook 保留安装，但每次调用都实时读取开关并执行微信原逻辑。

`call_ringtone_block` 放在聊天分组，提供 `屏蔽通话呼入铃声` 和 `屏蔽通话呼出铃声` 两个独立开关，默认均关闭，只阻止对应方向的微信语音/视频通话铃声启动，不改变系统音量、通话音频、语音消息或视频播放。运行时按铃声入口中的 `isOutCall` 区分呼入与呼出，同时覆盖旧 `VoIPAudioManager` 三参数入口、`VoipRingtoneLogic` / `VoIPMPRingtoneController` 五参数入口、CoreV2 原生回调，以及群聊多人通话独立使用的 `MultiTalkAudioManager` 单布尔方向入口；CoreV2 被屏蔽时仍调用同一原生确认方法，避免阻断通话建立流程。群聊入口通过 `MicroMsg.MT.MultiTalkAudioManager` 与两条 `requestAudioDeviceToStartRing` 日志定位路由方法，再从其唯一同类 `void(boolean)` 调用解析真实铃声入口，不写死混淆类名。上述入口已横向确认微信 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`，定位结果写入运行时版本隔离的 `DexMethodCache`。

底层限制来自微信 `DeviceOccupy`：动态定位带有 `MicroMsg.DeviceOccupy` 标签的 `isMultiTalking`、`isCameraUsing`、`isVoiceUsing` 和 `checkAppBrandVoiceUsingAndShowToast isVoiceUsing:%b, isCameraUsing:%b` 方法，并从群通话检查方法的调用关系补出静态无参的原始群通话状态方法。功能开启期间这些方法始终返回 `false`；该层不依赖 `AudioManager` 或语音播放器状态，因此语音播放改变音频路由后不会使功能失效。

语音消息入口还会绕过 `DeviceOccupy`，直接派发 `VoipCheckIsDeviceUsingEvent` 并读取结果决定是否显示“正在语音通话”提示。功能动态定位聊天语音消息的这层检查方法，并用线程作用域把事件结果改为未占用；作用域退出后其它业务继续看到真实通话状态，不能全局清空事件结果，否则会影响发起新通话等流程。

不额外 Hook `SceneVoiceAudioManager` 或播放器 `setMute(boolean)`，避免播放器实例、音频焦点和通话状态切换产生相互竞争。DexKit 缓存 `DeviceOccupy` 占用方法组和语音消息通话检查方法，安装统一走 `DexInstallScheduler`。

以上 `DeviceOccupy` 方法组、语音消息直接事件检查和 `MsgQuoteItem` 参数形态已横向确认微信 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`，运行时不能固定混淆类名或方法名。

## 指定骰子猜拳

`game_emoji_result` 位于实用页签的 `杂项` 分组，提供两个互斥模式：`使用固定结果` 按设置直接改写骰子点数和猜拳结果，`发送时选择` 在点击微信骰子或猜拳表情后用模块 Miuix 弹窗选择本次结果；两个模式都关闭时完全保留微信原逻辑。取消选择不会发送本次游戏表情，弹窗不可用时回退为微信原结果。

运行时复用公共表情 API 已定位的 `NetSceneUploadEmoji: msgId` 发送方法，只处理 `EmojiInfo` 中名称、协议内容或 md5 明确属于 `dice` / `jsb` 的对象。骰子按 `gameext type=2` 使用内容值 `4~9` 表示 `1~6` 点，猜拳按 `gameext type=1` 使用内容值 `1~3` 表示剪刀、石头、布；发送对象直接沿用微信本次调用的 `talker` 和其它参数，不额外推断当前会话。

发送方法已用 DexClub 横向确认：`8.0.49` 到 `8.0.76` 均为 `void(String, EmojiInfo, ...)`，后续参数数量和类型随版本变化但前两项稳定；`EmojiInfo` 的游戏目录 `50`、`field_md5`、`field_size`、`field_content`、`field_name` 等持久化字段，以及按 `jsb` / `dice` 名称读取内置资源的规则在 `8.0.49` 与 `8.0.76` 两端一致。功能不得写死 `hp.t`、`os1.h` 等单版本混淆类名。

## Release 构建

Release 构建必须使用现有 release 配置。release build type 已开启 R8/minify（`isMinifyEnabled = true`），并对内部实现类启用增强混淆：访问放宽、激进重载混淆、类字符串适配和包重打包。keep 规则必须继续保留 LSPosed 入口类、脚本插件 API、JNI 编解码类、WA 兼容 Bean 和反射桥接类；不要为了让 jadx 输出更短而削弱这些 keep 规则。

Release 打包会排除协程调试探针和常见依赖元数据，例如 `META-INF/*.version`、`META-INF/**/LICENSE*` 和 `META-INF/**/NOTICE*`。不要为了删除 AGP 生成的 `META-INF/com/android/build/gradle/app-metadata.properties` 或 `META-INF/version-control-info.textproto` 去二次处理已签名 APK；如果当前 AGP 版本有稳定 DSL 开关，再使用 DSL 关闭。

任务是产出 release APK 时，不要跑 debug 构建。

当前构建栈：

- Gradle wrapper: 9.5.1
- Android Gradle Plugin: 9.0.1
- Kotlin Compose plugin: 2.4.0
- Compose plugin: 1.11.1
- Miuix: `top.yukonga.miuix.kmp:miuix:0.8.8`
- Miuix blur/shader: `app/libs/` 里的本地修补 AAR，用于 KSU 风格悬浮底栏玻璃效果。
- 底栏图标：本地 `ImageVector` 路径数据，来源于 AndroidX Material Rounded。除非已经验证 Termux 下 R8 内存占用，否则不要添加完整 `material-icons-extended` 依赖；完整图标包可能让设备上 release 混淆不稳定。

Termux 注意事项：SDK build-tools 自带的 `aapt2` 是 x86_64 二进制，不能在当前 Termux 环境直接运行。使用 `/data/data/com.termux/files/usr/bin/aapt2`。

AGP 9 资源注意事项：当前环境使用 Termux `aapt2` 时，`optimizeReleaseResources` 可能不会产出 `resources-release-optimize.ap_`。不要为了这个本地问题提交项目级 Gradle 兜底；本地构建绕行处理只保留在命令或当前会话里。

在项目根目录使用这个命令：

```sh
ANDROID_HOME="/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu/root/Android/Sdk" \
ANDROID_SDK_ROOT="/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu/root/Android/Sdk" \
GRADLE_USER_HOME="$PWD/.gradle-home" \
sh ./gradlew :app:assembleRelease -x :app:checkReleaseAarMetadata \
  --no-daemon --no-watch-fs --max-workers=1 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8" \
  -Dkotlin.daemon.jvm.options="-Xmx768m" \
-Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

预期输出是：

```text
dist/Hchat-release-signed.apk
```

如果构建环境设置了 `HCAT_APK_NAME=Hchat-alt-entry-release-signed.apk`，输出会改为：

```text
dist/Hchat-alt-entry-release-signed.apk
```

构建后验证 APK 文件、SHA-256 和签名：

```sh
ls -lh --full-time dist/Hchat-release-signed.apk app/build/outputs/apk/release/app-release.apk
sha256sum dist/Hchat-release-signed.apk app/build/outputs/apk/release/app-release.apk

SDK=/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu/root/Android/Sdk
APKSIGNER=$(find "$SDK/build-tools" -path '*/apksigner' -type f 2>/dev/null | sort | tail -1)
"$APKSIGNER" verify --verbose --print-certs dist/Hchat-release-signed.apk
```

## 生命周期清理

`BaseFeature` 会自动清理：

- 通过 `registerSettingsProvider` 注册的 settings provider
- 通过 `subscribe` 注册的 EventBus 订阅
- 传给 `trackSubscription` 的 API 订阅

原始 Xposed Hook 应使用 `HookRegistry.get().hook(...)`，单独解除时使用 `HookRegistry.get().unhook(handle)`，同时释放 Xposed 注册和模块注册表中的强引用。脚本手动解除、关闭及加载失败清理均走该入口，避免反复重载保留旧回调及解释器。

普通功能的延迟任务、去重和限频统一使用 `WeChatApis.runtime().tasks()`，不要在功能内自建 timer。脚本 `delay()` 因需要按每次加载实例取消，使用独立的生命周期管理：主进程仍在主线程回调，小程序进程使用共享的两线程调度器，关闭、重载及加载失败取消该实例尚未执行的任务。每实例最多等待 128 项、每进程最多等待 512 项，超限跳过并限频记录；已开始执行的用户脚本不能被强制终止，旧实例不能继续追加延迟任务。

脚本普通消息分发使用容量 128 的单线程 FIFO 队列。满载丢弃新脚本事件并限频计数，不得使用 CallerRunsPolicy 将用户脚本转移到微信调用线程。无消费者时跳过包装和去重；最后消费者卸载时清空队列。入队时固定目标插件实例，执行及取得解释器锁后均检查实例身份，避免旧消息进入重载后的新解释器。

## 运行时设置

所有功能开关和用户可编辑设置在保存后必须立即生效。普通设置不要要求重启微信。

规则：

- 不要只在 `hookAll()`、`onInit()` 或 `onFeatureInstall()` 里读取一次设置，然后一直使用旧值。
- 如果功能为了性能缓存设置，必须在处理事件前刷新缓存，或在设置页保存时接收明确刷新信号。
- 保存设置时必须先写入值，再让运行时代码读取。下一步操作立即依赖新值时使用 `commit()`。
- 开关控件可以点击即保存，但保存按钮仍必须写入完整表单。
- 普通运行时设置包括启用开关、通知开关、自动回复、自动关闭、跳过自己、自动屏蔽新进群、延迟、模式、白名单/黑名单、关键词过滤、假红包选项和文本/媒体回复模板。红包助手总开关、自动关闭页面、通知、随机回复等开关默认关闭，必须由用户主动开启。
- 红包助手的模板和适用聊天属于运行时规则配置，保存后必须被下一条红包识别立即读取；不要只在启动时构造一次规则快照。
- 红包助手和自动收款的白名单/黑名单联系人选择支持好友、群聊和标签分组；标签分组只选择标签下好友，联系人行底部显示所属标签，并支持对当前筛选结果全选/取消全选。选择页保存时必须用当前勾选结果覆盖原名单，允许空选保存为空，不能只追加旧名单。
- DexKit 定位、Hook 安装和其它只在启动期决定的结构性能力仍可能需要重启，但普通功能行为不应该需要重启。

红包设置不要只在启动时刷新：

```text
sFastSkipSelf
sFastListMode
sFastKwMode
sFastKeywords
sFastMyWxid
```

应在红包识别/过滤前刷新这些值，或替换成保存时会刷新的运行时设置快照。

## 日志

运行时日志默认保持安静，只记录：

- 错误
- 缺失的类/方法
- 不可用的关键 API
- 明确开启的调试或冒烟测试输出

规则：

- 普通模式不要记录每个成功的 Hook/API 初始化。
- DexKit 成功命中日志必须放在 `VERBOSE=false` 或冒烟/调试开关后面。
- 启动兼容性检查可以自动运行，但只能打印缺失项。
- 数据库变更等高频事件必须按表过滤并限频。
- 除非相关调试设置已开启，功能代码不要为普通业务流程调用 `XposedBridge.log(...)`。

## 群昵称自定义颜色

- `群昵称自定义颜色`位于娱乐页签的`群聊`分组，使用独立总开关、颜色和字重配置，不与实名尾字颜色互相覆盖。
- 颜色配置复用实名尾字的拾色器和格式，支持 `#RRGGBB`、`#AARRGGBB` 与起止色渐变；留空时保持微信原昵称颜色。字重范围为 `100..900`，按 100 进位规整。
- 昵称颜色、群成员头衔和实名尾字共用 `ChatNameDecorationLayout` 状态与同一个群昵称绑定 Hook。颜色只应用到基础群昵称文本，不能覆盖头衔或尾字 span，也不能再新增聊天 Adapter bind Hook。
- 群昵称绑定入口已横向确认微信 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`；`8.0.76` 对应 `a0.o(g0, fd5.d, e9, String)`，仍直接读取 holder 的 `userTV`。

## 实名尾字

`实名尾字`在群聊昵称后追加缓存到的实名尾字，显示格式为：

```text
昵称(实名尾字)
```

显示页提供两个独立开关：

- `KEY_SHOW_GENDER`: 在尾字后追加性别。男、女、未知三种文案分别由 `KEY_GENDER_MALE_TEXT`、`KEY_GENDER_FEMALE_TEXT`、`KEY_GENDER_UNKNOWN_TEXT` 控制；未知文案留空时不显示。
- `KEY_SHOW_REGION`: 在尾字后追加地区，地区来自联系人资料原始省市文本，模块不做全称补全。
- `KEY_TAIL_COLOR`、`KEY_BRACKET_COLOR`、`KEY_GENDER_COLOR`、`KEY_REGION_COLOR`: 分别控制实名尾字、左右括号、性别、地区颜色。设置页提供 PS 风格拾色器和十六进制输入，颜色行标题右侧带 `单击` 提示；展开后可选择起始色/结束色，左侧色盘和右侧色相条都支持点击取色与滑动取色，值支持 `#RRGGBB`、`#AARRGGBB` 或 `#RRGGBB,#RRGGBB` 渐变，留空时跟随微信原昵称颜色。
- `KEY_TAIL_WEIGHT`、`KEY_BRACKET_WEIGHT`、`KEY_GENDER_WEIGHT`、`KEY_REGION_WEIGHT`: 分别控制实名尾字、左右括号、性别、地区粗细。设置值为 `100..900`，会按 100 进位规整；渲染层使用自绘 span 按真实绘制位置应用渐变和字重，Android 9 及以上按真实权重绘制，旧系统退化为普通/加粗两档。
- 如果开启性别或地区但本地资料缺失，实名尾字会后台预取联系人资料。预取优先调用微信原生 `MicroMsg.GetContactService`，失败时才回退到原生网络请求兜底；请求按联系人 ID 冷却，避免高频刷包。资料返回并落库后会重新刷新当前昵称。
- 实名尾字只读取 `/cgi-bin/mmpay-bin/beforetransfer` 回包 protobuf 字段 `4`，在 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 的微信生成类中均对应响应对象字段 `f`；不能再遍历 `e/g/i` 等其它字符串字段猜姓名。`@openim` 企业联系人不支持该查询，服务端可能在字段 `4` 返回长纯数字业务标识，因此不发起实名查询；纯数字、联系人 ID、URL 和 OpenIM 的历史错误缓存会在读取时删除，不再渲染。
- 如果群成员昵称全是换行符或空白符，渲染时会尝试用联系人显示名兜底；仍取不到时使用不换行空格 `\u00A0` 占位，避免因为空昵称导致括号内尾字不显示。
- 已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74` 的 `GetContactService` 入口都存在，但混淆类名和方法名不同。兼容时只能按字符串锚点 `dkverify add Contact` / `[addContact] has consume` 和参数形态动态定位：`String,String` 或 `String,String,int`。拿服务实例时优先反查返回该服务类型的静态无参 getter，对齐资料页里的 `n8.a()` / `r7.a()` 这类包装入口。
- 已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 的群聊昵称填充都经过 `MicroMsg.ChattingItem` 的 `fillingUsername:need getKfInfo` 方法，签名固定为 `(holder, chattingContext, nativeMessage, memberId)`，最后一个 `String` 就是成员 ID；真实昵称控件是 holder 的 `userTV` 字段。实名尾字、昵称颜色和头衔渲染必须优先使用该方法传入的成员 ID，并以 holder 的 `userTV` 作为唯一昵称定位源；显示层统一在 `userTV` 内组合“头衔 / 昵称 / 尾字”，不要额外注册聊天适配器 bind hook 二次拆消息和猜 sender，也不要按 `brc`、`0x7f091065` 这类资源名或固定 ID 兜底查找昵称 View，资源 ID 在不同版本会漂移并可能指向其它控件。
- 已横向确认 `8.0.49` 的 `o0(holder, nick)` 和 `8.0.74` 的 `X(holder, nick)` 在昵称为空时会把 `userTV` 设为 `GONE`。红包、语音等卡片消息仍会复用同一个 `userTV` 字段；模块渲染头衔/实名尾字时如果发现原生昵称控件已隐藏，必须先按当前成员 ID 兜底恢复联系人显示名，再挂载头衔和尾字，同时对自己发送消息和无效成员清理旧装饰，避免 RecyclerView 复用残留。

两个开关都打开时格式为：

```text
昵称(实名尾字 男 浙江 杭州)
```

关闭开关后再次绑定昵称时会自动清理旧的追加后缀，避免重复显示。

## 群员头衔

`群员头衔`在群聊昵称左侧插入圆角头衔徽标。默认头衔来自群成员身份：群主、管理员、群员；点击聊天里的头衔可以给当前群的当前成员单独设置自定义头衔名称和颜色。

设置项：

- `KEY_ENABLE`: 开启群员头衔。
- `KEY_SHOW_MEMBER`: 是否显示普通群员；关闭后仍显示群主、管理员和已自定义头衔的成员。
- `KEY_OWNER_TITLE`、`KEY_ADMIN_TITLE`、`KEY_MEMBER_TITLE`: 分别控制群主、管理员、群员的默认头衔名称，留空时恢复默认文案。
- `KEY_OWNER_COLOR`、`KEY_ADMIN_COLOR`、`KEY_MEMBER_COLOR`、`KEY_CUSTOM_COLOR`: 分别控制群主、管理员、群员、自定义头衔默认颜色。默认色为群主黄色、管理员绿色、群员灰色。设置页取色盘可选择起始色/结束色；颜色支持单色 `#RRGGBB` / `#AARRGGBB`，也支持 `#RRGGBB,#RRGGBB` 渐变。
- 聊天内点击头衔弹出的 Miuix 弹窗可按当前群和当前成员单独设置头衔名称与颜色，颜色行默认只显示输入框和预览色块，点击颜色行后才展开同款取色盘，单人颜色同样支持渐变；内容超出屏幕时必须可向下滚动。弹窗只通过保存、重置、取消或空白关闭，不再拦截系统返回。
- 头衔、昵称、实名尾字统一塞回微信原生昵称 `userTV` 这一个 `TextView` 里显示，顺序为头衔、昵称、实名尾字；不要再包装 `userTV` 或向其父容器追加兄弟 View，避免红包、语音等特殊消息布局被微信后续 bind 逻辑重新定位后错位。头衔背景、头衔文字、实名尾字/性别/地区颜色必须用 span 自绘支持起始色/终止色渐变。昵称和尾字不能省略，过长时允许在同一个 `TextView` 内换到第二行或更多行完整显示。
- 群员头衔和实名尾字共用昵称装饰状态。任一功能显示或隐藏装饰前，如果 `userTV` 当前文本不是模块上次渲染结果，必须先把微信刚绑定的新文本同步为基础昵称，再组合头衔和尾字；不能让先执行的关闭态 Hook 用 RecyclerView 上一条消息缓存的基础昵称覆盖当前成员昵称。
- 头衔绑定成员 ID 时要沿用实名尾字的有效联系人 ID 校验规则，允许非 `wxid_` 开头的自定义微信号，但必须过滤纯 hex、长纯数字、URL、群 ID 和媒体消息里的 24~64 位客户端标识，避免媒体消息把头衔绑定到随机 ID 上；自己发送消息必须清理旧头衔，不显示群员身份。
- 头衔和左滑引用这类依赖聊天适配器 bind hook 的功能，安装时不能在定位失败后永久标记成功；缓存失效、微信刚 attach 完 ClassLoader 未稳定或 DexKit 暂时失败时，必须后台有限重试补装，hook 真正安装成功后才停止重试。

逆向依据：

- 已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74` 的 `GroupAdminManagerUI` 都从 chatroom 对象成员列表读取管理员状态。
- 群主来自 `chatroom.roomowner`，实现应直接查询该列，不能依赖联系人表是否已经把该会话识别为群聊。
- 管理员来自 `chatroom.roomdata` protobuf：顶层字段 `1` 是成员子消息，成员字段 `1=userName`、字段 `2=displayName`、字段 `3=roomFlag`，`roomFlag & 0x800 != 0` 表示管理员。实现直接解析本地 `roomdata`，不写死微信混淆类名或方法名；读取 blob 时要兼容 `byte[]`、hex 字符串和带 `toByteArray/getBytes` 的包装对象。
- 微信升级、降级或热更新后的首次启动，`chatroom` 表可能短时间未就绪；字段探测和 `roomdata` 空结果不能永久缓存。头衔不应按消息逐条轮询身份数据，应订阅 `chatroom` 表变更，等 `roomowner` 或 `roomdata` 真正更新后刷新当前群可见头衔。

## 防撤回

防撤回提示模板支持 `{name}`、`{content}`、`{sendTime}` 和 `{recallTime}`，设置页分别显示为撤回者、文字内容、发送时间和撤回时间。发送时间取被撤回原消息的创建时间；撤回时间优先取微信撤回事件的服务端时间，无法解析时回退到本机收到事件的时间。模板包含任一时间变量时显示共用的时间格式输入，默认 `yyyy-MM-dd HH:mm:ss`；保存前按 `SimpleDateFormat` 校验，运行时遇到旧配置或异常格式则回退默认格式。

`防撤回`开启“保留自己撤回的内容”时不能只恢复 `message` 表字段。8.0.66/68/72/74 已逆向确认，撤回入口会先调用 `MsgProcessingManager` 清理 `talker_msgId` 对应的 `MsgProcessingInfo` 内存/MMKV 记录，图片、视频、语音等媒体查看依赖该处理信息；模块应动态定位该清理方法，并且只阻止已确认处于自己撤回链路的媒体消息清理处理信息，而不是拦截通用 `File.delete()`。

拦截自己撤回的消息存储更新时必须按目标方法返回类型返回兼容值：`int` 返回 `0`、`boolean` 返回 `false`、`void` 返回 `null`。不要对 primitive 返回方法直接 `setResult(null)`，否则 8.0.74 这类版本会在 `NetSceneRevokeMsg.onGYNetEnd` 拆箱时闪退。

8.0.49/58 没有 `MsgProcessingManager`，自己撤回回包会先调用旧版统一媒体清理入口，再更新 `message` 表。49 已确认 `NetSceneRevokeMsg.onGYNetEnd -> tj0.a9.e(k9,false)`，58 已确认 `NetSceneRevokeMsg.onGYNetEnd -> fq0.x8.e(q9,false)`；这类旧版入口按 `MicroMsg.NetSceneRevokeMsg` / `[oneliang][doSceneEnd.revokeMsg]` / `cannot find the msg:%d after revoke.` 动态定位，只拦调用栈来自 `NetSceneRevokeMsg.onGYNetEnd`、第二个参数为 `false`、消息是自己发送且类型为图片/语音/视频/表情的调用，避免影响普通删除消息。

## 兼容性自检

`WechatApiFeature` 初始化公共 API 后，会运行一个安静的兼容性自检。

自检只在被关注能力缺失时打印日志，日常使用不输出完整成功报告。

关注的能力包括：

- 运行时版本/指纹 API
- 消息观察/来源 API
- 文本发送 API
- 网络分发 API
- 数据库变更监听
- 群成员显示名方法
- 聊天页启动、Fragment 进入与退出方法
- 红包领取/打开 DexKit 目标

新增可复用微信 API 时，在目标 APK 验证完成后，也要把它加入 `WeChatDiagnosticsApi.buildCompatibilityIssues()`。

## Agent 交互架构

### 一次请求的生命周期

Hchat 的一次生成按以下顺序运行：

1. 保存用户消息、附件和引用快照。
2. 根据当前会话、插件草稿、压缩摘要、已授权路径和工具目录组装请求。
3. 优先向 OpenAI 兼容接口发送 `tools`。工具名称和参数来自插件工作区、本地逆向目录、MCP `tools/list` 以及模块内置的联网和外部文件工具。
4. 收到工具调用后，调用插件工作区、本地逆向、联网、外部文件或 MCP 实现，并把工具调用、参数、状态和结果写入独立事件。
5. 使用标准的 `assistant.tool_calls` 与 `tool` 消息把结果回传给模型，继续同一任务。
6. 插件文件有变更时，模型必须在最后一次写操作后依次调用 `workspace_status` 和 `show_diff(path=".")`，再返回 `workspace_done`。客户端从暂存目录重新计算标准统一 diff、执行静态检查并事务提交；没有文件任务时直接回答用户。

### 会话生命周期

- 首次进入插件 Agent 都从一个临时新对话开始，不自动续接最近历史会话；离开时仍在查看的会话会在下一次进入时恢复，其他旧会话需要从历史列表主动打开。
- 开启 `界面 -> 悬浮快捷菜单` 后，可通过其中的 `插件 Agent` 快捷项展开或收起完整 Agent 页面。收起只销毁界面并保存当前会话位置，不取消正在运行的请求、上下文压缩或等待确认的插件修改。
- 临时会话发送首条用户消息后才写入历史。未产生用户对话就退出、切换或再次新建时，不生成历史记录，并清理该临时会话的附件和工具结果。
- 已保存会话仍保留标题、排序、置顶、锁定、草稿、压缩摘要和工具记录；主动打开后可以继续原有上下文。
- Agent 运行状态按会话独立保存。返回设置页、收到微信新消息或切换到其它会话都不会取消当前任务；页面只切换当前显示的会话，后台任务继续执行并持续保存自己的消息、工具事件和确认状态。
- 多个会话可以同时运行。停止按钮只取消当前打开的会话；重新进入 Agent 时会优先恢复离开前正在查看的会话，后台完成或等待确认的结果不会丢失。
- 插件文件确认、最终提交确认和压缩状态也归属具体会话。切换会话不会替另一个会话取消、确认或提交文件变更；删除正在运行的会话必须先停止任务。

不支持原生 `tools` 的接口会自动退回兼容协议：工具调用仍使用旧的 JSON 状态字段，但工具准备文本不会进入聊天正文。接口拒绝 `tools` 时只重试一次兼容请求，不把错误响应当作搜索结果或工具结果。

### 显示层事件

聊天正文、思考、工作状态和工具事件是四种不同的数据：

- `Working (耗时)` 是客户端请求状态，只在还没有正文时显示，不写入消息正文和上下文。
- `reasoning_content`、`reasoning_details`、`thinking` 等字段是服务端实际返回的思考内容，单独折叠展示；客户端不伪造“正在分析需求”之类的思考。
- 正文只接收当前 assistant 回合的增量，工具开始后当前回合结束，工具完成后新建后续 assistant 回合。
- 工具调用单独显示名称、参数摘要、结果摘要、状态和耗时；详情页保留可复制的完整参数和结果。插件工作区的 `write_file` / `apply_patch` 是例外，只显示本次标准统一 diff，不展示调用参数、结果摘要或完整结果入口。开始、完成和取消时间会随会话保存，重开后不会把已完成工具重新显示成进行中。
- 插件工作区进入“已暂存”和“已写入/已删除”状态时，各自追加并持久化为独立会话消息；提交失败也追加独立状态消息，均不改写模型已经输出的正文。真实提交成功后的代码快照记录在对应成功状态消息上。

工具调用前的“准备调用工具”、兼容 JSON 的控制字段和联网进度不会短暂渲染成 assistant 正文，因此不会出现 `Working -> 正文 -> Working` 的闪回。

### 工具协议

原生路径使用标准 Chat Completions 结构：

```json
{
  "role": "assistant",
  "tool_calls": [
    {"id": "call_x", "type": "function", "function": {"name": "...", "arguments": "{}"}}
  ]
}
```

工具结果使用：

```json
{"role": "tool", "tool_call_id": "call_x", "content": "..."}
```

`tool_call_id` 会保存在会话中。重新打开会话、编辑或重试前面的消息时，会清理不再属于当前分支的工具历史，避免把旧工具结果错接到新请求。

当前客户端会接收同一响应中的全部工具调用，不再只执行第一个。联网能力分为关键词搜索 `hchat_web_search` 和指定 URL 正文读取 `hchat_web_fetch`；连续的联网搜索、网页读取和外部文件读取这类只读工具最多并行 3 个。插件工作区、DexKit、MCP 和其它无法确认无副作用的工具保持串行并作为顺序屏障。每个工具都有排队、执行阶段、结果保存、完成、失败和取消状态；没有可靠总量时只显示阶段，不伪造百分比。

一轮 Agent 任务不设置固定的工具调用总次数上限，多文件修改、分页读取和多版本逆向可以持续执行到完成或用户主动停止。同一工具和参数允许再次执行，用于刷新状态、复核结果或重试非确定性操作；每次执行都生成独立工具事件。只有控制响应格式校正保留独立的有限重试。

插件工作区提供 `list_files`、`read_file`、`search_files`、`create_directory`、`write_file`、`apply_patch`、`move_path`、`delete_path`、`restore_path`、`reset_workspace`、`delete_plugin`、`show_diff` 和 `workspace_status`。`read_file` 使用稳定行号和行内续读，`search_files` 支持正则、路径 glob、排除路径和前后文。`apply_patch` 使用 Codex 风格的 `*** Begin Patch` 协议，一次调用可新增、更新、移动或删除多个文件；全部区块先完成上下文和最终大小校验，再写入暂存区。补丁优先逐字符匹配，失败时只接受唯一的行首/行尾空白差异候选，并保留原文件的上下文行；存在多个候选时仍拒绝修改，避免改错代码块。`restore_path` 可撤销单个路径，`reset_workspace` 可丢弃整轮暂存修改，`show_diff` 返回标准统一 diff。每轮只允许绑定一个插件目录，所有路径限定在该目录的缓存副本内。重复调用签名包含工作区 revision，因此写操作后可以重新读取或搜索同一路径；工作区写操作不能并行。当前 revision 已通过 `workspace_status` 和完整 `show_diff(path=".")` 后，客户端会把模型的最终 `workspace_done`、`answer`、`ready` 或 `delete` 响应统一收束为待应用变更；即使模型随后返回了无法解析的收尾文本，也直接使用本地工作区校验结果进入待确认，不重新请求模型或重放工具。尚未完成本地校验时，格式异常重试不会写入协议历史或反复追加校正上下文；重试耗尽后仍会保留暂存 checkpoint，并明确提示继续任务。被客户端拒绝并要求重试的控制响应会从聊天中撤掉，不能累积成重复回复。快捷选项的“插件文件修改确认”设为“每次询问”时，每次 `write_file` / `apply_patch` 工具调用都会显示本次 Diff 并暂停，整轮结束后还会显示完整目录 Diff 供最终确认；关闭弹窗只会隐藏它，聊天页保留“待确认的插件修改”入口，明确取消才丢弃暂存副本。工具级和最终确认弹窗都可在确认前勾选“始终允许”，确认后立即保存该模式，本轮后续写入和最终提交也直接放行。设为“始终允许”时，工具写入和最终创建、修改或删除会在静态检查通过后直接提交，不再弹确认框。

暂存工作区会随运行 checkpoint 保存路径、插件身份、基线、revision 以及 `workspace_status` / `show_diff` 的完成状态。API 或网络失败后保留最多 24 小时；“继续任务”沿用原 `turnId`、原用户消息和协议转录，校验后恢复同一个暂存区，不重复执行已经成功的工具或写入。若中断前当前 revision 已完成状态检查和完整 Diff，继续时直接恢复最终确认，不再依赖一次新的模型请求。恢复点过期、路径越界、内容不可读或状态无效时才丢弃并重新读取真实插件。新用户消息、“重新开始”、明确取消或成功提交会清理旧恢复点。同一会话仍可多次修改同一插件，每次确认提交后下一轮基于最新真实文件继续。模型连续返回无效结束状态时最多校正两次，仍未实际建立并校验本轮工作区就停止请求，不能无限闪烁重试。

超过单页大小的工具结果会写入当前会话的 `Agent/tool-results/<sessionId>`，模型收到 `truncated=true`、`handle` 和 `nextOffset`，通过 `hchat.reverse.read_tool_result` 继续读取。聊天详情默认显示预览，用户可以加载并复制完整结果；创建会话分支时会复制对应结果文件，删除会话或删除消息时清理不再引用的结果。

### 中断、重试和压缩

- 停止按钮会取消当前会话的 HTTP、联网、MCP 和文件读取请求；返回页面或切换会话不会触发停止。
- 已收到的正文、真实思考和已完成工具事件会保留，并将 assistant 消息标记为中断。
- 如果中断发生在工具执行期间，对应工具事件也会标记为已中断并保留已有结果片段。
- 模型请求尚未形成完整控制响应时没有执行新工具；发生短暂断流或 `408/425/429/5xx` 时，即使已经显示部分正文或思考，也会撤下这段不完整响应并使用同一请求自动退避重试最多 6 次，支持服务端数字秒格式的 `Retry-After`。重试不会追加协议记录或重放工具。
- “继续任务”从最后一个已保存的模型/工具边界继续，不新增“继续任务”用户消息，也不删除之前的中断记录；“重新开始”才删除原分支结果并从原用户消息完整重跑。
- 上下文压缩生成 Codex 风格的结构化交接摘要，固定保留当前目标、用户约束、已确认决策、插件与工作区状态、完成项、验证结果、关键 descriptor/路径/结果 handle、失败尝试、待办和最近上下文；不把思维链、`Working`、重复进度或客户端状态词写进摘要。
- 压缩成功后，模型请求会用交接摘要替换压缩边界之前的聊天消息和原生工具协议历史，只发送摘要与边界后的新消息；本地可见聊天、Diff 和完整工具结果文件保持不变。后续重建工具历史也只读取未压缩消息，不能把旧工具结果重新注入。
- 手动压缩在聊天尾部显示 `Working (耗时) · 正在压缩上下文`，自动压缩在模型请求前显示 `Working (耗时) · 正在自动压缩上下文`；压缩状态不写入消息正文，成功后显示估算的压缩前后 Token，失败则保留原边界和上下文。
- 压缩期间停止按钮会取消当前会话的压缩请求；可以切换到其它会话，但当前会话不能同时发起新的生成。
- 原生工具协议历史、工具参数和结果也计入 Token 估算，避免聊天正文较短但工具上下文很大时压缩阈值失真。
- 模型请求把内置开发指南放在固定 system 前缀，固定前缀不包含构建版本号、联网开关或工具协议分支。会话另存一份不显示在聊天 UI 中的协议转录：用户消息、模型原始响应、函数调用、工具结果和运行状态更新只能追加，普通网络重试复用完全相同的请求前缀。
- 会话切换、退出页面和进程恢复继续使用已落盘的协议转录；手动/自动压缩成功后以摘要开启新缓存周期。编辑重发、重新生成、删除或回滚历史消息以及创建对话分支会从保留的 UI 历史建立新周期，不会把被覆盖分支继续发给模型。旧会话缺少协议转录时首次请求从现有消息迁移，之后不再由 UI 消息重建。
- 长按消息菜单按稳定的消息 ID 绑定目标，不保存打开菜单时的列表下标。编辑重发、重新生成、回滚、删除和创建分支会在后台重建工具协议历史并异步保存会话；大工具结果只恢复首个分页，不在主线程完整读取或同步 `fsync`。回滚缩短消息列表前还会取消正在执行的旧尾部滚动，并把列表定位到仍然存在的消息，避免滚动状态继续访问已经删除的消息位置。
- 自动压缩的摘要、消息边界和新协议周期随同一个会话 checkpoint 保存；Token 估算会在已有转录之外补算本轮尚未追加的用户消息和附件。工具执行被停止或进程中断时，下次请求会先为缺失结果的函数调用补充“已中断”结果，不会自动重放可能带副作用的工具。

### Operit 对照结论

Operit 的主要优势不是某个动画，而是把回合、工具调用、工具结果、思考和摘要当作结构化事件，并在取消后继续保存事件。Hchat 已对齐回合 ID、工具父子关系、多工具队列、只读工具并行、阶段进度和结果续读；仍有差异的部分是工具级权限询问、子 Agent 编排和 Provider 专属 reasoning metadata。

## 功能开发模板

新增功能时复制这个结构，不要把业务逻辑写到 `hooks/api` 或 `loader`。

代码语言原则：能用 Kotlin 写就尽量写 Kotlin；如果某段代码因为 Xposed、反射、DexKit、R8 或 Java 互调原因更适合 Java，就用 Java，不要硬转。

### Directory

简单功能使用单层目录：

```text
hooks/items/example/
  ExampleFeature.kt
  ExampleSettingsProvider.kt
  ExampleSettings.kt
```

复杂功能按职责拆子包，入口类保持很薄：

```text
hooks/items/example/core/
  ExampleFeature.kt            # 只注册设置、订阅、组装组件
  ExampleSettings.kt           # 只读配置
  ExampleState.kt              # 只放内存状态
  ExampleCoordinator.kt        # 串联业务流程
hooks/items/example/detect/    # 识别、解析、过滤
hooks/items/example/action/    # 执行动作，例如发送消息、网络请求
hooks/items/example/notify/    # Toast、系统通知、模板变量
```

不要把功能私有逻辑放进 `hooks/api`。只有多个功能都能复用、并且已经确认兼容的微信能力才放 API 层。

设置 UI 不放在功能包里。新增功能的设置页面统一写到 `ui/miuix/MiuixSettingsPage.kt`，并从 `FeatureSettingsPage(...)` 按 `featureId()` 分发。

主设置页已经内置 KSU 风格 Miuix 液态玻璃底部导航：实用、娱乐、插件、设置。不要为单个功能再做独立底部导航；功能详情页需要底部操作时使用统一的 `BottomActionBar`。

当前页签归类：

- `实用`: 红包、转账等日常工具，当前放 `AutoRedPacketFeature` 和 `AutoTransferFeature`。
- `娱乐`: 消息/玩法类功能，当前暂无默认功能。
- `插件`: 插件相关功能，例如脚本插件。
- `设置`: 全局 UI 设置和关于信息。

支付相关功能不要在业务包里重复找微信混淆类。普通转账领取/退回使用 `WeChatApis.payment().transfers()`，消息识别和规则判断可以放在功能包内。

### Feature

```kotlin
package h.Hchat.hooks.items.example

import h.Hchat.event.Events
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext

class ExampleFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "示例功能"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(ExampleSettingsProvider())
        subscribe(Events.MessageReceived::class.java) {
            // lightweight event handling
        }
    }

    @Throws(Throwable::class)
    override fun onFeatureInstall(context: FeatureContext) {
        if (!featureBoolean("enabled", true)) return
        if (WeChatApis.message().observe() != null && WeChatApis.message().hasObserve()) {
            trackSubscription(WeChatApis.message().observe().subscribe {
                // API subscription, auto cleanup by BaseFeature
            })
        }
    }

    companion object {
        const val ID = "example"
    }
}
```

### Settings Provider

Provider 只提供入口元信息，不打开页面。

```kotlin
package h.Hchat.hooks.items.example

import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class ExampleSettingsProvider : SimpleFeatureSettingsProvider(
    ExampleFeature.ID,
    "示例功能",
    "一句话说明",
    FeatureSettingsProvider.CATEGORY_ENHANCE
)
```

不要实现或恢复 `showDetail(Context)`。不要在 Provider 中创建 `Dialog`、`AlertDialog`、`Activity` 或自定义悬浮 View。Provider 只保留 `featureId/title/subtitle/category`，不要重新加图标、颜色或旧 `UIEntry`。

分类规则：

- `FeatureSettingsProvider.CATEGORY_PRACTICAL`: 放到 `实用`
- `FeatureSettingsProvider.CATEGORY_ENTERTAINMENT`: 放到 `娱乐`
- `FeatureSettingsProvider.CATEGORY_ENHANCE`: 放到 `插件`

三参数 `SimpleFeatureSettingsProvider(featureId, title, subtitle)` 默认放到 `插件`。不要在 `MiuixSettingsPage` 主页面里按功能 ID 写分组判断。

### Miuix Settings Page

在 `app/src/main/java/h/Hchat/ui/miuix/MiuixSettingsPage.kt` 的 `FeatureSettingsPage(...)` 添加分支：

```kotlin
@Composable
private fun FeatureSettingsPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    when (provider.featureId()) {
        ExampleFeature.ID -> ExampleMiuixPage(context, provider, onBack)
        else -> UnsupportedFeaturePage(provider, onBack)
    }
}
```

页面模板：

```kotlin
@Composable
private fun ExampleMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, "example_settings") }
    var optionPicker by remember { mutableStateOf<OptionPickerRequest?>(null) }
    var picker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    optionPicker?.let { request ->
        OptionPickerPage(
            request = request,
            onBack = { optionPicker = null },
            onSelected = { selected ->
                request.onSelected(selected)
                optionPicker = null
            }
        )
        return
    }

    picker?.let { request ->
        ContactPickerPage(
            context = context,
            request = request,
            onBack = { picker = null },
            onConfirm = { selected ->
                request.onValue(selected.joinToString("|") { it.id })
                picker = null
            }
        )
        return
    }

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = {
                    // write full form values here
                    Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                },
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "功能") }
            item {
                SettingsCard {
                    SwitchRow(sp, "enabled", "启用", "开启示例功能", true)
                    InsetDivider()
                    OptionRow(
                        sp = sp,
                        key = "mode",
                        title = "模式",
                        options = optionItems("模式一" to 0, "模式二" to 1),
                        defaultValue = 0,
                        openPicker = { optionPicker = it }
                    )
                    InsetDivider()
                    ActionRow("选择联系人", "从好友或群聊列表选择") {
                        picker = ContactPickerRequest(
                            title = "选择联系人",
                            mode = ContactPickerMode.BOTH,
                            multiSelect = true,
                            existingValue = "",
                            onValue = { value ->
                                sp.edit().putString("contacts", value).apply()
                            }
                        )
                    }
                }
            }
        }
    }
}
```

UI 规则：

- 使用 `PageScaffold` + Compose `LazyColumn` + Miuix `SettingsCard` / rows。`PageScaffold` 负责 Miuix top app bar、backdrop 和 bottom bar。
- 需要二级选择的配置用 `OptionRow`，不要点击一行就循环切换值。
- 好友/群聊选择用 `ContactPickerPage`，它已经处理头像、头像进程内缓存、右侧选中框和完整列表点击。
- 会跳转或选择的行使用现有 `ActionRow` / `OptionRow` / `ContactRow`，不要直接写普通 `Modifier.clickable`。
- 所有页面都要给 `LazyColumn` 传 `rememberLazyListState()`，返回二级菜单时才能保持滚动位置。
- `LazyColumn` 要加 `.nestedScroll(scrollBehavior.nestedScrollConnection)`，保证 Miuix 顶栏滚动行为一致。
- 主设置页的 设置 tab 已有 `悬浮底栏` 和 `液态玻璃` 开关，值存在 `Hchat_miuix_ui`。不要在功能页重复做这些全局 UI 设置。
- 新增功能配置统一使用 `HchatStorage.preferences(context, name)` 或 `ConfigStore`，文件位于微信私有目录 `Hchat/`，日常读写由 FastKV 负责。不要直接 `context.getSharedPreferences(...)` 或自行实例化 FastKV 写模块配置。
- 主底栏实现位于 `ui/miuix/FloatingBottomBar.kt` 及其 `animation/`、`liquid/` 辅助文件。它使用 KSU 风格的多层 backdrop：普通内容层、隐藏主色 tint 层、移动选中液态胶囊。新增功能页不要改这套底栏状态或颜色机制。
- 底栏图标是本地 `ImageVector`，路径来自 AndroidX Material Rounded。不要在微信内嵌 Compose UI 中使用 `vectorResource(R.drawable...)`。
- 主功能列表不显示 Provider 图标，保持普通设置列表样式。图标只保留在底部导航。
- 液态玻璃使用 compose-miuix-ui 的 `miuix-blur`。不要在功能页单独创建 blur/backdrop；统一复用 `PageScaffold` 的结构。
- 液态玻璃只支持 Android 13 及以上；低版本必须回退普通底栏，不能构造 `RuntimeShader` 相关效果。
- `layerBackdrop(...)` 只能包住页面内容层，底栏必须作为 overlay 放在内容层外。底栏玻璃由 `FloatingBottomBar` 内部的 `drawBackdrop(...)` / `lens(...)` 处理。不要把底栏放进被记录的 backdrop 层里。
- 不要新增 Dialog，不要启动模块 Activity，不要加右上角关闭按钮。

### Register

Add one line in `FeatureRegistry`:

```kotlin
.register(ExampleFeature())
```

### Common Patterns

订阅消息：

```kotlin
trackSubscription(WeChatApis.message().observe().subscribe { message ->
    if (message.isText()) {
        val talker = message.talker
        val sender = message.sender
        val content = message.content
    }
})
```

延迟、限频、防重复：

```kotlin
WeChatApis.runtime().tasks().runOnMainDelayed("example:$id", 1000) {
    // delayed work
}

WeChatApis.runtime().tasks().runOnce("example_once:$id") {
    // only once per key
}

WeChatApis.runtime().tasks().runThrottled("example_rate:$talker", 3000) {
    // rate limited per talker
}
```

安装 Xposed Hook：

```kotlin
HookRegistry.get().hook(method, object : XC_MethodHook() {
    override fun afterHookedMethod(param: MethodHookParam) {
        // hook body
    }
})
```

需要微信内部类：

```text
1. 先用 DexKit/DexClub 在目标版本确认类、方法、构造器。
2. 把解析结果放进 DexFinder，并加版本/热更新缓存。
3. 业务功能只消费 DexFinder 或 WeChatApis，不散落 findClass。
4. 反射查找、类加载、实例创建和方法调用必须走 h.Hchat.utils.KavaReflector。
5. 未确认的能力只写 supports/isAvailable，不写猜测实现。
```

KavaRef 约定：

```kotlin
val ctor = KavaReflector.findConstructor(clazz, String::class.java)
val instance = KavaReflector.newInstance(ctor, value)
val field = KavaReflector.readField(instance, "fieldName")
```

不要在新功能里直接散落 `getDeclared*`、`setAccessible`、`Constructor.newInstance`、`Class.forName` 或 `Method.invoke`。如果必须保留原始 `Method` / `Field` / `Constructor` 给 Xposed 或 DexKit，也必须由 `KavaReflector` 获取。

`h/Hchat/compat/kavaref/**` 是 KavaRef core 的本地兼容层。目录在 Hchat 下，文件内部包名仍是 `com.highcapable.kavaref...`，不要改包名。

运行时设置实时生效：

```kotlin
// Good: read current value when handling the event.
if (!settings.getBoolean(ExampleSettings.KEY_ENABLE, true)) return

// Good: if cached, refresh before event processing.
settingsSnapshot.refresh()
if (settingsSnapshot.skipCurrentEvent()) return

// Good: settings page writes immediately when later runtime code depends on it.
sp.edit().putBoolean(ExampleSettings.KEY_ENABLE, enabled).commit()
```

不要这样写：

```kotlin
// Bad: this value becomes stale after user saves settings.
private var enabled = false

fun hookAll() {
    enabled = settings.getBoolean(ExampleSettings.KEY_ENABLE, true)
}

fun onEvent() {
    if (!enabled) return
}
```

### Rules

- Use `BaseFeature` for lifecycle.
- Use `registerSettingsProvider`, `subscribe`, and `trackSubscription` for cleanup.
- Use `WeChatApis` before adding new DexKit logic.
- Put new DexKit results into `DexFinder` only after verifying target WeChat versions by reverse engineering.
- 新增反射代码使用 `KavaReflector`，不要直接散落原始 Java 反射。
- Runtime logs should be quiet by default. Log failures, missing classes, or explicit debug output only.
- Keep feature entry classes small. Put parsing, action, notification, and state into separate classes.
- Do not use WA/WAuxiliary API.
- Do not use Dialog/AlertDialog/module Activity for settings. All settings pages must be embedded Miuix pages under `MiuixSettingsPage.kt`.
- Settings providers are metadata only. Do not add `showDetail(Context)`.
- Use shared Miuix rows/pickers so tap handling, scroll-position retention, contact avatars, and right-side selection marks stay consistent.
- All switches and editable settings must take effect immediately after saving. Do not require restarting WeChat for normal settings.
- If settings are cached, refresh the cache before processing events or when the settings page saves.
- If a WeChat internal detail is uncertain, reverse engineer first. Do not guess class names, method names, fields, database tables, intent extras, or request parameters.
- Put every new file in the correct layer. Feature-specific code belongs in `hooks/items/<feature>/**`; only reusable verified WeChat APIs belong in `hooks/api/**`.

## 悬浮菜单方向与消息标签扩展（2026-09-11）

- 悬浮快捷菜单在原有向上、向下之外支持向左、向右展开，配置值为 `left` / `right`。左右方向使用横向滚动容器，保留三种显示模式；展开/收起动画沿横轴，展开时拖动仍保持菜单跟随。靠近屏幕边缘时入口会向内避让，并保存调整后位置。
- 冷启动时，在协议已通过的 Application 初始化阶段提前注册原生 Activity 生命周期回调，免于等待完整 DexKit 初始化；恢复页面时立即挂载并以 0/80/250 ms 有限补偿，暂停、关闭和销毁时取消过期补偿。
- 消息详情的 `${type}` 使用 `MessageTypeLabels` 细分卡片类型，涵盖文件、音乐、聊天记录、小程序、引用回复、红包、转账、视频号、接龙等。只解析外层 `appmsg` 的直接子节点 `type`，不把引用/聊天记录内的类型作为外层类型；未知类型保留编号，损坏 XML 回退为通用标签。
- 新增可插入变量：`${appType}` 卡片子类型编号（非卡片或未识别时为空）、`${baseType}` 归一化基础类型、`${direction}` 收到/发出、`${talker}` 会话 ID、`${createTime}` 原始毫秒时间戳（无有效值时为空）、`${rawAtUserList}` 原始艾特名单。既有变量和用户模板保持兼容，正文按所需变量读取。
- 依据：`com.qechat.wechat` 1.0.0 debug APK（SHA-256 `7c22adb3d1aa444aa7088676ada05b63d476b64719a49556e15abc646b84afce`）中 `HchatExtraHooker.detailedMessageTypeLabel` 的 DexClub Java 导出与对应本地源码。分类扩展只消费已有消息模型，不新增微信 Hook、表字段或 DexKit 定位；未完成多版本真机验证。
- 本轮分类检查覆盖文件、红包、转账、小程序、引用回复、嵌套类型、群聊正文前缀、未知编号、视频号、拍一拍、损坏 XML 和拒绝 DTD，共 17 个样例。界面触摸与边缘布局仍需设备验证。
- 本地测试包通过临时 Gradle init script 使用 Release 编译模式、关闭 R8 和资源压缩、改用本机调试证书签名。该脚本不改仓库发布配置；测试包名 `Hchat-alt-entry-test-signed.apk`，不等同于官方签名发布包。此方式避开现有单文件设置页在 Debug Compose 编译中出现的 `ClassTooLargeException`。

## 启动加载优化（2026-09-11）

版本指纹仍在每次定位前实时计算，但 WeChatVersionApi 按原优先级逐项判空读取 BuildConfig、加载器信息和 Tinker 文件，仅信息不足时访问后备来源，避免 Java 可变参数提前求值造成多次目录扫描。没有新增 TTL 或永久运行时键缓存。

DexFinder 将 Protobuf 基类描述与其它类一起保存到现有版本/热更新/加载器指纹限定的缓存；旧缓存没有该项时补定位一次，之后复用。公共 API 缓存命中后的其它补定位保持执行。

NativeLibraryLoader 只记录同一模块 ClassLoader 下已经成功加载的库；后续调用跳过 APK 路径查找、ZIP 打开、跨进程文件锁和目录清理，失败及异常不缓存，仍可重试。ARM 32/64 位选择与原提取校验流程保持一致。

平板模式的缓存 Hook 不使用 DexKit，先在共享 DexKit 串行门外尝试安装，命中即返回；缓存缺失时仍进入原串行门并复查，再按原阶段定位，保留早期安装时序。详见 STARTUP_REVIEW_2026-09-11.md。

### 悬浮快捷菜单响应优化（2026-09-11）

悬浮快捷菜单在初始化及快捷项变更时更新启用项快照，展开时不再反复解析配置；自定义图标在独立后台队列按需加载及预取，以 4 MB 内存缓存复用，解码结果最大边长 256 像素，待办不超过 64 项。读取期间先显示默认图标，深色图标选择与回退规则保持不变。同路径替换、删除、关闭功能会使图标缓存及待执行结果失效。菜单直接在当前窗口尺寸内测量定位，取消滚动容器对子项按压反馈的等待；移除菜单后在下一帧遍历完成后打开页面，切后台或卸载会取消待执行跳转。生产图标加载器回归可通过 scripts/run_floating_icon_tests.cjs 执行；未进行真机帧时间测量。
