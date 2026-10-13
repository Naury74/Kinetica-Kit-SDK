# Contributing

## 빌드

JDK 25(Android Studio에 포함된 JBR)를 씁니다.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew check :sample-studio:assembleDebug :sample-studio:assembleRelease :sample-gallery:assembleRelease
```

PR 전에 위 명령이 통과해야 합니다. 같은 명령과 소비자 확인 빌드가 CI(`.github/workflows/ci.yml`)에서 실행됩니다. 형식은 `./gradlew ktlintFormat`으로 고칩니다.

- JVM 모듈의 공개 ABI는 `<모듈>/api/<모듈>.api`에 고정되어 있습니다. 의도한 변경이면 `./gradlew updateKotlinAbi`로 갱신해 함께 커밋하고 CHANGELOG에 적습니다.
- `sample-gallery`의 release 런타임에는 재생에 필요한 모듈(player·render·motion·core)만 있어야 합니다(`verifyReleaseClasspath`).

## 작업 흐름

1. `main`에서 티켓 단위 branch를 만듭니다: `feat/KIN-105-pointer-fsm`, `fix/KIN-503-json-depth`
2. 커밋 하나에 의도 하나. [Conventional Commits](https://www.conventionalcommits.org/)를 쓰고 제목은 한국어, scope는 모듈입니다.

```text
feat(core): 회전한 도형 hit test
fix(serialization): 깊은 JSON 중첩 거부
test(motion): seek 경계 확인
build: Kotlin 2.4.21로 올림
```

| scope | 모듈 |
| --- | --- |
| `core` · `history` · `motion` · `guides` · `presets` · `serialization` · `java` | `kinetica-*` JVM 모듈 |
| `render` · `gestures` · `editor` · `player` · `export` | Android 모듈 |
| `sample` | sample-studio, sample-gallery |
| `build` · `ci` | 빌드 설정, CI |

## 규칙

- core·history·motion·guides·presets·serialization에는 Android·Compose 타입을 넣지 않습니다(docs 02).
- 문서는 명령으로만 바꾸고, 명령 결과는 전체 검증을 통과해야 합니다. 거부되면 문서를 그대로 둡니다.
- 제스처 하나는 트랜잭션 하나(실행 취소 한 단계)입니다. 프레임마다 기록을 쌓지 않습니다.
- 애니메이션 평가는 상태 없이 시각만으로 결정합니다. 프레임마다 값을 누적하지 않습니다.
- 외부 문서는 신뢰하지 않습니다. 새 필드를 읽을 때는 상한·유한값·형식을 확인하고 적대적 입력 테스트를 더합니다.
- 주석과 KDoc은 한국어로 씁니다. KDoc은 공개 API 계약(단위·범위·실패 조건)에만, 그 밖의 주석은 코드가 설명하지 못하는 이유만 씁니다.
- 티켓 번호와 제거 기준 없는 `TODO`/`FIXME`를 남기지 않습니다. 효과 없는 버튼이나 성공을 가장하는 stub을 두지 않습니다.
- 버전은 `gradle/libs.versions.toml`에서만 관리하고 버전 변경은 기능 변경과 섞지 않습니다.
- 모듈 사이 의존을 더하거나 detekt·lint 규칙을 완화하려면 `docs/adr`에 이유를 남깁니다.
- 로그·오류 메시지에 장면 글자·이름·URI를 넣지 않습니다.
- 기기에서 확인하지 않은 동작을 "검증 완료"로 적지 않습니다.

## 저장소에 넣지 않는 것

빌드 산출물, APK/AAB, keystore, 서명 정보, 로컬 설정(`local.properties`), 테스트·lint 리포트, IDE 설정.
