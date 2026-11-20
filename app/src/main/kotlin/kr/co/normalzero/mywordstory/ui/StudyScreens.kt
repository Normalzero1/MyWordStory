package kr.co.normalzero.mywordstory.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kr.co.normalzero.mywordstory.AppViewModel
import kr.co.normalzero.mywordstory.Word
import kr.co.normalzero.mywordstory.study.Answered
import kr.co.normalzero.mywordstory.study.Direction
import kr.co.normalzero.mywordstory.study.LoopSession
import kr.co.normalzero.mywordstory.study.NewWordSession
import kr.co.normalzero.mywordstory.study.NewWordSession.Stage
import kr.co.normalzero.mywordstory.study.ReviewSession

@Composable
fun NewWordsFlow(vm: AppViewModel, s: NewWordSession) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick // recompose on session changes
    BackHandler { vm.closeOverlay() }
    val act = { block: () -> Unit -> block(); vm.changed() }
    Page {
        when (s.stage) {
            Stage.CARDS -> {
                val word = s.currentCard ?: return@Page
                StudyHeader("새 단어 ${s.cardIndex + 1} / ${s.words.size}", (s.cardIndex + 1f) / s.words.size, vm::closeOverlay)
                CardView(word, Modifier.weight(1f).verticalScroll(rememberScrollState()))
                Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("이미 앎", { vm.dropCard(s, "known") }, Modifier.weight(1f))
                        SecondaryButton("필요 없음", { vm.dropCard(s, "excluded") }, Modifier.weight(1f))
                    }
                    PrimaryButton("다음 카드", { act { s.nextCard() } })
                }
            }
            Stage.PRACTICE -> PracticeCard(
                label = "연습 · 외움 ${resolved(s.practice!!, s.words.size)} / ${s.words.size}",
                loop = s.practice!!, total = s.words.size, direction = s.direction,
                onClose = vm::closeOverlay,
                onPass = { act { s.passPractice() } },
                onAnswer = { right -> act { s.answerPractice(right) } },
            )
            Stage.PRACTICE_DONE -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Column(
                    Modifier.background(Palette.surface, RoundedCornerShape(24.dp)).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${s.words.size}개 연습을 마쳤어요", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    PrimaryButton("4지선다로", { act { s.startQuiz() } })
                    SecondaryButton("연습 한 번 더 · ${s.words.size}개 다시 섞기", { act { s.practiceAgain() } }, Modifier.fillMaxWidth())
                }
            }
            Stage.QUIZ, Stage.CLOZE -> ChoiceQuestion(vm, s)
            Stage.SUMMARY -> Summary(vm, s)
        }
    }
}

private fun resolved(loop: LoopSession<*>, total: Int) = total - loop.remainingThisRound - loop.comingBackCount

/** Practice and review card: tap to flip, then 틀렸음 / 외웠음. */
@Composable
private fun ColumnScope.PracticeCard(
    label: String,
    loop: LoopSession<Word>,
    total: Int,
    direction: Direction,
    onClose: () -> Unit,
    onPass: (() -> Unit)?,
    onAnswer: (Boolean) -> Unit,
    flipped: Boolean? = null,
    onFlip: (() -> Unit)? = null,
    note: String? = null,
) {
    val word = loop.current ?: return
    // Practice keeps its own flip state per card; review passes it in.
    var localFlip by rememberSaveable(word.id, loop.round, loop.remainingThisRound) { mutableStateOf(false) }
    val isFlipped = flipped ?: localFlip
    StudyHeader(label, resolved(loop, total).toFloat() / total, onClose) {
        if (onPass != null) OutlinedButton(onClick = onPass, shape = RoundedCornerShape(999.dp)) { Text("연습 통과 ›", fontSize = 13.sp) }
    }
    Column(
        Modifier.weight(1f).fillMaxWidth().background(Palette.surface, RoundedCornerShape(24.dp))
            .clickable(enabled = !isFlipped) { if (onFlip != null) onFlip() else localFlip = true }
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val c = word.content
        if (!isFlipped) {
            Chip(if (direction == Direction.WORD_TO_MEANING) "단어 → 뜻" else "뜻 → 단어", Palette.accent, Palette.accentSoft)
            if (direction == Direction.WORD_TO_MEANING) {
                Text(c.headword, fontSize = 38.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            } else {
                Text(c.meaningsKo.first(), fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("이 뜻의 영어 단어는?", color = Palette.muted)
            }
            Text("카드를 누르면 뒤집혀요", color = Palette.muted, fontSize = 13.sp)
        } else {
            Text(c.meaningsKo.joinToString(", "), color = Palette.muted, textAlign = TextAlign.Center)
            Text(c.headword, fontSize = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("${c.ipa} · ${c.partOfSpeech}", color = Palette.muted, fontSize = 14.sp)
            c.examples.firstOrNull()?.let {
                Text(it.en, fontSize = 16.sp, textAlign = TextAlign.Center)
                Text(it.ko, color = Palette.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
    }
    Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isFlipped) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("틀렸음", { onAnswer(false) }, Modifier.weight(1f).height(64.dp), color = Palette.bad)
                Button(onClick = { onAnswer(true) }, modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("외웠음", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Text(
                "이번 바퀴 남은 카드 ${loop.remainingThisRound} · 다시 나올 카드 ${loop.comingBackCount}",
                color = Palette.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
        }
        note?.let { Text(it, color = Palette.muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
    }
}

@Composable
private fun ColumnScope.ChoiceQuestion(vm: AppViewModel, s: NewWordSession) {
    val isQuiz = s.stage == Stage.QUIZ
    val loop = (if (isQuiz) s.quiz else s.cloze) ?: return
    val answered: Answered? = s.answered
    val word = answered?.word ?: s.currentQuestion() ?: return
    val choices = answered?.choices ?: s.choices ?: return
    val total = if (isQuiz) s.words.size else s.words.count { it.content.cloze != null }
    StudyHeader(
        "${if (isQuiz) "4지선다" else "빈칸 채우기"} · 맞힘 ${resolved(loop, total)} / $total",
        resolved(loop, total).toFloat() / total, vm::closeOverlay,
    )
    Column(Modifier.weight(1f).padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isQuiz) {
            Text("알맞은 뜻을 고르세요", color = Palette.muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(word.content.headword, fontSize = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        } else {
            Text("빈칸에 들어갈 단어를 고르세요", color = Palette.muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Column(Modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(20.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(word.content.cloze!!.sentence, fontSize = 21.sp, lineHeight = 32.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        choices.options.forEachIndexed { i, option ->
            val (border, bg, tag) = when {
                answered == null -> Triple(Palette.line, Palette.surface, null)
                i == choices.answerIndex -> Triple(Palette.ok, Palette.okSoft, "정답")
                i == answered.picked -> Triple(Palette.bad, Palette.badSoft, "내가 고름")
                else -> Triple(Palette.line, Palette.surface, null)
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    .border(BorderStroke(if (border == Palette.line) 1.dp else 2.dp, border), RoundedCornerShape(14.dp))
                    .background(bg, RoundedCornerShape(14.dp))
                    .clickable(enabled = answered == null) { s.answer(i); vm.changed() }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", color = Palette.muted, modifier = Modifier.padding(end = 12.dp))
                Text(option, fontSize = 16.sp, modifier = Modifier.weight(1f))
                tag?.let { Text(it, color = border, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (answered != null) {
            Text(
                if (answered.correct) "정답이에요." else "아쉬워요. 이 문제는 마지막에 다시 나와요.",
                color = if (answered.correct) Palette.ok else Palette.bad, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center,
            )
        }
    }
    Box(Modifier.padding(vertical = 16.dp)) {
        PrimaryButton("다음 문제", { s.next(); vm.changed() }, enabled = answered != null)
    }
}

@Composable
private fun ColumnScope.Summary(vm: AppViewModel, s: NewWordSession) {
    val results = s.results()
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("새 단어 ${results.size}개 끝!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "한 번에 맞히면 0점, 두 번째에 맞히면 1점, 세 번째면 2점. 점수가 높은 단어일수록 빨리 다시 나와요.",
            color = Palette.muted, fontSize = 14.sp, lineHeight = 21.sp,
        )
        Column(Modifier.background(Palette.surface, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 4.dp)) {
            results.sortedByDescending { it.total }.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.word.content.headword, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("연습 ${r.practice} · 4지선다 ${r.quiz} · 빈칸 ${r.cloze}", color = Palette.muted, fontSize = 12.sp)
                    }
                    val (fg, bg) = when {
                        r.total <= 1 -> Palette.ok to Palette.okSoft
                        r.total <= 3 -> Palette.business to Palette.businessSoft
                        else -> Palette.bad to Palette.badSoft
                    }
                    Box(Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("${r.total}", color = fg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text("0~1점 가장 늦게 · 2~3점 중간 · 4점 이상 가장 빨리 복습해요.", color = Palette.muted, fontSize = 12.sp)
    }
    Box(Modifier.padding(vertical = 16.dp)) {
        PrimaryButton(if (vm.busy) "저장 중…" else "저장하고 복습하러 가기", { vm.finishNewWords(s) }, enabled = !vm.busy)
    }
}

@Composable
fun ReviewFlow(vm: AppViewModel, s: ReviewSession) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    BackHandler { vm.closeOverlay() }
    Page {
        if (s.loop.isDone) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("복습 ${s.total}개를 마쳤어요", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(if (vm.busy) "저장 중…" else "저장하기", { vm.finishReview(s) }, enabled = !vm.busy)
            }
        } else {
            PracticeCard(
                label = "복습 ${resolved(s.loop, s.total)} / ${s.total}",
                loop = s.loop, total = s.total, direction = s.direction,
                onClose = vm::closeOverlay, onPass = null,
                onAnswer = { s.answer(it); vm.changed() },
                flipped = s.flipped, onFlip = { s.reveal(); vm.changed() },
                note = "틀린 단어는 끝에 다시 나와요. 다시 나왔을 때 결과는 채점에 넣지 않아요.",
            )
        }
    }
}

@Composable
fun MessageScreen(title: String, body: String, button: String, onClick: () -> Unit) {
    Page {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(body, color = Palette.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
            PrimaryButton(button, onClick)
        }
    }
}

