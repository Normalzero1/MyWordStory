package kr.co.normalzero.mywordstory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.normalzero.mywordstory.Word

/** The full word card (new-word screen and word detail). */
@Composable
fun CardView(word: Word, modifier: Modifier = Modifier) {
    val c = word.content
    Column(
        modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(20.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (c.tag == "business") Chip("IT 비즈니스", Palette.business, Palette.businessSoft)
            else Chip("일상", Palette.ok, Palette.okSoft)
            c.cefr?.let { Chip(it, Palette.accent, Palette.accentSoft) }
            if (word.source == "manual") Chip("직접 추가", Palette.muted, Palette.ground)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.headword, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("${c.ipa} · ${c.partOfSpeech}", color = Palette.muted, fontSize = 14.sp)
        }
        Section("뜻") { c.meaningsKo.forEachIndexed { i, m -> Text("${i + 1}. $m", fontSize = 16.sp) } }
        Section("영영 정의") { Text(c.definitionEn, fontSize = 14.sp) }
        Section("현지에서 쓰는 법") { Text(c.usageNote, fontSize = 14.sp, lineHeight = 22.sp) }
        if (c.synonyms.isNotEmpty()) Section("동의어") {
            c.synonyms.forEach { Text("${it.word} — ${it.nuance}", fontSize = 14.sp, lineHeight = 22.sp) }
        }
        if (c.collocations.isNotEmpty()) Section("자주 같이 쓰는 표현") { c.collocations.forEach { Text("• $it", fontSize = 14.sp) } }
        Section("예문") {
            c.examples.forEach {
                Text(tagLabel(it.context), color = Palette.muted, fontSize = 12.sp)
                Text(it.en, fontSize = 16.sp)
                Text(it.ko, color = Palette.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(title)
        content()
    }
}
