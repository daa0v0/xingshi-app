package app.shixing.client.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shixing.client.BuildConfig
import app.shixing.client.data.Groups
import app.shixing.client.data.Msg
import app.shixing.client.data.StarCard
import app.shixing.client.data.Thread
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio

/**
 * 五个页面，和网页的底栏一致：今天 / 连向我 / 星轨 / 星友圈 / 我的。
 * （地图榜和「我打过的地图」收在「我的」里，避免底栏超过五个。）
 */
@Composable
fun Home(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableStateOf(0) }
    var boardOpen by rememberSaveable { mutableStateOf(false) }
    var chatOpen by rememberSaveable { mutableStateOf<Long?>(null) }

    state.hint?.let { h ->
        LaunchedEffect(h) {
            snack.showSnackbar(h)
            vm.clearHint()
        }
    }
    state.error?.let { e ->
        LaunchedEffect(e) {
            snack.showSnackbar(e)
        }
    }

    Scaffold(
        containerColor = MaterialTheme_color.bg,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (state.session && !boardOpen && chatOpen == null) {
                BottomBar(tab) { picked ->
                    tab = picked
                    // 切页就重新拉一次：审核/授权状态可能刚变，别把上一次的报错留在页面上。
                    vm.refreshTab(picked)
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .background(MaterialTheme_color.bg)
                .statusBarsPaddingIfNeeded(),
        ) {
            if (state.loading) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme_color.accent,
                    trackColor = Color.Transparent,
                )
            } else {
                Spacer(Modifier.height(2.dp))
            }

            val chatPeer = chatOpen
            when {
                !state.session -> ConnectPage(vm, state)
                boardOpen -> BoardPage(
                    maps = state.boards,
                    onBack = { boardOpen = false },
                    vm = vm,
                )
                chatPeer != null -> ChatPage(
                vm,
                chatPeer,
                photoUrl = state.cardPhotos[chatPeer]?.url,
                photoGrid = state.cardPhotos[chatPeer]?.grid,
            ) { chatOpen = null }
                tab == 0 -> TodayPage(vm, state)
                tab == 1 -> InboxPage(vm, state, onOpenChat = { n -> chatOpen = n })
                tab == 2 -> PostsScreen(vm, state)
                else -> MePage(vm, state, onOpenBoard = { boardOpen = true }, onOpenChat = { n -> chatOpen = n }, photoUrl = state.card?.myStarId()?.let { state.cardPhotos[it]?.url }, photoGrid = state.card?.myStarId()?.let { state.cardPhotos[it]?.grid })
            }
        }
    }
}

/** 一个够用的配色容器；不引 MaterialTheme 的 colorScheme，避免和主题里那份重复。 */
object MaterialTheme_color {
    val bg = Color(0xFF05060A)
    val card = Color(0xFF11141F)
    val card2 = Color(0xFF1A1F2E)
    val ink = Color(0xFFF3F4F8)
    val ink2 = Color(0xFFAAB0C4)
    val ink3 = Color(0xFF6B7189)
    val accent = Color(0xFF4DB4FF)
    val accentDeep = Color(0xFF0397FE)
    val accent2 = Color(0xFFD69BFF)
    val line = Color(0x21FFFFFF)
    val warn = Color(0xFFFF7A7A)
}

private fun Modifier.statusBarsPaddingIfNeeded(): Modifier = this

// ---------------- 底栏 ----------------

@Composable
private fun BottomBar(current: Int, onPick: (Int) -> Unit) {
    val items: List<Pair<String, ImageVector>> = listOf(
        "今天" to Icons.Filled.DateRange,
        "连向我" to Icons.Filled.Email,
        "星友圈" to Icons.Filled.ThumbUp,
        "我的" to Icons.Filled.Person,
    )
    Column(Modifier.background(Color(0xFF070910))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item ->
                val label = item.first
                val icon = item.second
                val on = i == current
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPick(i) }
                        .background(if (on) Color(0x1F4DB4FF) else Color.Transparent)
                        .padding(horizontal = 13.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (on) MaterialTheme_color.accent else MaterialTheme_color.ink3,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        label,
                        fontSize = 10.5.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) MaterialTheme_color.accent else MaterialTheme_color.ink2,
                    )
                }
            }
        }
    }
}

// ---------------- 连接页 ----------------

@Composable
private fun ConnectPage(vm: MainViewModel, s: UiState) {
    val ctx = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    val dev = s.device

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Box(
            Modifier
                .size(66.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF7FD3FF), Color(0xFF4DB4FF), Color(0xFFD69BFF)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = Color(0xFF05060A),
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("拾星", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme_color.accent)
        Spacer(Modifier.height(4.dp))
        Text("只用官方开放接口的第三方客户端（非官方）", fontSize = 13.sp, color = MaterialTheme_color.ink2)
        Spacer(Modifier.height(18.dp))

        BoxCard {
            Text("一步连上", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme_color.ink)
            Spacer(Modifier.height(8.dp))

            if (dev == null) {
                Text(
                    "点下面的按钮会生成一个连接码；然后在网页上打开「我的资料 → 第三方客户端 → 连接新客户端」，把码填进去点「同意」。",
                    fontSize = 13.sp,
                    color = MaterialTheme_color.ink2,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { vm.startDevice() },
                    Modifier.fillMaxWidth(),
                    enabled = !s.loading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme_color.accent),
                ) { Text("生成连接码", color = Color(0xFF04121F), fontWeight = FontWeight.SemiBold) }
            } else {
                Text("在网页上输入这串码", fontSize = 13.sp, color = MaterialTheme_color.ink2)
                Spacer(Modifier.height(6.dp))
                Text(
                    dev.userCode,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme_color.accent,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "还剩 ${left(s.deviceLeft)}；同意后会自动跳进去",
                    fontSize = 13.sp,
                    color = MaterialTheme_color.ink2,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        val url = dev.verificationUriComplete
                            ?: (dev.verificationUri ?: "https://vrcc.cc/match/?connect=").let {
                                if (it.contains("?")) "$it=${dev.userCode.replace("-", "")}" else it
                            }
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        }
                    },
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme_color.accent),
                ) { Text("在浏览器里打开同意页", color = Color(0xFF04121F)) }
                Spacer(Modifier.height(9.dp))
                Text(
                    "取消",
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.cancelDevice() }
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    color = MaterialTheme_color.ink2,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        BoxCard {
            Text("已经有 8 位连接码？", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme_color.ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "网页「我的资料 → 在 VRCX 里提醒」生成的那种码，也可以直接填在这里。",
                fontSize = 13.sp,
                color = MaterialTheme_color.ink2,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() }.take(8) },
                placeholder = { Text("8 位连接码", color = MaterialTheme_color.ink2) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 17.sp, color = MaterialTheme_color.ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { vm.connect(code) },
                Modifier.fillMaxWidth(),
                enabled = code.length == 8 && !s.loading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme_color.accent,
                    disabledContainerColor = MaterialTheme_color.card2,
                    disabledContentColor = MaterialTheme_color.ink2,
                ),
            ) { Text("连接") }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "每个客户端（包括这个）都有自己的授权，不会顶掉 VRCX 脚本或其他客户端。玩家可以在「我的资料 → 第三方客户端」里看到并单独断开。",
            fontSize = 12.sp,
            color = MaterialTheme_color.ink2,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "本应用不是官方应用，名字、图标都不使用「星遇同频 / VRCC / VRChat / 官方 / 管理员」。",
            fontSize = 12.sp,
            color = MaterialTheme_color.ink2,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun left(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}

// ---------------- 今天 ----------------

@Composable
private fun TodayPage(vm: MainViewModel, s: UiState) {
    val t = s.today
    val cards = t?.cards.orEmpty()
    var subTab by remember { mutableStateOf(0) }
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { cards.size })
    val scope = rememberCoroutineScope()
    val round = t?.round
    val left = ((round?.max ?: 0) - (round?.used ?: 0)).coerceAtLeast(0)
    var sayFor by remember { mutableStateOf<app.shixing.client.data.StarCard?>(null) }

    // 当前这张卡的照片：先试 v1 的 photo（要 photos 权限），不行再用网页接口那份
    LaunchedEffect(pager.currentPage, cards.size) {
        cards.getOrNull(pager.currentPage)?.let { c ->
            if (s.cardPhotos[c.n] == null) vm.loadPhoto(c.n)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "今天",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme_color.ink,
            )
            Spacer(Modifier.weight(1f))
            if (cards.isNotEmpty()) {
                Text(
                    if (left > 0) "这一轮还能连 $left 颗" else "这一轮连满了",
                    fontSize = 12.sp,
                    color = MaterialTheme_color.ink2,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${pager.currentPage + 1} / ${cards.size}",
                    fontSize = 12.sp,
                    color = MaterialTheme_color.ink3,
                )
            }
        }
        PageError(vm, s, "today")

        if (cards.isEmpty()) {
            Empty("这一轮还没有星路过。")
            return@Column
        }

        androidx.compose.foundation.pager.HorizontalPager(
            state = pager,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            pageSpacing = 10.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
        ) { i ->
            val c = cards[i]
            val alreadyLinked = c.linked || c.mutual || s.linked.contains(c.n)
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    FlippableStarCard(
                        face = c.toFace().copy(overlap = overlapOf(s.card?.scores?.asMap(), c.scores)),
                        photoUrl = s.cardPhotos[c.n]?.url,
                photoGrid = s.cardPhotos[c.n]?.grid,
                        modifier = Modifier.fillMaxHeight(),
                        intro = c.intro,
                        about = c.about.map { it.q to it.a },
                        footer = null,
                    )
                }
                // 卡下面那行小字：网页写「新芽 · 刚来不久的新星」
                val note = if (c.sprout) "新芽 · 刚来不久的新星" else (c.lead ?: "")
                if (!note.isNullOrBlank()) {
                    Row(
                        Modifier.padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            note,
                            fontSize = 12.sp,
                            color = Color(0xFF5FE0B4),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (alreadyLinked) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "已连线 · 等 TA 连回来",
                            fontSize = 12.sp,
                            color = Color(0xFF5FE0B4),
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (alreadyLinked) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme_color.card2)
                                .clickable { sayFor = c }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Text("捎一句话", fontSize = 13.sp, color = MaterialTheme_color.ink)
                        }
                    } else {
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x1A5FE0B4))
                                .border(1.dp, Color(0x665FE0B4), RoundedCornerShape(16.dp))
                                .clickable { vm.link(c.n, null, null) { m -> vm.showHint(m) } }
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("＋", fontSize = 15.sp, color = Color(0xFF5FE0B4))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "连一条线",
                                fontSize = 14.sp,
                                color = Color(0xFF5FE0B4),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme_color.card2)
                            .clickable {
                                scope.launch { if (i < cards.size - 1) pager.animateScrollToPage(i + 1) }
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text("下一颗 →", fontSize = 14.sp, color = MaterialTheme_color.ink, fontWeight = FontWeight.Medium)
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme_color.card2)
                            .clickable { vm.showHint("更多操作稍后再补；不想再看到这颗可以到网页端点举报。") }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        Text("⋯", fontSize = 14.sp, color = MaterialTheme_color.ink2)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    sayFor?.let { c ->
        SayDialog(
            name = c.name ?: "这颗星",
            onDismiss = { sayFor = null },
            onSend = { txt ->
                vm.say(c.n, txt) { m -> vm.showHint(m) }
                sayFor = null
            },
        )
    }
}

/** 捎一句话：TA 还没连回来时，附一句话让 TA 在「连向我」里看到。 */
@Composable
private fun SayDialog(name: String, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme_color.card)
                .padding(18.dp),
        ) {
            Text("给 $name 捎一句话", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme_color.ink)
            Spacer(Modifier.height(4.dp))
            Text("TA 在「连向我」里能看到。", fontSize = 12.sp, color = MaterialTheme_color.ink3)
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 40) text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("比如：我也是刚玩，要不要一起逛", fontSize = 13.sp) },
                minLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            Text("${text.length} / 40", fontSize = 11.sp, color = MaterialTheme_color.ink3)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme_color.card2)
                        .clickable { onDismiss() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("算了", fontSize = 13.sp, color = MaterialTheme_color.ink2) }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x1A5FE0B4))
                        .clickable { if (text.isNotBlank()) onSend(text) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("捎过去", fontSize = 13.sp, color = Color(0xFF5FE0B4), fontWeight = FontWeight.Medium) }
            }
        }
    }
}


// ---------------- 连向我 ----------------

@Composable
private fun InboxPage(
    vm: MainViewModel,
    s: UiState,
    onOpenChat: (Long) -> Unit,
    onSeeAll: () -> Unit = {},
) {
    val i = s.inbox
    val counts = i?.counts.orEmpty()
    fun n(key: String): Int? = counts[key]?.let {
        runCatching { it.jsonPrimitive.intOrNull }.getOrNull()
    }
    val sub = buildList {
        n("in")?.let { add("连向我 $it") }
        n("out")?.let { add("我连出 $it") }
        n("mutual")?.let { add("互相连上 $it") }
    }.joinToString(" · ")
    val cards = i?.cards.orEmpty()
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { cards.size })
    var subTab by remember { mutableStateOf(0) }
    var expandedThreads by remember { mutableStateOf(false) }
    var previewStar by remember { mutableStateOf<app.shixing.client.data.StarCard?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 顶：标题 + 进度
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "连向我",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme_color.ink,
            )
            Spacer(Modifier.weight(1f))
            if (cards.isNotEmpty()) {
                Text(
                    "${pager.currentPage + 1} / ${cards.size}",
                    fontSize = 12.sp,
                    color = MaterialTheme_color.ink2,
                )
            }
        }
        Text(
            sub.ifBlank { "连向我的星" },
            fontSize = 11.5.sp,
            color = MaterialTheme_color.ink2,
            modifier = Modifier.padding(horizontal = 18.dp),
        )

        // 分段：连向我 / 星轨（星轨不再是底栏选项，收进这里）
        Row(
            Modifier
                .padding(start = 18.dp, top = 10.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color(0x14FFFFFF))
                .padding(3.dp),
        ) {
            listOf("连向我", "星轨").forEachIndexed { idx, label ->
                val on = subTab == idx
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (on) Color(0x1F4DB4FF) else Color.Transparent)
                        .clickable { subTab = idx }
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                ) {
                    Text(
                        label,
                        fontSize = 12.sp,
                        color = if (on) MaterialTheme_color.accent else MaterialTheme_color.ink2,
                    )
                }
            }
        }

        if (subTab == 1) {
            CrossingsPage(vm, s, showTitle = false)
            return@Column
        }

        // 上：和我连上的人（照片头像 + VRC 名 + 最后一句话）
        val threads = s.threads.ifEmpty { emptyList() }

        // 查看全部：摊成网格，点头像开大卡
        if (expandedThreads) {
            ThreadsGrid(
                threads = threads,
                cards = cards,
                photos = s.cardPhotos,
                onClose = { expandedThreads = false },
                onPick = { previewStar = it },
            )
            previewStar?.let { c ->
                CardPreviewDialog(
                    card = c,
                    photoUrl = s.cardPhotos[c.n]?.url,
                    photoGrid = s.cardPhotos[c.n]?.grid,
                    onDismiss = { previewStar = null },
                )
            }
            return@Column
        }
        var showAllThreads by remember { mutableStateOf(false) }
        LaunchedEffect(threads.map { it.peer?.n }) {
            threads.forEach { t -> t.peer?.n?.let { if (s.cardPhotos[it] == null) vm.loadPhoto(it) } }
        }
        if (threads.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                threads.take(8).forEach { t ->
                    val star = t.peer?.n ?: return@forEach
                    Column(
                        Modifier
                            .width(64.dp)
                            .clickable { onOpenChat(star) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PhotoAvatar(
                            photoUrl = s.cardPhotos[star]?.url,
                            grid = s.cardPhotos[star]?.grid,
                            name = t.peer?.vrc ?: t.peer?.name,
                            size = 44.dp,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            t.peer?.vrc ?: t.peer?.name ?: "一颗星",
                            fontSize = 10.5.sp,
                            color = MaterialTheme_color.ink,
                            maxLines = 1,
                        )
                        Text(
                            (t.last?.text ?: "").take(6),
                            fontSize = 9.sp,
                            color = MaterialTheme_color.ink3,
                            maxLines = 1,
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color(0xFF141826))
                        .clickable { expandedThreads = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        "查看全部",
                        fontSize = 11.sp,
                        color = MaterialTheme_color.ink2,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }

        PageError(vm, s, "inbox")
        if (cards.isEmpty()) {
            Empty("还没有星连向你。")
            return
        }

        // 下：一张卡一屏，左右滑动
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    (
                        (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 36) * 0.86f / 0.75f
                        ).dp + 34.dp,
                ),
        ) { page ->
            val c = cards[page]
            LaunchedEffect(c.n) { if (s.cardPhotos[c.n] == null) vm.loadPhoto(c.n) }
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 状态行：来自X星群 · 和你重合 N%
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "● 来自${c.groupName ?: "星群"}",
                        fontSize = 11.sp,
                        color = Color(Groups.color(c.group)),
                    )
                    Spacer(Modifier.width(6.dp))
                    val ov = overlapOf(s.card?.scores?.asMap(), c.scores)
                    if (ov != null) {
                        Text("和你重合 $ov%", fontSize = 11.sp, color = MaterialTheme_color.ink2)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (c.mutual) "互相连上了" else if (c.linked) "已连线，等 TA 连回来" else "还没连上",
                        fontSize = 11.5.sp,
                        color = if (c.mutual) Color(0xFF2FD08A) else MaterialTheme_color.ink2,
                    )
                }
                Spacer(Modifier.height(4.dp))
                androidx.compose.runtime.key(c.n) {
                FlippableStarCard(
                    face = c.toFace().copy(overlap = overlapOf(s.card?.scores?.asMap(), c.scores)),
                    photoUrl = s.cardPhotos[c.n]?.url,
                    photoGrid = s.cardPhotos[c.n]?.grid,
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .height(
                            (
                                (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 36) * 0.86f / 0.75f
                                ).dp,
                        ),
                    intro = c.intro,
                    about = c.about.map { it.q to it.a },
                    footer = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when {
                                    c.mutual -> "互相连上"
                                    c.linked -> "已连线"
                                    else -> "未连"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme_color.ink2,
                            )
                            val fr = c.friends ?: 0
                            if (fr > 0) {
                                Spacer(Modifier.width(8.dp))
                                Text("共同星友 $fr", fontSize = 11.sp, color = MaterialTheme_color.ink2)
                            }
                            Spacer(Modifier.weight(1f))
                            if (c.mutual) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(Color(0x332FD08A))
                                        .clickable { onOpenChat(c.n) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) { Text("聊天", fontSize = 11.sp, color = Color(0xFF2FD08A)) }
                            } else {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(Color(0x332FD08A))
                                        .clickable { vm.link(c.n, null, null) { m -> vm.showHint(m) } }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) { Text("连一条线", fontSize = 11.sp, color = Color(0xFF2FD08A)) }
                            }
                        }
                    },
                )
                }
            }
        }
    }
}

// ---------------- 共用零件 ----------------

@Composable
fun StarCardItem(
    c: StarCard,
    photoUrl: String? = null,
    photoGrid: android.graphics.Bitmap? = null,
    onLink: (() -> Unit)? = null,
    onChat: (() -> Unit)? = null,
    myScores: Map<String, Int>? = null,
) {
    FlippableStarCard(
        face = c.toFace().copy(overlap = overlapOf(myScores, c.scores)),
        photoUrl = photoUrl,
        photoGrid = photoGrid,
        modifier = Modifier.fillMaxWidth(),
        intro = c.intro,
        about = c.about.map { it.q to it.a },
        footer = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.mutual) {
                    Pill("互相连上", Color(0x332FD08A), Color(0xFF2FD08A))
                    c.vrc?.let {
                        Spacer(Modifier.width(8.dp))
                        Text("VRChat：$it", fontSize = 11.sp, color = MaterialTheme_color.ink2)
                    }
                } else if (c.linked) {
                    Pill("已连上", Color(0x33FFB547), Color(0xFFFFB547))
                } else {
                    Pill("未连", Color(0x1FFFFFFF), MaterialTheme_color.ink2)
                }
                c.friends?.let {
                    Spacer(Modifier.width(8.dp))
                    Text("共同星友 $it", fontSize = 11.sp, color = MaterialTheme_color.ink2)
                }
                Spacer(Modifier.weight(1f))
                if (onChat != null && c.mutual) {
                    Text(
                        "聊天",
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x332FD08A))
                            .clickable { onChat() }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        fontSize = 11.sp,
                        color = Color(0xFF2FD08A),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (onLink != null && !c.linked) {
                    Text(
                        "连上",
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onLink() }
                            .background(MaterialTheme_color.accent)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        color = Color(0xFF04121F),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    )
}

@Composable
fun BoxCard(modifier: Modifier = Modifier, tint: Color? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val top = (tint ?: Color(0xFF161A28)).copy(alpha = 0.15f)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(top, Color(0xFF090A11)),
                    start = androidx.compose.ui.geometry.Offset.Zero,
                    end = androidx.compose.ui.geometry.Offset(900f, 1400f),
                ),
            )
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(22.dp))
            .then(modifier),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 14.dp), content = content)
    }
}

@Composable
fun ScreenTitle(title: String, sub: String? = null) {
    Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme_color.ink)
    if (sub != null) {
        Spacer(Modifier.height(5.dp))
        Text(sub, fontSize = 13.sp, color = MaterialTheme_color.ink2)
    }
}

@Composable
fun Empty(text: String) {
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        textAlign = TextAlign.Center,
        fontSize = 13.sp,
        color = MaterialTheme_color.ink2,
    )
}

@Composable
fun ErrorBar(text: String) {
    Surface(
        color = Color(0x22FF7A7A),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FF7A7A)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            fontSize = 12.sp,
            color = Color(0xFFFFB4B4),
        )
    }
}

@Composable
fun PageError(vm: MainViewModel, s: UiState, key: String) {
    s.pageErrors[key]?.let {
        Spacer(Modifier.height(8.dp))
        ErrorBar(it)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, size: Int = 11) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        fontSize = size.sp,
        color = fg,
    )
}

@Composable
fun GroupPill(group: String?) {
    if (group == null) return
    val label = Groups.label(group)
    if (label.isEmpty() || label == "未知") return
    val c = Color(Groups.color(group))
    Pill(label, c.copy(alpha = 0.16f), c)
}

fun clock(at: Long?): String {
    if (at == null) return ""
    return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(at))
}

fun clockShort(at: Long?): String {
    if (at == null) return ""
    return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(at))
}

@Composable
fun Centered(loading: Boolean, empty: String, emptyWhen: Boolean, content: @Composable () -> Unit) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme_color.accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        }
        emptyWhen -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(empty, fontSize = 13.sp, color = MaterialTheme_color.ink2)
        }
        else -> content()
    }
}

/** 顶部错误条（点击错误可以把错误清掉）。 */
@Composable
fun Notice(text: String?) {
    if (text == null) return
    ErrorBar(text)
}


/** CardScores 是四个具名字段，转成 map 好算重合度。 */
fun app.shixing.client.data.CardScores.asMap(): Map<String, Int> = pairs().toMap()

/** 和我的成分有多像：逐项取 min/max，加权后取百分比。 */
fun overlapOf(mine: Map<String, Int>?, theirs: Map<String, Int>?): Int? {
    if (mine == null || theirs == null) return null
    val keys = listOf("G", "W", "Z", "L")
    var same = 0.0
    var all = 0.0
    keys.forEach { k ->
        val a = (mine[k] ?: 0).toDouble()
        val b = (theirs[k] ?: 0).toDouble()
        same += kotlin.math.min(a, b)
        all += kotlin.math.max(a, b)
    }
    if (all <= 0.0) return null
    return (same / all * 100).toInt().coerceIn(0, 100)
}
