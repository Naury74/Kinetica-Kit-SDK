# ADR-0001: 정적 분석 기본값 조정

상태: 채택

## 배경

CI에서는 `./gradlew check`로 ktlint와 detekt 기본 규칙을 강제한다. 그런데 일부 기본값은 이 SDK의 코드 형태와 맞지 않아,
의미상 하나인 코드를 억지로 쪼개게 만든다.

## 결정

`config/detekt/detekt.yml`에는 기본값을 덮어쓰는 항목만 둔다.

- `MaxLineLength` 140: `.editorconfig`의 ktlint 값과 맞춘다.
- `MagicNumber`: 테스트·testFixtures는 제외한다. 기대값을 상수로 빼면 테스트가 읽기 어려워진다.
- `ReturnCount`: 3으로 올리고, guard clause는 세지 않는다.
- `TooManyFunctions`: 파일은 20, 클래스와 object는 15로 둔다. 저장 형식 변환 코드(`kinetica-serialization`)는 필드 그룹마다 작은 함수가 하나씩 있어 기본값(11)을 넘는다.
- 명령 종류마다 분기가 하나씩 있는 `when`(`SceneReducer.apply`)은 그 함수에만 `@Suppress("CyclomaticComplexMethod")`와 이유를 적은 주석을 단다.

## 결과

규칙을 끄지 않고 기준만 조정하므로 새 코드도 같은 검사를 받는다. 기준을 더 바꾸려면 이 ADR을 고친다.
