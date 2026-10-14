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

### 호스트 UI 조립 (ADR-0006)

| # | 해 볼 것 | 기대 결과 | 자동 테스트 |
| --- | --- | --- | --- |
| H1 | 도구 줄 **대시보드** | 인사말·매출·주문 카드, 매출 그래프·시계·날씨 위젯, 새로고침 버튼이 있는 화면이 열림 | `DashboardSceneTest` |
| H2 | 2초 기다리기 | 매출·주문 숫자가 바뀌고 그래프 막대가 움직임(호스트 데이터 → 바인딩) | `HostComposerTest` |
| H3 | 위젯을 끌어 옮기고 크기·회전 바꾸기 | 위젯이 살아 있는 채로 따라옴. 편집 중에는 위젯 안 버튼이 눌리지 않음 | `HostComposerTest` |
| H4 | **호스트 뷰** 메뉴 → 카운터 | 카운터 위젯이 들어옴 | `HostComposerTest` |
| H5 | 글자를 두 번 누르기 | 글자 수정 창이 뜨고, 고친 뒤 실행 취소 한 번에 되돌아감 | `HostComposerTest` |
| H6 | 속성 패널 **데이터**: 내용 키·보임 키·탭 동작 | 키를 넣으면 그 데이터 값이 보이고, 동작 이름이 저장됨 | `PropertyEditsTest` |
| H7 | **미리보기** → 카운터 +1, 새로고침 버튼 | 카운터 숫자가 오름(호스트 상태), "호스트가 받은 동작: refresh" 알림과 함께 값이 바뀜 | `PlayerHostTest` |
| H8 | 대시보드에서 "widgets" 레이어 고르기 → 속성 **자동 배치** 가로↔세로 | 위젯이 줄·열로 다시 쌓임. 실행 취소 한 번에 되돌아감 | `AutoLayoutEditorTest`, `LayoutsTest` |
| H9 | 도형 두 개 고르기 → 선택 줄 **자동 배치** | 위치 순서대로 쌓인 Frame으로 감싸지고, 간격·여백·정렬을 속성에서 바꿈 | `AutoLayoutEditorTest`, `LayoutsTest` |
| H10 | 데이터로 "영업 중"이 숨을 때(2초마다 무작위) | "배달 가능"이 빈자리로 당겨지고 카드 높이가 맞춰짐 | `BindingsTest` |
| H11 | **미리보기**(폴더블 펼침·접힘, 가로 화면) | 화면 모양대로 펼쳐짐: 카드·위젯은 반씩 채우고, 그래프·버튼은 좌우로 늘고, 버튼은 아래에 붙음 | `LayoutsTest`, `SceneLayoutTest` |

### 위젯 보드 — Kinetica Board 앱 (docs 31)

| # | 해 볼 것 | 기대 결과 | 자동 테스트 |
| --- | --- | --- | --- |
| B1 | 앱 실행 | 매출·주문·주간 매출·시계 위젯이 빈자리에 채워지고 숫자가 2초마다 바뀜 | `KineticaBoardStateTest` |
| B2 | **편집** → 위젯 끌기 | 손가락을 따라오고 다른 위젯·가장자리에 붙음. 위젯 안 버튼은 눌리지 않음 | `KineticaBoardTest`, `BoardGeometryTest` |
| B3 | 위젯을 화면 오른쪽 끝·모서리·위쪽으로 끌어 놓기 | 반·4분의 1·화면 가득 자리를 미리 보여 주고 그 자리에 들어감. 겹친 위젯은 아래로 밀림 | `KineticaBoardTest`, `BoardGeometryTest` |
| B4 | 오른쪽 아래 손잡이, 왼쪽 위 −, **+ 위젯 추가** | 크기 바뀜, 빠짐, 메모·공지 추가 | `KineticaBoardTest` |
| B5 | **되돌리기·다시** | 끌기 한 번이 한 단계 | `KineticaBoardStateTest` |
| B6 | 메모에 글을 쓰고 다른 위젯 옮기기 | 쓴 글이 그대로 | `SceneLayoutTest` |
| B7 | **격자 배치** | 칸에 맞춰 놓이고 겹치면 아래로 밀림 | `KineticaBoardStateTest` |
| B8 | **완료** → 앱 종료 → 다시 실행, 폴더블 접고 펴기 | 같은 배치. 넓은 화면은 가로 비율대로 펼쳐짐 | `KineticaBoardStateTest` |
| B9 | **XML 화면** | XML View 위젯도 같은 방식으로 편집 | `KineticaBoardViewTest` |
| B10 | 편집 아닐 때 위젯을 길게 누르기 | 편집 모드가 켜짐. 짧게 누르거나 스크롤하면 그대로 동작 | `KineticaBoardTest` |
| B11 | 편집 중 위젯을 화면 아래 끝으로 끌고 머물기 | 보드가 아래로 스크롤되고 위젯이 따라감 | `KineticaBoardTest` |
| B12 | TalkBack 켜고 편집 중 위젯의 동작 메뉴 | 옮기기·크게·작게·빼기 | `KineticaBoardTest` |
| B13 | **+ 위젯 추가** → 메모를 두 번 | 메모 1·메모 2가 따로 생기고 각자 쓴 글이 유지됨 | `KineticaBoardStateTest` |
| B16 | 편집 중 위젯을 다른 위젯 가운데로 끌어 놓기 → 완료 → 묶음 옆으로 넘기기 → 다시 편집에서 숫자 배지 누르기 | 한 자리에서 넘겨 보이고 점 표시. 배지를 누르면 다시 따로 놓임 | `BoardStacksTest`, `KineticaBoardTest` |
| B15 | **효과 예** → 타일·목록·XML 버튼 누르기 | 리플·눌림·기울기, 목록이 차례로 등장, 배지 두근·반짝임·빛 번짐·흔들기. 눌린 수가 올라감 | `NodeEffectsTest`, `ViewEffectsTest` |
| B14 | 키보드 연결 → 편집 중 Tab으로 위젯 고르고 방향키·Shift+방향키·Delete | 옮기기·크기·빼기 | `KineticaBoardTest` |

### 디자인 — Kinetica Studio (docs 32)

| # | 해 볼 것 | 기대 결과 | 자동 테스트 |
| --- | --- | --- | --- |
| D1 | **도형 더보기** → 별·다각형·화살표 | 별·육각형·끝에 화살표 달린 선이 들어옴 | `StyleInspectorTest` |
| D2 | 속성 **채우기** 선형·원형, 시작·끝 색, 각도 | 그라디언트가 바로 보임 | `StyleEditsTest`, `SceneLayoutTest` |
| D3 | **효과** + 바깥 그림자·+ 안쪽 그림자, 퍼짐·흐림 | 그림자가 여러 개 쌓이고 값대로 바뀜 | `StyleEditsTest`, `SceneLayoutTest` |
| D4 | 사각형 **모서리별**, 선 **점선** | 모서리마다 다른 둥글기, 점선 | `StyleEditsTest` |
| D5 | 글자 **글자 수정** → 일부 고르고 굵게·색 | 고른 부분만 바뀌고, 이어 쓰면 구간이 늘어남. 실행 취소 한 번에 되돌아감 | `RichTextTest`, `TextSpansTest` |
| D6 | 속성 **글꼴** 명조·기울임·밑줄 | 글자 전체 모양이 바뀜 | `RichTextTest` |
| D7 | **목업 내보내기** | 휴대폰 틀 안에 장면이 든 PNG | `PngExporterTest` |
| D8 | 빈 곳 눌러 선택 해제 → **기본 키트 넣기**, 토큰 `color.brand` 색 바꾸기 | 키트 9종이 artboard 오른쪽에 생기고 버튼·토글 색이 함께 바뀜 | `ComponentEditorTest`, `UiKitTest` |
| D9 | 카드 원본 **넣기** → 인스턴스 글자 고치기 → 원본 색 바꾸기 | 인스턴스 글자는 유지되고 색은 따라감. **덮어쓴 값 되돌리기**로 원래 글자 | `ComponentsTest` |
| D10 | Frame 고르고 **컴포넌트로** | 원본이 되고 **넣기**로 인스턴스를 넣을 수 있음 | `ComponentEditorTest` |
| D11 | 흐림을 준 도형을 PNG로 내보내기 | 내보낸 그림에도 흐림이 보임 | `SceneCanvasTest` |
| D12 | 자동 배치 Frame **줄 바꿈** 켜기 | 폭을 넘는 자식이 다음 줄로 | `LayoutsTest`, `AutoLayoutEditorTest` |
| D13 | 노드 **크기 제한** 최소 너비 150 | 너비가 150 아래로 줄지 않음 | `LayoutsTest`, `AutoLayoutEditorTest` |
| D14 | 글자 **너비 자동** 후 글자 늘이기 | 상자가 글자에 맞춰 넓어짐 | `RichTextTest`, `LayoutsTest` |
| D15 | 레이어 패널로 자동 배치 안 노드를 고르고 캔버스에서 옆으로 끌기 | 형제 순서가 바뀜, 실행 취소 한 번 | `CanvasGestureControllerTest` |

### 모션

| # | 해 볼 것 | 기대 결과 | 자동 테스트 |
| --- | --- | --- | --- |
| 18 | 예제 장면에서 **모션** 켜기 → **재생** | 글자가 올라오며 나타나고, 카드가 튕기며 커지고, 원이 돌며 색이 바뀌고, 칩이 차례로 올라옴 | `TimelineEvaluatorTest`, `EasingTest` |
| 19 | 눈금자를 누르거나 끌기 | 그 시각의 장면이 보임(캔버스는 움직이지 않음) | `TimelinePanelTest` |
| 20a | 도형을 고르고 timeline **너비**·**모서리** 키 → 다른 시각에서 값 바꾸기 | 재생하면 크기·둥글기가 바뀜 | `StyleAnimationTest` |
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
| 28 | **서버 화면 예** → 서버 B → 새로 고침 | 배너·장바구니 자리가 바뀌고 장바구니 숫자(호스트 뷰 상태)는 유지 | `RemoteKineticaPlayerTest` |
| 29 | 깨진 JSON·오프라인 → 새로 고침 | 화면은 그대로, 아래에 이유 표시. 다시 켜면 캐시가 먼저 보임 | `RemoteSceneLoaderTest` |
| 30 | (코드) `SceneVerifier.signature(publicKey)` | 서명이 없거나 틀린 본문은 `Unverified`로 거절 | `RemoteSceneLoaderTest` |

## 3. SDK로서 쓸 만한지

| 관점 | 확인 방법 | 지금 상태 |
| --- | --- | --- |
| 연동 난이도 | [README 연동](../README.md#연동)의 재생기 예를 새 앱에 붙여 보기 | 의존성 1줄 + 코드 10줄 안팎. `consumer-check/`가 같은 코드를 배포 artifact만으로 빌드 |
| 필요한 것만 쓰기 | `./gradlew :sample-gallery:verifyReleaseClasspath` | 재생만 쓰면 core·motion·render·serialization·player 5개만 들어감 |
| 앱 크기 | `consumer-check` release APK | 약 680 KB(Compose 런타임 포함, R8). Kinetica만의 몫은 따로 재지 않음 |
| R8·난독화 | `./gradlew -p consumer-check :app:assembleRelease` | 추가 keep 규칙 없이 빌드됨(리플렉션 없음) |
| 설계 문서 | [architecture.md](architecture.md), [integration.md](integration.md), [adr/](adr) | 모듈 경계·좌표계·데이터 흐름·결정 이유 |
| 공개 API 관리 | `<모듈>/api/*.api` | JVM 7개 모듈은 자동 검사. Android 6개 모듈은 아직 없음(ADR-0005) |
| 안정성 | `./gradlew check` | 테스트 340여 개(단위·Robolectric 화면·적대적 입력 fuzz) |
| 안전 | [SECURITY.md](../SECURITY.md) | SDK 자체 네트워크 없음(서버 화면은 호스트가 받음), 외부 문서 상한·검증, 웹·파일 URI 거부 |
| 성능 | [performance.md](performance.md) | JVM에서 노드 2,000개 명령 p95 약 0.4 ms. 실기기 미측정 |
| Java | [integration.md#java](integration.md#java) | `kinetica-java` 정적 입구, Java 테스트 있음 |

## 4. 알려진 제한 (검증 때 막힐 수 있는 것)

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
