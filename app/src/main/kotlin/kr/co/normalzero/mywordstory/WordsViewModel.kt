package kr.co.normalzero.mywordstory

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class WordsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var serverUrl by mutableStateOf(prefs.getString("serverUrl", "").orEmpty())
        private set
    var token by mutableStateOf(prefs.getString("token", "").orEmpty())
        private set
    var showSettings by mutableStateOf(serverUrl.isBlank() || token.isBlank())
    var words by mutableStateOf<List<Word>>(emptyList())
        private set
    var selected by mutableStateOf<Word?>(null)
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var api = WordApi(serverUrl, token)

    init {
        if (!showSettings) refresh()
    }

    fun saveSettings(url: String, newToken: String) {
        prefs.edit { putString("serverUrl", url.trim()).putString("token", newToken.trim()) }
        serverUrl = url.trim()
        token = newToken.trim()
        api = WordApi(serverUrl, token)
        showSettings = false
        refresh()
    }

    fun refresh() = run { words = api.listWords() }

    fun add(headword: String) = run {
        val word = api.addWord(headword)
        words = listOf(word) + words.filter { it.id != word.id }
        selected = word
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            message = null
            try {
                block()
            } catch (e: ApiException) {
                message = e.message
            } finally {
                busy = false
            }
        }
    }
}
