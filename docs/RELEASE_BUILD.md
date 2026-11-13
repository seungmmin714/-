# 제출용 release APK 만들기

JDK 17, Python 3, Android SDK Platform 35와 Build-Tools 35.0.0이 필요합니다. SDK 위치는 `ANDROID_SDK_ROOT`, `ANDROID_HOME` 순으로 확인하며, 둘 다 없으면 macOS의 `~/Library/Android/sdk`를 사용합니다.

## 서명 키

기본 서명 자료 위치는 저장소 밖의 `~/.local/share/moa/signing/`입니다. 다른 위치는 `MOA_SIGNING_DIR`로 지정할 수 있습니다.

- `moa-release.p12`: alias가 `moa-release`인 PKCS12 release 키
- `store-password.txt`: 해당 키 저장소의 비밀번호 한 줄

PKCS12의 개인 키 비밀번호와 저장소 비밀번호는 같아야 합니다. 디렉터리 권한은 `700`, 두 파일 권한은 `600`으로 관리합니다. 스크립트는 키가 없으면 중단하며 키 생성이나 debug 키로의 대체를 하지 않습니다. 키가 없는 새 개발 환경에서는 기존 키와 비밀번호를 안전한 백업에서 복원한 뒤 실행하세요.

이 키는 같은 앱을 업데이트할 때 필요하므로 별도로 안전하게 백업하세요. 키·비밀번호를 소스 저장소나 GitHub Releases에 업로드하지 마세요.

## 패키징

저장소 루트에서 접수에 등록한 정확한 팀명을 인자로 전달합니다.

```sh
./scripts/package-release.sh '이승민'
```

스크립트는 release 빌드, 16 KB 페이지 정렬, 서명, 서명·정렬 검증을 순서대로 실행합니다. 패키지가 `ai.moa.app`인지와 `debuggable=false`인지 확인하고, 검증이 끝난 APK만 아래 위치에 배치합니다.

```text
apk/이승민_모아_release.apk
apk/이승민_모아_release.apk.sha256
```

비밀번호는 파일에서 읽으므로 명령 인자나 출력에 포함되지 않습니다. APK 파일명에 쓰이는 팀명의 앞뒤 공백·경로 구분자·제어 문자는 허용하지 않습니다.

## 설치와 실행 검증

```sh
adb install -r 'apk/이승민_모아_release.apk'
adb shell am start -n ai.moa.app/ai.moa.app.MainActivity
```

release에는 일반 Android 기기용 시연 스위치가 없습니다. 지원 AAOS의 차량 이용 허용 상태가 있어야 질문 여행을 시작할 수 있습니다. 실제 Pleos Connect 에뮬레이터에서 설치·시작·제한 시 중단·수동 재개를 확인하고, 사용한 에뮬레이터 버전과 화면 규격을 README에 기록해야 합니다. 서명 검증만으로 Pleos 실행 검증이 완료되는 것은 아닙니다.

APK와 해당 소스를 함께 커밋하고 GitHub에 올린 뒤 그 최종 커밋 번호를 제출합니다.
