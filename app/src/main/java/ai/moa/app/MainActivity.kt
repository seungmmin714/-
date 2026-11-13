package ai.moa.app

import ai.moa.app.platform.SpeechController
import ai.moa.app.platform.SoundscapeController
import ai.moa.app.platform.VehicleSafetyMonitor
import ai.moa.app.storage.FamilyCardStore
import ai.moa.app.storage.SavedFamilyCard
import ai.moa.app.ui.MoaApp
import ai.moa.core.Safety
import ai.moa.core.SessionEngine
import ai.moa.core.Stage
import ai.moa.core.Theme
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private lateinit var controller: MoaController
    private var previouslyGrantedMic = false
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (::controller.isInitialized) {
            if (granted && controller.state.stage == Stage.QUESTION && controller.state.safety == Safety.ALLOWED) {
                controller.listen()
            } else {
                controller.notice = if (granted) "음성 입력을 허용했어요. 놀이를 재개한 뒤 ‘말로 질문’을 눌러 주세요." else "마이크를 허용하지 않아도 질문 카드와 직접 입력을 사용할 수 있어요."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(245, 248, 253)
        window.navigationBarColor = android.graphics.Color.rgb(245, 248, 253)
        controller = MoaController(this)
        setContent {
            MoaApp(controller) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) controller.listen()
                else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        controller.startMonitoring()
    }

    override fun onResume() {
        super.onResume()
        val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (::controller.isInitialized && previouslyGrantedMic && !granted) controller.pause("음성 권한이 해제되어 잠시 멈췄어요.")
        previouslyGrantedMic = granted
    }

    override fun onPause() {
        if (::controller.isInitialized) controller.pause("화면을 벗어나 잠시 멈췄어요. 보호자가 다시 시작해 주세요.")
        super.onPause()
    }

    override fun onDestroy() {
        if (::controller.isInitialized) controller.close()
        super.onDestroy()
    }
}

/** UI state stays in memory, including across neither process death nor Activity recreation. */
class MoaController(activity: ComponentActivity) {
    private val engine = SessionEngine()
    private val store = FamilyCardStore(activity)
    var state by mutableStateOf(engine.state)
        private set
    var savedCards by mutableStateOf(emptyList<SavedFamilyCard>())
        private set
    var savedCurrentId by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf("")
    var voiceInput by mutableStateOf("")
    var demoMode by mutableStateOf(false)
        private set
    val canUseDemo = BuildConfig.DEBUG && !activity.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    private val soundscape = SoundscapeController(activity)
    private val speech = SpeechController(activity, onTranscript = { text ->
        if (state.stage == Stage.QUESTION && state.safety == Safety.ALLOWED) {
            voiceInput = text.take(SessionEngine.MAX_QUESTION_LENGTH)
            notice = "질문을 들었어요. 내용을 확인한 뒤 ‘질문 확인’을 눌러 주세요."
        }
    }, onStatus = { message -> notice = message })
    private val monitor = VehicleSafetyMonitor(activity) { safety ->
        if (!demoMode) updateSafety(safety)
    }

    init { refreshSaved() }

    fun startMonitoring() = monitor.start()

    fun act(action: SessionEngine.() -> Unit) {
        speech.stop()
        soundscape.stop()
        engine.action()
        state = engine.state
        if (state.stage != Stage.QUESTION) voiceInput = ""
        notice = ""
    }

    fun startDemo() {
        if (!canUseDemo || state.stage != Stage.HOME) return
        demoMode = true
        updateSafety(Safety.ALLOWED)
    }

    fun stopDemo() {
        if (!canUseDemo) return
        end()
        demoMode = false
        updateSafety(Safety.UNKNOWN)
        monitor.start()
    }

    fun simulate(safety: Safety) {
        if (canUseDemo && demoMode) updateSafety(safety)
    }

    private fun updateSafety(safety: Safety) {
        if (safety != Safety.ALLOWED) {
            speech.stop()
            soundscape.stop()
            voiceInput = ""
            notice = ""
        }
        engine.updateSafety(safety)
        state = engine.state
    }

    fun pause(reason: String = "보호자가 잠시 멈췄어요.") {
        speech.stop()
        soundscape.stop()
        voiceInput = ""
        notice = ""
        engine.pause(reason)
        state = engine.state
    }

    fun end() {
        speech.stop()
        soundscape.stop()
        engine.end()
        state = engine.state
        voiceInput = ""
        notice = ""
        savedCurrentId = null
    }

    fun speak(text: String) {
        if (state.safety == Safety.ALLOWED && state.stage in listOf(Stage.FACT, Stage.STORY, Stage.ENDING, Stage.FAMILY)) {
            soundscape.stop()
            speech.speak(text)
        }
    }

    fun listen() {
        if (state.safety == Safety.ALLOWED && state.stage == Stage.QUESTION) {
            soundscape.stop()
            // Repeated speech is a new input even after the prior text was edited.
            voiceInput = ""
            speech.listen()
        }
    }

    fun playThemeSound() {
        if (state.safety != Safety.ALLOWED || state.stage !in listOf(Stage.STORY, Stage.ENDING)) return
        speech.stop()
        notice = if (soundscape.play(state.theme)) "테마의 짧은 소리를 듣고 있어요." else "지금은 테마 소리를 재생할 수 없어요."
    }

    fun stopSpeech() {
        speech.stop()
        soundscape.stop()
        notice = "소리를 멈췄어요."
    }

    fun saveCurrent() {
        if (state.safety != Safety.ALLOWED || state.stage != Stage.FAMILY || savedCurrentId != null) return
        val card = state.familyCard ?: return
        runCatching { store.save(card) }.onSuccess {
            savedCurrentId = it.id
            refreshSaved()
            notice = "이 기기에 대화 카드를 저장했어요. 보관함에서 언제든 지울 수 있어요."
        }.onFailure { notice = "카드를 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요." }
    }

    fun deleteSaved(id: String) {
        if (state.safety != Safety.ALLOWED) return
        runCatching { store.delete(id) }.onSuccess {
            if (savedCurrentId == id) savedCurrentId = null
            refreshSaved()
        }.onFailure { notice = "카드를 지우지 못했어요. 다시 시도해 주세요." }
    }

    fun clearSaved() {
        if (state.safety != Safety.ALLOWED) return
        runCatching { store.clear() }.onSuccess {
            savedCurrentId = null
            refreshSaved()
        }.onFailure { notice = "보관함을 비우지 못했어요. 다시 시도해 주세요." }
    }

    private fun refreshSaved() {
        runCatching { store.all() }.onSuccess { savedCards = it }.onFailure { notice = "보관함을 불러오지 못했어요." }
    }

    fun close() {
        speech.close()
        soundscape.close()
        monitor.close()
        engine.end()
    }
}
