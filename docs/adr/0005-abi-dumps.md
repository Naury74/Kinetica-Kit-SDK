# ADR-0005: 공개 API dump는 Kotlin 내장 ABI 검사로 관리한다

상태: 채택

## 배경

docs 03은 공개 API의 바이너리 호환성 dump를 두라고 한다. Kotlin 2.4에는 ABI 검사(`abiValidation`)가 들어 있어
binary-compatibility-validator 같은 별도 Gradle 플러그인이 필요 없다.

## 결정

- JVM 라이브러리 convention 플러그인에서 `abiValidation`을 켠다. 모듈마다 `api/<모듈>.api`를 기준으로 삼고, `check`가 `checkKotlinAbi`를 실행한다.
- 공개 API를 바꾸면 `./gradlew updateKotlinAbi`로 dump를 갱신해 같은 커밋에 넣는다. 리뷰어는 dump diff로 API 변경을 본다.
- Android 라이브러리(render, gestures, editor, player, export)는 AGP 9에 내장된 Kotlin에서 이 기능이 동작하지 않아(`Cannot query the value of this provider`) 아직 dump가 없다.
  당분간 explicit API mode로 공개 범위를 관리하고, AGP나 Kotlin이 지원하게 되면 같은 방식으로 켠다.

## 결과

- core, history, motion, guides, presets, serialization, java 모듈에서 의도하지 않은 API 변경이 생기면 CI가 실패한다.
- Android 모듈의 API 변경은 리뷰로만 잡을 수 있다. 이 위험은 남아 있다.
