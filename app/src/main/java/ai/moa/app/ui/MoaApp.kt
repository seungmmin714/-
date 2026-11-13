package ai.moa.app.ui

import ai.moa.app.MoaController
import ai.moa.app.storage.SavedFamilyCard
import ai.moa.core.FamilyCard
import ai.moa.core.QuestionCatalog
import ai.moa.core.Safety
import ai.moa.core.Stage
import ai.moa.core.Theme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF162744)
private val Blue = Color(0xFF1658DD)
private val Muted = Color(0xFF5B6C86)
private val Line = Color(0xFFDCE6F4)
private val PaleBlue = Color(0xFFEEF5FF)
private val Shape = RoundedCornerShape(26.dp)

@Composable
fun MoaApp(controller: MoaController, onMicrophone: () -> Unit) {
    val c = controller
    val state = c.state
    var dialog by remember { mutableStateOf<String?>(null) }
    var selectedSaved by remember { mutableStateOf<SavedFamilyCard?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val contentScroll = rememberScrollState()
    val allowed = state.safety == Safety.ALLOWED
    LaunchedEffect(state.stage, state.choices.size) { contentScroll.scrollTo(0) }
    LaunchedEffect(state.stage, state.safety) {
        if (!allowed || state.stage == Stage.PAUSED) {
            dialog = null
            selectedSaved = null
            deleteId = null
        }
    }
    BackHandler(state.stage != Stage.HOME) {
        if (state.stage == Stage.PAUSED) c.end() else c.pause()
    }
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Blue, onPrimary = Color.White, secondary = Ink,
            background = Color(0xFFF6F8FC), surface = Color.White, onSurface = Ink, onBackground = Ink,
            outline = Line, surfaceVariant = PaleBlue, onSurfaceVariant = Muted),
        typography = Typography(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 30.sp),
        ),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFFCF7), Color(0xFFF1F6FE))))
                .safeDrawingPadding().imePadding(),
        ) {
            val compact = maxWidth < 650.dp
            val wide = maxWidth >= 850.dp
            Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 18.dp else 36.dp, vertical = if (wide) 12.dp else 16.dp)) {
                AppHeader(c, compact, onLibrary = { dialog = "library" }, onSettings = { dialog = "settings" }, onEnd = { dialog = "end" })
                Spacer(Modifier.height(if (wide) 12.dp else 18.dp))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(contentScroll), verticalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 18.dp)) {
                    if (c.demoMode) DemoBar(c)
                    when (state.stage) {
                        Stage.HOME -> Welcome(c)
                        Stage.QUESTION -> QuestionScreen(c, onMicrophone)
                        Stage.FACT -> FactScreen(c)
                        Stage.STORY -> StoryScreen(c)
                        Stage.ENDING -> EndingScreen(c)
                        Stage.FAMILY -> FamilyScreen(c)
                        Stage.PAUSED -> PausedScreen(c)
                    }
                    if (c.notice.isNotBlank() && allowed && state.stage != Stage.PAUSED) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFEAF0FA)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(c.notice, Modifier.weight(1f), color = Muted, fontSize = 14.sp)
                                TextButton(onClick = c::stopSpeech) { Text("소리 멈춤") }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (c.demoMode) "시연 환경 · 실제 차량 제어 없음 · 저장은 보호자가 선택한 카드만"
                    else "휴게·충전 중 함께하는 질문 여행 · 주행 제한 시 중단 · 보호자가 직접 재개",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (allowed && state.stage != Stage.PAUSED) {
            when (dialog) {
                "settings" -> SettingsDialog(c, onDismiss = { dialog = null })
                "library" -> LibraryDialog(c.savedCards, c.libraryError, onRetry = c::reloadSaved, onDismiss = { dialog = null }, onSelect = { selectedSaved = it },
                    onDelete = { deleteId = it }, onClear = { dialog = "clear" })
                "clear" -> ConfirmDialog("보관함을 비울까요?",
                    "이 기기의 대화 카드와 보관함 파일을 모두 지웁니다. 삭제 후에는 복구할 수 없어요.", "모두 지우기",
                    onConfirm = { c.clearSaved(); dialog = "library" }, onDismiss = { dialog = "library" })
                "end" -> ConfirmDialog("오늘의 여행을 마칠까요?", "저장하지 않은 질문과 선택은 사라져요. 따로 저장한 대화 카드는 보관함에 남아요.", "마치기",
                    onConfirm = { c.end(); dialog = null }, onDismiss = { dialog = null })
            }
            selectedSaved?.let { saved ->
                AlertDialog(onDismissRequest = { selectedSaved = null }, title = { Text("가족 대화 카드") },
                    text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) { FamilyContent(saved.card) } },
                    confirmButton = { TextButton(onClick = { selectedSaved = null }) { Text("닫기") } })
            }
            deleteId?.let { id ->
                ConfirmDialog("이 카드를 지울까요?", "삭제한 대화 카드는 다시 불러올 수 없어요.", "지우기",
                    onConfirm = { c.deleteSaved(id); deleteId = null; if (selectedSaved?.id == id) selectedSaved = null }, onDismiss = { deleteId = null })
            }
        }
    }
}

@Composable
private fun AppHeader(c: MoaController, compact: Boolean, onLibrary: () -> Unit, onSettings: () -> Unit, onEnd: () -> Unit) {
    val allowed = c.state.safety == Safety.ALLOWED && c.state.stage != Stage.PAUSED
    val brand: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("moa", color = Blue, fontSize = 42.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
            Column {
                Text("모아", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("질문에서 시작해, 가족 대화로", color = Muted, fontSize = 11.sp)
            }
        }
    }
    val actions: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onLibrary, enabled = allowed) { Text(if (c.libraryError != null) "보관함 확인 필요" else "보관함 ${c.savedCards.size}") }
            TextButton(onClick = onSettings, enabled = allowed) { Text("보호자 설정") }
            if (c.state.stage != Stage.HOME) TextButton(onClick = { if (allowed) onEnd() else c.end() }) { Text("종료") }
        }
    }
    if (compact) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        brand()
        actions()
        SafetyBadge(c)
    }
    else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        brand(); Spacer(Modifier.width(24.dp)); SafetyBadge(c); Spacer(Modifier.weight(1f)); actions()
    }
}

@Composable
private fun SafetyBadge(c: MoaController) {
    val text = when (c.state.safety) {
        Safety.ALLOWED -> if (c.demoMode) "시연 · 이용 허용" else "이용 허용 상태"
        Safety.BLOCKED -> if (c.demoMode) "시연 · 주행 제한" else "주행 제한"
        Safety.UNKNOWN -> "차량 상태 미확인"
    }
    Badge(text, if (c.state.safety == Safety.ALLOWED) Color(0xFFE6F3EC) else Color(0xFFFFEEDD),
        if (c.state.safety == Safety.ALLOWED) Color(0xFF286549) else Color(0xFF815326))
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DemoBar(c: MoaController) {
    val controls: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DemoButton("허용", c.state.safety == Safety.ALLOWED) { c.simulate(Safety.ALLOWED) }
            DemoButton("주행 제한", c.state.safety == Safety.BLOCKED) { c.simulate(Safety.BLOCKED) }
            DemoButton("연결 끊김", c.state.safety == Safety.UNKNOWN) { c.simulate(Safety.UNKNOWN) }
            if (c.state.stage == Stage.HOME) TextButton(onClick = c::stopDemo) { Text("시연 종료", fontSize = 12.sp) }
        }
    }
    Surface(color = Color(0xFFFFF2DC), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFF2DFBA))) {
        if (isWideLayout()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("개발용 시연 · 차량 상태를 연결하지 않았어요", color = Color(0xFF80623B), fontSize = 12.sp, modifier = Modifier.weight(1f))
                controls()
            }
        } else Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("개발용 시연 · 차량 상태를 연결하지 않았어요", color = Color(0xFF80623B), fontSize = 12.sp)
            controls()
        }
    }
}

@Composable
private fun DemoButton(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) })
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun Welcome(c: MoaController) {
    SplitPanel(
        left = {
            SceneArt(Theme.SEA, "cloud", Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 390.dp).aspectRatio(1.25f).clip(Shape))
        },
        right = {
            Badge("우리 가족의 질문 여행")
            Text("작은 궁금증 하나,\n함께 떠나는 이야기.", style = MaterialTheme.typography.headlineLarge, color = Ink)
            Text("오는 길에 스쳐 간 질문을 꺼내 볼까요?\n짧게 알아보고, 상상하고, 가족과 나눠요.", style = MaterialTheme.typography.bodyLarge, color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge("10가지 질문", Color(0xFFF1F4FA), Muted)
                Badge("두 번의 선택", Color(0xFFF1F4FA), Muted)
                Badge("가족 대화", Color(0xFFF1F4FA), Muted)
            }
            if (c.state.safety == Safety.ALLOWED) {
                PrimaryButton("보호자와 함께 시작하기  →") { c.act { start() } }
                Text("보호자가 함께하며, 허용된 정차 상태에서만 이용해요.", color = Muted, fontSize = 13.sp)
            } else {
                Text(if (c.state.safety == Safety.BLOCKED) "지금은 주행 제한 상태예요. 허용된 정차 상태에서 시작할 수 있어요." else "차량의 이용 허용 상태를 확인한 뒤 시작할 수 있어요.", color = Muted, fontSize = 15.sp)
                if (c.canUseDemo && !c.demoMode) {
                    PrimaryButton("시연 모드 시작") { c.startDemo() }
                    Text("개발용 빌드에서 화면 흐름을 체험해요. 실제 차량 상태나 조명을 제어하지 않아요.", color = Muted, fontSize = 13.sp)
                }
            }
            HorizontalDivider(color = Line)
            Text("질문과 선택은 여행이 끝나면 사라져요.\n대화 카드는 보호자가 저장을 누른 경우에만 남아요.", color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
        },
    )
}

@Composable
private fun QuestionScreen(c: MoaController, onMicrophone: () -> Unit) {
    val question = c.questionDraft
    var search by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val submit = { focus.clearFocus(); c.act { submitQuestion(question) } }
    ScreenTitle("01  질문을 꺼내요", "오는 길에 무엇이 궁금했어?", "그림 카드를 고르거나, 나만의 질문을 들려줘.")
    MoaCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = question, onValueChange = c::editQuestion,
                modifier = Modifier.weight(1f), label = { Text("궁금한 것을 적어 주세요") }, placeholder = { Text("예: 구름은 왜 움직여?") },
                shape = RoundedCornerShape(18.dp), maxLines = 3, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }))
            Button(onClick = { submit() }, enabled = question.isNotBlank(), modifier = Modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) { Text("질문 확인") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onMicrophone, modifier = Modifier.heightIn(min = 48.dp)) { Text("말로 질문") }
            Text("기기 내 음성 입력 · 원본 음성 저장 안 함", fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
        }
        if (c.state.clarificationCount > 0) {
            Surface(color = PaleBlue, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.state.message, color = Ink, fontSize = 16.sp, lineHeight = 25.sp)
                    Text("확인 ${c.state.clarificationCount} / 2 · 설명 카드 밖의 질문은 답을 지어내지 않아요.", color = Muted, fontSize = 12.sp)
                    TextButton(onClick = { c.act { handOffQuestion(question) } }, enabled = question.isNotBlank()) { Text("가족과 함께 알아볼 질문으로 남기기  →") }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("오늘의 질문 카드", fontWeight = FontWeight.Bold, fontSize = 21.sp, modifier = Modifier.weight(1f))
        OutlinedTextField(search, { search = it.take(30) }, modifier = Modifier.widthIn(max = 220.dp).weight(1f), placeholder = { Text("카드 검색") }, singleLine = true, shape = RoundedCornerShape(16.dp))
    }
    val cards = QuestionCatalog.cards.filter { search.isBlank() || it.subject.contains(search.trim()) || it.question.contains(search.trim()) }
    if (cards.isEmpty()) Text("맞는 카드가 없어요. 검색어를 바꾸거나 위에 질문을 적어 주세요.", color = Muted)
    BoxWithConstraints {
        val columns = if (maxWidth >= 1050.dp) 5 else if (maxWidth >= 700.dp) 4 else if (maxWidth >= 480.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { card ->
                        Surface(onClick = { focus.clearFocus(); c.act { selectCard(card.id) } }, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(22.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                SceneArt(themeFor(card.id), card.id, Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(14.dp)))
                                Text(card.subject, fontSize = 12.sp, color = Blue, fontWeight = FontWeight.Bold)
                                Text(card.question, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink, lineHeight = 23.sp, modifier = Modifier.heightIn(min = 46.dp))
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FactScreen(c: MoaController) {
    val card = c.state.card ?: return
    ScreenTitle("01  알아보는 시간", card.question, "짧게 알아보고, 상상으로 이어 가요.")
    SplitPanel(left = {
        SceneArt(themeFor(card.id), card.id, Modifier.fillMaxWidth().height(if (isWideLayout()) 250.dp else 320.dp).clip(Shape))
        TextButton(onClick = { c.act { backToQuestions() } }) { Text("← 다른 질문 고르기") }
    }, right = {
        Badge("사실 설명")
        Text(card.fact, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp, lineHeight = 38.sp), color = Ink)
        OutlinedButton(onClick = { c.speak(card.fact) }, modifier = Modifier.heightIn(min = 50.dp)) { Text("설명 읽어 주기") }
        HorizontalDivider(color = Line)
        Text("이제 상상 이야기를 만들어볼까?", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("다음 이야기는 우리가 함께 만드는 상상이야.", color = Muted, fontSize = 15.sp)
        PrimaryButton("${card.subject} 이야기 시작하기  →") { c.act { startStory() } }
    })
}

@Composable
private fun StoryScreen(c: MoaController) {
    val state = c.state
    ScreenTitle("02  상상으로 이어가요", if (state.choices.isEmpty()) "어디로 여행을 떠날까?" else "그곳에서 무엇을 해볼까?", "네 선택으로 이어지는 짧은 이야기 · 선택 ${state.choices.size + 1} / 2")
    SplitPanel(left = {
        Box {
            SceneArt(state.theme, state.card?.id ?: "cloud", Modifier.fillMaxWidth().height(if (isWideLayout()) 230.dp else 330.dp).clip(Shape))
            Box(Modifier.padding(18.dp)) { Badge("상상 이야기", Color.White, Blue) }
        }
        ThemePicker(state.theme) { c.act { setTheme(it) } }
        OutlinedButton(onClick = c::playThemeSound, modifier = Modifier.heightIn(min = 48.dp)) { Text("테마 소리 듣기") }
        Text("화면 테마 · 차량 조명 미연결", color = Muted, fontSize = 12.sp)
    }, right = {
        Text(state.storyText, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, lineHeight = 35.sp), color = Ink)
        OutlinedButton(onClick = { c.speak(state.storyText) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("이야기 읽어 주기") }
        state.availableChoices.forEachIndexed { index, choice ->
            if (index == 0) PrimaryButton("${choice.label}  →") { c.act { choose(choice.id) } }
            else OutlinedButton(onClick = { c.act { choose(choice.id) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFB9CEF0))) {
                Text("${choice.label}  →", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
    })
}

@Composable
private fun EndingScreen(c: MoaController) {
    ScreenTitle("02  오늘의 여행 끝", "작은 여행을 함께 마쳤어.", "상상 이야기를 마무리하고, 가족의 이야기로 돌아가요.")
    SplitPanel(left = {
        SceneArt(c.state.theme, c.state.card?.id ?: "cloud", Modifier.fillMaxWidth().height(if (isWideLayout()) 250.dp else 320.dp).clip(Shape))
    }, right = {
        Badge("상상 이야기 · 마지막 장")
        Text(c.state.storyText, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, lineHeight = 35.sp), color = Ink)
        OutlinedButton(onClick = { c.speak(c.state.storyText) }) { Text("마지막 이야기 읽어 주기") }
        PrimaryButton("가족 대화 카드 보기  →") { c.act { showFamilyCard() } }
    })
}

@Composable
private fun FamilyScreen(c: MoaController) {
    val card = c.state.familyCard ?: return
    val wide = isWideLayout()
    ScreenTitle("03  가족의 이야기로 남겨요", if (card.isHandoff) "가족과 함께 알아볼 질문이야." else "오늘의 호기심, 가족과 나눠요.", if (card.isHandoff) c.state.message else "정답을 찾기보다, 서로의 이야기를 들어 주세요.")
    SplitPanel(left = {
        Badge("가족 대화 카드")
        FamilyContent(card)
    }, right = {
        Column(verticalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 16.dp)) {
            SceneArt(c.state.theme, c.state.card?.id ?: "cloud", Modifier.fillMaxWidth().height(if (wide) 100.dp else 180.dp).clip(RoundedCornerShape(20.dp)))
            if (!wide) Text("대화는 여기서부터", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(if (wide) "함께 이야기하거나, 지금은 편하게 마쳐도 괜찮아요." else "카드의 질문으로 이야기를 나눠도 좋고,\n지금은 편하게 마쳐도 괜찮아요.", color = Muted, lineHeight = 24.sp)
            OutlinedButton(onClick = c::saveCurrent, enabled = c.savedCurrentId == null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
                Text(if (c.savedCurrentId != null) "이 기기에 저장했어요 ✓" else "보호자가 이 카드 저장하기")
            }
            Text("자동 저장 꺼짐 · 질문과 실제 선택만 저장해요.", color = Muted, fontSize = 12.sp)
            PrimaryButton("오늘의 여행 마치기") { c.end() }
            Text("다음 쉬는 시간에 궁금한 게 있으면 말해 줘. 못 봐도 괜찮아.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
        }
    })
}

@Composable
private fun FamilyContent(card: FamilyCard) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("오늘의 질문", color = Muted, fontSize = 13.sp)
            Text(card.question, fontSize = 26.sp, lineHeight = 36.sp, color = Ink, fontWeight = FontWeight.Bold)
        }
        if (card.choices.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("내가 고른 길", color = Muted, fontSize = 13.sp)
                card.choices.forEachIndexed { i, choice -> Badge("${i + 1}  $choice") }
            }
        }
        HorizontalDivider(color = Line)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("가족과 나눠요", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(card.openQuestion, color = Ink, fontSize = 27.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PausedScreen(c: MoaController) {
    MoaCard {
        Badge("잠시 쉬어 가요", Color(0xFFFFEEDD), Color(0xFF815326))
        Text("이야기를 잠시 멈췄어요.", style = MaterialTheme.typography.headlineLarge, color = Ink)
        Text(c.state.message, style = MaterialTheme.typography.bodyLarge, color = Muted)
        Text("소리를 멈추고 화면 테마를 기본으로 돌렸어요.\n이용 허용 상태가 되어도 자동으로 이어지지 않아요.", color = Muted, lineHeight = 26.sp)
        PrimaryButton("보호자가 다시 시작하기", enabled = c.state.safety == Safety.ALLOWED) { c.act { resume() } }
        OutlinedButton(onClick = c::end, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) { Text("여기서 마치기") }
    }
}

@Composable
private fun SettingsDialog(c: MoaController, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("보호자 설정") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("함께 이용해 주세요", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("허용된 정차 상태에서 보호자가 시작하고 재개해요. 화면을 벗어나거나 차량 제한이 바뀌면 즉시 멈춰요.", color = Muted)
            if (c.state.stage != Stage.HOME) {
                Text("화면 테마", fontWeight = FontWeight.Bold)
                ThemePicker(c.state.theme) { c.act { setTheme(it) } }
            }
            Text("차량 조명은 연결하지 않았어요. 현재 테마는 화면에만 적용돼요.", color = Muted, fontSize = 13.sp)
            HorizontalDivider(color = Line)
            Text("음성과 저장", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("읽기는 버튼을 눌렀을 때만 재생돼요. 설치된 한국어 오프라인 음성이 필요해요. 음성 입력은 지원 기기에서만 제공돼요.", color = Muted)
            Text("외부 AI 전송 · 원본 음성 저장 · 위치 수집 · 카메라 사용 없음\n대화 카드 자동 저장 꺼짐", color = Muted, lineHeight = 22.sp)
            OutlinedButton(onClick = c::stopSpeech, modifier = Modifier.fillMaxWidth()) { Text("모든 소리 멈추기") }
            if (c.state.stage != Stage.HOME) Button(onClick = { c.pause(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("놀이 잠시 멈추기") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

@Composable
private fun LibraryDialog(cards: List<SavedFamilyCard>, error: String?, onRetry: () -> Unit, onDismiss: () -> Unit, onSelect: (SavedFamilyCard) -> Unit, onDelete: (String) -> Unit, onClear: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("우리 가족의 보관함") }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("보호자가 이 기기에 저장한 대화 카드예요.", color = Muted)
            Text("최근 100개까지 이 기기에 보관해요.", color = Muted, fontSize = 13.sp)
            if (error != null) {
                Text(error, color = Ink)
                OutlinedButton(onClick = onRetry) { Text("다시 불러오기") }
            } else if (cards.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("아직 저장한 카드가 없어요.", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("여행을 마친 뒤 마음에 남는 카드를 저장해 보세요.", color = Muted)
            }
            cards.forEach { saved ->
                Surface(shape = RoundedCornerShape(16.dp), color = PaleBlue) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(SimpleDateFormat("yyyy.MM.dd  HH:mm", Locale.KOREAN).format(Date(saved.createdAt)), color = Muted, fontSize = 12.sp)
                        Text(saved.card.question, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                        Row {
                            TextButton(onClick = { onSelect(saved) }) { Text("카드 열기") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onDelete(saved.id) }) { Text("지우기", color = Color(0xFF9D4C44)) }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }, dismissButton = {
        if (cards.isNotEmpty() || error != null) TextButton(onClick = onClear) { Text("모두 지우기", color = Color(0xFF9D4C44)) }
    })
}

@Composable
private fun ConfirmDialog(title: String, message: String, label: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(label) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

@Composable
private fun ScreenTitle(step: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(if (isWideLayout()) 4.dp else 8.dp)) {
        Text(step, color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(title, style = MaterialTheme.typography.headlineLarge, color = Ink)
        Text(subtitle, color = Muted, fontSize = 16.sp, lineHeight = 25.sp)
    }
}

@Composable
private fun SplitPanel(left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 760.dp) Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Top) {
            MoaCard(Modifier.weight(0.95f), left)
            MoaCard(Modifier.weight(1.05f), right)
        } else Column(verticalArrangement = Arrangement.spacedBy(18.dp)) { MoaCard(content = left); MoaCard(content = right) }
    }
}

@Composable
private fun MoaCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = Color.White.copy(alpha = 0.95f), shape = Shape, border = BorderStroke(1.dp, Color.White), shadowElevation = 2.dp) {
        Column(Modifier.padding(if (isWideLayout()) 20.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if (isWideLayout()) 12.dp else 17.dp), content = content)
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp), shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Badge(text: String, background: Color = Color(0xFFE8F1FF), foreground: Color = Blue) {
    Surface(color = background, shape = RoundedCornerShape(10.dp)) {
        Text(text, Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ThemePicker(selected: Theme, onSelect: (Theme) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Theme.entries.forEach { theme ->
            FilterChip(selected = theme == selected, onClick = { onSelect(theme) }, label = { Text(theme.label) },
                leadingIcon = { Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(Color(theme.colors[1]))) })
        }
    }
}

private fun themeFor(subject: String): Theme = when (subject) {
    "tree", "mountain" -> Theme.FOREST
    "moon", "star" -> Theme.SPACE
    else -> Theme.SEA
}

@Composable
private fun isWideLayout(): Boolean = LocalConfiguration.current.screenWidthDp >= 850
