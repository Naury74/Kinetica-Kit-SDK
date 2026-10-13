# ADR-0001: 정적 분석 기본값 조정

상태: 채택

## 배경

ktlint·detekt 기본 규칙을 CI에서 강제한다(`./gradlew check`). 일부 기본값은 이 SDK의 코드 형태와 맞지 않아
같은 의미의 코드를 억지로 쪼개게 만든다.

## 결정

`config/detekt/detekt.yml`에 덮어쓰는 값만 둔다.

- `MaxLineLength` 140: `.editorconfig`의 ktlint 값과 맞춘다.
- `MagicNumber`: 테스트·testFixtures는 제외한다. 기대값을 상수로 빼면 테스트가 읽기 어려워진다.
- `ReturnCount` 3, guard clause 제외.
- `TooManyFunctions`: 파일 20, 클래스·object 15. 저장 형식 변환(`kinetica-serialization`)은 필드 묶음마다 작은 함수가 하나씩 있어 기본값(11)을 넘는다.
- 명령 종류별로 한 갈래씩인 `when` 분기(`SceneReducer.apply`)는 해당 함수에만 `@Suppress("CyclomaticComplexMethod")`와 이유 주석을 단다.

## 결과

규칙을 끄지 않고 기준만 조정하므로 새 코드도 같은 검사를 받는다. 기준을 더 바꾸려면 이 ADR을 고친다.
