# 微信多版本适配与验证记录

## 当前结论

项目已有公共 API、DexKit 定位、运行时指纹缓存和统一安装调度器，可以继续在此基础上做多版本适配。支持范围必须按公共能力和功能分别确认；不能把版本号出现在文档中、某个方法定位成功或 JVM 回归通过解释为整个插件适配成功。

当前仓库为 Hchat-alt-entry，默认分支为 alt-entry。本轮只处理模块自身的缓存完整性与元数据解析，没有改动微信 Hook 入口、混淆签名或业务参数，也没有新增已验证的微信版本。

## 已有分层与优先事项

| 层级 | 当前实现 | 后续适配要求 |
| --- | --- | --- |
| 版本身份 | WeChatVersionApi / WeChatVersionInfo | 每次定位读取当前 versionName、versionCode、clientVersion、Tinker、APK 时间戳和 ClassLoader 指纹；版本名相同不代表同一构建 |
| 定位缓存 | DexMethodCache | 整组描述符完整可解析才算命中；版本变化时重建，子进程不能删除主进程有效缓存 |
| 安装调度 | DexInstallScheduler | 复用阶段、串行门和有限重试；只有必要入口全部安装成功才返回 true |
| 公共能力 | WeChatApis、聊天 UI 与菜单定位器等 | 功能复用公共能力；结构相同的版本共享实现，结构不同的路径单独适配 |
| 功能行为 | hooks/items 下的各项功能 | 分别记录依赖能力、降级行为、已验证版本，不以模块整体加载成功代替功能验证 |

下一轮优先验证聊天行绑定、单消息菜单和输入区。它们是 UI 功能的公共依赖，应该先收集各版本 APK 证据，再决定是否需要多个结构适配器。现有会话时间样式仍含字符串定位后选择第一个候选的逻辑，这一入口的唯一性和各版本覆盖尚待核验；本轮未更换它，也没有宣称它已全版本适配。

## 本轮基础修复

### 多入口列表缓存

旧实现对每个描述符单独解析并丢弃失败项。例如缓存包含 A、B 两个入口，B 在当前 ClassLoader 下不可解析时，仍返回 A。调用方常以非空列表判断命中，因此可能长期漏装 B。

现在列表中任意非空描述符解析失败，整组返回空列表。同进程读取只移除对应列表记录，让调用方重新定位；其它记录保留。跨进程读取只返回未命中，保留主进程持有的记录。空行保持兼容，不作为方法描述符。

这只保证持久化列表的解析完整性，不保证 DexKit 初次发现的候选集合完整，也不替代调用方的签名校验、业务范围确认和全部必要入口安装检查。

### 版本元数据

Tinker 文本元数据按等号或冒号前的完整键名匹配，跳过空行、以 # 或 ! 开头的注释和空值。类似 backup.patch.client.ver、NEW_TINKER_ID.old 以及其它值中包含的键名不能充当当前版本信息。来源优先级维持现有顺序，每次读取仍可观察到元数据变化。

## 证据与版本矩阵

“历史记录”只表示仓库已有相应文字证据，本轮未重新核验 APK。不同功能必须建立各自的矩阵；以下只登记已经有专门证据文档的消息分类，不能外推至聊天菜单、时间样式或整个插件。

| 微信版本 | 消息分类静态证据 | 本轮 Hook 安装验证 | 本轮无 R8 真机验证 | 本轮 R8 真机验证 |
| --- | --- | --- | --- | --- |
| 8.0.49 | 尚未建立该版本专门记录 | 未验证 | 未验证 | 未验证 |
| 8.0.58 | 尚未建立该版本专门记录 | 未验证 | 未验证 | 未验证 |
| 8.0.66 | 尚未建立该版本专门记录 | 未验证 | 未验证 | 未验证 |
| 8.0.68 | 尚未建立该版本专门记录 | 未验证 | 未验证 | 未验证 |
| 8.0.72 | 尚未建立该版本专门记录 | 未验证 | 未验证 | 未验证 |
| 8.0.74 | 尚未建立该版本专门记录 | 未验证 | 未验证 | 未验证 |
| 8.0.76 (3141) | 历史记录：MESSAGE_TYPE_CLASSIFICATION_EVIDENCE.md | 未验证 | 未验证 | 未验证 |
| 8.0.77 | 尚未建立该版本专门记录 | 未验证 | 未验证 | 未验证 |

已有依据：[消息分类](MESSAGE_TYPE_CLASSIFICATION_EVIDENCE.md)、[合并转发分类](FORWARDED_RECORD_TYPE_EVIDENCE.md)、[消息卡片标签](MESSAGE_CARD_LABEL_FIX_EVIDENCE.md)、[功能框架中的各功能说明](FEATURE_FRAMEWORK.md)。后续取得 APK 后，为每个公共能力记录 APK SHA-256、versionCode、ABI、定位查询、完整候选集合、经实现确认的描述符和调用时序；实际安装验证还须记录模块提交、模块 APK 指纹与 R8 状态。

## APK 静态入口证据（2026-09-12）

DexClub 已读取 `/data/data/com.termux/files/home/wechat-apks/` 中的 8 个目标 APK，并核对 Manifest 的 `versionName/versionCode`。本轮重点查询聊天行绑定、单消息菜单创建和 `ChatFooter.onAttachedToWindow()`；结果证明这些入口在目标版本都能被独立特征命中，但不等同于 Hook 已在设备运行成功。

| 版本 | versionCode | 聊天行绑定候选 | 单消息菜单创建候选 | 输入区挂载 |
| --- | ---: | --- | --- | --- |
| 8.0.49 | 2600 | `ic4.f.h(...IIZLjava/util/List;)V` | `com.tencent.mm.ui.chatting.viewitems.k0.a(...View;ContextMenuInfo)V` | `ChatFooter.onAttachedToWindow()V` |
| 8.0.58 | 2841 | `es4.f.h(...IIZLjava/util/List;)V` | `com.tencent.mm.ui.chatting.viewitems.l0.a(...View;ContextMenuInfo)V` | 同上 |
| 8.0.66 | 2980 | `d25.g.h(...IIZLjava/util/List;)V` | `com.tencent.mm.ui.chatting.viewitems.l0.a(...View;ContextMenuInfo)V` | 同上 |
| 8.0.68 | 3020 | `m55.g.h(...IIZLjava/util/List;)V` | `com.tencent.mm.ui.chatting.viewitems.m0.a(...View;ContextMenuInfo)V` | 同上 |
| 8.0.72 | 3100 | `nb5.g.h(...IIZLjava/util/List;)V` | `com.tencent.mm.ui.chatting.viewitems.m0.a(...View;ContextMenuInfo)V` | 同上 |
| 8.0.74 | 3120 | `od5.g.h(...IIZLjava/util/List;)V` | `com.tencent.mm.ui.chatting.viewitems.m0.a(...View;ContextMenuInfo)V` | 同上 |
| 8.0.76 | 3140 | `ve5.g.h(...IIZLjava/util/List;)V` | `com.tencent.mm.ui.chatting.viewitems.m0.a(...View;ContextMenuInfo)V` | 同上 |
| 8.0.77 | 3160 | `zh5.g.h(...IIZLjava/util/List;)V` | `com.tencent.mm.ui.chatting.viewitems.n0.a(...View;ContextMenuInfo)V` | 同上 |

聊天行候选在 8 个版本都保持 5 参数形状，且 8.0.49 与 8.0.77 的实现检查均能读到 RecyclerView holder 的根 View；8.0.49 的 holder 还确认存在 `timeTV` 字段。单消息菜单查询在新版本会同时返回 Kotlin/函数对象委托候选，生产定位器必须继续用具体参数、非抽象、宿主 viewitems 包过滤，不能按第一个结果安装。输入区挂载方法的完整描述符在 8 个版本保持不变。

本轮结论：现有三条定位路线可以继续作为公共适配器的基础，版本差异主要集中在混淆 owner 和消息 holder 类型。还需要设备 Hook 安装、完整菜单点击入口和 R8 包验证；目前不把它们标成“运行时已适配”。

`8072.apk` 与带完整文件名的 8.0.72 APK 都解析为 8.0.72/versionCode 3100；8.0.65/versionCode 2960 也已存在，但不在默认八版本矩阵中。

## 回归与验收

本轮缓存完整性 64 项、版本元数据 28 项 JVM 断言通过。版本元数据新增用例在旧实现上已复现注释值误读。

模块自身的 JVM 回归命令：

~~~sh
node scripts/run_dex_cache_integrity_tests.cjs
node scripts/tests/startup_version/run.js
~~~

缓存测试使用真实 DexKit 2.0.1 描述符解析和方法解析；Android 接口及 SharedPreferences 使用同步替身。版本元数据测试同样使用 Android 替身。这些测试不能证明宿主实际布局、Hook、热更新文件格式全集、跨进程存储一致性或 R8 产物正常。本轮没有执行 Gradle 或生成 APK。

每个待适配功能至少覆盖：冷启动首次定位、缓存命中后重启、升级、降级、热更新、部分缓存损坏、功能开关切换、实际交互和正式 R8 包。多入口能力还需覆盖所有已确认可达的并行入口。无法确认候选或运行时结构时，只让对应能力返回不可用并记录原因，不猜测参数继续执行。
