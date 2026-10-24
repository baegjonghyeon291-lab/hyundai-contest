# 개발 환경 설치 (Pleos Connect 에뮬레이터)

새 PC에서는 Claude에게 "docs/dev-setup.md 대로 세팅해줘"라고 하면 된다.

1. Android Studio 설치: `winget install --id Google.AndroidStudio -e`
2. SDK: `%LOCALAPPDATA%\Android\Sdk`에 commandlinetools(win) 압축 해제 → `cmdline-tools/latest`
   - `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`
   - `sdkmanager --licenses` 후 `platform-tools`, `emulator`, `platforms;android-35`, `platforms;android-34`, `build-tools;35.0.0`
   - 패키지 이름에 `;`가 있어 .bat 인자로 넘기면 깨짐 → `--package_file` 사용
3. Pleos 이미지 (sdkmanager가 사용자 저장소를 못 읽어서 수동 설치)
   - 저장소 XML: https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml
   - 이미지 zip(약 4GB, 압축 해제 19GB): `crp_car_emulator.zip` (sha1 b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa)
   - `Sdk/system-images/android-34/pleos_car_emulator/x86_64/` 에 압축 해제 (`crp_avd_out_x86_64` 폴더 이름 변경)
4. AVD `Pleos_Connect`: `~/.android/avd/Pleos_Connect.ini` + `Pleos_Connect.avd/config.ini`
   - `image.sysdir.1=system-images\android-34\pleos_car_emulator\x86_64\`, `tag.id=android-automotive`
   - 2560x1440 / 160dpi, RAM 4096, cold boot, GPU host
5. 실행: `emulator -avd Pleos_Connect -no-snapshot-load` (첫 부팅 약 2~3분, 가속 WHPX)
6. 환경변수: `ANDROID_HOME`, `JAVA_HOME` (setx)

확인: `adb shell pm list packages | grep pleos` → `ai.pleos.service.car` 등이 보이면 정상.

## 앱 빌드 (2026-10-24 추가)
- **JDK 17 필요**: `winget install --id EclipseAdoptium.Temurin.17.JDK -e` → `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17...` (Android Studio 내장 JBR 25는 Gradle 8.10과 호환 안 됨)
- 빌드: `cd android && ./gradlew assembleDebug` / 설치: `adb install -r -g app/build/outputs/apk/debug/app-debug.apk`
- release 서명: `android/keystore.properties`(gitignore)에 storeFile/storePassword/keyAlias/keyPassword. 키스토어는 이 PC의 `~/.android/databox-release.jks`. **다른 PC에서 같은 서명이 필요하면 이 두 파일을 USB 등으로 옮길 것** (GitHub에 올리지 말 것).
- 앱 데이터는 user 10: `adb shell run-as com.databox.blackbox --user 10 ls files`
