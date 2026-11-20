package kr.co.normalzero.mywordstory

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kr.co.normalzero.mywordstory.ui.AddWordsScreen
import kr.co.normalzero.mywordstory.ui.AppColors
import kr.co.normalzero.mywordstory.ui.CardScreen
import kr.co.normalzero.mywordstory.ui.HomeScreen
import kr.co.normalzero.mywordstory.ui.LevelTestScreen
import kr.co.normalzero.mywordstory.ui.MessageScreen
import kr.co.normalzero.mywordstory.ui.NewWordsFlow
import kr.co.normalzero.mywordstory.ui.Page
import kr.co.normalzero.mywordstory.ui.ReviewFlow
import kr.co.normalzero.mywordstory.ui.SettingsScreen
import kr.co.normalzero.mywordstory.ui.WordsScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent { MaterialTheme(colorScheme = AppColors) { App(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Text shared from another app ("공유 → MyWordStory") opens the add-words screen. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.openAddWords(it) }
        }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    when (val o = vm.overlay) {
        is Overlay.LevelTest -> LevelTestScreen(vm)
        is Overlay.NewWords -> NewWordsFlow(vm, o.session)
        is Overlay.Review -> ReviewFlow(vm, o.session)
        is Overlay.ReviewDone -> MessageScreen("오늘 학습을 마쳤어요", "내일 또 만나요.", "홈으로", vm::closeOverlay)
        is Overlay.AddWords -> AddWordsScreen(vm)
        is Overlay.Card -> CardScreen(vm, o.word)
        null -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                Page {
                    when (vm.tab) {
                        Tab.STUDY -> HomeScreen(vm)
                        Tab.WORDS -> WordsScreen(vm)
                        Tab.SETTINGS -> SettingsScreen(vm)
                    }
                }
            }
            NavigationBar {
                listOf(Tab.STUDY to "학습", Tab.WORDS to "단어", Tab.SETTINGS to "설정").forEach { (tab, label) ->
                    NavigationBarItem(
                        selected = vm.tab == tab,
                        onClick = { vm.tab = tab },
                        icon = {},
                        label = { Text(label) },
                        modifier = Modifier.padding(),
                    )
                }
            }
        }
    }
}
