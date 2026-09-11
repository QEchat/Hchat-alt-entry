# Hchat WeChat API

本文档记录模块内公共微信 API 的分组、用途和调用方式。新增功能优先调用 `WeChatApis`，不要直接在业务功能里重复写 DexKit 定位、数据库查询或 Xposed hook。

## 入口

```java
WeChatApis.message()
WeChatApis.contact()
WeChatApis.runtime()
WeChatApis.interaction()
WeChatApis.payment()
```

旧的平铺入口仍保留，例如 `WeChatApis.database()`、`WeChatApis.contacts()`，但新代码优先使用分组入口。

## Settings UI Boundary

模块设置入口通过 `hooks/api/ui/SettingsInjector` 注入到微信设置页。`main` 和 `alt-entry` 当前使用同一套入口：微信设置页入口，以及按入口设置开关启用的 LauncherUI 右上角加号菜单和长按右上角加号入口。点击任一入口后只调用 `ui/SettingsUI.show(context)`，再由 `ui/miuix/MiuixSettingsPage.kt` 把 Miuix Compose 页面嵌入当前微信 `Activity`。

微信设置页入口必须在 `Hchat-Init` 功能安装阶段尽早安装，不等待普通 `DexInstallScheduler` 队列或 `DexReady` 补装，避免微信设置页 UI 已构建后入口才补 Hook 导致偶发不显示。入口定位和安装仍通过 `DexInstallScheduler.runDexKitTask(...)` 与其它共享 `DexKitBridge` 查询串行执行，不能再启动独立线程并发进入 DexKit。

新版微信设置入口使用 `Context.getString(fakeResId)` 给动态 SettingItem 提供标题时，`fakeResId` 必须使用 Hchat 独有负值，不要复用 WeKit/WA 等同源模块常见的 `-1337`。多个模块同时启用时，伪资源 ID 撞车会导致标题和点击代理串台，例如显示 Hchat 但实际打开其它模块页面。

新版微信设置入口通过 `SettingLocation(parentGroup, frontItem)` 排序时，不能让两个入口共用同一个 `frontItem`。如果 `SettingGroupPersonalInfo` 已经被 WeKit 或其它模块挂到自定义入口之后，Hchat 要接到这个当前前置类之后，再把 `SettingGroupPersonalInfo` 接到 Hchat 之后，避免设置项链路分叉导致入口或微信原生设置项消失。

右上角加号菜单必须动态定位 `MicroMsg.PlusSubMenuHelper`，通过 `SparseArray` 菜单数据和 `onItemClick(AdapterView, View, int, long)` 统一追加、识别和分发 Hchat 设置入口、全部已读及快捷终止。不要写死 `of/rg`、`lf/og`、`mf/pg` 这类混淆类名，也不要为单个菜单动作重复安装另一套 Hook。
长按右上角加号入口只绑定 `HomeUI$PlusActionView.h()` 返回的真实按钮 `View`，不再安装 `View.setOnClickListener`、`dispatchTouchEvent` 或 `performLongClick` 兜底 Hook。

已横向确认常用版本的 PlusSubMenuHelper 结构稳定：8.0.49=`com.tencent.mm.ui.of`、8.0.58=`com.tencent.mm.ui.ig`、8.0.66=`com.tencent.mm.ui.gg`、8.0.68=`com.tencent.mm.ui.hg`、8.0.72=`com.tencent.mm.ui.sg`、8.0.74/8.0.76=`com.tencent.mm.ui.rg`。这些类都持有 `SparseArray` 菜单数据、`Context`、返回 `BaseAdapter` 的无参方法，以及 `onItemClick(AdapterView, View, int, long)`。七个版本的 adapter 都按 `SparseArray.get(position)` 读取 wrapper，因此菜单 key 必须按最终显示顺序重建为连续的 `0..size-1`；不能使用负 key，也不能在插件移除或重载后保留位置空洞。

加号菜单项不要把模块 `R.drawable` 直接传给微信菜单 item。当前实现追加菜单项时 icon resource id 固定为 `0`，再 hook 菜单 adapter 的 `getView(...)`，按模块菜单 ID 替换行内 `ImageView`：设置入口和全部已读使用透明底白色线性 `H` 自绘图标，快捷终止使用白色 `android.R.drawable.ic_lock_power_off` 电源图标。

8.0.58、8.0.66、8.0.68 的 `PlusSubMenuHelper.d()` 会先填充 `SparseArray` 菜单数据再调用父类显示逻辑；8.0.74 的 `SubMenuHelperBase` 还会在父类构造阶段提前调用 `b()` 创建并缓存 adapter。入口不能只依赖 adapter 方法 hook，必须同时在子类填充方法执行后、父类显示方法执行前都尝试追加一次。

规则：

- 不要从功能代码启动模块 app 的 Activity。
- 不要用 `Dialog` / `AlertDialog` / feature-local floating view 做功能设置页；当前唯一例外是脚本插件 README 说明窗口，它只是轻量文档弹窗，不承载配置流程。
- 不要在 `FeatureSettingsProvider` 里恢复 `showDetail(Context)`；Provider 只提供 `featureId/title/subtitle/category` 元信息。
- 新增功能设置页要接入 `MiuixSettingsPage.FeatureSettingsPage(...)` 的 `featureId()` 分支。
- 页面内二级选择统一用 `OptionPickerPage` / `ContactPickerPage`，不要在功能页里手写新选择弹窗；群成员这类共享联系人选择器无法覆盖的场景才使用专用选择页。
- 设置 UI 里需要联系人数据时优先复用 `ContactPickerPage`，不要每个功能重复查好友、群聊、公众号和头像。
- 主设置页底部导航统一由 `MiuixSettingsPage.kt` 管理，四个 tab 是 实用、娱乐、插件、设置，右侧最后一个 tab 固定为 设置。
- 当前主设置页分为 `实用`、`娱乐`、`插件`、`设置`；实用和娱乐页签首页展示可点击的大分组入口，进入分组二级菜单后再展示功能项。实用页分组顺序固定为 `聊天`、`红包转账`、`增强`、`群组`、`朋友圈`、`美化`、`界面`、`杂项`。
- 全局 UI 开关 `悬浮底栏` / `液态玻璃` 存在 `Hchat_miuix_ui`，功能代码不要重复定义。
- `液态玻璃` 依赖 Android 13 的 `RuntimeShader`。Android 13 以下自动回退普通底栏，并在设置页禁用该开关。
- 液态玻璃使用 compose-miuix-ui `miuix-blur`，只在统一底栏里接入。`layerBackdrop(...)` 只能记录页面内容层，底栏作为 overlay 调用 KSU 风格 `drawBackdrop(...)` / `lens(...)` / `vibrancy(...)`，避免把底栏录进自己的 backdrop。
- 底栏慢滑时的主色跟随依赖隐藏的 primary-tinted content layer。不要在 visible icon/text 层按 selected item 直接切主色，否则会变成滑完才变色。

## 源码分组

公共 API 已按职责拆包：

```text
hooks/api/core          统一入口与初始化：WeChatApis、WechatApiFeature
hooks/api/message       消息发送、读取、解析、事件、观察
hooks/api/contact       账号、联系人、群聊、群成员、联系人变化
hooks/api/conversation  会话读取、会话变化
hooks/api/runtime       配置、数据库、数据库监听、任务、自检、能力检测
hooks/api/ui            UI 跳转、通知、Activity、聊天页
hooks/api/net           网络发包器与网络请求封装
hooks/api/media         媒体能力
hooks/api/payment       支付能力：普通转账领取/退回
hooks/api/model         公共数据模型
```

新增 API 时按这个目录放置；业务功能仍放在 `hooks/items/...`。

## API Status

当前公共 API 状态：

| 分组 | API | 用途 | 状态 |
| --- | --- | --- | --- |
| message | `sender()` / `text()` | 发送文本消息、群聊 @ 文本、XML/AppMsg 原始内容 | 已接入 DexKit + 网络发包 |
| message | `store()` | 读取 message 表、按时间范围查询、原生清空会话消息 | 已接入数据库查询与 MsgInfoStorageLogic 清理链路 |
| message | `parser()` | 解析 AddMsg/XML/nativeurl | 已可复用 |
| message | `events()` | AddMsg 实时事件 | 已安装 hook |
| message | `changes()` | message 表变更事件 | 已接入数据库变更 |
| message | `observe()` | 统一消息观察 | 已封装 AddMsg + 数据库新消息/出站补偿 |
| contact | `account()` | 当前账号资料、自身 wxid | 已接入 userinfo/login_info |
| contact | `contacts()` | 好友、群聊、公众号、搜索、显示名、群成员昵称 | 已接入 rcontact/chatroom/DexKit 群昵称 |
| contact | `chatrooms()` | 群资料、成员列表、成员数 | 已接入 chatroom 表 |
| contact | `users()` | 自己/好友/群/公众号判断 | 已可用 |
| conversation | `conversations()` | 会话读取、搜索、未读会话、免打扰、删除本地首页会话项 | 已接入 rconversation、原生联系人/RoomSDK 和会话存储 |
| runtime | `config()` | 宿主包名、进程、基础版本 | 已可用 |
| runtime | `version()` | 微信版本、clientVersion、Tinker/缓存指纹 | 已可用 |
| runtime | `database()` | 数据库查询 | 已可用 |
| runtime | `databaseChanges()` | insert/update/delete 监听 | 已安装 hook |
| runtime | `storage()` | 表/字段/单值查询辅助 | 已可用 |
| runtime | `network()` | 发送微信 NetScene 请求 | 已接入网络发包器 |
| runtime | `tasks()` | 延迟、限频、防重复任务 | 已可用 |
| runtime | `diagnostics()` | 兼容自检 | 默认低噪音 |
| payment | `transfers()` | 普通转账领取、退回 | 已按 8.0.49/58/66/68/72/74/76 transferoperation 接入 |
| interaction | `notifier()` | Toast、系统通知、点击进聊天 | 已可用 |
| interaction | `currentActivity()` | 当前 Activity | 已安装 hook |
| interaction | `activityStart()` | Activity 跳转事件 | 已安装 hook |
| interaction | `lifecycle()` | Activity 生命周期事件 | 已安装 hook |
| interaction | `chatPage()` | 聊天页进入/当前会话 | 已接入 DexKit，仍需各版本实测 |
| interaction | `media().images()` | 图片发送 | 已按 8.0.49/58/66/68/72/74/76 接入，静默，支持原图模式和图片 `appinfo.appid` |
| interaction | `media().voices()` | 语音发送 | 已按 voiceinfo/uploadvoice 链路接入，静默，支持 60 秒以上及原生群发语音入库 |
| interaction | `media().videos()` | 视频发送 | 已按 8.0.49/58/68/72/74 CopyVideoTask 锚点接入，静默 |
| interaction | `media().emojis()` | 表情发送 | 已按 EmojiFeatureService/NetSceneUploadEmoji 链路接入，支持本地路径静默发送，并可生成微信原生群发所需的表情元数据 |
| interaction | `media().files()` | 文件发送 | 已按 8.0.49/58/68/72/74 AppMsgLogic/uploadappattach 链路接入，静默 |
| interaction | `media().favorites()` | 收藏列表/发送 | 已按 8.0.49/58/66/68/72/74/76 `FavItemInfo` 与 `FavSendLogic` 链路接入；收藏语音优先复用语音发送 |
| interaction | `sns()` | 朋友圈缓存读取、媒体准备、发布、互动、观察和刷新 | 已按 8.0.49/58/66/68/72/74/76 的 `SnsCore` / `SnsInfoStorage` 原生入口接入 |

失败返回规则：

- 查询类 API 找不到数据时返回空字符串、空列表或 `null`，不抛业务异常。
- 发送/执行类 API 返回 `boolean`，失败时返回 `false` 并只打印错误级日志。
- 观察/监听类 API 不可用时 `has...()` 返回 `false`，业务功能必须有 fallback 或跳过。
- 媒体发送 API 里图片、语音、视频、表情、文件和收藏当前可用；失败时明确返回 `false`。

## Payment

`WeChatApis.payment().transfers()`

普通微信转账操作 API。领取使用 `op=confirm`，退回使用 `op=refuse`，底层构造微信 `transferoperation` NetScene 并交给统一网络发包器。

```kotlin
val params = TransferOperationParams(
    transactionId = transactionId,
    transId = transId,
    totalFee = totalFee,
    username = payerWxid,
    invalidTime = invalidTime
)

val received = WeChatApis.payment().transfers().receive(params)
val refunded = WeChatApis.payment().transfers().refund(params)
```

参数来源：

- `transactionId`: 转账 XML 里的 `transcationid` / `transactionid` / `transaction_id`。
- `transId`: 转账 XML 里的 `transferid` / `transfer_id` / `trans_id`，发送请求时对应微信字段 `trans_id`。
- `totalFee`: 金额，单位分；微信 AppMessage parser 确认字段是 `total_fee`，兼容旧解析里的 `feederval`。
- `username`: 付款人 wxid；微信 AppMessage parser 确认字段是 `payer_username`，收款人字段是 `receiver_username`。
- `invalidTime`: XML 常见字段是 `invalidtime`。
- `groupUsername`、`recvAccountType`、`bindSerial`、`subRecvChannelId`、`transferAttach` 只在对应微信版本或场景需要时传入。
- 领取/退回请求按完整构造签名选择参数布局，不能只按参数数量或前六个公共参数猜测。8.0.58 的 12 参数构造在 `invalid_time` 后仍依次要求 `left_button_continue`、`group_username`、`recv_account_type`、`sub_title_clicked`、`sub_recv_channel_id` 和显示文案；旧实现从 `group_username` 开始整体错位，会导致请求对象无法实例化。群聊转账必须把当前群 wxid 传入 `group_username`。
- 微信原生“收款到哪里”不是固定枚举：详情响应按账号动态下发 `recv_channel_type`、`recv_channel_name`、`recv_channel_avail_state` 和 `sub_recv_channel_info`；账户解析递归遍历完整响应，不依赖固定外层 JSON 路径。`WeChatTransferApi.query(...)` 主动发送原生 remittance query，并按请求对象身份关联 `onGYNetEnd(int, String, JSONObject)` 响应；查询构造的两个单号按微信原生顺序传入：`transcationid/transactionid` 对应构造第一个字符串并写入请求 `trans_id`，`transferid` 对应构造第二个字符串并写入请求 `transfer_id`；8.0.49/8.0.58 使用五参数构造，8.0.66 至 8.0.76 使用带 `transfer_attach` 的六参数构造，均通过签名和字符串动态定位，不固定混淆名。自动收款选择零钱时不查询；选择零钱通、经营账户或其它具体账户时优先复用按当前 wxid 保存的 `recvAccountType`、`bindSerial`、`subRecvChannelId`，缓存缺失才查询并保存，查询失败、超时或账户无效时传默认值回退零钱；普通诊断日志会标明设置值、缓存命中、查询结果、账户匹配和回退原因，不再制造异常堆栈。
- `paysubtype`: 转账状态字段，不作为 `transferoperation` 请求参数发送。微信聊天气泡状态机确认 `1`、`7`、`21`、`27` 都属于可领取分支，其中 `21/27` 覆盖高版本延迟到账场景；其它状态不应自动领取。

兼容证据：

- 8.0.49: `com.tencent.mm.plugin.remittance.model.n0` 为 10 参数完整构造，另有省略 `left_button_continue` 的 9 参数委托构造。
- 8.0.58: 同类为 12 参数完整构造和 11 参数委托构造，新增 `sub_recv_channel_id` 和显示文案参数，但尚无 `transfer_attach`。
- 8.0.66: 同类为 13 参数完整构造和 12 参数委托构造，在 8.0.58 布局末尾新增 `transfer_attach`。
- 8.0.68 / 8.0.72 / 8.0.74 / 8.0.76: 同类均为 14 参数完整构造和 13 参数委托构造，在账户类型后新增 `bind_serial`；聊天转账状态机确认 `1/7/21/27` 跳到同一待领取 UI 分支。
- 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 均存在账户项 JSON 解析链路，稳定字段为 `recv_channel_type`、`recv_channel_name`、`sub_recv_channel_info`；解析方法名随版本变化，不应固定。
- `getUri()` 均为 `/cgi-bin/mmpay-bin/transferoperation`，`getFuncId()` 为 `1691`。

注意：`receive()` / `refund()` 返回的是请求是否成功交给微信网络队列，不代表服务端最终一定完成。遇到实名、拦截窗、半页确认等服务端回包时，还需要单独处理回包流程。

## Message

`WeChatApis.message().sender()`

发送文本消息。底层使用本模块 DexKit 解析出的微信文本发送类和网络发包器，不使用 WA API。启用 `发送文本格式` 后，`sendText()`、`sendTextAsync()`、`sendTextWithAtList()`、`sendAt()`、`sendAtAll()` 以及 `sendRaw(..., type=1)` 都会先经过统一出站文本格式化器；因此模块和脚本插件只要复用这些公共文本接口，发送内容同样应用模板。公共接口没有输入框交互时，`${sendDuration}` 为 `0秒`；从发送按钮同步回调中调用公共接口时会沿用本次输入框计时上下文。非文本类型的 `sendRaw()` 不应用文本格式。

```java
boolean ok = WeChatApis.message().sender().sendText(talker, "hello");
```

群聊 @ 文本：

```java
boolean canAt = WeChatApis.message().sender().canSendAt();
boolean atOne = WeChatApis.message().sender().sendAt(groupId, memberWxId, "你好");
boolean atAll = WeChatApis.message().sender().sendAtAll(groupId, "请注意");

List<String> atList = Arrays.asList(memberWxId1, memberWxId2);
boolean custom = WeChatApis.message().sender().sendTextWithAtList(
        groupId,
        "@张三\u2005@李四\u2005你好",
        atList);
```

实现证据：

- `NetSceneSendMsg(String, String, int, int, Object)` 在 `flag & 1` 且 `Object instanceof HashMap` 时把 `HashMap` 合并进 `<msgsource>`。
- 微信 `ChatFooter/AtSomeOneHelper` 在 8.0.49、8.0.58、8.0.72、8.0.74 中均生成 `atuserlist -> <![CDATA[wxid1,wxid2]]>`。
- 文本显示格式使用 `@显示名\u2005`，其中 `\u2005` 是微信自己用于 @ 后分隔的空白字符。
- @ 全体使用 `notify@all` 作为 `atuserlist` 值；是否具备 @ 全体权限由微信自身处理。

确认过的版本：`8.0.49`、`8.0.58`、`8.0.72`、`8.0.74`。APK 的绝对路径由开发者在本机自行设置，不在文档中固定目录或文件名。

不要使用 WA 文档里的 `[AtWx=...]` 作为本模块协议；那是 WA 封装层语法，不是这里使用的微信原生发送参数。

发送自定义 XML/AppMsg 内容：

```java
String xml = "<msg><appmsg appid=\"\" sdkver=\"0\"><title>测试</title></appmsg></msg>";
boolean xmlOk = WeChatApis.message().sender().sendXml(talker, xml);

// 特殊调试场景才使用 raw 发送；AppMsg/XML 不要用它代替 sendXml。
boolean rawOk = WeChatApis.message().sender().sendRaw(talker, xml, 49);
```

规则：

- `sendXml(talker, xml)` 对 `<msg><appmsg>...</appmsg></msg>` 先调用微信内部 AppMessage 解析器，再走 AppMsgLogic 的 raw AppMsg 发送链路。
- AppMsg 发送链路会插入 `message` 和 `appmsg` 记录，并创建微信自己的发送任务；不要用 `NetSceneSendMsg type=49` 硬发 AppMsg，否则部分小程序/卡片 XML 会一直转圈。
- `sendXml()` 不替调用方生成业务 XML，也不包装 XML；微信解析后会按 AppMessage 模型重新生成本地 content。
- XML 卡片的缩略图使用内存缓存；缓存未命中时后台预取，当前发送不会同步等待网络，因此首次发送可能没有缩略图，后续发送可复用缓存。
- `sendRaw(talker, content, type)` 只保留给明确知道消息类型和 NetSceneSendMsg 行为的低层调试场景。
- 如果 XML 本身格式不符合微信某类 AppMsg 的要求，微信可能发送失败或接收端无法正确展示；API 不替调用者生成业务 XML。

已用 DexClub 确认的 AppMsg raw 发送锚点：

- 8.0.49: `summerbig sendAppMsg attachFilePath`，签名末尾为 `long`。
- 8.0.58/8.0.68/8.0.72/8.0.74: 同一锚点，签名末尾为引用消息对象、`boolean`、`String`。

新增或修复 XML 发送时必须先逆向目标微信版本确认签名变化，不能固定混淆类名。

`WeChatApis.message().store()`

读取 `message` 表。常用能力：

```java
WeChatApis.message().store().getLatestMessage(talker);
WeChatApis.message().store().getRecentMessages(talker, 20);
WeChatApis.message().store().searchMessages(talker, "keyword", 50);
WeChatApis.message().store().getMessagesAfter(talker, createTime, 50);
WeChatApis.message().store().getMessagesBefore(talker, createTime, 50);
WeChatApis.message().store().getMessagesBetween(talker, startTime, endTime, 100);
WeChatApis.message().store().getOutgoingMessages(startTime, endTime);
WeChatApis.message().store().getOutgoingMessagesOrNull(startTime, endTime);
WeChatApis.message().store().clearConversationMessages(talker);
WeChatApis.message().store().clearConversationMessages(talkers);
```

常规会话查询读取 `message` 表稳定字段；`getOutgoingMessages(startTime, endTime)` 专门汇总时间范围内 `isSend=1` 的消息，每次完整校准都会重新发现 `message`、`message_*`、`*_message` 表并确认统计所需列，再按 `msgId` 去重，因此运行期间新增的分表不会被旧缓存漏掉。`getOutgoingMessagesOrNull(...)` 语义相同，但任一有效消息表读取失败时返回 `null`，供不能把查询失败当成空结果的内部统计使用。输入框提示和发送文本格式用该接口生成本地自然日统计，所以手动、模块和脚本插件发送的消息只要已由微信实际记录，都会进入同一统计快照。已用 DexClub 在 `8.0.49`、`8.0.58`、`8.0.72`、`8.0.74` 确认 `message/createTime/talker/content` 查询基础仍存在。APK 的绝对路径由开发者在本机自行设置，不在文档中固定目录或文件名。

`clearConversationMessages(...)` 复用微信设置页的 `MsgInfoStorageLogic` Stage1/Stage2 异步清理链路，不直接 SQL 删除消息。单个或批量方法返回 `true` 只表示清理任务已成功提交，不表示后台删除已经完成。8.0.49 至 8.0.74 的批量入口为 `(List,callback,long)`，8.0.76 为 `(List,callback)`；运行时通过 `summerdel deleteMsgByTalker`、`AsyncDeleteMessageStage1` 等稳定字符串定位并按微信运行时 key 缓存。

引用消息发送：

```java
boolean ok = WeChatApis.message().sender().sendQuote(talker, msgId, "这是引用回复");
```

规则：

- `sendQuote(talker, msgId, content)` 对外保持 WA 同款 `talker/msgId/content` 语义。
- 当前实现会先从 `message` 表读取 `msgId` 对应源消息；如果未命中，会把传入值按 `msgSvrId` 再查一次，兼容 PB observe 事件只有服务端消息 ID 的场景。命中源消息后按微信引用消息真实结构拼出 `<appmsg><type>57</type><refermsg>...</refermsg></appmsg>`，最后复用 `sendXml()` 的 AppMsg 发送链路。
- 引用块会带上 `type/svrid/fromusr/chatusr/displayname/msgsource/content/createtime`；这些字段是根据微信 `MsgQuoteItem` 和线上真实引用消息 XML 确认的，其中 `fromusr` 是被引用消息所在会话，`chatusr` 是被引用消息发送者，`fromusername` 是当前发送引用的人。
- 如果 `content` 为空，会回退使用源消息正文作为引用回复标题。

撤回消息：

```java
boolean byId = WeChatApis.message().sender().revoke(msgId);
boolean nativeMessage = WeChatApis.message().sender().revokeNative(msgInfo);
```

规则：

- 已持有微信原生消息对象时优先调用 `revokeNative(msgInfo)`；该接口会把对象直接交给 `NetSceneRevokeMsg`，避免按 ID 查库或重建对象丢失原生字段。
- `revoke(msgId)` 仍供只有本地消息 ID 的调用方使用，它会先验证消息由当前账号发送并尽量取得对应原生对象，最终复用 `revokeNative()`。
- `NetSceneRevokeMsg` 的两个字符串参数固定为 `"你撤回了一条消息"` 和空字符串，不能把消息正文放进第三个参数。三参数构造已横向确认微信 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`。

原生消息重发载荷：

- 模块内已经持有 `WeChatMessage` 和可选原生消息对象时，统一调用 `WeChatRetransmitPayloadFactory.build(message, nativeMessage)` 生成 `WeChatRetransmitPayload`，不要在复读、批量发送或定时发送功能里各自维护 `Retr_*` 参数映射。
- 工厂负责群聊正文去发送者前缀、AppMsg 子类型到 `Retr_Msg_Type` 的映射，以及旧版图片完整路径解析。返回 `null` 表示当前消息不能走微信 `MsgRetransmitUI` 快速重发；语音仍通过 `WeChatApis.media().voices().send(...)` 发送。

媒体消息发送：

```java
Object media = ... // com.tencent.mm.opensdk.modelmsg.WXMediaMessage
boolean ok = WeChatApis.media().sendMediaMessage(talker, media, appId);
```

规则：

- `sendMediaMessage()` 直接复用微信 `WXMediaMessage -> AppMsgLogic` 高层发送链路，不自己伪造业务 NetScene。
- 当前底层复用已确认的 `u0.z/A/B/D` 这一套 AppMsg 发送入口；文件发送和 WA 风格 `sendMediaMsg(...)` 也走同一链路。
- `media` 必须是微信自己的 `com.tencent.mm.opensdk.modelmsg.WXMediaMessage` 实例；脚本层保持 WA 同款 `sendMediaMsg(String talker, Object mediaMessage, String appId)`。
- 同一层还补了 WA 风格卡片快捷接口：`shareFile(...)`、`shareMiniProgram(...)`、`shareMusic(...)`、`shareMusicVideo(...)`、`shareText(...)`、`shareVideo(...)`、`shareWebpage(...)`。模块内需要同时传递歌词和专辑图时使用 `shareMusicWithMetadata(talker, title, description, musicUrl, musicDataUrl, songLyric, songAlbumUrl, thumbData, appId)`；它在普通音乐卡片字段之外设置 `WXMusicObject.songLyric/songAlbumUrl`。这些接口内部也是先构造微信 opensdk 的 `WX*Object/WXMediaMessage`，再走同一条 AppMsgLogic 高层发送链路。
- 当前 `shareMiniProgram(...)` 只暴露 WA 同款最小参数集：`title/description/userName/path/thumbData/appId`。由于 WA 签名本身不传 `webpageUrl`，这里会按 `userName/path` 生成微信可接受的兜底 `webpageUrl`，避免小程序对象参数不完整直接被微信拒绝。

不要在未确认微信内部参数前添加新的发送增强 API。

`WeChatApis.message().parser()`

解析 AddMsg 对象和 XML，供红包、消息事件等功能复用。群聊发送者优先读取正文开头的 `wxid:\n` 前缀；前缀不存在且 AddMsg 发送方不是群 ID 时使用发送方，最后才回退 XML `fromusername`，避免引用内容里的发送者字段污染本次消息发送者。私聊直接优先使用 AddMsg 发送方。

`WeChatApis.message().events()`

订阅 AddMsg 层消息事件和红包事件。

```java
WeChatApis.message().events().subscribeMessage(event -> {
    // event.talker / event.sender / event.content
});
```

`WeChatApis.message().changes()`

监听 `message` 表新增、更新、删除。来源是 `runtime().databaseChanges()`，不额外猜微信混淆类。需要监听消息分表的功能应在自己的业务范围内订阅底层变更，不能扩大该共享 API 的事件语义。

```java
WeChatApis.message().changes().subscribe(change -> {
    if (change.isInsert() && change.message != null) {
        String talker = change.message.talker;
    }
});
```

`WeChatApis.message().observe()`

业务级消息观察 API，统一封装 AddMsg 实时事件和 `message` 表变化。数据库链路只把入站 `insert/replace` 作为新消息候选，不派发已读、下载、状态变化等入站 `update`；候选还必须晚于本次数据库观察启动边界、具有五分钟内的有效创建时间，并按服务端消息 ID、本地消息 ID 或稳定回退键去重，避免高版本用 `replace/upsert` 更新旧记录时被业务功能重复消费。自己手动发送消息仍允许通过出站更新补偿。可识别文本、图片、语音、视频、表情、位置、AppMsg、红包、转账、引用、文件、拍一拍、撤回等常用消息类型。

监听路径：

- 优先走微信 AddMsg PB 层。8.0.49 / 8.0.58 / 8.0.66 / 8.0.72 / 8.0.74 均确认存在 `MessageSyncExtension` 的 AddMsg 处理入口。
- PB 可用时，数据库变化只补自己发出的消息，避免收到的消息重复触发。
- 用户在微信输入框手动发送的消息不经过 `WeChatMessageApi`，靠 DB 出站补偿，来源为 `source=message_db`。
- 模块或脚本通过 `WeChatMessageApi` 主动发送成功后，会先登记一条延迟兜底事件；PB 或 DB 任一路观察到同一条出站消息都会取消兜底并正常派发，只有 2.5 秒内两路都没有观察到时才派发 `source=local_send`，避免插件发送的消息漏监听。
- 脚本插件的普通消息 `onHandleMsg` 不订阅 `observe()`，而是只订阅 `message` 数据库变化，以获得更稳定的本地 `msgId`；`onNewFriend` 优先监听 `fmessage_msginfo` 好友申请表，消息观察入口只补充归一化类型 `37` 的好友申请消息，不能把含相似票据字段的联系人名片 `42/66` 当成好友申请；其它脚本回调来源不受影响。
- DB 出站补偿不能只 hook 微信 DB wrapper。手动发送消息可能直接走 `com.tencent.wcdb.database.SQLiteDatabase` 或系统 `android.database.sqlite.SQLiteDatabase` 的 `insertWithOnConflict/updateWithOnConflict/replace` 等方法，所以公共 `WeChatDatabaseListenerApi` 同时 hook wrapper、WCDB SQLiteDatabase 和 Android SQLiteDatabase，并从参数中泛化查找 table、ContentValues、where、whereArgs。微信 wrapper 的写入方法名会被混淆，必须按已横向确认的参数与返回值签名识别 insert/replace/update/delete，不能只匹配英文方法名。同一笔写入从 wrapper 委托到 WCDB/Android 或从 `insert` 委托到 `insertWithOnConflict` 时，使用线程内嵌套深度只派发最外层成功写入；不按时间窗口去重，后续独立写入仍会逐笔派发。启动早期 wrapper 尚未解析时可以先尝试通用 SQLite Hook，但只有真实 wrapper 至少一个写入方法完成 Hook 后才算可用；DexKit warmup 后通过独立任务补定位和有限重试，Hook 失败的方法不能提前写入成功集合，也不能被首次 `installed` 状态锁死。消息变更 API 和脚本消息监听必须等该可用状态成立后才能标记安装成功，同时接受 `message`、`message_*` 和 `*_message` 命名的消息表。
- 同一条接收消息可能先以 `msgSvrId=0` 的半成品记录插入，再通过独立写入补齐服务端 ID、正文或媒体元数据。普通 `onHandleMsg` 仍按稳定消息 ID 去重；图片、视频和视频号下载必须使用独立媒体队列重新读取最新记录，不能让第一次半成品事件提前占用普通消息的 60 秒去重键。
- PB 不可用时，业务可以按自身需要决定是否 fallback 到数据库层；红包助手有 fallback，自动收款没有 fallback。

类型规则：

- PB 的外层 `type` 是微信自己用于 `s0.a(type)` 分发的消息类型，适合判断 `1` 文本、`3` 图片、`34` 语音、`43` 视频、`47` 表情、`48` 位置、`49` AppMsg、`10000/10002` 系统/撤回。
- 红包、转账、引用、文件都不是单靠外层 type 判断。它们通常是外层 `49`，必须解析 `<appmsg><type>` 和对应 XML 子结构。
- 红包识别依据：`<appmsg><type>2001</type>`、`nativeurl` 命中 `receivehongbao/wxhb/hongbao`、或内容包含 `receivehongbao`、`wxhb_personalreceive`、`/hongbao/`。不能把 `<wcpayinfo>` 单独当红包，因为转账也使用 `<wcpayinfo>`。
- 转账识别依据：`<appmsg><type>2000</type>` / `2011`，以及 `<wcpayinfo>` 下的 `transcationid/transactionid/transferid/total_fee/payer_username/receiver_username` 等字段。8.0.49 / 8.0.58 / 8.0.66 / 8.0.72 / 8.0.74 已在微信 `AppMessage` 解析方法确认：`type=2000` 时微信读取 `.msg.appmsg.wcpayinfo.transcationid`、`.transferid`、`.invalidtime`、`.total_fee`、`.payer_username`、`.receiver_username`。
- 链接消息优先按 `<appmsg><type>4</type>` / `5` 判断。音乐消息按 `<appmsg><type>3</type>` / `76` 判断。数据库完整类型 `0x2D000031`、`0x2E000031`、`0x3A000031` 分别表示视频号动态、视频号名片和视频号直播；实时 AddMsg 仍可能只给归一化外层类型 `49`，此时必须按 `<appmsg><type>51</type>` 或明确的 `finderFeed/finderObject/finderUsername/objectId+objectNonceId` 结构识别。不能因为 XML 里出现普通 `finder/channels` 字符串就判视频号，否则链接、音乐、小程序会被误识别。
- 引用消息识别依据：`<appmsg><type>57</type>` 和 `<refermsg>`。微信 `MsgQuoteItem` 对应字段是 `type/svrid/fromusr/chatusr/displayname/content/msgsource/strid/createtime/partialtext`；部分表情引用还会把原发送者写在 `emoji.fromusername` 属性中，解析引用发送者时优先使用该非群聊 ID，不能把引用所在群 ID 当作发送者。
- 文件消息识别依据：`<appmsg><type>6</type>` 和 `<appattach>`。微信 AppMessage parser 确认字段包括 `totallen/attachid/filename/fileext/cdnattachurl/aeskey/cdnthumbaeskey/md5`。
- 拍一拍是系统消息，不是 AppMsg XML。8.0.49 / 8.0.58 / 8.0.74 已确认拍一拍 PB 结构前三个字符串稳定为 `fromUser/pattedUser/template`，时间和服务端 id 字段按版本分支读取。当前通过 `MicroMsg.PluginPatMsg` / `parseDisplayTemplate realtime templateStr:%s` 锚点 hook 模板解析方法，派发 `source=pat_pb`、`kind=pat` 的 observe 事件。这个方法属于显示模板链路，不是纯 AddMsg 入口，所以代码加了 5 分钟时间过滤和去重；如果要进一步压到接收瞬间，需要继续找更上游的拍一拍系统消息处理器。
- `msgSource` 的完整值在微信 MsgInfo 的 `G` 字段。已逆向确认 8.0.49 `com.tencent.mm.storage.k9.T1(String)` 和 8.0.74 `com.tencent.mm.storage.f9.s2(String)` 都是解析 `G` 里的 `<msgsource>`，再取 `.msgsource.atuserlist` 判断 @。实时 AddMsg 路径会优先读取 PB 的 `p` 字段作为 msgSource；数据库历史路径会尝试读取 `message.msgSource` 列并回退到 content/reserved 中已有的 `<msgsource>`。解析时需要兼容 XML 转义、CDATA、逗号/分号/空格分隔；`notify@all` 表示 @ 全体，`announcement@all` 表示公告全体。部分接收链路会把 `notify@all` 展开成当前账号 wxid，此时公共模型会结合微信真实艾特使用的 `U+2005` 分隔符恢复为 @ 全体。PB 字段读取只能接受字符串或微信文本容器，不能把 Boolean/Number 当文本，否则会产生 `talker=false/content=0` 这类假消息。

```java
WeChatApis.message().observe().subscribe(message -> {
    if (message.isRedPacket()) {
        String talker = message.talker;
        String sender = message.sender;
    }
});
```

建议新功能优先用 `observe()`，不要同时分别处理 AddMsg、数据库变化、红包事件，避免重复触发。高频 UI/脚本回调还需要在调用层做轻量去重，因为自发消息可能先后经过模块 API 派发、微信 PB 处理和数据库补偿。

`ObservedMessage` 和 `WeChatMessage` 按 WA `MsgInfoBean` 的常用结构对齐。常用判断：

- `isPrivateChat/isOpenIM/isGroupChat/isChatroom/isImChatroom/isOfficialAccount/isSend`
- `isText/isImage/isVoice/isShareCard/isVideo/isEmoji/isLocation/isApp`
- `isVoip/isVoipVoice/isVoipVideo/isSystem/isRecalled`
- `isLink/isTransfer/isRedBag/isVideoNumberVideo/isNote/isQuote/isPat/isFile`
- `getPatMsg()` 会优先返回拍一拍 PB hook 得到的 `fromUser/pattedUser/template`；只有非 PB 来源才退回系统消息文本层的保守识别。

结构对象：

- `getImageMsg()`: md5、bigImgUrl、midImgUrl、thumbUrl、key。8.0.49 / 8.0.58 / 8.0.66 / 8.0.72 / 8.0.74 均能定位到微信图片消息 XML 字段锚点；8.0.74 已导出确认图片消息 XML 使用 `.msg.img.$md5`、`.msg.img.$cdnbigimgurl`、`.msg.img.$cdnmidimgurl`、`.msg.img.$cdnthumburl`、`.msg.img.$aeskey`。
- `getQuoteMsg()`: title、msgSource、sendTalker、displayName、talker、type、content、svrId、strId、createTime。
- `getPatMsg()`: talker、fromUser、pattedUser、template、createTime。
- `getFileMsg()`: title、size、ext、md5、url、key、attachId、fileName。
- `getTransferMsg()`: transactionId、transferId、payerUsername、invalidTime；另外保留 receiver、fee、description、rawXml、transId、payer 供内部自动收款和兼容代码使用。`transferId` 等同微信 XML 的 `transferid`，`payerUsername` 等同 `payer_username`。

基础字段：

- `getMsgId/getType/getCreateTime/getTalker/getSendTalker/getContent`
- `getMsgSource/getAtUserList/isAnnounceAll/isNotifyAll/isAtMe`

`getAtMentionType()` 将普通 @ 我、@ 全体、群公告全体和其他成员艾特归为互斥类型；`isNotifyAll()`、`isAnnounceAll()` 优先于 `isAtMe()`，因此服务端展开后的 @ 全体不会同时命中 @ 我。`getAtUserList()` 保留原始名单，`getEffectiveAtUserList()` 返回恢复后的语义名单。

注意：`isFile()` 只认文件类 AppMsg，优先依据 `<appmsg><type>6</type>` 和文件字段。不能因为 XML 里出现 `<appattach>` 就判定为文件，因为接龙/笔记等 AppMsg 也可能带附件节点。接龙通过 `isNote()` 识别，依据是 `<appmsg><type>53</type>`、`solitaire` 或接龙特征。

红包助手当前也按这个规则接入：优先订阅 `observe()`，只有当公共消息观察 API 不可用时才回退到自己的 AddMsg/数据库检测 Hook。红包过滤使用的发送者必须优先取红包 XML 的 `fromusername`，其次取 nativeUrl 的 `sendusername` 和消息前缀，最后才使用观察层 `sender`，避免“跳过自己的红包”误判。

`WeChatApis.message().types()`

常用消息类型常量和判断：

```java
int type = WeChatApis.message().types().TEXT;
String name = WeChatApis.message().types().nameOf(type);
```

类型判断会归一化微信附带高位标志的系统类型，例如 `0x10002710` 与 `0x10002712` 分别按 `10000` 系统消息和 `10002` 撤回提示处理。

## Contact

`WeChatApis.contact().account()`

获取当前登录账号信息，例如 `selfWxId()`。

`selfWxId()` 依次读取 `userinfo id=2`、`notify_key_pref_no_account/login_weixin_username`、旧 `login_info` 登录偏好；如果这些为空，会用 `userinfo id=42` 的微信号到 `rcontact.alias` 反查真实 `username`。读取 SharedPreferences 时，微信 `MMApplicationContext` 反射返回 `null` 也必须继续回退宿主 `Context`，不能只在抛异常时兜底。已横向确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 的微信原生登录偏好入口均使用 `notify_key_pref_no_account` 与 `login_weixin_username`；该路径用于数据库尚未就绪、多账号、分身和部分热更新环境下仍能识别自己。

`WeChatApis.contact().contacts()`

联系人、好友、群聊基础查询：

```java
WeChatApis.contact().contacts().getContact(wxid);
WeChatApis.contact().contacts().getFriends();
WeChatApis.contact().contacts().getPickerContacts();
WeChatApis.contact().contacts().getPickerGroups();
WeChatApis.contact().contacts().getPickerOfficialAccounts();
WeChatApis.contact().contacts().getGroups();
WeChatApis.contact().contacts().getOfficialAccounts();
WeChatApis.contact().contacts().searchContacts("keyword", 50);
WeChatApis.contact().contacts().searchFriends("keyword", 50);
WeChatApis.contact().contacts().searchGroups("keyword", 50);
WeChatApis.contact().contacts().getContactsByIds(wxIds);
WeChatApis.contact().contacts().getGroupMembers(groupId);
WeChatApis.contact().contacts().getGroupMemberDisplayNames(groupId);
WeChatApis.contact().contacts().getDisplayName(wxid);
WeChatApis.contact().contacts().getGroupMemberDisplayName(groupId, memberWxId);
WeChatApis.contact().contacts().getContactLabelList();
WeChatApis.contact().contacts().isFriend(wxid);
WeChatApis.contact().contacts().getContactByLabelId(labelId);
WeChatApis.contact().contacts().getContactByLabelName(labelName);
WeChatApis.contact().contacts().hasContactLabel(wxid, labelName);
WeChatApis.contact().contacts().modifyContactLabelList(wxid, labelNames);
WeChatApis.contact().contacts().replaceContactLabelList(wxid, labelNames);
WeChatApis.contact().contacts().modifyContactRemark(wxid, remarkName);
WeChatApis.contact().verifyUser().verifyUser(wxid, ticket, scene);
WeChatApis.contact().verifyUser().verifyUser(wxid, ticket, scene, privacy);
```

`getPickerContacts()` 用于模块通用好友选择器：只保留 `getFriends()` 语义下的好友、`@openim` 企业微信联系人，以及微信 Clawbot 特殊联系人，不把单向联系人或群成员混入公共候选。Clawbot 通过联系人 ID、微信号、备注或昵称中的 `clawbot` 标识识别，即使微信把它标成非普通好友类型，也会进入通用选择器。`getPickerGroups()` 和 `getPickerOfficialAccounts()` 为名单选择器提供轻量群聊及公众号记录；三者只读取名单展示、搜索和头像所需字段，不解析每条联系人的 `lvbuff`。需要显式选择群成员的功能应在自己的候选范围内调用群成员接口追加，不能扩大公共好友集合。

参数含义：

- `wxid`: 好友或用户 wxid。
- `groupId/chatroomId`: 群聊 id，例如 `xxx@chatroom`。
- `memberWxId`: 群成员 wxid。
- `labelId`: 联系人标签 ID。
- `labelName`: 联系人标签名称。
- `remarkName`: 好友备注。

联系人搜索、批量联系人、公众号和群成员完整信息只读取 `rcontact/img_flag/chatroom` 稳定字段。群聊备注来自 `rcontact.conRemark`，群列表展示需要同时合并 `chatroom` 的成员资料和 `rcontact` 的备注字段。标签读取使用微信 `ContactLabel(labelID,labelName)` 表；名单加载会一次读取 `rcontact.contactLabelIds` 并在内存中关联全部标签成员，不能按标签数量逐个重复扫描联系人表。`modifyContactLabelList(...)` 保留联系人已有的有效标签并追加指定标签，`replaceContactLabelList(...)` 则用传入名称精确替换整组标签，空列表会清空全部标签。两者都会先用标签表把全部名称解析成已验证的 ID，并按微信格式生成以 `\u0000` 结尾的 ID 串；存在尚未同步的标签时直接返回失败，历史无效 ID 也不会继续带入请求。好友备注修改会先尝试微信 `setcontactproperty` 同步接口，成功后刷新 `rcontact.conRemark`；旧版本没有该 CGI 时改走微信联系人存储原生保存入口，再刷新本地联系人表，不做只改本地数据库的假备注。已用 DexClub 在 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 确认 `ContactLabel/labelID/labelName` 与 `rcontact/contactLabelIds` 锚点存在；原生标签名称转 ID 方法在 8.0.49、8.0.58 返回 ID 列表，在 8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 直接返回 ID 串，因此公共 API 不再按单一返回类型猜测该方法。8.0.72、8.0.74、8.0.76 确认普通联系人备注同步 CGI 为 `/cgi-bin/micromsg-bin/setcontactproperty`，type 为 `10022`，请求字段为 username、payload.remark 和可选 from_roomname；8.0.49、8.0.58、8.0.66、8.0.68 未命中该 CGI 字符串，按微信旧版联系人存储保存路径处理。

通过好友申请使用微信原生 `NetSceneVerifyUser`，opcode 固定为 `3`，请求 URI 为 `/cgi-bin/micromsg-bin/verifyuser`。脚本 `onNewFriend(...)` 会优先把 `fromusername/username` 解析为可聊天的真实联系人 ID，并短时缓存 `encryptusername` 这类 `v3_...@stranger` 验证用户名；脚本随后调用 `verifyUser(wxid, ticket, scene)` 时会自动映射回验证用户名发包。已用 DexClub 确认 8.0.49 的构造为 `int,String,String,int,String,int`，8.0.58、8.0.66、8.0.68、8.0.72、8.0.74 的构造为 `int,String,String,int,String,int,List,Object`；运行时按字符串锚点解析类，不写死混淆类名。

添加群成员使用微信原生 `NetSceneAddChatRoomMember`，请求 URI 为 `/cgi-bin/micromsg-bin/addchatroommember`，type 为 `120`，funcId/route 分别为 `36/1000000036`。已用 DexClub 确认 8.0.49 和 8.0.74 的构造均为 `String,List,String,Object`；运行时按字符串锚点解析类，不写死混淆类名。

`WeChatContact` 的常用字段：

- `wxId`: 联系人或群聊 id。
- `remarkName`: 备注名；群聊时来自 `rcontact.conRemark`。
- `displayName()`: UI 展示名，内部按备注/昵称/群名等字段兜底。
- `avatarUrl`: 头像路径或 URL，来源于联系人 API 对 `rcontact/img_flag` 的封装。

`ContactLabelBean` 的常用方法：

- `getLabelId()` / `getId()`: 标签 ID。
- `getLabelName()` / `getName()`: 标签名称。
- `getUserNameList()` / `getUsernameList()` / `getContactList()`: 标签下的联系人 wxid 列表。

设置 UI 中展示好友/群聊列表时不要直接重复实现头像加载。`MiuixSettingsPage.ContactPickerPage` 已经：

- 同时支持好友、群聊、好友+群聊三种模式。
- 使用独立 lazy list item 渲染每个联系人，长列表后面的联系人也能选中。
- 在右侧展示小选中框。
- 通过 `AvatarMemoryCache` 做微信进程内头像缓存；同一微信进程里重复打开列表不会重新获取头像，进程退出后自然重新加载。
- 给头像网络加载设置超时，避免 UI 长时间卡住。
- 开启 `enableLabels` 后支持好友标签分组、联系人行标签显示，以及对当前筛选结果全选/取消全选；红包助手和自动收款的白名单/黑名单选择使用该模式。
- 公共联系人显示名 API 对群聊保持返回原群名称，不把群聊备注带入通知、消息模板或其它业务展示。仅名单选择器在群聊存在 `conRemark` 备注时显示为 `群备注(群名称)`，并通过独立搜索别名同时匹配群备注、群名称和群号；聊天内转发名单和群成员选群页沿用相同规则。

屏蔽消息名单管理里的好友、群聊、公众号和标签好友使用共享 `ContactPickerPage`；群成员使用 `GroupMemberPickerPage`。标签只用于筛选并选择标签下联系人，保存结果仍是联系人 wxid、公众号 ID 或群号。

`WeChatApis.contact().chatrooms()`

群聊读取：

```java
WeChatApis.contact().chatrooms().getChatroom(chatroomId);
WeChatApis.contact().chatrooms().getMemberIds(chatroomId);
WeChatApis.contact().chatrooms().getMemberDisplayName(chatroomId, memberWxId);
WeChatApis.contact().chatrooms().getMemberInviter(chatroomId, memberWxId);
```

`WeChatApis.contact().users()`

用户类型判断和账号辅助能力。

`WeChatApis.contact().changes()`

监听 `rcontact`、`img_flag` 变化，适合做好友资料、头像变化感知。

`WeChatApis.contact().chatroomChanges()`

监听 `chatroom` 表变化，适合做群成员、群资料变化感知。

## Runtime

`WeChatApis.message().conversations()`

会话读取和搜索：

```java
WeChatApis.message().conversations().getConversation(talker);
WeChatApis.message().conversations().getRecentConversations(50);
WeChatApis.message().conversations().getRecentConversationUsernames(10000);
WeChatApis.message().conversations().isWechatDoNotDisturb(talker);
WeChatApis.message().conversations().getWechatDoNotDisturbState(talker);
WeChatApis.message().conversations().setWechatDoNotDisturb(talker, true);
WeChatApis.message().conversations().setWechatDoNotDisturb(talker, false);
WeChatApis.message().conversations().getUnreadConversations(50);
WeChatApis.message().conversations().searchConversations("keyword", 50);
WeChatApis.message().conversations().getUnreadCount(talker);
WeChatApis.message().conversations().getTotalUnreadCount();
WeChatApis.message().conversations().deleteConversation(talker);
```

`getRecentConversationUsernames(...)` 只读取按最近时间排序的会话 ID，供大型名单排序使用，避免为了排序构造完整会话对象。会话查询只读取 `rconversation` 稳定字段。已用 DexClub 在 8.0.49、8.0.58、8.0.72、8.0.74 确认 `rconversation/unReadCount/conversationTime` 字段锚点存在。

`getWechatDoNotDisturbState(...)` 读取微信原生 Contact 的免打扰状态，明确开启或关闭时分别返回 `true`、`false`，原生判断尚未就绪且没有可靠回退时返回 `null`；`isWechatDoNotDisturb(...)` 保留布尔便捷接口。普通联系人使用微信原生状态方法，群聊优先解析 `rcontact.lvbuff` 中的 `ChatRoomNotify` 持久状态，值为 `0` 表示免打扰；不能对群聊套用普通联系人的 `type & 0x200` 判断，也不要使用只能表示“当前免打扰未读数量”的 `rconversation.unReadMuteCount` 猜开关。`setWechatDoNotDisturb(...)` 对私聊调用微信 `setMute/unSetMute` 联系人入口，对群聊调用 RoomSDK `notifyMsg=0/1` 并提交原生操作；返回值表示原生调用或请求提交成功。私聊 Contact 通过 `setMute` 的真实调用链定位 Messenger Foundation 联系人存储 getter 与 `(String,boolean)->Contact` 查询入口，不从 CoreStorage 对象树猜联系人存储；免打扰状态方法也属于补定位任务的就绪条件。上述读取、私聊和群聊链路已横向确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76，运行时按字符串、签名和调用关系定位，不固定混淆名。

`boolean deleteConversation(String talker)` 调用微信原生会话存储删除指定会话的本地首页会话项，并触发会话列表刷新；目标会话项已不存在时也返回 `true`。该接口只处理本地首页会话项，不删除消息历史，不删除联系人或群资料，也不会退出群聊。

删除入口已用 DexClub 横向确认微信 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76：模块通过稳定日志字符串和严格的 `com.tencent.mm.storage.*` 实例方法 `void(String)` 签名定位，不写死各版本混淆类名或方法名。

`WeChatApis.runtime().config()`

模块配置读取。

`WeChatApis.runtime().database()`

微信数据库 API。提供 `rawQuery/query/queryFirstString/insert/update/delete` 基础 CRUD 能力，其中写操作通过参数签名定位当前微信 SqliteDB wrapper 的原生方法。常规业务优先只用查询封装；确实需要调用微信原生 storage 方法时，应先用 DexKit 精确定位目标 `Method`，再通过 `storageObjectForMethod(method)` 从 CoreStorage 对象图找到真实实例，不要在业务功能里重复遍历微信 storage 字段。脚本插件通过 `getDatabaseApi()` 获取同一 API，R8 必须保留该类的方法名。修改已有消息正文可调用 `updateNativeMessageContent(msgId, content, nativeMessage)`：传入当前菜单或消息行已经持有的原生消息对象时会优先使用该对象，并按已确认的 `(long, MsgInfo) -> int` 签名调用原生 `MsgInfoStorage` 更新入口；该签名覆盖 8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76，8.0.49 不匹配时返回 `false`，由调用方决定是否回退 SQL。

```java
String value = WeChatApis.runtime().database().queryFirstString(sql, args, column);
Object storage = WeChatApis.runtime().database().storageObjectForMethod(method);
boolean updated = WeChatApis.runtime().database()
        .updateNativeMessageContent(msgId, content, nativeMessage);
```

`WeChatApis.runtime().databaseChanges()`

微信 SqliteDB wrapper 变更与原始查询监听。写入监听已按 8.0.49、8.0.58、8.0.72、8.0.74 确认过以下签名：

- `insert`: `(String, String, ContentValues) -> long`
- `update`: `(String, ContentValues, String, String[]) -> int`
- `delete`: `delete(String, String, String[]) -> int`

```java
WeChatApis.runtime().databaseChanges().subscribe(change -> {
    String table = change.table;
    String op = change.operation;
});
```

模块内部需要在 SQLite 执行前改写原始查询时使用 `subscribeQuery(...)`，回调必须返回原 SQL 或改写后的 SQL。公共入口同时覆盖 SqliteDB wrapper、`com.tencent.wcdb.compat.SQLiteDatabase`、`com.tencent.wcdb.database.SQLiteDatabase` 和系统 SQLite 的 `rawQuery/rawQueryWithFactory`，并通过线程内嵌套深度只处理最外层查询，避免同一条 SQL 在 wrapper 委托链中重复改写。该低层入口不向脚本插件开放。

`WeChatApis.runtime().conversationChanges()`

监听 `rconversation` 表变化，适合做会话未读、最后消息、置顶/免打扰状态感知。`ConversationChange.affectedUsernames()` 同时处理 `ContentValues.username` 和 `UPDATE/DELETE` 的 `whereArgs`；微信已读操作常把 username 放在 `WHERE username=?` 参数中，不能只读取更新值。

`WeChatApis.runtime().storage()`

通用表、字段、单值查询辅助。

`WeChatApis.runtime().network()`

网络发包能力检测和请求发送封装。

`WeChatApis.runtime().version()`

微信版本、clientVersion、Tinker/热更新标识和 DexKit 缓存指纹。

Tinker 文本元数据只匹配分隔符前的完整键名，支持等号和冒号，忽略注释及空值，避免旧键名、备份键名或注释中的值污染缓存指纹。元数据来源优先级不变；版本信息是定位缓存身份，不代表该版本所有功能已验证，覆盖情况见 [多版本适配记录](WECHAT_COMPATIBILITY.md)。

```java
WeChatVersionInfo version = WeChatApis.runtime().version().current();
String display = version.displayVersion();
String cacheKey = version.cacheKey;
boolean patched = version.hasTinkerPatch();
```

用途：

- DexKit 缓存 key，微信版本或热更新变化时自动重新解析。
- 兼容自检日志中定位当前微信构建。
- 后续按版本做能力分支时统一从这里读取，不要各功能自己拼版本号。

`WeChatApis.runtime().permissions()`

API 能力检测。

```java
boolean ok = WeChatApis.runtime().permissions().supportsDatabaseChanges();
```

`WeChatApis.runtime().diagnostics()`

API 自检报告。默认启动时只在缺失关键能力时输出低噪音摘要；需要完整报告时手动获取：

```java
String report = WeChatApis.runtime().diagnostics().buildReport();
```

`WeChatApis.runtime().tasks()`

模块任务调度 API。普通短延迟使用 Android `Handler`，按墙上时钟执行的远期任务使用 `AlarmManager` 精确唤醒并保留 Handler 同刻兜底；异步执行使用 Java 线程池，不依赖微信内部实现。

```java
WeChatApis.runtime().tasks().runOnMain(() -> {
    // UI/main thread work
});

WeChatApis.runtime().tasks().runOnMainDelayed("auto_reply:" + talker, 1000, () -> {
    // delayed work
});

WeChatApis.runtime().tasks().runOnMainAtExact("daily_task", triggerAtMillis, () -> {
    // exact wall-clock work; can wake a living but idle WeChat process
});

WeChatApis.runtime().tasks().runThrottled("send:" + talker, 3000, () -> {
    // rate limited work
});
```

任务 key 规则：

- 防重复：`feature_once:` + 业务唯一 id。
- 延迟覆盖：`feature_delay:` + 业务唯一 id，相同 key 的旧延迟任务会被取消。
- 限频：`feature_rate:` + 会话或用户 id。

当前接入点：

- 红包检测后的静默抢包/UI 抢包延迟调度使用 `runtime().tasks()`。
- 抢到红包后的自动回复延迟、按红包防重复、按会话短限频使用 `runtime().tasks()`。

## Interaction

`WeChatApis.interaction().ui()`

打开聊天页、联系人页、群设置页等 UI。

`WeChatApis.interaction().notifier()`

模块通知和通知点击跳转。

`WeChatApis.interaction().media()`

媒体发送能力。

```java
boolean ok = WeChatApis.interaction().media().images().send(talker, imagePath);
boolean appImageOk = WeChatApis.interaction().media().images().send(talker, imagePath, appId);
String voicePath = WeChatApis.interaction().media().voices().resolvePath(voiceFileName);
boolean voiceOk = WeChatApis.interaction().media().voices().send(talker, voicePath, durationMillis);
boolean voicePlaying = WeChatApis.interaction().media().voices().playOriginal(
    voicePath,
    new WeChatVoiceApi.PlaybackListener() {
        @Override public void onCompletion() {}
        @Override public void onError(String message) {}
    }
);
String videoPath = WeChatApis.interaction().media().videos().resolvePathToken(videoFileName);
boolean videoOk = WeChatApis.interaction().media().videos().send(talker, videoPath);
boolean emojiOk = WeChatApis.interaction().media().emojis().send(talker, emojiMd5);
boolean localEmojiOk = WeChatApis.interaction().media().emojis().sendLocalPath(talker, localGifOrImagePath);
boolean fileOk = WeChatApis.interaction().media().files().send(talker, filePath);
boolean namedFileOk = WeChatApis.interaction().media().files().send(talker, filePath, "report.pdf");
List<WeChatFavoriteItem> favorites = WeChatApis.interaction().media().favorites().listRecent(20);
boolean favoriteOk = WeChatApis.interaction().media().favorites().send(talker, 123456789L);
```

`WeChatApis.interaction().sns()`

朋友圈缓存读取、媒体准备、发布、入库观察、互动与时间线刷新能力。缓存读取向调用方返回 Hchat 稳定模型，不暴露微信 `SnsInfo`、`TimeLineObject` 或混淆 Protobuf 对象。发布接口沿用 `uploadText(...)`、`uploadTextAndPicList(...)`、`uploadTextAndVideo(...)`，并提供单张 `uploadLivePhoto(...)` / `uploadTextAndLivePhoto(...)` 与多张 `uploadLivePhotoList(...)` / `uploadTextAndLivePhotoList(...)` 实况照片扩展；内部互动接口仍可把数据库写入的完整 `ContentValues` 还原为微信 `SnsInfo`，公共入库观察器直接返回微信已经解析好的 `SnsInfo`：

```java
Object snsInfo = WeChatApis.interaction().sns().snsInfoFrom(values);
List<WeChatSnsPost> cached = WeChatApis.interaction().sns().getSnsPostList(50);
List<WeChatSnsPost> userCached = WeChatApis.interaction().sns().getSnsPostList(wxid, 50);
WeChatSnsPost one = WeChatApis.interaction().sns().getSnsPost(snsId);
WeChatSnsPrepareResult prepared = WeChatApis.interaction().sns().prepareSnsPostMedia(snsId);
boolean republished = prepared.isSuccess()
    && WeChatApis.interaction().sns().publishSnsPost(prepared);
boolean likeSubmitted = WeChatApis.interaction().sns().like(snsInfo, 1);
boolean commentSubmitted = WeChatApis.interaction().sns().comment(snsInfo, "评论内容", 1);
boolean refreshSubmitted = WeChatApis.interaction().sns().refreshTimeline();
boolean livePhotoSubmitted = WeChatApis.interaction().sns().uploadTextAndLivePhoto(
    "实况测试",
    livePhotoPath
);
boolean livePhotoListSubmitted = WeChatApis.interaction().sns().uploadTextAndLivePhotoList(
    "多张实况测试",
    livePhotoList
);
WeChatSnsPostObserver.Subscription subscription =
    WeChatApis.interaction().sns().observePosts(storedSnsInfo -> {
        ContentValues storedValues = WeChatApis.interaction().sns().contentValuesFrom(storedSnsInfo);
    });
```

- `getSnsPostList(...)`、`getSnsPost(...)` 只读取当前账号本机已经缓存的朋友圈。列表按发布时间倒序对外返回，广告会被过滤，数量最多 200；这不是服务端完整历史接口。按 `snsId` 和按发布者读取复用微信 `SnsInfoStorage` 原生方法，发布者查询的 `isSelf` 参数严格按 `userName == selfWxId` 传入。脚本层的 `prepareSnsPostMedia(...)` 在后台线程调用同步媒体准备 API。
- 朋友圈模型类型是 `text`、`image`、`video`、`live_photo`、`card` 或 `unknown`。网页链接、音乐和当前已确认的扩展协议类型归为 `card`，未识别类型归为 `unknown`，原始协议值仍由 `contentType` 保留；媒体解析、自动互动、自动转发和界面类型文案复用同一个公共分类器。媒体准备复用手动转发和自动转发的同一解析器：优先读取微信本地原图/完整视频，缺失时调用原生下载并等待落地，VFS 文件复制到模块缓存。`publishSnsPost(...)` 可直接发布成功的准备结果；多张及混合实况逐项保留可用动态媒体，只有配套视频失败的项退成静态封面，卡片和未知类型仍不降级处理。
- 七版 `SnsCore` 存储 getter 都包含 `getSnsInfoStorage` 与 `com.tencent.mm.plugin.sns.model.SnsCore`；按 ID 方法稳定为实例 `(long) -> SnsInfo`，按发布者方法稳定为实例 `(boolean,String,int,boolean,String,int,int) -> Cursor` 并带 `getCursorByUserName` 标记。首页时间线稳定为实例 `(String,int,int) -> Cursor`，调用参数为 `("", 0, limit)`；8.0.49/58/66/68/72/74/76 的时间线方法依次为 `d2.g2`、`b2.p1`、`c2.a0`、`d2.N0`、`f2.e2`、`f2.d1`、`f2.j1`，运行时以 `getAdCursorForTimeLine`、`SnsInfoStorage`、` from AdSnsInfo where createTime >` 和 ` limit ` 联合定位。七版 getter 分别为 `f4.z8`、`j4.Z9`、`l4.li`、`l4.ni`、`l4.kj`、`l4.Fj`、`l4.Uj`；实现只按字符串、签名和返回 storage 关系定位并写入 `DexMethodCache`，不固定这些混淆名。
- 原生点赞先以 `MicroMsg.SnsService` 和 `can not add Comment` 锁定 `SnsLogic.SnsServer` 类，再按静态 `(SnsInfo,int,引用类型,int) -> 非 void` 结构选择 `sendComment` 包装方法。该包装方法内部写入微信自己的朋友圈评论管理器并把点赞请求加入微信网络队列；其正常返回值可以为 `null`，所以 API 的 `true` 表示原生调用已无异常提交，不把返回对象是否为空作为发送结果。
- 原生文字评论复用同一个 `SnsLogic.SnsServer`，普通完整朋友圈按静态 `(SnsInfo,int,String,long,String,boolean,int) -> 非 void` 结构定位直接接收正文的 `sendComment` 包装方法，并按微信评论页实参提交 `type=2`、`replyId=0`、空回复对象、`returnLocal=false` 和调用方来源场景；非完整标记的朋友圈按微信原生分支回退四参数包装方法，8.0.49/58 直接传正文字符串，8.0.66+ 根据第三参数类型动态创建内容对象并调用唯一的字符串 setter。七参数方法在 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 分别为 `w5.l`、`b6.m`、`d6.o`、`d6.m`、`d6.m`、`d6.m`、`d6.o`，运行时按微信版本与 ClassLoader 指纹缓存，不固定方法名。与点赞相同，API 返回 `true` 只表示原生包装方法已无异常调用，不代表服务端已经确认成功；调用方提交后应持久化去重，不能因暂时未看到评论就自动重发。
- 原生刷新以 `MicroMsg.NetSceneSnsTimeLine` 锁定时间线请求类，只接受 `(long,long,int)` 构造，并以 `(0L,0L,0)` 创建首页下拉刷新请求后交给公共网络 API。两个 `long` 同时为 0 表示首页，第三参数为 1 才是上拉方向。
- 点赞方法横向确认：8.0.49=`w5.k`、8.0.58=`b6.l`、8.0.66=`d6.m`、8.0.68/72/74=`d6.n`、8.0.76=`d6.m`；刷新请求横向确认：8.0.49=`c3(JJI)`、8.0.58=`g3(JJI)`、8.0.66/68/72/74/76=`h3(JJI)`。运行时不固定这些混淆名，定位结果统一写入 `DexMethodCache`。
- 实况发布使用 `UploadPackHelper(54, context)`，通过同时包含 `setUploadList` 与 `livePhotoElement != null >> path:` 的方法定位原生媒体列表入口，再从该方法使用的字段类中按 `(String,int)` 构造、自类型实况子字段和 `m/p/t` 字段结构确认上传元素。公开 API 可直接接收包含静态图和内嵌视频的 Android Motion Photo 文件，优先读取 XMP 偏移并以经过 `ftyp`、`moov`、`mdat` 校验的 MP4 盒结构拆分到缓存；旧的封面加视频双路径重载继续兼容，多项接口最多接收 9 项。静态封面元素类型为 `2`，配套视频元素类型为 `6`，视频只按微信原生提交链路设置缩略图路径、实况类型 `54` 和封面时间戳后挂到对应封面的实况子字段；不能额外把视频尺寸和文件大小写入原生未设置的 `g/h/i` 字段。原生 `setUploadList(List)` 在 8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 均逐项检查实况子元素并保留列表索引，因此支持同一条内多个实况及实况/静态封面混排；8.0.49 没有原生实况编辑与上传入口，多项接口降级为静态图文，单张旧接口仍返回 `false`。方法和元素类按微信运行时 key 缓存，不固定混淆名称。
- `observePosts(...)` 只 Hook SNS 时间线写入使用的 `(long snsId, SnsInfo) -> boolean` `replaceUserBySnsId` 方法并在写入成功后派发，不同时消费数据库 Hook 或 UI 绑定回调。该入口在 8.0.49/58/66/68/72/74/76 分别为 `d2.K5`、`b2.b4`、`c2.y3`、`d2.u3`、`f2.c5`、`f2.Y2`、`f2.C3`，定位结果按运行时 key 缓存。
- 七个确认版本均保留 `SnsInfo.getTimeLine()` 和 `SnsInfo.convertFrom(ContentValues)`；API 优先调用其自动生成父类的 `convertFrom(ContentValues,boolean)` 安全处理部分字段，再回退单参数方法。

当前状态：

- `media().images().send(...)`: 已通过 `sendImg: args error` / `MicroMsg.SendMsgMgr` 短签名锚点确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76；实现不创建 Dialog、不跳转 Activity，属于静默发送。图片 `appId` 语义是消息 `appinfo.appid`：8.0.49/8.0.58 低版本仍把 `<msg><appinfo><appid>...</appid></appinfo></msg>` 传入图片发送 8 参数签名第 6 个参数；8.0.66+ 新版异步图片链路会按微信外部 App 发图同款写入 `crossParams.appinfo.appid` 后提交图片异步任务。不要把该参数写入第 5 个 String 的 `imgsourceurl`、第 7 个 String 或 `ImgSourceInfo.imgSourceUrl`。
- `media().images().downloadCdn(...)`: 使用微信 Mars `CdnManager` 的 `C2CDownloadRequest` / `DownloadCallback` 和 `startC2CDownload` 提交图片 CDN fileid，写入 AES key、目标路径、文件类型、业务类型和应用类型。带 `WeChatImageApi.DownloadCallback` 的重载会在 `onC2CDownloadCompleted` 返回后校验 `errorCode == 0` 及目标文件已经非空落盘，再回调 `onSuccess(File)`；提交失败、下载失败和取消分别回调 `onError(String)`，无回调重载保持原有只返回是否成功提交的语义。已横向确认 8.0.49/58/66 使用 `CdnLogic` 内部请求与回调类，8.0.68/72/74/76 使用 `CdnManager` 内部类，七个版本的提交方法都稳定为 `CdnManager.startC2CDownload(request,callback): int`，完成方法都为 `onC2CDownloadCompleted(String,C2CDownloadResult)` 且结果对象包含 `errorCode`。管理器实例按实际 `CdnManager Class<?>` 隔离并允许初始与 Tinker ClassLoader 分别补装捕获 Hook；提交前优先从当前 ClassLoader 的 `MarsContext.getManager(CdnManager.class)` 获取新版实例，旧版从 `Mars2.getContext().getManager(CdnManager.class)` 获取，再回退静态实例扫描，避免微信分身因独立补丁 ClassLoader 未命中早期构造 Hook 而无法下载。DexFinder 定位结果随微信运行时 key 缓存。
- `media().images().sendOriginal(...)` 复用同一静默发送入口并把图片模式设为原图，仅在调用方明确需要保留群发图片质量时使用；普通 `send(...)` 的既有压缩语义不变。`resolveBestAvailablePath(nativeMessage)` 通过 `[getBigPicPath] msg is null.` 唯一定位微信 `ImgInfoStorage` 的大图路径方法，优先返回本地完整原图或高清父图；`resolvePathToken(token)` 通过 `THUMBNAIL://`、`THUMBNAIL_DIRPATH://` 和 `read img buf failed:` 定位图片路径标识解析入口，在大图未下载或原图入口返回空时解析消息 `imgPath` 对应的本地中图/缩略图。路径可能是微信 VFS 引用而不是普通 `File`，API 会通过 `com.tencent.mm.vfs.w6/p6` 的静态 `InputStream(String)` 入口读取并落地到模块缓存后再返回，不能因 `File.isFile()` 为假而丢弃。两个入口及 storage/service getter 均纳入 `DexFinder` 运行时缓存，并已横向确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76。
- `media().voices().send(...)`: 已通过 `startRecord insert voicestg success`、`getAmrFullPath cost:`、`/cgi-bin/micromsg-bin/uploadvoice` 锚点确认 8.0.49、8.0.58、8.0.68、8.0.72、8.0.74；实现创建 voiceinfo、复制语音文件、完成本地消息并发送 `NetSceneUploadVoice`，不经过录音 UI；`send(talker, voicePath)` 会优先用模块 SilkCodec 读取 Silk/常见音频真实时长，再回退系统媒体元数据；`send(talker, voicePath, durationMillis)` 的第三个参数固定为毫秒。文件识别和外部音频转 Silk 在调用线程完成，微信内部 voiceinfo 与上传请求仍统一切到主线程，因此后台业务应从自己的工作线程调用该接口，避免外部音频转码阻塞界面。发送链路用真实时长判断是否走长语音/CDN，写入 `voiceinfo.VoiceLength` 的显示时长最多 60000ms，因此超过 60 秒的语音仍按真实文件发送，但微信 UI 只显示 60 秒。上传请求优先使用 DexKit 定位到的精确构造，构造失败时会枚举同一上传类中首参为 `String` 的构造并填入基础默认值兜底，同时日志输出上传类和构造签名，避免部分版本或热更新签名漂移导致语音发送失败。开启 `伪造语音时长` 后，微信原生录音器和该公共 API 发送的所有语音都会统一写入设置的显示时长，包括右滑复读、收藏语音、自动回复、红包回复、定时任务和脚本发送；调用方显式传入的时长仍用于真实发送链路与长语音/CDN判断，但不再覆盖全局伪造显示值。聊天语音转发和右滑复读共用 `VoiceMessageDurationResolver`；解析时优先读当前已持有原生消息对象显式字段，再解析语音 content 的原生 `fileName:duration:flag` 结构，新版本两处均无时长时通过按 FileName 查询原生语音信息对象并读取 `ContentValues["VoiceLength"]`，最后才使用默认时长。原生查询包装方法通过调用关系动态定位并缓存，不硬编码混淆类名或字段名。输入文件应为微信语音链路可识别的 AMR/SILK/SPX 类语音文件。`resolvePath(voiceFileName)` 是业务功能内部使用的辅助方法，用于把微信语音消息里的文件名解析为真实本地语音路径。
- `media().voices().playOriginal(...)` 直接使用微信原生 `SceneVoicePlayer` 播放已经落地的 Silk/AMR 语音文件，提供完成与错误回调；`pauseOriginalPlayback()`、`resumeOriginalPlayback()`、`stopOriginalPlayback()` 控制当前播放，`canPlayOriginal()` 用于检查定位状态。播放方法会切到微信主线程并按扬声器模式启动，微信播放器仍会自行处理耳机、蓝牙、音频焦点和格式识别。8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 的播放器实例播放签名均为 `(String,boolean,boolean,int)->boolean`，运行时以 `MicroMsg.SceneVoicePlayer`、完整播放日志、暂停/继续/停止日志和签名定位并纳入 `DexFinder` 运行时缓存，不固定 `qi.j`、`bk.j`、`dk.j`、`il.j`、`sl.j` 等混淆类名。该接口只负责本地播放，不模拟语音气泡点击、不修改聊天 UI，也不负责下载文件；调用方应先通过消息存储和 `resolvePath(...)` 等待真实文件落地。
- `media().voices().existingMassSendPayload(...)` 校验已有微信语音文件名及本地文件后直接生成原生群发载荷，不重新创建 `voiceinfo` 或群发助手中转消息；聊天内已存在的语音必须优先走该入口。`prepareMassSendPayload(...)` 仅用于外部音频，复用格式转换、`voiceinfo` 创建和微信语音路径复制逻辑，返回原生群发需要的微信文件名与毫秒时长，不发起普通语音上传。原生群发不得直接使用外部绝对路径，因为 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 的 type 34 网络场景都会把内容字段作为文件名查询微信语音存储长度。
- `media().videos().send(...)`: 已通过 `MicroMsg.MsgRetransmitUI` / `CopyVideoTask ori[%s] status[%d] new[%s]` 锚点确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76；实现直接执行 CopyVideoTask，`Dialog=null`、不启动聊天页、不显示 snackbar，属于静默发送。`resolvePathToken(...)` 把聊天消息 `imgPath` 视频令牌解析成实际 `.mp4`：49/58 使用 `MicroMsg.VideoInfoStorage` 及其静态单例 getter，66/68/72/74/76 使用 `MicroMsg.C2CVideoPathFeatureService` 服务实例，七版签名均为 `(String) -> String`。返回值是微信 VFS 路径且普通 `File` 不可见时，API 会通过微信 VFS 输入流将视频落地到模块缓存后返回。`resolveDownloadInfo(imgPath)` 通过同版本原生 `VideoInfo` 查询包装方法读取持久化 `ContentValues`，从 `reserved4` 的 `videomsg` XML 取得 `cdnvideourl/aeskey/md5`，并从 `totallen/filenowsize/videomd5` 补齐长度与摘要；定位只依赖 `VideoInfo{fileName='`、方法签名和同包关系，不固定混淆类名。`downloadCdn(md5,cdnUrl,aesKey,savePath,callback)` 复用同一 Mars 下载器并固定使用聊天视频文件类型 `4`，成功和失败通过 `WeChatVideoApi.DownloadCallback` 返回；七个确认版本的微信聊天视频重发链路均从原生 `VideoInfo.reserved4` 的 `.msg.videomsg.$cdnvideourl/$aeskey/$md5` 构造文件类型 `4` 的 CDN 请求，消息正文 XML 不能作为必要条件。
- `media().emojis().send(...)`: 已通过 `NetSceneUploadEmoji: msgId`、`getEmojiByMd5`、`getAccPath`、`EmojiLogic.p` 锚点确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76；传入 md5 时发送微信表情库内已有表情；传入本地路径时复制文件到微信表情上传路径，创建内存临时 `EmojiInfo` 后静默插入 type=47 消息并发送 `NetSceneUploadEmoji`，不调用 `createEmojiInfo/updateEmojiInfo`，不主动加入表情库。原生发送方法在七个版本中均以 `String talker, EmojiInfo` 开头，后续参数允许变化；需要处理骰子、猜拳等原生游戏表情时应复用该定位和当前调用持有的 `EmojiInfo`，不要写死发送器混淆类名。
- `media().emojis().resolvePath(...)` 接受本地路径或表情 md5；md5 会先按空 productId 解析普通表情文件，未命中时读取 `EmojiInfo.field_groupId` 再解析分组表情文件。`decodeData(...)` 会优先通过微信 `EmojiFileEncryptMgr` 将 `EmojiInfo` 对应缓存解密为字节，再用 `MMWXGFJNI.nativeWxamToGif(byte[])` 转为可保存的 GIF，路径读取仅作为兜底。解码管理器在 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 均为静态无参单例获取方法加实例 `(IEmojiInfo)->byte[]` 方法；WXAM 转换签名七版一致。路径方法依次为 49=`EmojiLogic.r`、58=`EmojiLogic.s`、66/68/72/74/76=`EmojiLogic.p`，七版均为静态 `(String basePath,String productId,String md5)->String`。
- `media().files().send(...)`: 已通过 `MicroMsg.AppMsgLogic`、`WXFileObject`、`/cgi-bin/micromsg-bin/uploadappattach` 锚点确认 8.0.49、8.0.58、8.0.68、8.0.72、8.0.74；实现构造 `WXFileObject(filePath)` 和 `WXMediaMessage`，调用微信内部 AppMsgLogic 高层入口，静默插入 type=49 文件消息、创建 appattach 记录并触发上传，不启动文件选择/分享 UI。
- `media().favorites().listRecent(...)` / `listAll()` / `send(...)`: 收藏列表优先复用微信收藏页自身的原生链路：动态定位收藏插件服务、收藏存储 getter、首屏查询、续页查询和原生游标推进方法，再直接把返回的收藏实体转换为展示项。收藏选择页先立即展示进程内轻量缓存，再在 `Dispatchers.IO` 后台按微信每批 20 条的原生接口连续加载完整列表，每批之间短暂让出执行权并持续更新界面，不要求用户滚动或点击分页。再次进入时只读取最新 1 条收藏的 `localId + updateTime` 标记；标记未变直接复用完整缓存，有新收藏才清空展示缓存并后台重载。完整缓存只保留 `WeChatFavoriteItem` 展示数据，微信原生收藏实体使用最多 80 条的 LRU 强引用缓存，旧实体发送或预览时按 `localId` 重新读取，避免收藏过多时一次强引用全部实体导致内存暴涨或微信闪退。底层使用微信收藏页相同的类型过滤值 `-1`，首屏后调用微信原生 `static long(long,int,int)` 游标方法推进位置；游标定位以 `tryStartBatchGet...` 稳定字符串和方法签名为依据，已横向确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76。收藏选择页提供图片、文字、语音、视频、其他和全部分类筛选，支持按标题、来源和标签搜索；收藏标题统一解码 HTML/XML 实体并过滤不可见控制符，文件优先使用 `datatitle`，链接优先使用 `pagetitle`，纯标点联系人名称不参与展示；列表显示收藏时间与标签，图片和本地视频通过微信收藏数据路径显示缩略图并支持页内预览。收藏实体的 `tagProto.f` 是实际标签 ID，读取完整列表后会一次性查询 `FavTagInfo(id,name)` 补齐展示和搜索名称；并合并微信原生适配器保留于 `tagProto.e` 的用户自定义标签名称，不使用 `tagProto.d` 的推荐标签。文字收藏优先展示收藏 XML 的正文，其他类型按语义 XML 标签提取标题、文件名或小程序名称，过滤哈希、格式名和路径等技术值，来源摘要保留 wxid 与群号并通过联系人 API 补充联系人昵称、群名称和群内昵称，语音收藏同时显示协议中的归一化时长，不向用户显示收藏 localId。缓存未命中时枚举收藏存储中所有 `long -> 收藏实体` 候选，并以返回实体的本地 ID 语义校验结果；不固定混淆类名、方法名或混淆字段名，也不访问收藏数据库。`send(...)` 对收藏语音先解析普通文件或微信 VFS 资源；文件不存在时按 `FavoriteVoiceDetailUI.onCreate` 的稳定字符串和调用关系定位 `static void(收藏实体, boolean)` 下载入口，在主线程发起下载并由公共任务调度异步等待文件就绪，再复用 `media().voices().send(...)`。该下载链路已横向确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76，定位结果按运行时 key 缓存。小程序收藏优先从持久化 XML 的 `appbranditem` 协议节点提取参数并复用 `media().shareMiniProgram(...)`；其它收藏走微信原生 `FavSendLogic` 转发入口。已确认 8.0.49 使用旧签名 `Context,String,String,List,Runnable`；8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 均从稳定日志所在的三参数校验入口定位同类中的 `Context,String,String,boolean,收藏实体,Runnable` 原生封装入口，由微信自身创建内部请求对象，模块不再依赖请求类的反射构造和字段枚举顺序。最终发送方法按运行时 key 纳入 `DexFinder` 缓存，不固定混淆类名、方法名或字段名。

- `media().favorites().get(...)` / `textContent(...)` / `previewPath(...)`: 按 `localId` 读取收藏展示项、文字收藏 XML 中的完整正文，以及已落地图片或视频收藏的可读文件路径，供收藏转发到朋友圈和系统分享复用。
- `media().favorites().listRecent(...)` / `listAll()` / `get(...)` / `textContent(...)` / `previewPath(...)` / `send(...)`: 收藏列表优先复用微信收藏页自身的原生链路：动态定位收藏插件服务、收藏存储 getter、首屏查询、续页查询和原生游标推进方法，再直接把返回的收藏实体转换为展示项；`listAll()` 使用微信收藏页相同的类型过滤值 `-1` 读取全部类型，每页读取 20 条，首屏后先调用微信原生 `static long(long,int,int)` 游标方法推进位置，再连续续页直到结束并按本地 ID 去重，收藏选择页使用该完整列表。游标定位以 `tryStartBatchGet...` 稳定字符串和方法签名为依据，已横向确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76，不直接把最后一条更新时间当作后续每一页游标。收藏选择页提供图片、文字、语音、视频、其他和全部分类筛选，支持按标题、来源和标签搜索；收藏标题统一解码 HTML/XML 实体并过滤不可见控制符，文件优先使用 `datatitle`，链接优先使用 `pagetitle`，文字、音乐、位置、文件、小程序、聊天记录、笔记和视频号等其它类型按微信收藏适配器实际读取的 `favProto` 协议字段生成原生标题，再用 XML 语义标题、正文或技术信息过滤后的字段兜底，纯标点联系人名称不参与展示；列表显示收藏时间与标签，图片和本地视频通过微信收藏数据路径显示缩略图并支持页内预览。收藏实体的 `tagProto.f` 是实际标签 ID，读取完整列表后会一次性查询 `FavTagInfo(id,name)` 补齐展示和搜索名称；并合并微信原生适配器保留于 `tagProto.e` 的用户自定义标签名称，不使用 `tagProto.d` 的推荐标签。`textContent(...)` 返回文字收藏 XML 中的完整正文，`previewPath(...)` 返回已落地图片或视频收藏的可读文件路径。文字收藏优先展示收藏 XML 的正文，其他类型按语义 XML 标签提取标题、文件名或小程序名称，过滤哈希、格式名和路径等技术值，来源摘要保留 wxid 与群号并通过联系人 API 补充联系人昵称、群名称和群内昵称，语音收藏同时显示协议中的归一化时长，不向用户显示收藏 localId。列表读取时会按本地 ID 缓存对应的微信原生收藏实体，发送时优先直接复用；缓存未命中时枚举收藏存储中所有 `long -> 收藏实体` 候选，并以返回实体的本地 ID 语义校验结果，避免误选按服务器收藏 ID 查询的同签名方法。定位依据是收藏页稳定错误日志、调用关系、方法签名和实体字段语义，不固定混淆类名、方法名或混淆字段名。收藏内容列表不以数据库作为回退来源；只有已持有原生收藏实体且需要将标签 ID 转为名称时才读取 `FavTagInfo` 映射，原生收藏存储链路不可用时仍直接返回不可用。`send(...)` 对收藏语音先解析普通文件或微信 VFS 资源；文件不存在时按 `FavoriteVoiceDetailUI.onCreate` 的稳定字符串和调用关系定位 `static void(收藏实体, boolean)` 下载入口，在主线程发起下载并由公共任务调度异步等待文件就绪，再复用 `media().voices().send(...)`。该下载链路已横向确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76，定位结果按运行时 key 缓存。小程序收藏优先从持久化 XML 的 `appbranditem` 协议节点提取参数并复用 `media().shareMiniProgram(...)`；其它收藏走微信原生 `FavSendLogic` 转发入口。已确认 8.0.49 使用旧签名 `Context,String,String,List,Runnable`；8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 均从稳定日志所在的三参数校验入口定位同类中的 `Context,String,String,boolean,收藏实体,Runnable` 原生封装入口，由微信自身创建内部请求对象，模块不再依赖请求类的反射构造和字段枚举顺序。最终发送方法按运行时 key 纳入 `DexFinder` 缓存，不固定混淆类名、方法名或字段名。

没有确认可用的微信内部发送实现前，不应在这里硬写猜测逻辑。

`WeChatApis.interaction().currentActivity()`

当前 Activity 跟踪：

```java
String name = WeChatApis.interaction().currentActivity().currentActivityName();
boolean chatting = WeChatApis.interaction().currentActivity().isInChatting();
```

`WeChatApis.interaction().activityStart()`

监听 Activity 跳转。

`WeChatApis.interaction().lifecycle()`

监听 Activity `onResume/onPause/onDestroy`。

```java
WeChatApis.interaction().lifecycle().subscribe(event -> {
    if (event.isResume() && event.isChatting()) {
        // 进入聊天相关页面
    }
});
```

`WeChatApis.interaction().chatPage()`

当前聊天页 API。基于目标版本验证过的 `com.tencent.mm.ui.chatting.ChattingUI`、`ChattingUIFragment` 和 `Chat_User` extra：

- 8.0.49
- 8.0.58
- 8.0.66
- 8.0.68
- 8.0.72
- 8.0.74
- 8.0.76

Fragment 进入和退出方法分别通过同一 `ChattingUIFragment` 内的 `onEnterBegin`、`onExitBegin` 字符串定位并缓存；没有确认的 extra 不会被使用，拿不到当前聊天对象时返回空。

```java
boolean inChat = WeChatApis.interaction().chatPage().isInChatPage();
String talker = WeChatApis.interaction().chatPage().currentTalker();
String title = WeChatApis.interaction().chatPage().currentTitle();

WeChatApis.interaction().chatPage().subscribe(event -> {
    if (event.isEnter()) {
        String current = event.talker;
    } else if (event.isExit()) {
        // 已离开嵌入式或独立聊天页
    }
});
```

聊天页事件适合做“只在当前会话提示/响应”的功能；不要用它替代消息监听。

## Feature Recipes

自动回复文本：

```java
trackSubscription(WeChatApis.message().observe().subscribe(message -> {
    if (!message.isText() || message.outgoing) return;
    String key = "reply_once:" + message.talker + ":" + message.content.hashCode();
    WeChatApis.runtime().tasks().runOnce(key, () -> {
        WeChatApis.runtime().tasks().runThrottled("reply_rate:" + message.talker, 3000, () -> {
            WeChatApis.message().sender().sendText(message.talker, "收到");
        });
    });
}));
```

只在当前聊天页处理：

```java
String current = WeChatApis.interaction().chatPage().currentTalker();
if (message.talker.equals(current)) {
    // current chat only
}
```

联系人/群聊选择：

```java
List<WeChatContact> friends = WeChatApis.contact().contacts().getFriends();
List<WeChatContact> groups = WeChatApis.contact().contacts().getGroups();
```

数据库事件调试：

```java
trackSubscription(WeChatApis.runtime().databaseChanges().subscribe(change -> {
    if ("message".equalsIgnoreCase(change.table)) {
        // inspect message table changes
    }
}));
```

## 新增功能规范

- 优先使用现有 `WeChatApis`，不要在功能模块里重复反射微信数据库或网络队列。
- 需要微信内部类时，先用 DexKit/DexClub 确认多个微信版本，再封装进 API 层。
- 不确定的能力只做 `supports...()` 或日志诊断，不要写假实现。
- 业务功能放在 `hooks/items/...`，公共能力放在 `hooks/api/...`。
- 不使用 WA/WAuxiliary API。
- 遇到不确定问题必须先逆向微信，再动手改代码。宁愿多花时间，也不能凭空捏造修复。
- 不要把不确定结论写成确定答案；必须说明证据来自哪个 APK/类/方法/字段。
- API 层只放可复用且已验证的微信能力。单个功能自己的业务逻辑、过滤逻辑、通知文案、统计逻辑不能放进 `hooks/api/**`。
- 新增反射代码必须使用 `h.Hchat.utils.KavaReflector`。不要在业务功能里散落 `getDeclared*`、`setAccessible`、`Constructor.newInstance`、`Class.forName` 或 `Method.invoke`。
- 如果 API 层必须保存或传递原始 `Method` / `Field` / `Constructor` 给 Xposed、DexKit 或缓存，也必须通过 `KavaReflector` 获取。
- `h/Hchat/compat/kavaref/**` 是 KavaRef core 的本地兼容层。目录放在 Hchat 下是为了项目结构好看；文件内部 `package com.highcapable.kavaref...` 不能改。

逆向确认清单：

- 目标微信版本和 APK 路径。
- 命中的类、方法、字段、构造器签名。
- 关键字符串、调用链或数据库表证据。
- 失败时的 fallback 或 `isAvailable()` 行为。
- 是否需要按微信版本/热更新缓存 DexKit 结果。

## Reflection / KavaRef

模块统一反射入口：

```kotlin
h.Hchat.utils.KavaReflector
```

已接入的路径：

- `WeChatTransferApi`: 普通转账领取/退回请求构造。
- `WeChatMessageApi`: 文本/XML 消息构造、AppMsg parse 方法枚举、辅助对象构造。
- `RedPacketReflector`: 通用字段读取、字段存在性判断、按参数构造实例。

依赖和兼容：

- 使用 `com.highcapable.kavaref:kavaref-core:1.1.0`。
- 未直接使用 `kavaref-android:1.1.0`，因为它要求 `compileSdk 37`。
- 未使用 `kavaref-jvm:1.1.0`，因为它和 `kavaref-core:1.1.0` 会产生 `KavaRefProperties` 重复类。
- 当前项目在 `h/Hchat/compat/kavaref/**` 提供 KavaRef core 需要的平台/runtime 类。

示例：

```kotlin
val ctor = KavaReflector.findConstructor(clazz, String::class.java, Int::class.javaPrimitiveType)
val instance = KavaReflector.newInstance(ctor, "value", 1)
val fieldValue = KavaReflector.readField(instance, "fieldName")
```

Java 示例：

```java
Object value = KavaReflector.readField(target, "fieldName");
Object request = KavaReflector.newInstanceByArgs(clazz, args);
```

## Network

`WeChatApis.runtime().network()`

发送微信内部网络请求。请求对象必须来自已确认的微信 NetScene 类，优先由 API 或业务模块通过 DexKit 解析后构造。

```java
boolean ok = WeChatApis.runtime().network().send(request);
```

返回值会按微信网络队列真实返回归一化：`boolean` 直接使用原值，`int`/`Integer` 必须 `>= 0` 才算入队成功，负数表示微信网络队列拒绝或入队失败。业务模块不能把非空返回值直接当成功，否则会出现拍一拍这类“本地消息已插入、对方实际收不到”的误判。

不要在业务模块里重复 hook 网络队列；网络发包器由 `WechatApiFeature` 初始化并缓存。DexKit 预热后会对网络队列主类及全部候选类执行增量补装，只 Hook 首参数类型具有 `getType(): int` 的请求队列方法，避免启动期先命中非执行类后让刷新、点赞等请求永久失去发包器。

## Media

`WeChatApis.interaction().media()`

媒体相关 API 里图片、语音、视频、表情、文件发送已接入。没有逆向确认上传/发送链路前，不要添加猜测实现。

## Config

配置使用规则：

- 功能开关：全局 key 使用 `{featureId}_enabled`。
- 功能私有配置：使用 `ConfigStore.getFeaturePrefs(featureId)` 或 `BaseFeature.featureBoolean/featureInt/featureString`。
- 模块自有配置统一通过 `HchatStorage.preferences(context, name)` / `ConfigStore` 访问。
- 配置文件保存在微信宿主私有目录的 `Hchat/` 文件夹，不放 `shared_prefs/`。
- 日常读写由 FastKV 负责。FastKV 可能为同一个配置名生成 `.kva/.kvb` 文件，不再做旧数据迁移。
- 不要在业务代码里新增散落的 `context.getSharedPreferences(...)`。读取微信自己的 `login_info` 等宿主原生配置除外。
- 历史功能已有固定 prefs 名时可以保留名字，但存储入口必须走 `HchatStorage`。
- 所有普通开关和可编辑设置都必须保存后实时生效，不能要求重启微信。
- 不要只在启动期读取设置并长期缓存；事件处理、抢红包、自动回复、通知、过滤前必须读取最新值或刷新运行时缓存。
- 保存后马上会被运行时代码读取的设置，使用 `commit()` 同步落盘。
- 如果为了性能使用内存缓存，必须提供刷新机制，例如保存设置时刷新，或每次处理事件前调用 `refresh()`。

普通运行时设置包括：

- 功能总开关
- 抢包模式、延迟、自动关闭
- 跳过自己的红包
- 黑名单、白名单、关键词过滤
- 红包规则模板和适用聊天分配
- 通知开关和通知文案
- 自动回复开关、回复类型和回复文案/媒体文件
- 祝福语开关和文案
- 假红包兼容开关

只有 DexKit 解析结果、Hook 安装、微信内部结构变更这类启动期能力可以要求重启。

## API Smoke Test

稳定包不注册 API Smoke Test / 媒体 API 测试功能，避免日常日志噪音和额外入口。

调版本兼容时如需恢复 smoke test，必须作为独立 debug feature 放在 `hooks/items/debug/**`，默认关闭，并且不能影响稳定包功能列表。测试日志必须限频、表过滤，只输出明确需要观察的事件。

## Compatibility Self-Check

`WechatApiFeature` 启动后会自动运行版本兼容自检。默认只在缺失能力时打印，不输出全量成功报告。

自检关注：

- `message.observe`: 统一消息观察入口
- `message.sender`: 发送文本消息能力
- `runtime.network`: 微信网络发包能力
- `runtime.databaseChanges`: 数据库变更监听
- `contact.groupMemberDisplayName`: 群成员群内昵称
- `interaction.chatPage.start` / `interaction.chatPage.fragmentEnter` / `interaction.chatPage.fragmentExit`: 聊天页进入和退出事件
- `dex.receiveLuckyMoney` / `dex.openLuckyMoney`: 红包收/拆包目标

如果自检打印缺失项，先用 DexKit/DexClub 在对应微信 APK 上确认目标，再修改 `dexkit/**` 或 API 层。不要凭空补类名、方法名、字段或表名。

## 构建

推荐构建命令：

```sh
ANDROID_HOME="/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu/root/Android/Sdk" \
ANDROID_SDK_ROOT="/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu/root/Android/Sdk" \
GRADLE_USER_HOME="$PWD/.gradle-home" \
sh ./gradlew :app:assembleRelease --no-daemon --no-watch-fs \
-x lintVitalRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease \
-Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

输出：

```text
dist/Hchat-release-signed.apk
```
