#!/usr/bin/env bash
# Build and sign the submission APK using an existing private release key.
set -euo pipefail

if [[ $# -ne 1 ]]; then
  printf 'Usage: %s "등록한 팀명"\n' "$0" >&2
  exit 1
fi

moa_team="$1"
python3 - "$moa_team" <<'PY'
import sys
import unicodedata

team = sys.argv[1]
filename = f"{team}_모아_release.apk"
if (not team.strip() or team != team.strip() or
        any(c in "/\\" or unicodedata.category(c) in ("Cc", "Cf") for c in team) or
        len(filename.encode("utf-8")) > 240):
    raise SystemExit("팀명은 앞뒤 공백, 경로 구분자, 제어 문자 없이 입력하세요. 파일명은 UTF-8 240바이트 이하여야 합니다.")
PY

cd "$(dirname "$0")/.."
moa_root="$PWD"
moa_sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
moa_tools="$moa_sdk/build-tools/35.0.0"
moa_signing_dir="${MOA_SIGNING_DIR:-$HOME/.local/share/moa/signing}"
moa_keystore="$moa_signing_dir/moa-release.p12"
moa_password="$moa_signing_dir/store-password.txt"

for moa_tool in zipalign apksigner aapt; do
  if [[ ! -x "$moa_tools/$moa_tool" ]]; then
    printf 'Android SDK Build-Tools 35.0.0이 필요합니다: %s\n' "$moa_tools/$moa_tool" >&2
    exit 1
  fi
done
if [[ ! -s "$moa_keystore" || ! -s "$moa_password" ]]; then
  printf '기존 release 키와 비밀번호 파일이 필요합니다. docs/RELEASE_BUILD.md를 참고하세요.\n' >&2
  exit 1
fi

# Private signing material must remain outside the source repository.
python3 - "$moa_root" "$moa_keystore" "$moa_password" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
for filename in sys.argv[2:]:
    path = Path(filename).resolve()
    if root == path or root in path.parents:
        raise SystemExit("서명 키와 비밀번호 파일은 저장소 밖에 보관하세요.")
    if not path.is_file():
        raise SystemExit("서명 키와 비밀번호는 일반 파일이어야 합니다.")
    if path.stat().st_mode & 0o077:
        raise SystemExit("서명 키와 비밀번호 파일의 권한을 600으로 제한하세요.")
PY

./gradlew :app:assembleRelease
moa_unsigned="$moa_root/app/build/outputs/apk/release/app-release-unsigned.apk"
if [[ ! -s "$moa_unsigned" ]]; then
  printf '서명 전 release APK를 찾을 수 없습니다: %s\n' "$moa_unsigned" >&2
  exit 1
fi
moa_badging=$("$moa_tools/aapt" dump badging "$moa_unsigned")
if [[ "$moa_badging" != "package: name='ai.moa.app' "* || "$moa_badging" == *application-debuggable* ]]; then
  printf '제출 APK는 ai.moa.app 패키지의 debuggable=false release 빌드여야 합니다.\n' >&2
  exit 1
fi

mkdir -p "$moa_root/apk"
moa_tmp=$(mktemp -d "$moa_root/apk/.package-release.XXXXXX")
trap 'rm -rf "$moa_tmp"' EXIT
moa_filename="${moa_team}_모아_release.apk"
moa_staged="$moa_tmp/$moa_filename"
"$moa_tools/zipalign" -P 16 -f 4 "$moa_unsigned" "$moa_tmp/aligned.apk"
# A PKCS12 key uses the keystore password; do not repeat the same one-line file
# as --key-pass (apksigner would try to consume a second line).
"$moa_tools/apksigner" sign \
  --ks "$moa_keystore" --ks-type PKCS12 --ks-key-alias moa-release \
  --ks-pass "file:$moa_password" --debuggable-apk-permitted false \
  --v4-signing-enabled false --out "$moa_staged" "$moa_tmp/aligned.apk" < /dev/null
"$moa_tools/apksigner" verify --verbose --print-certs "$moa_staged"
"$moa_tools/zipalign" -c -P 16 4 "$moa_staged"

python3 - "$moa_staged" <<'PY'
import hashlib
from pathlib import Path
import sys

apk = Path(sys.argv[1])
with apk.open("rb") as source:
    digest = hashlib.sha256()
    for chunk in iter(lambda: source.read(1024 * 1024), b""):
        digest.update(chunk)
apk.with_name(apk.name + ".sha256").write_text(f"{digest.hexdigest()}  {apk.name}\n", encoding="utf-8")
PY

# Staging shares the destination filesystem, so the APK appears only after
# successful signing and verification, as a complete file.
mv -f "$moa_staged" "$moa_root/apk/$moa_filename"
mv -f "$moa_staged.sha256" "$moa_root/apk/$moa_filename.sha256"
printf '\n서명·검증 완료: apk/%s\nSHA-256: apk/%s.sha256\n' "$moa_filename" "$moa_filename"
