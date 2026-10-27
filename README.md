# 拾星 · Android 客户端

星遇同频（vrcc.cc）的第三方 Android 客户端。**只使用官方开放的 `/api/v1`**，不复刻网页端的未公开接口（不调 `/api/match`，不读网页的登录密钥）。

> 本应用不是官方应用。名字、图标、包名都不含「星遇同频」「VRCC」「VRChat」「官方」「管理员」等字样。

- 仓库：https://github.com/daa0v0/xingshi-app
- 客户端标识：`app_f91c740693315463`（`BuildConfig.CLIENT_ID`，每个请求都带 `X-Client`）
- 许可：MIT

<p align="center">
  <img src="docs/screenshot-connect.png" width="320" alt="连接页">
</p>

## 能做什么

| 页面 | 数据来源 | 说明 |
| --- | --- | --- |
| 今天 | `GET /me/today`、`POST /me/links`、`POST /me/stars/{n}/say` | 这一轮路过的星；「连上」「说一句」**只在玩家点的时候发** |
| 连向我 | `GET /me/inbox`、`GET /me/crossings`、`GET /me/mutuals`、`GET /me/chats` | 连向我的星、星轨相交（`why=hide` 时整块不显示）、私聊入口 |
| 星友圈 | `GET /me/posts`、`POST /me/posts`、`POST /me/posts/{id}/light` | 全部 / 好友 / 我的（接口的 `f`）、点亮 / 取消、发文字动态 |
| 地图榜 | `GET /boards/maps`、`GET /me/maps/{world}`、`POST /me/maps/{world}/rating` | 点一张图打 1–10 分 + 30 字短评，**只在玩家点提交时写** |
| 私聊 | `GET /me/chats/{n}`、`POST /me/chats/{n}` | 只能和互相连上的人 |
| 我的 | `GET /me/card`、`GET /me/maps` | 星卡（装扮用 `me/card` 的 `deco`）、我打过分的地图、本地日配额用量、断开连接 |
| 照片 | `GET /me/stars/{n}/photo` | 没连上是颜色格，互相连上才清晰；同一颗星不重复问 |

连接方式（两条都走官方接口）：

1. **设备授权（推荐，RFC 8628）**：App 里点「生成连接码」，拿到 `ABCD-2345` 这样的码；在网页「我的资料 → 第三方客户端 → 连接新客户端」填进去点「同意」，App 每 5 秒问一次，同意后自动进入。
2. **8 位连接码**：网页「我的资料 → 在 VRCX 里提醒」生成的一次性码，直接填进 App 的第二个输入框。

每个客户端有**自己的授权**，不会顶掉 VRCX 脚本或其他客户端；玩家可以在「我的资料 → 第三方客户端」里单独断开。令牌只存在这台设备上，只发往 `vrcc.cc`。

## 不能做什么（官方接口没开）

装扮编辑、仓库 / 兑换码 / 礼物、账号与设备、星搭、测星问卷——这些只在网页自己的 `/api/match` 上，官方《使用要求》写的是「新的客户端请一律使用 `/api/v1/`」，所以这里不做（1.0.1 曾经用网页登录密钥走过这条路，已经去掉，升级时会清掉本机存的密钥）。改资料、注销账号也不在接口里，请回网页端。

> 2026-10-24 实测（重要）：自助登记但**未审核**的应用（`X-Client: app_…`）连上后，老的四个接口 `me/notifications`、`me/notifications/recent`、`me/crossings`、`me/mutuals`（以及 `me/maps/{world}`、打分）会返回 `403 {"error":"client_unknown"}`；同一枚令牌打 `me/card`、`me/today`、`me/inbox`、`me/links`、`me/posts`、`me/chats` 则正常走到 scope 检查（`403 {"error":"scope","need":"…"}`）。结论：老接口按「客户端白名单」校验（`dev-` 或审核通过的应用），新接口按 scope 校验。要跑老接口就去点「申请审核」。`dev-` 标识能跑老接口，但 `POST /device/code` 只能要普通权限（`profile notifications maps`），敏感权限会 `403 scope_unreviewed`。

> 2026-10-24 更新：官方接口已扩展到 24 条路径，新增 `GET /me/card`、`/me/today`、`/me/inbox`、`/me/links`、`/me/chats`、`/me/chats/{n}`、`/me/posts`、`/me/maps`，以及 `POST /device/code`、`/device/token`、`/oauth/token`、`/oauth/userinfo`。也就是说「今天」「连向我」「私聊」「星友圈」「我的星卡」现在都在合规范围内，见路线图下一个阶段。

## 合规清单（对着官方要求逐条落）

- **登记**：已在「我的资料 → 第三方客户端 → 我登记的应用」登记，`client_id = app_f91c740693315463`（`BuildConfig.CLIENT_ID`，每个请求都带 `X-Client`）。登记时拿到的密钥 `sec_…` 属于网站/OAuth 流程，**不在 App 里、不进仓库**。
- **只走官方接口**：`VrccClient` 里 `require(!path.startsWith("http"))` 硬拦绝对地址，基址写死 `https://vrcc.cc/api/v1/`。不调网页自己的 `/api/match`。
- **限流**：`Throttle` 每分钟 120 次上限 + 每个接口的最小间隔（照片也走这里）；「第一份读」（最近提醒、星轨、互相连上、所在地图、我打过分的地图）本地合计每天 300 次，按北京时间算日期，在 `TokenStore.takeDailyQuota()` 里先扣，宁可少问。提醒轮询官方没有每天上限，不扣这份。收到 `429` 按 `Retry-After` 等（每分钟的限制 60 秒，每天的次数到北京时间零点），带 `RateLimit-*` 的接口用到 0 次也等到重置，在那之前这个接口不再发请求。
- **写操作只在点击时**：打分、连线、说一句、私聊、点亮、发动态都只在玩家点按钮时发一次，不做后台批量。
- **令牌**：只存本机 DataStore，只发给 `vrcc.cc/api/v1`（贴纸素材不需要令牌，也不带），断开即删；不转存、不上传。
- **scope**：只申请界面上用到的 `profile card maps maps.rate notifications crossings mutuals today inbox link chat chat.send posts posts.write photos`（`Repository.DEFAULT_SCOPE`）；举报 / 隐藏做好以后再加 `safety`。
- **Android 权限**：只申请 `INTERNET`、`ACCESS_NETWORK_STATE`、`POST_NOTIFICATIONS`。
- **只给本人看**：所有数据只渲染给当前连接的星，没有分享/导出/统计上报。
- **开源**：本仓库（`https://github.com/daa0v0/xingshi-app`），App 里 `BuildConfig.SOURCE_URL` 指向同一地址。

## 构建

```bash
# 需要 JDK 17+ 与 Android SDK（compileSdk 34）
./gradlew assembleDebug        # Windows: .\gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

正式包（R8 压缩，约 1.5 MB）：

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

单元测试用 MockWebServer 钉住契约（`X-Client` 头、相对路径、错误码和 `need`、`Retry-After`、`gone`、发动态的 `kind`、申请的 scope，以及弃用字段缺了照样能解析）：

```bash
./gradlew testDebugUnitTest
```

## 代码结构

```
app/src/main/java/app/shixing/client/
├── MainActivity.kt          单 Activity，edge-to-edge
├── ShixingApplication.kt    组装 Repository
├── data/
│   ├── Models.kt            /api/v1 的返回形状（@Serializable）
│   ├── VrccClient.kt        唯一出口，负责头、令牌、错误解析
│   ├── TokenStore.kt        DataStore：令牌、游标、本地日配额
│   ├── Throttle.kt          每分钟限流 + 每接口最小间隔 + 按 Retry-After 暂停
│   ├── Repository.kt        把限流/配额/存储收成一层，申请的 scope 也在这里
│   ├── Photo.kt / SignedImages.kt  照片的颜色格解码、签名图片过期重拿
│   ├── Deco.kt              星卡装扮（deco）的解析
│   └── UpdateChecker.kt     查 GitHub Releases
├── notify/
│   └── ReminderNotifier.kt  通知渠道与通知内容（不发请求）
├── work/
│   ├── ReminderWorker.kt    后台增量拉取 + 退避
│   └── ReminderScheduler.kt 每 15 分钟登记一次
└── ui/
    ├── MainViewModel.kt     状态 + 错误翻译
    ├── Home.kt              底栏 4 页、连接页、今天、连向我
    ├── Pages.kt             我的、地图榜 + 打分框、私聊
    ├── PostsScreen.kt       星友圈
    ├── StarCardView.kt …    星卡、贴纸
    └── theme/Theme.kt       深色配色，对齐站点 #05060A / #4DB4FF
```

## 路线图

- [x] P0 工程与主题
- [x] P1 数据层（客户端、令牌、限流、配额）
- [x] P2 五个页面 + 评分
- [x] P3 后台提醒（WorkManager 每 15 分钟增量拉，见下）
- [ ] P4 网页登录兜底（WebView 只用于过 Turnstile，拿不到就引导扫码连接）
- [ ] P5 上架材料 / 开源仓库 / 登记 Issue

## 后台提醒怎么工作

`ReminderWorker` 每 15 分钟（系统允许的最小周期）用一次 `GET /me/notifications?since=<游标>`，只取增量：

- 首次运行只记基线，不弹历史提醒；
- 提醒轮询官方没有每天上限（只要求间隔 25 秒），所以不扣本地日配额，不跟前台的星轨、互相连上抢次数；
- 只把 `id > 游标` 的条目合成一条通知，点开回到 App；
- 遇到 `429` 按 `Retry-After` 等到下一轮（旧版服务器不带这个头时交给 WorkManager 退避）；遇到 `gone`（令牌失效：断开了、重新授权过，或 90 天没调用自动断开）删掉本机令牌，弹一次「连接已失效」后静默。

官方文档建议按返回的 `every`（60 秒）轮询——那是网页端前台的节奏；后台照做会把电池烧掉，所以这里刻意放慢到 15 分钟。

## 已知限制

- 没有 Web Push：后台提醒是 15 分钟一轮的增量拉取，不是实时推送。
- 星轨的 `intro` 只有前 60 字，原文要回网页端看。
- 连接码用一次就失效，每天最多连 30 次；每颗星同时只有一个连接，在别处连接会让这边的登录失效。
