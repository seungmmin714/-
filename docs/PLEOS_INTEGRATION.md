# Pleos / Android 연동 상태

확인일: 2026-11-13. 공개 공식 문서 확인 기준이며, 실제 Pleos 에뮬레이터·양산 차량의 실행 검증이나 심사 승인을 뜻하지 않습니다.

## 지금 구현한 범위

| 기능 | 구현 및 제한 |
| --- | --- |
| 주행 제한 | `VehicleSafetyMonitor`가 실제 AAOS `CarUxRestrictionsManager`를 구독합니다. 서비스 준비 완료, `isRequiresDistractionOptimization == false`, `activeRestrictions == UX_RESTRICTIONS_BASELINE`을 모두 만족해야 `Safety.ALLOWED`입니다. 제한 상태는 `BLOCKED`, 서비스 없음·연결 끊김·오류는 `UNKNOWN`입니다. 속도를 읽거나 보호자 확인을 차량 신호로 간주하지 않습니다. |
| 일반 기기 | Automotive feature가 없으면 `android.car` 구현 클래스를 생성하지 않습니다. 선택적 라이브러리 누락도 `UNKNOWN`으로 처리합니다. 비동기 Car lifecycle API 사용 때문에 실제 감시는 API 29 이상이며, API 28에서는 `UNKNOWN`을 유지합니다. |
| 음성 입력 | Android API 31 이상에서 `isOnDeviceRecognitionAvailable()`을 확인한 뒤 `createOnDeviceSpeechRecognizer()`만 사용합니다. 기본/온라인 인식기로 대체하지 않습니다. 한국어 인식 모델·마이크 권한이 없거나 인식에 실패하면 카드로 진행합니다. 15초 후 종료합니다. |
| 음성 읽기 | 설치된 한국어 Android TTS 음성 중 `isNetworkConnectionRequired == false`인 음성만 선택합니다. `KEY_FEATURE_NOT_INSTALLED`가 있으면 제외하여 음성 다운로드를 유발하지 않습니다. 명시적으로 누를 때만 읽고 오디오 포커스를 잃으면 중단합니다. 포커스 회복 후 자동 재생하지 않습니다. |
| 취소와 수명주기 | `stop()`은 입력·출력을 모두 중단하고 지연된 음성 결과를 무시합니다. 화면/세션 종료에는 `close()`를 호출합니다. 원본 음성을 저장하거나 로그로 남기지 않습니다. |
| 차량 조명 | 현재는 화면 테마만 바뀝니다. Pleos Light 제어·원래 조명 복원은 아직 연결하지 않았습니다. 차량 조명 연동 성공으로 표시하면 안 됩니다. |

안전 상태가 `ALLOWED`여도 세션 시작·재개는 보호자가 직접 선택합니다. 실제 차량 신호와 디버그 시연 상태는 UI에서 구분하며, 릴리스 빌드에는 시연 허용 스위치를 노출하지 않습니다. `SpeechController` 호출 전 허용 상태를 UI/세션에서 검사하고, 제한·앱 중단·권한 해제 시 `stop()`을 호출해야 합니다.

Android 근거: [Car 연결과 lifecycle](https://developer.android.com/reference/android/car/Car), [CarUxRestrictionsManager](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager), [제한 상태 의미](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictions), [기기 내 SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer), [Voice 네트워크 요건](https://developer.android.com/reference/android/speech/tts/Voice), [미설치 음성 표시](https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine).

## 빌드와 실행 준비

- Pleos 제품 앱은 `minSdk >= 28`입니다. 공식 Vehicle 샘플의 확인 가능한 조합은 JDK 17, Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0, compile/targetSdk 35입니다. [샘플 기준](https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk-sample-app)
- Pleos Connect Emulator 2.1.0은 Android 14/API 34입니다. RAM 4GB와 Cold boot를 사용합니다. Mac SDK Update Site: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`. [설치 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- Android 모듈은 `useLibrary("android.car", false)`와 optional `android.car` uses-library를 사용합니다. `android.car.jar`는 컴파일 전용이며 APK에 플랫폼 클래스를 복사하지 않습니다.
- 네이티브 음성 입력에는 `android.permission.RECORD_AUDIO` 런타임 동의가 필요합니다. Android 11 이상에서는 `android.speech.RecognitionService`와 `android.intent.action.TTS_SERVICE`의 패키지 조회 선언도 필요합니다.
- `SpeechController`는 Android 기본 API이며 아래의 Pleos STT/TTS를 호출하는 구현이 아닙니다. 설치된 엔진의 오프라인 지원·실제 동작은 실행 대상에서 따로 검증합니다.

## 다음에 연결할 Pleos SDK

Maven 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/`

```kotlin
// 지원·권한·실행 대상 검증 후 별도 어댑터에서 도입할 의존성. 현재 앱에 포함하지 않음.
implementation("ai.pleos.playground:Vehicle:2.3.2")
implementation("ai.pleos.playground:SpeechToText:3.3.1")
implementation("ai.pleos.playground:TextToSpeech:3.3.1")
```

한국어 [Vehicle 가져오기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup), [STT 가져오기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/importing-sdk), [TTS 가져오기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/importing-sdk)를 기준으로 고정했습니다. 일부 이전/영문 문서는 Vehicle 2.3.1을 표시하므로 실제 다운로드한 SDK·Service 버전과 대조해야 합니다.

### 주행 제한

Pleos도 AAOS `CarUxRestrictions` 사용을 안내합니다. DO 미선언 앱은 Parking에서만 허용됩니다. 게임/선택형 이야기의 주행 중 사용을 허용하도록 DO를 선언하지 않습니다. [연동 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide), [지역별 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy)

### Light

공식 [Light API 목록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/entry)에 다음 API가 있습니다.

- `checkAmbientLightBrightnessColorCapabilities`
- `getAmbientLightBrightnessColor`
- `setAmbientLightBrightnessColor`
- `registerAmbientLightBrightnessColorListener` / `unregisterAmbientLightBrightnessColorListener`

문서에서 이름을 확인했지만 이번 조사에서는 개별 함수의 인자·콜백 타입 본문을 얻지 못했습니다. AAR/IDE의 실제 시그니처를 확인하기 전 추정 호출을 추가하지 않습니다. 조회는 `CAR_LIGHTS`, 제어는 `CONTROL_CAR_LIGHTS` 계열을 확인하되 각 함수의 `@RequiresPermission`이 기준입니다. 권한 선언만으로 승인이나 기능 지원을 보장하지 않습니다. [권한 문서](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)

연결 순서: capability·쓰기 허용 범위 확인 → 원래 값 읽기 → 검증된 테마 ID를 지원 색으로 매핑 → 적용 → 재조회/구독으로 실제 반영 확인 → 종료·제한·오류 시 원래 값 복원 및 결과 확인. 원래 값을 읽지 못했으면 조명을 바꾸지 않습니다. set 성공 콜백만으로 실제 조명이 바뀌었다고 판단하지 않습니다. 복원 실패는 숨기지 않습니다.

### Pleos 음성

`SpeechToText(context, Mode.ON_DEVICE)`와 `TextToSpeech(context, Mode.ON_DEVICE)`가 지원됩니다. 모드 생략 시 기본값은 서버 우선 `HYBRID`이므로 기기 내 처리만 허용하려면 명시적으로 `ON_DEVICE`를 선택합니다. 해당 모드만 사용하면 문서상 앱 등록과 네트워크가 필요 없습니다. [STT 모드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro), [TTS 모드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro)

Pleos SDK는 각각 `pleos.car.permission.STT_SERVICE`, `pleos.car.permission.TTS_SERVICE`가 필요합니다. 초기화 성공 후 리스너 등록/호출, 중단 시 `stop()`, 종료 시 리스너 제거와 `release()`를 적용합니다. 일반 Android 음성 서비스 지원 여부로 Pleos Speech Service를 지원한다고 판단하면 안 됩니다. [STT 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions), [TTS 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/declaring-permissions)

## 실제 Pleos에서 남은 검증

1. 초기 연결/서비스 재시작/제한 전환에서 잠금 및 음성 중단, 정차 후 수동 재개.
2. 자동차가 아닌 기기·라이브러리 없음·권한 해제·오프라인 모델 없음에서 안전한 대체 흐름.
3. 음성 입력 중 이탈 시 늦게 도착한 결과가 다음 세션에 적용되지 않는지 확인.
4. Pleos Light 권한 승인 및 capability 확인 후 제한된 프리셋과 복원 실패 처리.
5. 외부 AI는 현재 미연결. 서버 중계·최소 데이터·보호자 동의·응답 검증을 갖춘 후 별도로 추가.
