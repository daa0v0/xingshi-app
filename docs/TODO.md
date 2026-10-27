# 待办清单（客户端 · 拾星）

> 这份清单是给「后面再做」的事情排队用的。已完成的不放这里。
> 每条都写了**为什么现在不做**，避免下次重新判断一遍。
> 最后更新：2026-10-27

---

## A. 只在网页接口上的功能：等 v1 开放再做

装扮编辑、我的仓库、兑换码、账号与设备、星搭，这些只在网页自己的 `/api/match` 上。
1.0.1 曾经在 WebView 里读网页的登录密钥走过这条路，已经整块去掉：《使用要求》写明
新客户端一律用 `/api/v1/`、不绕过检查，也不要去逆向网页表单。

- 只读的部分 v1 已经有了：贴纸和星尘 `GET me/bag`（权限 `bag`）、星卡装扮 `me/card` 的 `deco`。
  要做「我的仓库」就用 `me/bag`，记得把 `bag` 加进 `Repository.DEFAULT_SCOPE`。
- 写装扮、兑换码、星搭：请作者在 v1 里补，补了再接。

---

## B. 举报和隐藏（权限 `safety`）

- 现状：卡片上的「⋯」只弹一句「去网页端举报」。开放接口文档要求「显示其他玩家内容的客户端，
  请把举报和隐藏做上」，拾星显示了今天的星、连向我、星友圈和私聊，属于这一类。
- 接口：`POST me/stars/{n}/report`（{why, note}）、`POST me/stars/{n}/hide` / `unhide`、
  `GET me/hidden`，动态、评论、私聊消息各有 `…/report`。
- 做的时候把 `safety` 加回 `Repository.DEFAULT_SCOPE`（现在没用到就没申请）。

---

## C. 贴纸（`/api/v1/stickers/{id}`）

- 现状：部分贴纸已经能画到卡上（`ui/StickerImages.kt`，OkHttp 取字节 →
  `BitmapFactory`），但同一批接口**有的返回 webp、有的是 SVG**（实测
  `decode fail 28143 head=<svg`），SVG 那张目前画不出来。
- 卡点：`/api/v1/stickers` 的每项没有 `kind: svg|raster` 字段，客户端只能看字节头猜。
- 下一步：按响应的 `Content-Type` / 首字节分流——webp 走 `BitmapFactory`，
  SVG 走 AndroidSVG（要先剔掉 `<mask>` 与内嵌位图）。

---

## D. 星轨（跨世界相遇）

- 现状：已并入「连向我」的子页，但**权限没下来**：审核过的 `app_` 客户端调
  `me/crossings` / `me/mutuals` / `me/notifications` 仍然 `403 client_unknown`，
  同令牌的 `me/card` / `me/today` / `me/inbox` 都 200。
- 已在给作者的报告里列出（见 `docs/给作者的接口问题.md` 之类，若未建则口述过）。
- 下一步：等作者确认这三个接口对 `app_` 客户端开不开放。

---

## E. 照片的中间档位

- 现状：`grid` 已能解码成像素格铺满卡面；互相连上时用签名 `url` 出原图；
  **自己**用 `me/card` 的 `n` 也能拿到清晰图（1.8.x 新增）。
- 缺口：`level` 字段文档里没有解释（README 出现 `grid` 3 次、`level` 0 次），
  中间两档（TA 连向你 / 你连向 TA）画质差异只能靠观感调。
- 下一步：等作者回 `level` 的定义，如果和「互相连上」无关，再按档位调模糊强度。

---

## F. 杂项

- [ ] 「连向我」的卡比可视区高一点点，底部「成分」要下滑十几 dp 才看全（可换成
      卡缩到一屏内 + 头部瘦身）。
- [ ] `我的 → 私聊` 里对方的「最后一句」有时是空的（`peer` 里没有摘要字段）。
- [ ] 卡片上的「⋯」菜单只有提示，没有真正做「跳过 / 举报」（举报见上面 B）。
- [ ] 后台提醒（P2 那套）在换令牌后要重新注册，目前只在启动时同步一次。
- [ ] 正式包现在用 debug 密钥签名（`app/build.gradle.kts` 的 `signingConfig`）。换成自己的发布密钥前装过的人
      没法覆盖升级，越早换越好；密钥文件和 `keystore.properties` 已经在 `.gitignore` 里。
- [ ] 申请的权限加了 `posts.write`，已经连上的玩家要重新连接一次才能点亮、发动态。
      可以在收到 `403 scope`（`need: posts.write`）时直接引导去重新连接。
