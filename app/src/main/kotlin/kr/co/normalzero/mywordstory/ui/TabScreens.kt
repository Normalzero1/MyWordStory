package kr.co.normalzero.mywordstory.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.Instant
import kr.co.normalzero.mywordstory.AppViewModel
import kr.co.normalzero.mywordstory.Overlay
import kr.co.normalzero.mywordstory.Word

@Composable
fun HomeScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("오늘의 학습", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (vm.startLevel == null) {
            Column(Modifier.background(Palette.surface, RoundedCornerShape(20.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("먼저 수준 테스트를 해 주세요", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("레벨마다 단어 10개씩 아는 단어만 누르면, 모르는 단어가 30%를 넘는 레벨부터 시작해요. 약 5분 걸려요.", color = Palette.muted, lineHeight = 21.sp)
                if (vm.untaggedWords > 0) Text("서버가 아직 단어 ${vm.untaggedWords}개의 레벨을 매기는 중이에요. 다 끝나야 모든 레벨이 나와요.", color = Palette.business, fontSize = 13.sp)
                PrimaryButton("수준 테스트 시작", vm::openLevelTest, enabled = !vm.busy)
            }
        } else {
            Column(Modifier.background(Palette.surface, RoundedCornerShape(20.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row {
                    Count("새 단어", vm.newToday, Modifier.weight(1f))
                    Count("복습", vm.dueCount, Modifier.weight(1f))
                }
                vm.reducedFrom?.let {
                    Text("복습이 밀려서 오늘 새 단어를 ${it}개에서 줄였어요.", color = Palette.muted, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().background(Palette.ground, RoundedCornerShape(10.dp)).padding(10.dp))
                }
                PrimaryButton("학습 시작", vm::startStudy, enabled = !vm.busy && (vm.newToday > 0 || vm.dueCount > 0))
                Text("새 단어부터 하고, 이어서 복습해요", color = Palette.muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            if (vm.manualFirst.isNotEmpty()) {
                Text("오늘 먼저 나오는 직접 추가한 단어", fontWeight = FontWeight.Bold)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.manualFirst.forEach {
                        Text(it, modifier = Modifier.border(1.dp, Palette.line, RoundedCornerShape(999.dp)).background(Palette.surface, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
            }
        }
        ErrorText(vm.message)
        OutlinedButton(onClick = { vm.openAddWords() }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("단어 직접 추가") }
        TextButton(onClick = vm::refreshHome) { Text("새로고침") }
    }
}

@Composable
private fun Count(label: String, value: Int, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Palette.muted, fontSize = 13.sp)
        Text("$value", fontSize = 44.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LevelTestScreen(vm: AppViewModel) {
    BackHandler { vm.closeOverlay() }
    val test = vm.levelTest
    val levels = vm.levelNames
    Page {
        if (test == null || levels.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text("아직 레벨이 매겨진 단어가 없어요", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("서버가 처음 단어 레벨을 매기는 데 몇십 분 걸릴 수 있어요. 잠시 후 다시 열어 주세요.", color = Palette.muted, modifier = Modifier.padding(vertical = 12.dp))
                PrimaryButton("닫기", vm::closeOverlay)
            }
            return@Page
        }
        val level = levels[vm.levelIndex]
        val words = test.levels.getValue(level)
        Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("처음 한 번 · 약 5분", color = Palette.muted, fontSize = 13.sp)
            Text("수준 테스트", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("뜻을 바로 아는 단어만 눌러 주세요. 모르는 단어가 30%를 넘는 레벨부터 학습을 시작해요.", lineHeight = 21.sp)
            Text("$level · ${vm.levelIndex + 1} / ${levels.size}", fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(top = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            words.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { w ->
                        val on = w in vm.knownInLevel
                        Box(
                            Modifier.weight(1f).height(52.dp)
                                .border(if (on) 2.dp else 1.dp, if (on) Palette.accent else Palette.line, RoundedCornerShape(12.dp))
                                .background(if (on) Palette.accentSoft else Palette.surface, RoundedCornerShape(12.dp))
                                .clickable { vm.toggleKnown(w) },
                            contentAlignment = Alignment.Center,
                        ) { Text(w, fontSize = 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(vm.levelHint(), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            PrimaryButton("다음", vm::submitLevel, enabled = !vm.busy)
        }
    }
}

@Composable
fun WordsScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.refreshWords() }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    val now = Instant.now()
    val shown = vm.words.filter { w ->
        val matches = query.isBlank() || w.headword.contains(query, true) || w.content.meaningsKo.any { it.contains(query) }
        val inFilter = when (filter) {
            "daily" -> w.content.tag != "business" && w.status !in setOf("known", "excluded")
            "business" -> w.content.tag == "business" && w.status !in setOf("known", "excluded")
            "dropped" -> w.status in setOf("known", "excluded")
            else -> true
        }
        matches && inFilter
    }
    Column(Modifier.fillMaxSize().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("단어", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("  ${vm.words.size}개", color = Palette.muted, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.openAddWords() }) { Text("+ 추가", fontWeight = FontWeight.Bold) }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("단어나 뜻으로 검색") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "전체", "daily" to "일상", "business" to "IT 비즈니스", "dropped" to "뺀 단어").forEach { (key, label) ->
                FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
            }
        }
        ErrorText(vm.message)
        LazyColumn {
            items(shown, key = { it.id }) { w ->
                Row(Modifier.fillMaxWidth().clickable { vm.overlay = Overlay.Card(w) }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.content.headword, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(w.content.meaningsKo.firstOrNull().orEmpty(), color = Palette.muted, fontSize = 13.sp)
                    }
                    StatusBadge(w, vm.dueTimes[w.id], now)
                }
                HorizontalDivider(color = Palette.line)
            }
        }
    }
}

@Composable
private fun StatusBadge(w: Word, due: Instant?, now: Instant) {
    when {
        w.status == "known" -> Chip("이미 앎", Palette.muted, Palette.ground)
        w.status == "excluded" -> Chip("필요 없음", Palette.muted, Palette.ground)
        due == null -> Chip("새 단어", Palette.muted, Palette.ground)
        !due.isAfter(now) -> Chip("오늘 복습", Palette.business, Palette.businessSoft)
        else -> {
            val days = Duration.between(now, due).toDays()
            Chip(if (days < 1) "내일" else "${days + 1}일 뒤", Palette.accent, Palette.accentSoft)
        }
    }
}

@Composable
fun CardScreen(vm: AppViewModel, word: Word) {
    BackHandler { vm.overlay = null }
    Page {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.overlay = null }) { Text("‹ 목록") }
        }
        CardView(word, Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp))
    }
}

@Composable
fun AddWordsScreen(vm: AppViewModel) {
    BackHandler { vm.closeOverlay() }
    val count = vm.addInput.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().size
    Page {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::closeOverlay) { Text("‹") }
            Text("단어 추가", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("한 줄에 하나씩 적어 주세요", fontWeight = FontWeight.Bold)
            OutlinedTextField(vm.addInput, { vm.addInput = it }, Modifier.fillMaxWidth().height(150.dp))
            Text("다른 앱에서 글자를 길게 눌러 선택하고 공유 → MyWordStory로 보내도 여기로 들어와요.", color = Palette.muted, fontSize = 13.sp)
            PrimaryButton(if (count > 0) "${count}개 추가" else "추가", vm::addWords, enabled = count > 0)
            if (vm.addResults.isNotEmpty()) {
                Text("방금 추가한 단어", fontWeight = FontWeight.Bold)
                vm.addResults.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Text(r.headword, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(r.state, fontSize = 13.sp, color = when (r.ok) { true -> Palette.ok; false -> Palette.bad; null -> Palette.muted })
                    }
                    HorizontalDivider(color = Palette.line)
                }
                Text("직접 추가한 단어는 다음 학습에서 먼저 나와요.", color = Palette.muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var url by rememberSaveable { mutableStateOf(vm.serverUrl) }
    var token by rememberSaveable { mutableStateOf(vm.token) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 28.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("설정", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Group("학습") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("하루 새 단어", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.changeDailyCap(-5) }) { Text("−") }
                Text("${vm.dailyCap}", fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                OutlinedButton(onClick = { vm.changeDailyCap(5) }) { Text("+") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("복습이 밀리면 새 단어 줄이기", modifier = Modifier.weight(1f))
                Switch(checked = vm.autoReduce, onCheckedChange = vm::changeAutoReduce)
            }
            Row(Modifier.clickable(enabled = vm.configured) { vm.openLevelTest() }.padding(vertical = 8.dp)) {
                Text("수준 테스트 다시 하기", modifier = Modifier.weight(1f))
                Text(vm.startLevel?.let { "현재 ${it}부터 ›" } ?: "아직 안 함 ›", color = Palette.muted)
            }
        }
        Group("서버") {
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("서버 주소") }, singleLine = true)
            Text("예: https://word.normalzero.co.kr · 같은 Wi-Fi의 PC라면 http://192.168.x.x:8080", color = Palette.muted, fontSize = 12.sp)
            OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("토큰 (서버의 API_TOKEN)") }, singleLine = true)
            PrimaryButton("저장", { vm.saveServer(url, token) }, enabled = url.isNotBlank() && token.isNotBlank())
        }
        ErrorText(vm.message)
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Palette.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}
