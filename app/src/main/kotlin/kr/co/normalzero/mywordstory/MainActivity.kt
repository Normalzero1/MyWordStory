package kr.co.normalzero.mywordstory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { App(viewModel()) } }
    }
}

@Composable
private fun App(vm: WordsViewModel) {
    val word = vm.selected
    when {
        vm.showSettings -> SettingsScreen(vm)
        word != null -> {
            BackHandler { vm.selected = null }
            CardScreen(word) { vm.selected = null }
        }
        else -> ListScreen(vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(title: String, action: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, actions = { action() }) }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize()) { content() }
    }
}

@Composable
private fun SettingsScreen(vm: WordsViewModel) {
    var url by rememberSaveable { mutableStateOf(vm.serverUrl) }
    var token by rememberSaveable { mutableStateOf(vm.token) }
    Screen("서버 설정") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("서버 주소") }, singleLine = true)
            Text(
                "예: https://word.normalzero.co.kr\n개발 중 에뮬레이터에서는 http://10.0.2.2:8080",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("토큰 (서버의 API_TOKEN)") }, singleLine = true)
            Button(onClick = { vm.saveSettings(url, token) }, enabled = url.isNotBlank() && token.isNotBlank()) {
                Text("저장")
            }
        }
    }
}

@Composable
private fun ListScreen(vm: WordsViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    Screen("MyWordStory", action = { TextButton(onClick = { vm.showSettings = true }) { Text("설정") } }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), label = { Text("단어 또는 표현") }, singleLine = true)
            Button(
                onClick = { vm.add(input.trim()); input = "" },
                enabled = input.isNotBlank() && !vm.busy,
            ) { Text("추가") }
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        vm.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        if (vm.words.isEmpty() && !vm.busy) {
            Text("아직 단어가 없습니다. 위에 단어를 입력하고 추가를 누르세요.", Modifier.padding(top = 24.dp))
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(vm.words, key = { it.id }) { word ->
                Column(Modifier.fillMaxWidth().clickable { vm.selected = word }.padding(vertical = 12.dp)) {
                    Text(word.content.headword, style = MaterialTheme.typography.titleMedium)
                    Text(word.content.meaningsKo.firstOrNull().orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CardScreen(word: Word, onBack: () -> Unit) {
    val c = word.content
    Screen(c.headword, action = { TextButton(onClick = onBack) { Text("목록") } }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${c.ipa} · ${c.partOfSpeech} · ${if (c.tag == "business") "IT 비즈니스" else "일상"}")
            Section("뜻") { c.meaningsKo.forEachIndexed { i, m -> Text("${i + 1}. $m") } }
            Section("영영 정의") { Text(c.definitionEn) }
            Section("현지에서 쓰는 법") { Text(c.usageNote) }
            Section("동의어") { c.synonyms.forEach { Text("${it.word} — ${it.nuance}") } }
            Section("자주 같이 쓰는 표현") { c.collocations.forEach { Text("• $it") } }
            Section("예문") {
                c.examples.forEach {
                    Text(if (it.context == "business") "비즈니스" else "일상", style = MaterialTheme.typography.labelMedium)
                    Text(it.en, style = MaterialTheme.typography.bodyLarge)
                    Text(it.ko, Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
