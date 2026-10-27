package app.shixing.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.shixing.client.data.Groups
import app.shixing.client.data.Post
import coil.compose.AsyncImage

/**
 * 星友圈：上面「动态 / 榜单」两个子页，动态页里带筛选和刷动态。
 * 榜单页直接复用地图榜，点一张图就能打分。
 */
@Composable
fun PostsScreen(vm: MainViewModel, s: UiState) {
    var sub by remember { mutableStateOf(0) }
    var showWrite by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(s.posts.map { it.author?.n }) {
        s.posts.forEach { p -> p.author?.n?.let { n -> if (s.cardPhotos[n] == null) vm.loadPhoto(n) } }
    }
    Column(Modifier.fillMaxSize()) {
        // 顶：动态 / 榜单 / 发动态
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 14.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("动态", "榜单").forEachIndexed { idx, label ->
                val on = sub == idx
                Column(
                    Modifier
                        .clickable { sub = idx }
                        .padding(end = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        fontSize = 20.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) MaterialTheme_color.ink else MaterialTheme_color.ink2,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .height(2.dp)
                            .width(if (on) 34.dp else 0.dp)
                            .background(MaterialTheme_color.accent),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { showWrite = !showWrite },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    Icons.Filled.Add,
                    contentDescription = "发动态",
                    tint = MaterialTheme_color.ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (sub == 1) {
            BoardPage(
                maps = s.boards,
                onBack = {},
                vm = vm,
                showBack = false,
            )
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // 筛选
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0x0FFFFFFF))
                    .padding(3.dp),
            ) {
                // 接口的 f：all 全部、friend 星友的、mine 自己的，由服务器筛。
                listOf("all" to "全部", "friend" to "好友", "mine" to "我的").forEach { (key, label) ->
                    val on = s.postsFilter == key
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (on) Color(0x1FFFFFFF) else Color.Transparent)
                            .clickable { if (!on) vm.refreshPosts(key) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            fontSize = 12.sp,
                            color = if (on) MaterialTheme_color.ink else MaterialTheme_color.ink2,
                        )
                    }
                }
            }

            if (showWrite) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.padding(horizontal = 18.dp)) { WritePostCard(vm) }
            }

            Spacer(Modifier.height(12.dp))
            PageError(vm, s, "posts")

            val list = s.posts
            if (list.isEmpty()) {
                Empty("还没有动态。")
            } else {
                list.forEach { p ->
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                        PostCard(vm, p, s.cardPhotos)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PostCard(vm: MainViewModel, p: Post, photos: Map<Long, app.shixing.client.data.CardPhoto> = emptyMap()) {
    val au = p.author
    val mutual = au?.mutual == true
    // 连上的用照片 + VRChat 名，没连上的用模糊格 + 代号
    val who = (if (mutual) au?.vrc ?: au?.name else au?.name)
        ?: if (p.kind == "official") "星遇同频" else "一颗星"
    BoxCard(tint = Color(Groups.color(p.author?.group))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhotoAvatar(
                photoUrl = if (mutual) au?.let { photos[it.n]?.url } else null,
                grid = au?.let { photos[it.n]?.grid },
                name = who,
                size = 32.dp,
            )
            Spacer(Modifier.width(9.dp))
            Text(who, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme_color.ink)
            p.author?.group?.let {
                Spacer(Modifier.width(6.dp))
                GroupPill(it)
            }
            Spacer(Modifier.weight(1f))
            Text(clock(p.at), fontSize = 11.sp, color = MaterialTheme_color.ink2)
        }
        p.text?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 13.sp, color = MaterialTheme_color.ink, lineHeight = 20.sp)
        }
        p.imgUrl?.let { url ->
            Spacer(Modifier.height(10.dp))
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme_color.card2),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "👍 ${p.lit}",
                fontSize = 12.sp,
                color = if (p.litMe) MaterialTheme_color.accent else MaterialTheme_color.ink2,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { vm.toggleLight(p.id) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("评论 ${p.cmts}", fontSize = 12.sp, color = MaterialTheme_color.ink2)
        }
    }
}
