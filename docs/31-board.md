# 31 — 호스트 위젯 보드 (kinetica-board-compose)

호스트 앱이 자기 화면에 넣어, 사용자가 앱의 위젯을 자유롭게 놓고 크기를 바꾸고 빼고 추가하게 하는 모듈이다.
스튜디오(`KineticaEditor`)는 디자이너·운영자용 도구이고, 보드는 앱 사용자가 앱 안에서 바로 쓰는 편집이다.

## 연동

```kotlin
val catalog = listOf(
    BoardWidget("sales", "오늘 매출", widthDp = 176f, heightDp = 120f),
    BoardWidget("chart", "주간 매출", widthDp = 360f, heightDp = 170f, columnSpan = 2),
    BoardWidget("memo", "메모", placedByDefault = false),          // 추가 메뉴에만 있다
)
val state = remember { KineticaBoardState.create(catalog, BoardArrangement.Free(), saved = prefs.getString("board", null)) }
DisposableEffect(state) { onDispose { prefs.edit().putString("board", state.toJson()).apply(); state.close() } }

KineticaBoard(state, Modifier.fillMaxSize(), editing = editing, permissions = BoardPermissions(remove = false)) { key ->
    when (key) { "sales" -> SalesCard(vm); "chart" -> SalesChart(vm); "memo" -> Memo() }   // 호스트 것 그대로
}
```

XML 화면은 `KineticaBoardView`에 `setBoard(state) { key, context -> View }`, `isEditing`을 쓴다. Java에서도 `KineticaBoardState.create(catalog)`로 부른다.

## 동작

| 상황 | 동작 |
| --- | --- |
| 사용 모드 | 위젯이 저장된 자리에서 평소처럼 동작한다. 화면보다 길면 세로 스크롤. `onLongPress`를 주면 길게 눌러 편집을 시작할 수 있다(짧게 누르기·스크롤은 위젯이 그대로 받는다) |
| 편집 모드 | 끌어 옮기기(다른 위젯 변·간격 자리와 보드 가장자리에 붙음), 오른쪽 아래 손잡이로 크기, 왼쪽 위 표시로 빼기, 오른쪽 아래 버튼으로 추가. 위젯 안은 눌리지 않는다 |
| 자유 배치 분할 | 손가락이 화면 왼쪽·오른쪽 가장자리 40dp 안이면 반, 모서리는 4분의 1, 위쪽은 화면 가득. 놓기 전 자리를 미리 보여 준다 |
| 겹침 | 놓은 위젯과 겹치는 위젯은 겹치지 않을 때까지 아래로 밀린다(이어서 밀림) |
| 격자 배치 | 칸 단위(열 수·간격·칸 높이 비율). 가장 가까운 칸에 맞추고 겹치면 아래로 민다 |
| 처음 배치 | 위에서부터, 같은 줄이면 왼쪽부터 첫 빈자리. 넓은 화면은 옆으로 채운다 |
| 자동 스크롤 | 끄는 손가락이 보이는 영역 위·아래 끝(분할 자리 바로 안쪽 48dp 띠나 그 너머)에 머물면 보드가 스크롤되고 위젯도 따라간다. 지금 내용 아래 한 화면까지만 늘어난다 |
| 접근성 | 편집 모드에서 위젯마다 TalkBack 동작: 위·아래·왼쪽·오른쪽으로 옮기기(8dp, 격자는 한 칸), 크게·작게, 빼기 |
| 여러 개 | `BoardWidget(multiple = true)`면 같은 위젯을 여러 번 추가한다. 자리 id는 `memo`, `memo.2`…이며 `content(id)`로 받고, 위젯 키는 `state.widgetKeyOf(id)` |
| 키보드 | 편집 모드에서 위젯에 초점을 두고 방향키로 옮기기, Shift+방향키로 크기, Delete로 빼기 |
| 실행 취소 | 끌기·크기·추가·빼기가 한 단계씩. `state.history.undo()/redo()` |

## 저장과 화면 크기

- 배치는 기준 폭 1080 문서 단위(약 393dp)로 저장한다. 자유 배치는 가로 위치·너비만 화면 폭 비율로 펼치고, 격자 배치는 칸으로 저장한다.
  그래서 폴더블을 접고 펴거나 돌려도 저장된 배치와 실행 취소 기록이 그대로다.
- `toJson()`은 장면 JSON(`SceneCodec`)에 그때의 위젯 목록 키를 함께 남긴다. `create(..., saved)`는 목록과 맞춘다:
  목록에 없는 위젯은 지우고, 지난 저장 뒤 새로 생긴 `placedByDefault` 위젯은 빈자리에 놓고, 사용자가 뺀 위젯은 다시 넣지 않는다.
  읽을 수 없는 저장본이면 기본 배치로 시작한다.
- 처음 배치와 목록 맞춤은 실행 취소 기록에 남지 않는다.

## 위젯 상태

렌더러는 노드 id로 Compose 요소를 식별하므로 순서·위치·크기가 바뀌어도 위젯 안의 상태(입력 중인 글, 스크롤, `remember`)가 유지된다.

## 남은 것

실기기 TalkBack·하드웨어 키보드 확인, 위젯 그룹(폴더) 묶기.
