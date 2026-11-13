package ai.moa.app

import ai.moa.app.storage.FamilyCardStore
import ai.moa.core.FamilyCard
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Real Activity/Compose journeys. The debug demo controls exercise the same session engine. */
@RunWith(AndroidJUnit4::class)
class MoaJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun beginWithEmptyLibrary() {
        FamilyCardStore(InstrumentationRegistry.getInstrumentation().targetContext).clear()
        // The controller loads the library on creation, so recreate after clearing disk.
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @After fun removeTestCards() {
        FamilyCardStore(InstrumentationRegistry.getInstrumentation().targetContext).clear()
    }

    @Test fun fullQuestionStoryAndFamilyCardCanBeSavedAndReopenedAfterRecreation() {
        beginJourney()
        capture("01-questions.png")
        reachCloudFamilyCard(captureScreens = true)

        click("보호자가 이 카드 저장하기")
        compose.onNodeWithText("이 기기에 저장했어요 ✓").assertIsNotEnabled()
        compose.onNodeWithText("보관함 1").assertIsDisplayed()

        // Activity state is intentionally discarded, but an explicitly saved card survives.
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("구름은 왜 움직여?").assertDoesNotExist()
        click("시연 모드 시작")
        click("보관함 1", scroll = false)
        compose.onNodeWithText("우리 가족의 보관함").assertIsDisplayed()
        compose.onNodeWithText("구름은 왜 움직여?").performScrollTo().assertIsDisplayed()
        click("카드 열기")
        compose.onNodeWithText("1  바다로 갈래").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2  새 친구에게 인사할래").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("구름을 타고 어디에 가보고 싶어?").performScrollTo().assertIsDisplayed()
        capture("05-saved-card.png")
    }

    @Test fun unsupportedQuestionGetsOnlyTwoClarificationsThenAFamilyHandoff() {
        beginJourney()
        compose.onNodeWithText("궁금한 것을 적어 주세요")
            .performScrollTo().performTextInput("공룡은 왜 사라졌어?")

        repeat(2) { attempt ->
            click("질문 확인")
            compose.onNodeWithText("확인 ${attempt + 1} / 2", substring = true)
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("사실 설명").assertDoesNotExist()
        }
        click("질문 확인")
        compose.onNodeWithText("가족과 함께 알아볼 질문이야.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("공룡은 왜 사라졌어?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이 질문을 가족과 어디에서 함께 알아볼까?")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("내가 고른 길").assertDoesNotExist()
        compose.onNodeWithText("사실 설명").assertDoesNotExist()
        compose.onNodeWithText("보관함 0").assertIsDisplayed()
        capture("06-family-handoff.png")
    }

    @Test fun drivingRestrictionAndConnectionLossNeedAnExplicitGuardianResume() {
        beginJourney()
        click("구름은 왜 움직여?")
        click("구름 이야기 시작하기  →")
        click("바다로 갈래  →")

        click("주행 제한")
        compose.onNodeWithText("이야기를 잠시 멈췄어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("보호자가 다시 시작하기").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("새 친구에게 인사할래  →").assertDoesNotExist()
        compose.onNodeWithText("보관함 0").assertIsNotEnabled()
        capture("07-driving-pause.png")

        click("허용")
        compose.onNodeWithText("보호자가 다시 시작하기").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("이야기를 잠시 멈췄어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("새 친구에게 인사할래  →").assertDoesNotExist()
        click("보호자가 다시 시작하기")
        compose.onNodeWithText("그곳에서 무엇을 해볼까?").performScrollTo().assertIsDisplayed()

        click("연결 끊김")
        compose.onNodeWithText("보호자가 다시 시작하기").performScrollTo().assertIsNotEnabled()
        click("허용")
        compose.onNodeWithText("새 친구에게 인사할래  →").assertDoesNotExist()
        click("보호자가 다시 시작하기")
        click("새 친구에게 인사할래  →")
        click("가족 대화 카드 보기  →")
        // The already-made choice survives both interruptions without another first choice.
        compose.onNodeWithText("1  바다로 갈래").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2  새 친구에게 인사할래").performScrollTo().assertIsDisplayed()
    }

    @Test fun finishingWithoutSaveLeavesAnEmptyLibraryAfterRecreation() {
        beginJourney()
        reachCloudFamilyCard()
        compose.onNodeWithText("보관함 0").assertIsDisplayed()
        click("오늘의 여행 마치기")
        compose.onNodeWithText("보호자와 함께 시작하기  →").performScrollTo().assertIsDisplayed()
        click("보관함 0", scroll = false)
        compose.onNodeWithText("아직 저장한 카드가 없어요.").performScrollTo().assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        click("시연 모드 시작")
        click("보관함 0", scroll = false)
        compose.onNodeWithText("아직 저장한 카드가 없어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("구름은 왜 움직여?").assertDoesNotExist()
    }

    @Test fun unfinishedQuestionSurvivesBackgroundPauseButIsClearedWhenJourneyEnds() {
        beginJourney()
        val input = compose.onNodeWithText("궁금한 것을 적어 주세요")
        input.performScrollTo().performTextInput("공룡은 왜 사라졌어?")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithText("궁금한 것을 적어 주세요").assertDoesNotExist()
        click("보호자가 다시 시작하기")
        input.performScrollTo().assertTextContains("공룡은 왜 사라졌어?")
        click("종료", scroll = false)
        compose.onNodeWithText("마치기").performClick()
        click("보호자와 함께 시작하기  →")
        input.performScrollTo().assertTextContains("")
        compose.onNodeWithText("질문 확인").assertIsNotEnabled()
        compose.onNodeWithText("보관함 0").assertIsDisplayed()
    }

    @Test fun clarificationAndEditedDraftSurviveRestrictionAndHandoffUsesLatestText() {
        beginJourney()
        val input = compose.onNodeWithText("궁금한 것을 적어 주세요")
        input.performScrollTo().performTextInput("구름의 무게는 얼마야?")
        click("질문 확인")
        input.performScrollTo().performTextReplacement("공룡은 왜 사라졌어?")
        click("주행 제한")
        click("허용")
        click("보호자가 다시 시작하기")
        input.performScrollTo().assertTextContains("공룡은 왜 사라졌어?")
        compose.onNodeWithText("‘구름은 왜 움직여?’가 궁금한 거야?", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("확인 1 / 2", substring = true).assertIsDisplayed()
        input.performScrollTo().performTextReplacement("")
        compose.onNodeWithText("가족과 함께 알아볼 질문으로 남기기  →")
            .performScrollTo().assertIsNotEnabled()
        input.performScrollTo().performTextReplacement("공룡은 왜 사라졌어?")
        click("가족과 함께 알아볼 질문으로 남기기  →")
        compose.onNodeWithText("공룡은 왜 사라졌어?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("구름의 무게는 얼마야?").assertDoesNotExist()
        click("보호자가 이 카드 저장하기")
        val saved = FamilyCardStore(InstrumentationRegistry.getInstrumentation().targetContext).all().single()
        org.junit.Assert.assertEquals("공룡은 왜 사라졌어?", saved.card.question)
    }

    @Test fun unreadableLibraryIsPreservedUntilGuardianConfirmsClearing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "moa_family_cards.json")
        file.writeText("{broken json")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        click("시연 모드 시작")
        click("보관함 확인 필요", scroll = false)
        compose.onNodeWithText("보관함을 불러오지 못했어요.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("아직 저장한 카드가 없어요.").assertDoesNotExist()
        click("다시 불러오기")
        org.junit.Assert.assertEquals("{broken json", file.readText())
        compose.onNodeWithText("모두 지우기").performClick()
        compose.onNodeWithText("취소").performClick()
        org.junit.Assert.assertEquals("{broken json", file.readText())
        compose.onNodeWithText("모두 지우기").performClick()
        compose.onNodeWithText("모두 지우기").performClick()
        compose.onNodeWithText("아직 저장한 카드가 없어요.").assertIsDisplayed()
        org.junit.Assert.assertFalse(file.exists())
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("보관함 0").assertIsDisplayed()
    }

    @Test fun deletingFromPartlyDamagedLibraryShowsAnErrorWithoutChangingTheFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        FamilyCardStore(context).save(FamilyCard("공룡은 왜 사라졌어?", "함께 알아볼 질문", emptyList(), "어디서 함께 알아볼까?", true))
        val file = File(context.filesDir, "moa_family_cards.json")
        val document = JSONObject(file.readText())
        document.getJSONArray("cards").put("unreadable record")
        val damaged = document.toString()
        file.writeText(damaged)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        click("시연 모드 시작")
        click("보관함 1", scroll = false)
        click("지우기")
        compose.onNodeWithText("이 카드를 지울까요?").assertIsDisplayed()
        compose.onAllNodesWithText("지우기").onLast().performClick()
        compose.onNodeWithText("카드를 지우지 못했어요.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("다시 불러오기").performScrollTo().assertIsDisplayed()
        org.junit.Assert.assertEquals(damaged, file.readText())
    }

    private fun beginJourney() {
        click("시연 모드 시작")
        click("보호자와 함께 시작하기  →")
        compose.onNodeWithText("오는 길에 무엇이 궁금했어?").assertIsDisplayed()
    }

    private fun reachCloudFamilyCard(captureScreens: Boolean = false) {
        click("구름은 왜 움직여?")
        compose.onNodeWithText("사실 설명").performScrollTo().assertIsDisplayed()
        if (captureScreens) {
            compose.onNodeWithText("구름은 왜 움직여?").performScrollTo()
            capture("02-fact.png")
        }
        click("구름 이야기 시작하기  →")
        compose.onNodeWithText("상상 이야기").performScrollTo().assertIsDisplayed()
        if (captureScreens) {
            compose.onNodeWithText("어디로 여행을 떠날까?").performScrollTo()
            capture("03-story.png")
        }
        click("바다로 갈래  →")
        compose.onNodeWithText("그곳에서 무엇을 해볼까?").assertIsDisplayed()
        click("새 친구에게 인사할래  →")
        compose.onNodeWithText("작은 여행을 함께 마쳤어.").performScrollTo().assertIsDisplayed()
        click("가족 대화 카드 보기  →")
        compose.onNodeWithText("구름은 왜 움직여?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1  바다로 갈래").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2  새 친구에게 인사할래").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("구름을 타고 어디에 가보고 싶어?").performScrollTo().assertIsDisplayed()
        if (captureScreens) {
            compose.onNodeWithText("오늘의 호기심, 가족과 나눠요.").performScrollTo()
            capture("04-family.png")
        }
    }

    private fun click(text: String, scroll: Boolean = true) {
        val node = compose.onNodeWithText(text)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed().assertIsEnabled().performClick()
    }

    /** Artifacts for manual visual review; pixel assertions are intentionally not used. */
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // UiAutomation can capture a previous SurfaceFlinger frame after semantics settle.
        // This delay is only for review artifacts; functional assertions use Compose idling.
        SystemClock.sleep(300)
        val directory = instrumentation.targetContext.getExternalFilesDir(null)?.let { File(it, "screenshots") }
            ?: return
        if (!directory.exists() && !directory.mkdirs()) return
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        try {
            FileOutputStream(File(directory, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }
}
