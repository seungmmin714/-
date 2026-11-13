# MVP 검증 결과

검증일: 2026-11-13. Mac의 일반 Android API 35 ARM64 에뮬레이터와 공식 Pleos Connect 2.1.0에서 수행했습니다. 실제 차량 검증은 포함하지 않습니다.

| 확인 항목 | 결과 |
| --- | --- |
| 순수 Kotlin 코어 | 13개 테스트 통과. 카드 10개 × 4가지 이야기 경로 포함 |
| 앱 내부 저장소 | Robolectric 10개 테스트 통과 |
| 가로 화면 UI | Compose 전체 흐름 8개 통과, 2560×1600 / 320dpi |
| 이전 MVP 세로 화면 UI | 이전 소스의 4개 통과 기록(최신 8개는 미실행), 1080×1920 / 420dpi |
| Debug APK | 빌드·에뮬레이터 설치·실행 및 APK 서명 검증 통과 |
| Release APK | 전용 release 키 서명·정렬 검증 통과, debuggable=false. Pleos 실행 검증은 아래 환경 기록 참고 |
| Android lint | 오류 0개. 고정 라이브러리의 새 버전 알림 5개 |
| 화면 검토 | 질문·사실·이야기·가족 카드·주행 제한 화면 실제 캡처 검토 |
| 배포 권한 | 마이크와 AndroidX 내부 수신기 권한만 포함. INTERNET·위치·카메라 권한 없음 |

## 제출 APK의 Pleos 실행 확인

- 대상 파일: [`apk/이승민_모아_release.apk`](../apk/이승민_모아_release.apk), 18,336,211 bytes
- SHA-256: `fcc7a225f6738da443e13f325ba0d0e77fa967e1a4766af8ce011b111034c286`
- 전용 release 키로 APK Signature Scheme v3 서명. `ai.moa.app`, `0.1.0`, `debuggable=false`
- 에뮬레이터: **Pleos Connect 2.1.0**, Android 14/API 34 ARM64
- 실제 빌드 식별자: `RELEASE.release_connect_v2.1.0 test-keys`
- 화면: **2560×1440 px / 160 dpi**, RAM 4GB, Cold boot. 기본 Pleos 분할 화면에서 앱을 실행했습니다.
- 호스트 Android Emulator: 37.2.13. 이 버전은 Pleos 이미지 버전 2.1.0과 별개입니다.

아래 항목은 제출 APK를 직접 설치하여 UI와 Car 서비스 상태를 확인한 수동 시나리오이며, 위 일반 Android 자동화 테스트 8개와 구분합니다.

| 확인 항목 | 실제 결과 |
| --- | --- |
| 설치·정차 상태 시작 | `adb install -r` 성공, `am start -W` 정상 실행. `DO=false`, `UxR=0`에서 보호자 시작 버튼 제공 |
| 질문 → 이야기 → 가족 카드 | 구름 → 바다 → 새 친구 인사 → 가족 카드 완주, 실제 두 선택과 열린 질문 확인 |
| 저장·프로세스 재실행 | 명시 저장 후 앱 강제 종료·재실행, 보관함에서 저장 카드와 두 선택 조회 성공 |
| 실제 플랫폼 제한 이벤트 | 공식 Virtual Console에서 Drive·20km/h 입력. Car 서비스 `DO=true`, `UxR=80`, `Display 0 is restricted` 확인 |
| 앱 중단·플랫폼 차단 | 앱 PAUSED 전환, 재개·보관함·설정 비활성 확인. 이후 Pleos의 ‘주행 중에는 사용할 수 없습니다’ 차단 화면 표시 |
| 정차 후 수동 재개 | 속도 0 → Park 복원, `DO=false`, `UxR=0` 확인. 자동 재개되지 않으며 보호자 재개 후 기존 첫 선택을 유지한 채 두 번째 선택·가족 카드까지 진행 |
| 한국어 음성 미설치 | 한국어 오프라인 음성 미설치 안내 표시. 음성 재생·음성 입력은 이 Pleos 환경에서 검증하지 않았으며 카드 흐름은 정상 완료 |

실제 캡처: [질문](screenshots/pleos/01-release-questions.png) · [저장된 가족 카드](screenshots/pleos/02-release-family.png) · [플랫폼 주행 차단](screenshots/pleos/03-release-driving-pause.png) · [정차 후 수동 재개 대기](screenshots/pleos/04-release-manual-resume.png)

재현 시 공식 Virtual Console을 열어 활성 상태를 유지한 뒤 Motion에서 기어·속도를 설정합니다. 테스트 후 속도 0과 Park로 복원합니다. [공식 Virtual Console 안내](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)

## 재현

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

스크린샷은 `./scripts/capture-demo.sh`로 전용 에뮬레이터에서 재생성합니다. Gradle 연결 테스트가 종료 후 앱을 제거할 수 있으므로 캡처 스크립트는 테스트 APK를 직접 설치·실행한 뒤 그림을 가져옵니다. 테스트마다 시연 카드가 초기화됩니다.

UI 검증은 질문 → 사실 → 두 번의 선택 → 가족 카드 → 저장·재실행, 최대 두 번 확인 후 범위 밖 질문 전달, 제한·연결 손실 후 수동 재개, 미저장 종료를 포함합니다. 새로운 이야기 선택 뒤 본문이 화면 위로 돌아오는 것도 확인했습니다.

## 남은 실환경 검증

- 양산 차량의 제한 이벤트·시스템 앱 차단, 서비스 재시작·연결 손실 처리
- 대상 차량의 오프라인 한국어 인식·읽기 지원 및 실제 음성 품질
- 아직 연결하지 않은 Light SDK 지원·권한·적용·복원
- 외부 AI의 서비스 정책·보호자 동의·서버 중계
- 보호자·아동 동반 테스트와 플랫폼 심사

에뮬레이터 통과 결과를 차량 인증이나 학습 효과의 증거로 해석하지 않습니다.
