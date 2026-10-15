# Maven Central 배포

Kinetica SDK의 14개 모듈을 `com.naurylab:<모듈>:<버전>` 좌표로 Maven Central에 올리는 방법입니다. `sample-showcase`, `benchmarks`, `consumer-check`는 배포하지 않습니다.

빌드 설정은 이미 되어 있습니다. FrameKit과 같은 `com.naurylab` 네임스페이스, 서명 키, 토큰을 쓰므로 FrameKit을 배포해 본 컴퓨터라면 **처음 한 번 준비**를 건너뛰고 [배포하기](#배포하기)부터 보면 됩니다.

## 처음 한 번 준비

1. https://central.sonatype.com 에 가입하고 도메인 TXT 레코드로 `com.naurylab` 네임스페이스를 인증합니다.
2. 서명 키(GPG)를 만들고 공개 키를 키 서버에 올립니다.

   ```bash
   brew install gnupg
   gpg --full-generate-key
   gpg --list-secret-keys --keyid-format=long
   gpg --keyserver keyserver.ubuntu.com --send-keys <키ID>
   ```

3. 포털의 계정 메뉴에서 **View Account** → **Generate User Token**을 눌러 배포용 토큰을 만듭니다.
4. 토큰과 키 정보를 사용자 홈의 `~/.gradle/gradle.properties`에 넣습니다. **저장소 밖**에 있는 파일입니다.

   ```properties
   centralPortalUsername=<토큰 사용자명>
   centralPortalPassword=<토큰 비밀번호>
   signing.gnupg.keyName=<키ID>
   signing.gnupg.passphrase=<키 비밀번호>
   ```

   CI처럼 gpg 대신 키 파일 내용으로 서명해야 하면 `signingInMemoryKey`와 `signingInMemoryKeyPassword`를 씁니다. CI에서는 `ORG_GRADLE_PROJECT_signingInMemoryKey` 같은 환경 변수로 넣습니다.

서명 키가 있으면 `publishToMavenLocal`로 올린 결과물에도 서명이 붙습니다. 키가 없으면 서명 없이 로컬 저장소에 올라가므로 CI의 consumer-check 빌드는 그대로 동작합니다.

## 배포하기

1. `gradle.properties`의 `KINETICA_VERSION`을 이번 버전으로 바꾸고, [CHANGELOG.md](../CHANGELOG.md)의 "다음 버전" 제목도 그 버전으로 바꿉니다. README와 docs에 적힌 의존성 버전도 맞춥니다.
2. 검증한 뒤 배포합니다.

   ```bash
   ./gradlew check :sample-showcase:assembleRelease
   ./gradlew publishToMavenLocal && ./gradlew -p consumer-check :app:assembleRelease
   ./gradlew cleanCentralStaging publishToCentral --no-configuration-cache
   ```

   - `publishToCentral`은 14개 모듈에 서명하고 `build/central-bundle.zip`으로 묶어 Central Portal에 올립니다. 서명되지 않은 파일이 하나라도 있으면 업로드하지 않고 멈춥니다.
   - 기본 동작은 **검증 후 대기**입니다. https://central.sonatype.com/publishing/deployments 에서 Validated 상태를 확인하고 **Publish**를 누르면 공개됩니다. 문제가 있으면 **Drop**한 뒤 고쳐서 다시 올립니다.
   - 확인 단계 없이 바로 공개하려면 `-PcentralAutoPublish=true`를 붙입니다.
   - 공개한 뒤 search.maven.org 검색에 나타나기까지 보통 10~30분 걸립니다.
   - gpg가 병렬 서명에서 실패하면 `--max-workers=1`을 붙여 다시 실행합니다.

3. 배포한 커밋에 태그를 달고 push합니다. 샘플 앱 APK도 GitHub Releases에 함께 올려야 README의 내려받기 링크가 동작합니다.

   ```bash
   git tag v1.0.0-alpha01
   git push origin main v1.0.0-alpha01
   cp sample-showcase/build/outputs/apk/release/sample-showcase-release.apk build/kinetica-showcase.apk
   gh release create v1.0.0-alpha01 build/kinetica-showcase.apk --title "1.0.0-alpha01" --notes-file <(sed -n '/^## 1.0.0-alpha01/,/^## /p' CHANGELOG.md | sed '1d;$d')
   ```

업로드하지 않고 번들만 만들어 내용을 확인하려면 `./gradlew cleanCentralStaging centralBundle --no-configuration-cache`를 실행합니다.

## 버전 규칙

[Semantic Versioning](https://semver.org/lang/ko/)을 따르며, 버전은 `주.부.수` 형식입니다.

| 바뀐 내용 | 올리는 자리 | 예 |
| --- | --- | --- |
| 호스트 코드를 고쳐야 하는 API 변경 | 주 | 1.4.2 → 2.0.0 |
| 기능 추가(기존 코드가 그대로 동작함) | 부 | 1.4.2 → 1.5.0 |
| 버그 수정 | 수 | 1.4.2 → 1.4.3 |

- 정식 버전 전에는 `-alpha01`, `-beta01`, `-rc01`을 붙입니다. 알파 기간에는 공개 API가 바뀔 수 있습니다.
- Maven Central에 한 번 올린 버전은 **지우거나 덮어쓸 수 없습니다.** 잘못 올렸다면 다음 번호로 고쳐서 다시 올립니다.
- 모든 모듈은 `KINETICA_VERSION` 하나로 관리하며, 항상 같은 버전으로 함께 배포합니다.
- 장면 JSON의 스키마 버전(`schemaVersion`)은 SDK 버전과 따로 관리합니다.

## 문제 해결

| 증상 | 원인과 해결 방법 |
| --- | --- |
| `서명되지 않은 파일이 있습니다` | `~/.gradle/gradle.properties`에 서명 설정이 없거나 틀렸습니다. `gpg --list-secret-keys`로 키ID를 확인합니다 |
| 업로드 401/403 | 토큰이 틀렸거나 토큰 대신 계정 비밀번호를 넣었습니다. 포털에서 User Token을 다시 만듭니다 |
| 검증 실패: namespace not verified | `com.naurylab` 네임스페이스 인증이 아직 끝나지 않았습니다 |
| 검증 실패: invalid signature | 공개 키를 키 서버에 올리지 않았습니다 |
| 검증 실패: version already exists | 이미 올린 버전입니다. `KINETICA_VERSION`을 올려서 다시 배포합니다 |
