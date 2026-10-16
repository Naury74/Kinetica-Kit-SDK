# Contributing

## 빌드

JDK 25를 씁니다. Android Studio에 들어 있는 JBR을 쓰면 됩니다.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew check :sample-showcase:assembleDebug :sample-showcase:assembleRelease
```

PR을 올리기 전에 위 명령이 통과해야 합니다. CI(`.github/workflows/ci.yml`)에서도 같은 명령과 소비자 확인 빌드를 실행합니다. 코드 스타일은 `./gradlew ktlintFormat`으로 맞춥니다.

- JVM 모듈의 공개 ABI는 `<모듈>/api/<모듈>.api`에 고정되어 있습니다. 의도한 변경이라면 `./gradlew updateKotlinAbi`로 파일을 갱신해 함께 커밋하고, CHANGELOG에도 적습니다.
- 재생기(`kinetica-player-compose`)의 release 런타임 클래스패스에는 재생에 필요한 모듈(render, motion, serialization, core)만 있어야 합니다. `verifyReleaseClasspath`가 이를 검사합니다.

## 작업 흐름

1. `main`에서 티켓마다 브랜치를 만듭니다. 예: `feat/KIN-105-pointer-fsm`, `fix/KIN-503-json-depth`
2. 커밋 하나에는 변경 의도 하나만 담습니다. [Conventional Commits](https://www.conventionalcommits.org/) 형식을 따르고, 제목은 한국어로, scope는 모듈 이름으로 씁니다.

```text
feat(core): 회전한 도형 hit test
fix(serialization): 깊은 JSON 중첩 거부
test(motion): seek 경계 확인
build: Kotlin 2.4.21로 올림
```

| scope | 모듈 |
| --- | --- |
| `core` · `history` · `motion` · `guides` · `presets` · `serialization` · `java` | `kinetica-*` JVM 모듈 |
| `ui` · `board` · `render` · `gestures` · `editor` · `player` · `export` | Android 모듈 |
| `sample` | sample-showcase |
| `build` · `ci` | 빌드 설정, CI |

## 규칙

- core, history, motion, guides, presets, serialization 모듈에는 Android나 Compose 타입을 넣지 않습니다(docs 02).
- 문서는 명령으로만 바꾸고, 명령을 적용한 결과는 전체 검증을 통과해야 합니다. 검증에서 거부되면 문서를 바꾸지 않습니다.
- 제스처 하나가 트랜잭션 하나, 즉 실행 취소 한 단계입니다. 프레임마다 기록을 쌓지 않습니다.
- 애니메이션 값은 상태 없이 시각만으로 계산합니다. 프레임마다 값을 누적하지 않습니다.
- 외부 문서는 신뢰하지 않습니다. 새 필드를 읽을 때는 상한, 유한한 숫자인지, 형식을 확인하고 적대적 입력 테스트를 추가합니다.
- 주석과 KDoc은 한국어로 씁니다. KDoc은 공개 API 계약에만 달고 단위, 범위, 실패 조건을 적습니다. 그 밖의 주석에는 코드만 보고는 알 수 없는 이유만 적습니다.
- 티켓 번호와 제거 조건이 없는 `TODO`/`FIXME`는 남기지 않습니다. 눌러도 아무 일이 없는 버튼이나 성공한 척하는 stub도 두지 않습니다.
- 의존성 버전은 `gradle/libs.versions.toml`에서만 관리하고, 버전 변경은 기능 변경과 다른 커밋으로 나눕니다.
- 모듈 간 의존성을 추가하거나 detekt, lint 규칙을 완화할 때는 `docs/adr`에 이유를 남깁니다.
- 로그와 오류 메시지에 장면의 텍스트, 이름, URI를 넣지 않습니다.
- 기기에서 직접 확인하지 않은 동작을 "검증 완료"라고 적지 않습니다.

## 저장소에 넣지 않는 것

빌드 산출물, APK/AAB, keystore와 서명 정보, 로컬 설정(`local.properties`), 테스트와 lint 리포트, IDE 설정은 커밋하지 않습니다.
