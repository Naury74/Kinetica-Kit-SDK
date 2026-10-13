# ADR-0005: 공개 API dump는 Kotlin 내장 ABI 검사로 관리한다

상태: 채택

## 배경

docs 03은 공개 API 바이너리 호환 dump를 두라고 한다. 별도 Gradle plugin(binary-compatibility-validator)을 넣지 않고
Kotlin 2.4에 들어 있는 ABI 검사(`abiValidation`)를 쓸 수 있다.

## 결정

- JVM 라이브러리 convention이 `abiValidation`을 켠다. 모듈마다 `api/<모듈>.api`가 기준이며 `check`가 `checkKotlinAbi`를 실행한다.
- 공개 API를 바꾸면 `./gradlew updateKotlinAbi`로 dump를 갱신해 같은 커밋에 넣는다. 리뷰어는 dump 차이로 API 변경을 본다.
- Android 라이브러리(render·gestures·editor·player·export)는 AGP 9 내장 Kotlin에서 이 기능이 동작하지 않아(`Cannot query the value of this provider`) 아직 dump가 없다.
  공개 범위는 explicit API mode로 계속 막고, AGP·Kotlin 지원이 생기면 같은 방식으로 켠다.

## 결과

- core·history·motion·guides·presets·serialization·java의 의도하지 않은 API 변경은 CI에서 실패한다.
- Android 모듈의 API 변경은 리뷰로만 잡는다(남은 위험).
