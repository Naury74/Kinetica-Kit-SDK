# 검증 가이드

Kinetica가 무엇을 하는지, SDK로 쓸 만한지 직접 확인하기 위한 순서입니다. 각 항목에 해 볼 것, 기대 결과, 같은 동작을 확인하는 자동 테스트를 적었습니다.
"기대 결과"와 다르면 그 자리에서 메모해 두세요.

## 0. 준비

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew check                                   # 테스트 250개·lint·형식·공개 API 검사
./gradlew :sample-studio:installDebug :sample-gallery:installDebug
```

- 기기: Android 8.0(API 26) 이상. 지금까지는 에뮬레이터(Android 16, 펼친 폴더블)에서만 확인했습니다.
  그 에뮬레이터에서 1·3·4·5·10·11·12·13·15·16·17(저장까지)·18·19·20·23·24·25를 손으로 확인했고, 사진·글자 수정은 다시 실행 뒤에도 남았습니다.
- 앱 두 개가 설치됩니다. **Kinetica Studio**(편집기 전체), **Kinetica Gallery**(재생기만 쓰는 앱).
- 넓은 화면(600dp 이상)은 오른쪽에 레이어·속성 패널이 붙고, 좁은 화면은 도구 줄의 "레이어"·"속성" 버튼으로 아래 시트를 엽니다.

## 1. 기능 — Kinetica Studio

| # | 해 볼 것 | 기대 결과 | 자동 테스트 |
| --- | --- | --- | --- |
| 1 | 도구 줄 **예제** | 글자·카드·원·곡선·칩 그룹이 있는 장면이 열림 | `DemoSceneTest` |
| 2 | **사각형·타원·글자**로 추가 | 보이는 영역 가운데에 추가되고 선택됨 | `KineticaEditorTest` |
| 3 | 도형 끌기 | 손가락을 따라 이동, 다른 도형·artboard 가장자리·가운데 근처에서 보라색 안내선과 함께 붙음 | `CanvasGestureControllerTest`, `MoveSnapperTest` |
| 4 | 선택 후 모서리·변 손잡이 끌기 | 반대쪽 손잡이는 제자리, 크기만 바뀜. 회전한 도형도 마찬가지 | `TransformOpsTest`(2,400가지 무작위) |
| 5 | 위쪽 동그란 손잡이 끌기 | 가운데를 축으로 회전 | `CanvasGestureControllerTest` |
| 6 | 빈 곳에서 끌기 | 사각 영역에 걸친 도형이 함께 선택됨 | 같음 |
| 7 | 두 손가락 벌리기·이동, 마우스 휠 | 손가락 사이 지점이 고정된 채 확대·이동 | `ViewportTest`, `CanvasGestureControllerTest` |
| 8 | 둘 이상 선택 → 선택 줄 **그룹**, 다시 **그룹 해제** | 화면 위치 그대로 묶이고 풀림 | `GroupCommandTest` |
| 9 | 선택 줄 **정렬** 메뉴(맞춤·간격 같게), **앞으로·뒤로** | 선택 범위(하나면 artboard) 기준 정렬, 순서 이동 | `ArrangeOpsTest`, `EditorActionsTest` |
| 10 | 속성 패널에서 X·Y·너비·높이·회전·불투명도·채우기 입력 | 완료 키나 포커스 이동 시 적용. `abc` 같은 값은 거부하고 이전 값 유지 | `PropertyEditsTest`, `EditorPanelsTest` |
| 11 | 글자를 고르고 속성 패널 **내용**·**글자 크기** 수정 | 캔버스 글자가 바뀜 | `PropertyEditsTest` |
| 12 | 레이어 패널 **보임**·**잠금** | 숨기면 안 그려지고, 잠그면 캔버스에서 잡히지 않음 | `EditorPanelsTest`, `HitTesterTest` |
| 13 | **실행 취소·다시 실행** (Ctrl+Z / Ctrl+Shift+Z) | 끌기 한 번이 한 단계. 여러 번 되돌리면 처음 장면 | `KineticaSessionTest`(무작위 1,000회) |
| 14 | 키보드(외부 키보드·에뮬레이터): 화살표, Shift+화살표, Delete, Esc | 1·10 단위 이동, 삭제, 선택 해제 | — (수동) |
| 15 | **사진** → 사진 고르기 | 원본 비율로 들어옴. 큰 사진도 메모리 상한(4 MP)으로 줄여 읽음 | `ContentImageLoaderTest` |
| 16 | **저장** → 앱 완전 종료 → 다시 실행 | 같은 장면이 열림(실행 취소 기록은 새로 시작) | `FileSceneRepositoryTest` |
| 17 | **PNG 내보내기** → 위치 고르기 | 1080 px 너비 PNG. 화면과 같은 모습 | `PngExporterTest` |

### 모션

| # | 해 볼 것 | 기대 결과 | 자동 테스트 |
| --- | --- | --- | --- |
| 18 | 예제 장면에서 **모션** 켜기 → **재생** | 글자가 올라오며 나타나고, 카드가 튕기며 커지고, 원이 돌며 색이 바뀌고, 칩이 차례로 올라옴 | `TimelineEvaluatorTest`, `EasingTest` |
| 19 | 눈금자를 누르거나 끌기 | 그 시각의 장면이 보임(캔버스는 움직이지 않음) | `TimelinePanelTest` |
| 20 | 도형을 고르고 **X +** → 눈금자 1초 → 캔버스에서 도형 끌기 | 1초에 X 키가 자동으로 생기고, 재생하면 두 키 사이를 움직임 | `AutoKeyTest`, `TimelinePanelTest` |
| 21 | 키 다이아몬드 끌기, 누른 뒤 easing·**키 삭제** | 시간 이동(같은 시각과 겹치면 거부), 곡선 변경, 삭제 | `TimelineCommandTest` |
| 22 | 여러 도형 선택 → **프리셋** → 차례로 등장 | 재생 위치부터 80 ms씩 늦게 나타남. 실행 취소 한 번에 사라짐 | `PresetRegistryTest`, `AutoKeyTest` |

## 2. 기능 — Kinetica Gallery (재생기만 쓰는 앱)

| # | 해 볼 것 | 기대 결과 | 자동 테스트 |
| --- | --- | --- | --- |
| 23 | 카드 누르기 | 카드가 상세 화면으로 이어지며 바뀜(같은 morphKey끼리 보간, 본문은 나타남) | `SceneMorphTest` |
| 24 | 상세에서 조금 끌고 놓기 | 다시 열린 상태로 돌아감 | `TransitionDriverTest`(35%) |
| 25 | 크게 아래로 끌기 / 빠르게 튕기기 / 뒤로 가기 | 닫히고 목록으로 돌아감(한 번만) | `TransitionDriverTest`(80%·속도), `SceneTransitionTest` |
| 26 | 오른쪽 위 **View 화면 예** | Compose가 아닌 View Activity 안에서 재생기가 그려짐 | — (수동) |
| 27 | 회전·다른 앱 다녀오기 | 보던 화면 유지 | — (수동) |

## 3. SDK로서 쓸 만한지

| 관점 | 확인 방법 | 지금 상태 |
| --- | --- | --- |
| 연동 난이도 | [README 연동](../README.md#연동)의 재생기 예를 새 앱에 붙여 보기 | 의존성 1줄 + 코드 10줄 안팎. `consumer-check/`가 같은 코드를 배포 artifact만으로 빌드 |
| 필요한 것만 쓰기 | `./gradlew :sample-gallery:verifyReleaseClasspath` | 재생만 쓰면 core·motion·render·player 4개만 들어감 |
| 앱 크기 | `consumer-check` release APK | 약 680 KB(Compose 런타임 포함, R8). Kinetica만의 몫은 따로 재지 않음 |
| R8·난독화 | `./gradlew -p consumer-check :app:assembleRelease` | 추가 keep 규칙 없이 빌드됨(리플렉션 없음) |
| 설계 문서 | [architecture.md](architecture.md), [integration.md](integration.md), [adr/](adr) | 모듈 경계·좌표계·데이터 흐름·결정 이유 |
| 공개 API 관리 | `<모듈>/api/*.api` | JVM 7개 모듈은 자동 검사. Android 5개 모듈은 아직 없음(ADR-0005) |
| 안정성 | `./gradlew check` | 테스트 250개(단위·Robolectric 화면·적대적 입력 fuzz) |
| 안전 | [SECURITY.md](../SECURITY.md) | 네트워크 없음, 외부 문서 상한·검증, 웹·파일 URI 거부 |
| 성능 | [performance.md](performance.md) | JVM에서 노드 2,000개 명령 p95 약 0.4 ms. 실기기 미측정 |
| Java | [integration.md#java](integration.md#java) | `kinetica-java` 정적 입구, Java 테스트 있음 |

## 4. 알려진 제한 (검증 때 막힐 수 있는 것)

- 캔버스에서 글자를 바로 고칠 수 없고 속성 패널에서 고칩니다.
- 그룹 안 도형은 캔버스에서 하나만 고를 수 없습니다(레이어 패널에서 고름).
- 여러 도형을 한꺼번에 크기 조절·회전할 수 없습니다.
- timeline 확대, 여러 키 한꺼번에 끌기, easing 곡선 편집기가 없습니다.
- 그룹은 자식을 자르지 않고, 그룹 불투명도는 자식마다 곱합니다(겹친 자식이 더 진해 보임).
- API 26·27에서는 사진의 EXIF 회전을 반영하지 않습니다.
- 실기기 성능, TalkBack, 저사양 기기는 확인하지 않았습니다.

## 5. 판단을 위한 질문

- 내 앱(또는 고객 앱)에 **앱 안 편집기**가 필요한가, 아니면 **재생만** 필요한가? 재생만이면 Lottie·Rive와 비교가 필요합니다.
- 문서 모델(명령·검증·실행 취소·JSON)을 직접 다룰 일이 있는가? Kinetica의 강점은 이 부분입니다.
- 위 제한 중 출시를 막는 것은 무엇인가?
