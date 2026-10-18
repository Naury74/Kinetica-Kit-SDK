# Maven Central 배포

Kinetica SDK 모듈 14개를 `com.naurylab:<모듈>:<버전>` 좌표로 Maven Central에 올리는 방법입니다. `sample-showcase`, `benchmarks`, `consumer-check`는 배포하지 않습니다.

빌드 설정은 이미 들어 있습니다. FrameKit과 같은 `com.naurylab` 네임스페이스, 서명 키, 토큰을 쓰므로 FrameKit을 배포해 본 컴퓨터라면 [처음 한 번 준비](#처음-한-번-준비)를 건너뛰고 [배포하기](#배포하기)부터 보면 됩니다.

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
4. 토큰과 키 정보를 `~/.gradle/gradle.properties`에 넣습니다. 저장소 밖에 있는 파일이라 커밋되지 않습니다.

   ```properties
   centralPortalUsername=<토큰 사용자명>
   centralPortalPassword=<토큰 비밀번호>
   signing.gnupg.keyName=<키ID>
   signing.gnupg.passphrase=<키 비밀번호>
   ```

   gpg가 없는 CI에서는 ASCII armor 비밀 키 내용을 `signingInMemoryKey`와 `signingInMemoryKeyPassword`로 넘깁니다. 환경 변수라면 `ORG_GRADLE_PROJECT_signingInMemoryKey`처럼 앞에 `ORG_GRADLE_PROJECT_`를 붙입니다. 두 방식이 다 있으면 `signingInMemoryKey`를 씁니다.

서명 키가 있으면 `publishToMavenLocal` 결과에도 서명이 붙습니다. 키가 없으면 서명 없이 로컬 저장소에 올라가므로, 키가 없는 CI에서도 consumer-check 빌드는 그대로 돕니다.

## 배포하기

1. 루트 `gradle.properties`의 `KINETICA_VERSION`을 이번 버전으로 바꿉니다. [CHANGELOG.md](../CHANGELOG.md)에 "다음 버전"으로 적어 둔 제목이 있으면 그 버전 번호로 바꾸고, README와 docs에 적힌 의존성 버전도 맞춥니다.
2. 검사를 통과하면 배포합니다.

   ```bash
   ./gradlew check :sample-showcase:assembleRelease
   ./gradlew publishToMavenLocal && ./gradlew -p consumer-check :app:assembleRelease
   ./gradlew cleanCentralStaging publishToCentral --no-configuration-cache
   ```

   - `publishToCentral`은 14개 모듈에 서명해 `build/central-bundle.zip`으로 묶고 Central Portal에 올립니다. 서명되지 않은 파일이 하나라도 있으면 올리지 않고 멈춥니다.
   - 기본은 검증 후 대기(`USER_MANAGED`)입니다. https://central.sonatype.com/publishing/deployments 에서 Validated 상태를 확인하고 **Publish**를 눌러야 공개됩니다. 문제가 있으면 **Drop**하고 고쳐서 다시 올립니다.
   - 확인 없이 바로 공개하려면 `-PcentralAutoPublish=true`를 붙입니다. 공개는 되돌릴 수 없습니다.
   - 공개한 뒤 search.maven.org에서 검색되기까지 보통 10~30분 걸립니다.
   - gpg가 병렬 서명에서 실패하면 `--max-workers=1`을 붙여 다시 실행합니다.

3. 배포한 커밋에 태그를 달고 push합니다. README의 내려받기 링크가 `releases/latest/download/kinetica-showcase.apk`를 가리키므로, 샘플 앱 APK를 이 이름으로 GitHub Release에 함께 올립니다.

   ```bash
   git tag v1.0.0-alpha02
   git push origin main v1.0.0-alpha02
   cp sample-showcase/build/outputs/apk/release/sample-showcase-release.apk build/kinetica-showcase.apk
   gh release create v1.0.0-alpha02 build/kinetica-showcase.apk --title "1.0.0-alpha02" \
     --notes-file <(awk '/^## /{if (p) exit; if ($0 == "## 1.0.0-alpha02") {p = 1; next}} p' CHANGELOG.md)
   ```

올리지 않고 번들만 만들어 내용을 보려면 `./gradlew cleanCentralStaging centralBundle --no-configuration-cache`를 실행합니다.

## 배포 전에 다른 프로젝트에서 시험하기

공식 배포 없이 고친 판을 다른 프로젝트에서 바로 받아 보려면 개발 판을 씁니다. mavenLocal과 따로 두는 파일 저장소라서, 받는 쪽이 켰을 때만 쓰이고 올리는 즉시 반영됩니다.

```bash
./gradlew publishDev                    # 커밋한 상태에서만 올린다
./gradlew publishDev -PallowDirty=true  # 커밋하지 않은 변경도 올린다
```

- 14개 모듈을 `gradle.properties`의 `KINETICA_DEV_VERSION`(예: `1.0.0-alpha03-SNAPSHOT`)으로 `~/.naurylab/dev-repo`에 서명 없이 올립니다. 위치는 `-PnaurylabDevRepo=<경로>`로 바꿉니다.
- 어느 커밋에서 올렸는지 저장소 맨 위의 `kinetica-dev.txt`에 남습니다. 커밋하지 않은 변경을 올렸다면 커밋 뒤에 `-dirty`가 붙습니다.

받는 쪽 프로젝트에는 한 번만 아래처럼 적어 둡니다. `-PnaurylabDev=true`를 줄 때만 개발 판 저장소를 보고, 그 저장소에서는 `com.naurylab` 묶음만 받습니다.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        if (providers.gradleProperty("naurylabDev").orNull == "true") {
            maven {
                url = uri("${System.getProperty("user.home")}/.naurylab/dev-repo")
                content { includeGroup("com.naurylab") }
            }
        }
        google()
        mavenCentral()
    }
}

// app/build.gradle.kts
val kineticaVersion = providers.gradleProperty("kineticaVersion").getOrElse("1.0.0-alpha02")
dependencies {
    implementation("com.naurylab:kinetica-ui:$kineticaVersion")
}
```

```bash
./gradlew :app:assembleDebug -PnaurylabDev=true -PkineticaVersion=1.0.0-alpha03-SNAPSHOT
```

플래그를 빼면 Maven Central의 정식 판으로 돌아갑니다. 파일 저장소는 Gradle이 캐시하지 않으므로 `--refresh-dependencies` 없이도 새로 올린 판을 받습니다.

## 버전 규칙

[Semantic Versioning](https://semver.org/lang/ko/)을 따릅니다. 버전은 `주.부.수` 형식입니다.

| 바뀐 내용 | 올리는 자리 | 예 |
| --- | --- | --- |
| 호스트 코드를 고쳐야 하는 API 변경 | 주 | 1.4.2 → 2.0.0 |
| 기존 코드가 그대로 도는 기능 추가 | 부 | 1.4.2 → 1.5.0 |
| 버그 수정 | 수 | 1.4.2 → 1.4.3 |

- 정식 버전 전에는 `-alpha01`, `-beta01`, `-rc01`을 붙입니다. 알파 기간에는 공개 API가 바뀔 수 있습니다.
- Maven Central에 한 번 올린 버전은 **지우거나 덮어쓸 수 없습니다.** 잘못 올렸다면 고쳐서 다음 번호로 올립니다.
- 모든 모듈은 `KINETICA_VERSION` 하나를 쓰고, 늘 같은 버전으로 함께 배포합니다.
- 장면 JSON의 스키마 버전(`schemaVersion`)은 SDK 버전과 따로 관리합니다.

### 알파 기간의 공개 API 약속

다른 SDK가 Kinetica를 안에 넣어 배포하므로, 알파 기간에도 공개 API는 아래처럼 바꿉니다.

- 공개 클래스, 함수, 파라미터의 이름을 바꾸거나 지우지 않고, 옛 이름을 `@Deprecated(ReplaceWith(...))`로 남긴 채 새 이름을 더합니다. 옛 이름은 적어도 다음 알파 버전까지 그대로 동작하고, 그 뒤에 지웁니다.
- 파라미터는 기본값을 둔 채 맨 뒤에 더하거나, 기존 위치 인자의 순서를 바꾸지 않는 이름 있는 인자로만 더합니다.
- 기본 동작이 눈에 띄게 바뀌면 CHANGELOG의 해당 버전 "바뀐 것"에 적습니다.
- 위 약속을 지킬 수 없는 변경은 CHANGELOG에 따로 표시하고, Kinetica를 쓰는 프로젝트에 먼저 알립니다.

### 배포 주기

정해진 주기 없이, 다른 프로젝트가 정식 판으로 받아야 할 변경이 생기면 그때 배포합니다. 그전까지는 개발 판(`publishDev`)으로 주고받습니다.

## 문제 해결

| 증상 | 원인과 해결 방법 |
| --- | --- |
| `서명되지 않은 파일이 있습니다` | `~/.gradle/gradle.properties`에 서명 설정이 없거나 틀렸습니다. `gpg --list-secret-keys`로 키ID를 확인합니다 |
| `centralPortalUsername / centralPortalPassword가 없습니다` | `~/.gradle/gradle.properties`에 토큰을 넣지 않았습니다 |
| 업로드 401/403 | 토큰이 틀렸거나 토큰 대신 계정 비밀번호를 넣었습니다. 포털에서 User Token을 다시 만듭니다 |
| 검증 실패: namespace not verified | `com.naurylab` 네임스페이스 인증이 아직 끝나지 않았습니다 |
| 검증 실패: invalid signature | 공개 키를 키 서버에 올리지 않았습니다 |
| 검증 실패: version already exists | 이미 올린 버전입니다. `KINETICA_VERSION`을 올려서 다시 배포합니다 |
