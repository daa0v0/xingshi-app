package app.shixing.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.shixing.client.BuildConfig
import app.shixing.client.data.BoardMap
import app.shixing.client.data.ChatResp
import app.shixing.client.data.Groups
import app.shixing.client.data.Msg
import app.shixing.client.data.MyMap
import java.util.Locale

// ---------------- 星轨 ----------------

@Composable
fun CrossingsPage(vm: MainViewModel, s: UiState, showTitle: Boolean = true) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        if (showTitle) {
            ScreenTitle("星轨", "与你在同一张地图里相遇的星")
        }
        PageError(vm, s, "crossings")

        // 官方要求：why 为 hide 时整块不显示。
        if (s.crossingsWhy == "hide") {
            Spacer(Modifier.height(28.dp))
            Empty("这一页现在不可用。")
            return@Column
        }
        whyText(s.crossingsWhy)?.let {
            Spacer(Modifier.height(10.dp))
            ErrorBar(it)
        }

        Spacer(Modifier.height(12.dp))
        if (s.crossings.isEmpty()) {
            Empty("还没有星轨相交。去 VRChat 里逛逛，遇到的人会记在这里。")
        } else {
            Row(
                Modifier.padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill("${s.crossings.size} 颗星", Color(0x1F4DB4FF), MaterialTheme_color.accent, size = 11)
                s.crossings.count { it.mutual }.let {
                    if (it > 0) Pill("$it 个互相连上", Color(0x1F2FD08A), Color(0xFF2FD08A), size = 11)
                }
            }
            var lastDay: String? = null
            s.crossings.forEach { c ->
                val day = clock(c.at).substringBefore(" ")
                if (day.isNotBlank() && day != lastDay) {
                    lastDay = day
                    Text(
                        day,
                        fontSize = 11.sp,
                        color = MaterialTheme_color.ink3,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp),
                    )
                }
                BoxCard(tint = Color(Groups.color(c.group))) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.name, c.group, 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    c.name ?: "一颗星",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme_color.ink,
                                )
                                Spacer(Modifier.width(6.dp))
                                GroupPill(c.group)
                                if (c.fresh) {
                                    Spacer(Modifier.width(6.dp))
                                    Pill("新", Color(0x33FF9EC4), Color(0xFFFF9EC4))
                                }
                            }
                            c.line?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, fontSize = 12.sp, color = MaterialTheme_color.ink2)
                            }
                        }
                        Text(clock(c.at), fontSize = 11.sp, color = MaterialTheme_color.ink2)
                    }
                    c.intro?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, fontSize = 13.sp, color = MaterialTheme_color.ink, lineHeight = 20.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when {
                            c.mutual -> Pill("互相连上", Color(0x332FD08A), Color(0xFF2FD08A))
                            c.linked -> Pill("已连上", Color(0x33FFB547), Color(0xFFFFB547))
                            else -> Pill("未连", Color(0x1FFFFFFF), MaterialTheme_color.ink2)
                        }
                        c.vrc?.let {
                            Spacer(Modifier.width(8.dp))
                            Text("VRChat：$it", fontSize = 12.sp, color = MaterialTheme_color.ink2)
                        }
                        Spacer(Modifier.weight(1f))
                        if (!c.linked) {
                            Text(
                                "连上",
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { vm.link(c.n, "cross", null) { m -> vm.showHint(m) } }
                                    .background(MaterialTheme_color.accent)
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                fontSize = 13.sp,
                                color = Color(0xFF04121F),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun whyText(why: String?): String? = when (why) {
    "verify" -> "先在网页上验证 VRChat 账号，这里才会显示。"
    "wait" -> "机器人还在路上，过一两天再来看看。"
    "queued" -> "已经排上队了，第一次同步要等一会儿。"
    "sent" -> "机器人的好友申请已经发出，去 VRChat 里同意一下。"
    "gone" -> "你的 VRChat 好友里已经没有机器人了，重新加一下。"
    "friend" -> "先同意「VRCC的小助手」的好友申请。"
    else -> null
}

// ---------------- 我的 ----------------

@Composable
fun MePage(
    vm: MainViewModel,
    s: UiState,
    onOpenBoard: () -> Unit,
    onOpenChat: (Long) -> Unit,
    photoUrl: String? = null,
    photoGrid: android.graphics.Bitmap? = null,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        ScreenTitle("我的")


        // 星卡：默认就摊开显示（和网页一样），点一下翻面
        PageError(vm, s, "card")
        s.card?.let { c ->
            Spacer(Modifier.height(12.dp))
            FlippableStarCard(
                face = c.toFace(),
                photoUrl = photoUrl,
                photoGrid = photoGrid,
                modifier = Modifier.fillMaxWidth(),
                intro = c.intro,
            )
        }

        // 私聊列表也要照片：这里给每个会话的对方拉一次头像
        androidx.compose.runtime.LaunchedEffect(s.threads.map { it.peer?.n }) {
            s.threads.forEach { th ->
                th.peer?.n?.let { n -> if (s.cardPhotos[n] == null) vm.loadPhoto(n) }
            }
        }
// 私聊
        Spacer(Modifier.height(14.dp))
        BoxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("私聊", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme_color.ink)
                Spacer(Modifier.weight(1f))
                Text(
                    "刷新",
                    Modifier.clickable { vm.refreshChats() },
                    fontSize = 12.sp,
                    color = MaterialTheme_color.accent,
                )
            }
            PageError(vm, s, "chats")
            Spacer(Modifier.height(8.dp))
            if (s.threads.isEmpty()) {
                Text("只有互相连上的人才能私聊。", fontSize = 12.sp, color = MaterialTheme_color.ink2)
            } else {
                s.threads.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { t.peer?.let { onOpenChat(it.n) } }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PhotoAvatar(
                            photoUrl = s.cardPhotos[t.peer?.n]?.url,
                            grid = s.cardPhotos[t.peer?.n]?.grid,
                            name = t.peer?.vrc ?: t.peer?.name,
                            size = 36.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.peer?.vrc ?: t.peer?.name ?: "一颗星",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme_color.ink,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                (t.last?.text ?: "[消息]").take(24),
                                fontSize = 12.sp,
                                color = MaterialTheme_color.ink2,
                            )
                        }
                        if (t.unread > 0) {
                            Pill("${t.unread}", Color(0x33FF7A7A), Color(0xFFFFB4B4))
                        } else {
                            Text(clockShort(t.last?.at), fontSize = 11.sp, color = MaterialTheme_color.ink2)
                        }
                    }
                }
            }
        }

        // 我打过的地图
        Spacer(Modifier.height(14.dp))
        BoxCard {
            Text("我打过的地图", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme_color.ink)
            PageError(vm, s, "mymaps")
            Spacer(Modifier.height(8.dp))
            if (s.myMaps.isEmpty()) {
                Text("还没有打过分。", fontSize = 12.sp, color = MaterialTheme_color.ink2)
            } else {
                s.myMaps.take(20).forEach { m ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name ?: m.world, fontSize = 13.sp, color = MaterialTheme_color.ink)
                            m.words?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it + when (m.wordsOk) {
                                        "wait" -> "（审核中）"
                                        "hidden" -> "（未通过）"
                                        else -> ""
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme_color.ink2,
                                )
                            }
                        }
                        Text("${m.score} 分", fontSize = 12.sp, color = MaterialTheme_color.accent)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "看地图榜",
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenBoard() }
                    .background(MaterialTheme_color.card2)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                fontSize = 12.sp,
                color = MaterialTheme_color.ink,
            )
        }

        // 关于
        Spacer(Modifier.height(14.dp))
        BoxCard {
            Text("关于", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme_color.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "拾星 ${BuildConfig.VERSION_NAME}（第三方客户端，非官方）",
                fontSize = 13.sp,
                color = MaterialTheme_color.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text("客户端标识 ${BuildConfig.CLIENT_ID}", fontSize = 12.sp, color = MaterialTheme_color.ink2)
            Spacer(Modifier.height(6.dp))
            Text(
                "今天已用个人接口 ${s.dailyUsed} / 300 次",
                fontSize = 12.sp,
                color = MaterialTheme_color.ink2,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "令牌只存在这台设备上，只发给 vrcc.cc；断开会从本机删除。",
                fontSize = 12.sp,
                color = MaterialTheme_color.ink2,
            )
            Spacer(Modifier.height(6.dp))
            Text("源代码：${BuildConfig.SOURCE_URL}", fontSize = 12.sp, color = MaterialTheme_color.accent)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color(0x1F4DB4FF))
                        .clickable { vm.checkUpdate(manual = true) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("检查更新", fontSize = 12.sp, color = MaterialTheme_color.accent)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    s.update?.let { "发现 " + it.tag } ?: ("当前 " + BuildConfig.VERSION_NAME),
                    fontSize = 12.sp,
                    color = if (s.update != null) Color(0xFF2FD08A) else MaterialTheme_color.ink2,
                )
            }
            s.update?.let { up ->
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x142FD08A))
                        .padding(12.dp),
                ) {
                    Text(
                        "新版本 " + up.tag + " 可用",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2FD08A),
                    )
                    if (up.notes.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            up.notes.take(240),
                            fontSize = 11.5.sp,
                            color = MaterialTheme_color.ink2,
                            maxLines = 6,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ctx2 = androidx.compose.ui.platform.LocalContext.current
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color(0xFF2FD08A))
                                .clickable {
                                    runCatching {
                                        ctx2.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(up.assetUrl ?: up.pageUrl),
                                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) { Text("去下载", fontSize = 12.sp, color = Color(0xFF04121F)) }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "也可以在浏览器打开 " + BuildConfig.SOURCE_URL + "/releases",
                            fontSize = 10.5.sp,
                            color = MaterialTheme_color.ink3,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { vm.disconnect() },
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme_color.card2),
        ) { Text("断开连接", color = MaterialTheme_color.ink) }

        Spacer(Modifier.height(10.dp))
        Text(
            "「改资料 / 注销账号」这一类操作官方不提供接口，请回到网页端做。",
            fontSize = 12.sp,
            color = MaterialTheme_color.ink2,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------- 地图榜 ----------------

@Composable
fun BoardPage(
    maps: List<BoardMap>,
    onBack: () -> Unit,
    vm: MainViewModel,
    showBack: Boolean = true,
) {
    var sel by remember { mutableStateOf<MyMap?>(null) }
    var busy by remember { mutableStateOf(false) }

    /** 先拿「我在这张图上的分」，再打开打分框；`off` 的图不给这位玩家看，也就不打开。 */
    fun open(world: String) {
        if (busy) return
        busy = true
        vm.openMap(world) { r ->
            busy = false
            when {
                r == null -> Unit // 出错时 openMap 已经把原因放进提示条
                r.off -> vm.showHint("这张地图不给你打分")
                else -> sel = r
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                Text(
                    "返回",
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onBack() }
                        .background(MaterialTheme_color.card2)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme_color.ink,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text("地图榜", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme_color.ink)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "有人评分就会进榜；点一张地图可以看我给它的分、给它打分。",
            fontSize = 12.sp,
            color = MaterialTheme_color.ink2,
        )
        Spacer(Modifier.height(10.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            items(maps) { m ->
                Surface2(onClick = { open(m.world) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "#${m.rank ?: "-"}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme_color.accent,
                            modifier = Modifier.width(34.dp),
                        )
                        Cover(m.img, Modifier.size(52.dp), 10.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                m.name ?: "未命名地图",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme_color.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${m.author ?: "未知作者"} · ${m.weight ?: "-"} · ${m.raters ?: 0} 人评",
                                fontSize = 11.sp,
                                color = MaterialTheme_color.ink2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            m.avg?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme_color.accent,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (sel != null) {
        RateDialog(
            map = sel!!,
            busy = busy,
            onDismiss = { sel = null },
            onSubmit = { score, text ->
                busy = true
                vm.rate(sel!!.world, score, text) { msg ->
                    busy = false
                    sel = null
                    vm.showHint(msg)
                }
            },
        )
    }
}

@Composable
private fun Surface2(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme_color.card,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme_color.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(12.dp),
        ) { content() }
    }
}

// ---------------- 打分框 ----------------

@Composable
fun RateDialog(map: MyMap, busy: Boolean, onDismiss: () -> Unit, onSubmit: (Int, String?) -> Unit) {
    var score by remember { mutableIntStateOf(map.mine ?: 8) }
    var text by remember { mutableStateOf(map.words ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme_color.card,
        title = { Text(map.name ?: "这张地图", fontSize = 16.sp, color = MaterialTheme_color.ink) },
        text = {
            // 2026-10-24 起谁都能打分：不再看 can / need（已弃用，恒为 true / null）。
            Column {
                Text("你给这张图打几分？", fontSize = 13.sp, color = MaterialTheme_color.ink2)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { n ->
                        ScorePill(n, score == n) { score = n }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (6..10).forEach { n ->
                        ScorePill(n, score == n) { score = n }
                    }
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(30) },
                    placeholder = { Text("写一句短评（可选，30 字内）", fontSize = 12.sp, color = MaterialTheme_color.ink2) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = MaterialTheme_color.ink),
                )
                Spacer(Modifier.height(4.dp))
                Text("${text.length} / 30", fontSize = 11.sp, color = MaterialTheme_color.ink2)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = { onSubmit(score, if (text.isBlank()) "" else text) },
            ) { Text("提交", color = MaterialTheme_color.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = MaterialTheme_color.ink2) } },
    )
}

@Composable
private fun ScorePill(n: Int, on: Boolean, onPick: () -> Unit) {
    Text(
        "$n",
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable { onPick() }
            .background(if (on) MaterialTheme_color.accent else MaterialTheme_color.card2)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        fontSize = 13.sp,
        color = if (on) Color(0xFF04121F) else MaterialTheme_color.ink,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
    )
}

// ---------------- 私聊 ----------------

@Composable
fun ChatPage(
    vm: MainViewModel,
    peer: Long,
    photoUrl: String? = null,
    photoGrid: android.graphics.Bitmap? = null,
    onBack: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var resp by remember { mutableStateOf<ChatResp?>(null) }
    var loading by remember { mutableStateOf(true) }
    var bump by remember { mutableIntStateOf(0) }

    LaunchedEffect(peer, bump) {
        loading = true
        resp = vm.openChat(peer)
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "返回",
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onBack() }
                    .background(MaterialTheme_color.card2)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 12.sp,
                color = MaterialTheme_color.ink,
            )
            Spacer(Modifier.width(10.dp))
            PhotoAvatar(
                photoUrl = photoUrl,
                grid = photoGrid,
                name = resp?.peer?.vrc ?: resp?.peer?.name,
                size = 34.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    resp?.peer?.vrc ?: resp?.peer?.name ?: "私聊",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme_color.ink,
                    maxLines = 1,
                )
                val star = resp?.peer?.name
                if (star != null && resp?.peer?.vrc != null) {
                    Text(star, fontSize = 10.sp, color = MaterialTheme_color.ink3)
                }
            }
        }

        Box(Modifier.weight(1f)) {
            if (loading) {
                CircularProgressIndicator(
                    color = MaterialTheme_color.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(26.dp)
                        .align(Alignment.Center),
                )
            } else if (resp == null) {
                Text(
                    "这里打不开。只有互相连上的人才能私聊。",
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme_color.ink2,
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(resp!!.msgs) { m -> Bubble(m) }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(resp?.max ?: 200) },
                placeholder = { Text("说点什么", fontSize = 13.sp, color = MaterialTheme_color.ink2) },
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = MaterialTheme_color.ink),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val t = input
                    input = ""
                    vm.send(peer, t) { err ->
                        if (err.isEmpty()) {
                            bump++
                        } else {
                            vm.showHint(err)
                            // 没发出去（比如没过检查）：把草稿放回去，免得玩家重打。
                            if (input.isEmpty()) input = t
                        }
                    }
                },
                enabled = input.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme_color.accent),
            ) { Text("发送", color = Color(0xFF04121F), fontSize = 13.sp) }
        }
    }
}

@Composable
private fun Bubble(m: Msg) {
    val mine = m.mine
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .fillMaxWidth(0.78f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (mine) MaterialTheme_color.accent else MaterialTheme_color.card)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                m.text ?: if (m.sticker != null) "[表情]" else "",
                fontSize = 14.sp,
                color = if (mine) Color(0xFF04121F) else MaterialTheme_color.ink,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                clockShort(m.at) + when (m.check) {
                    "wait" -> " · 检查中"
                    "hidden" -> " · 没通过"
                    else -> ""
                },
                fontSize = 10.sp,
                color = if (mine) Color(0x9904121F) else MaterialTheme_color.ink2,
            )
        }
    }
}

/** 发一条星友圈：官方只说「只能发文字」，所以这里只有一个多行输入框。 */
@Composable
fun WritePostCard(vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    BoxCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "发一条",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme_color.ink,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (open) "收起" else "写点什么",
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { open = !open }
                    .background(Color(0x1F4DB4FF))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 12.sp,
                color = MaterialTheme_color.accent,
            )
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 500) text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("写点给别的星看的话", fontSize = 13.sp) },
                minLines = 3,
                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme_color.ink),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme_color.accent,
                    unfocusedBorderColor = MaterialTheme_color.line,
                    cursorColor = MaterialTheme_color.accent,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${text.length} / 500", fontSize = 11.sp, color = MaterialTheme_color.ink3)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        busy = true
                        vm.writePost(text.trim()) { msg ->
                            busy = false
                            if (msg == "ok") {
                                text = ""
                                open = false
                            } else {
                                vm.showHint(msg)
                            }
                        }
                    },
                    enabled = !busy && text.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme_color.accent,
                        contentColor = Color(0xFF04121F),
                    ),
                ) { Text(if (busy) "发送中…" else "发出去", fontSize = 13.sp) }
            }
        }
    }
}
