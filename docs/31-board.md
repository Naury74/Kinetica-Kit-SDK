# 31 — 위젯 보드 (`kinetica-board-compose`)

UI 키트와 함께 제공하는 고급 기능이다. 호스트 앱의 홈 화면이나 대시보드에 넣으면 앱 사용자가 위젯을 옮기고, 크기를 바꾸고, 빼거나 추가한다.
위젯 내용은 호스트의 Composable이나 View를 그대로 쓴다. 편집기 `KineticaEditor`가 디자이너와 운영자를 위한 도구라면, 보드는 앱 사용자가 앱 안에서 직접 쓰는 편집 기능이다.

## 연동

```kotlin
val catalog = listOf(
    BoardWidget("sales", "오늘 매출", widthDp = 176f, heightDp = 120f),
    BoardWidget("chart", "주간 매출", widthDp = 360f, heightDp = 170f, columnSpan = 2),
    BoardWidget("memo", "메모", placedByDefault = false),          // 처음에는 놓이지 않고 추가 메뉴에만 나온다
)
// 배치와 실행 취소 기록은 ViewModel에 있어 화면을 회전해도 남고, 화면이 가려질 때(ON_STOP)마다 저장한다.
// 저장본을 읽지 못했으면 state.restoreFailed가 true가 되고, 사용자가 배치를 바꾸기 전까지 저장본을 덮어쓰지 않는다.
val state = rememberKineticaBoardState(catalog, BoardStorage.sharedPreferences(context, "home", "board"))

KineticaBoard(state, Modifier.fillMaxSize(), editing = editing, permissions = BoardPermissions(remove = false)) { id ->
    when (state.widgetKeyOf(id)) { "sales" -> SalesCard(vm); "chart" -> SalesChart(vm); "memo" -> Memo() }   // 호스트의 Composable을 그대로 쓴다
}
```

XML 화면에서는 `KineticaBoardView`의 `setBoard(state) { id, context -> View }`, `isEditing`, `setOnLongPressListener`를 쓴다.
위젯 키는 `state.widgetKeyOf(id)`로 얻는다. XML 속성은 `app:kinetica_editing`, `app:kinetica_stackAutoAdvanceMs`, `app:kinetica_accentColor`, `app:kinetica_widgetCornerRadius`이고,
격자 열 수를 정하는 속성은 아래 [넓은 화면과 폴더블](#넓은-화면과-폴더블)에 있다. View에 id가 있으면 화면을 회전해도 편집 모드가 유지된다. Java에서는 `KineticaBoardState.create(catalog)`로 상태를 만든다.

편집 테두리와 크기 조절 핸들의 모서리 반지름은 기본 16dp다. 위젯 카드의 반지름과 다르면 테두리가 카드 모서리와 어긋나 보이므로 `KineticaBoard(widgetCornerRadius = 20.dp)`처럼 카드와 같게 맞춘다.
View에서는 `widgetCornerRadiusDp`, XML에서는 `app:kinetica_widgetCornerRadius`를 쓴다.

XML 화면의 상태는 `BoardStates.obtain(activity, catalog, storage)`로 얻는다. Compose의 `rememberKineticaBoardState`와 마찬가지로
화면을 회전해도 배치와 실행 취소 기록이 남고, 화면이 가려질 때(ON_STOP)마다 저장하며, 화면이 끝나면 스스로 닫힌다. `close()`는 직접 부르지 않는다.
한 화면에 보드가 둘 이상이면 마지막 인자 `key`를 보드마다 다르게 준다. 이 값은 ViewModel 키로 쓰인다.

```kotlin
class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home)
        val state = BoardStates.obtain(this, catalog, BoardStorage.sharedPreferences(this, "home", "board"))
        findViewById<KineticaBoardView>(R.id.board).setBoard(state) { id, context -> widgetView(state.widgetKeyOf(id), context) }
    }
}
```

`BoardViewFactory`는 슬롯마다 View를 한 번만 만든다. 매출처럼 실시간으로 바뀌는 값은 호스트가 만든 View를 들고 있다가 직접 갱신하거나, View 안에서 데이터를 구독해 반영한다.

## 동작

| 상황 | 동작 |
| --- | --- |
| 사용 모드 | 위젯은 저장된 위치에서 평소처럼 동작한다. 보드가 화면보다 길면 세로로 스크롤된다. `onLongPress`를 주면 길게 눌러 편집을 시작하게 할 수 있고, 짧은 탭과 스크롤은 위젯이 그대로 받는다 |
| 편집 모드 | 위젯을 끌어 옮기면 다른 위젯의 가장자리, 간격이 맞는 위치, 보드 가장자리에 스냅된다. 위젯 오른쪽 아래 핸들로 크기를 바꾸고, 왼쪽 위 표시를 눌러 뺀다. 보드 오른쪽 아래의 버튼으로 위젯을 추가한다. 위젯 안쪽은 터치에 반응하지 않는다 |
| 자유 배치 분할 | 끄는 손가락이 화면 왼쪽이나 오른쪽 가장자리 40dp 안에 들어가면 위젯이 화면 절반에, 모서리에 들어가면 4분의 1에 붙는다. 놓기 전에 들어갈 위치를 미리 보여 준다 |
| 스냅 레이아웃 | Windows 11처럼 위젯을 위쪽 가장자리로 끌면 레이아웃 선택 패널이 뜬다. 레이아웃은 반반, 2:1, 1:2, 3등분, 위아래, 큰 칸 하나와 작은 칸 둘, 4분할이다. 손가락을 레이아웃의 칸 위에 올리면 그 위치를 미리 보여 주고, 놓으면 그 칸에 들어간다. 손가락이 패널 아래로 내려가면 패널이 닫힌다 |
| 겹침 | 놓은 위젯과 겹치는 위젯은 겹치지 않을 때까지 아래로 밀린다. 밀린 위젯이 다른 위젯과 겹치면 그 위젯도 이어서 밀린다 |
| 격자 배치 | 열 수, 간격, 칸 높이 비율로 정한 칸 단위로 놓는다. 가장 가까운 칸에 맞추고, 겹치면 아래로 민다 |
| 넓은 화면 열 수 | 격자 배치에 `columnsByWidth`를 주면 보드 폭에 맞춰 열 수를 늘린다. 자세한 내용은 아래 [넓은 화면과 폴더블](#넓은-화면과-폴더블)에 있다 |
| 처음 배치 | 위에서부터, 같은 줄이면 왼쪽부터 첫 빈 위치에 놓는다. 넓은 화면에서는 옆으로 먼저 채운다 |
| 자동 스크롤 | 위젯을 끄는 손가락이 보이는 영역의 위나 아래 끝에 머물면 보드가 스크롤되고 위젯도 따라간다. 여기서 끝은 분할 영역 바로 안쪽의 48dp 띠와 그 바깥이다. 보드는 지금 내용보다 최대 한 화면만큼만 아래로 늘어난다 |
| 접근성 | 편집 모드에서 위젯마다 TalkBack 사용자 정의 동작을 제공한다. "위로 이동", "아래로 이동", "왼쪽으로 이동", "오른쪽으로 이동", "크기 키우기", "크기 줄이기", "보드에서 삭제"가 있다. 이동은 한 번에 8dp, 격자에서는 한 칸이다 |
| 여러 개 | `BoardWidget(multiple = true)`이면 같은 위젯을 여러 번 추가할 수 있다. 슬롯 id는 `memo`, `memo.2`…처럼 붙고 `content(id)`로 받는다. 위젯 키는 `state.widgetKeyOf(id)`로 얻는다. 뺀 슬롯의 id는 저장했다가 다시 열어도 다시 쓰지 않으므로, 호스트가 슬롯 id 기준으로 저장한 데이터가 새 슬롯에 섞이지 않는다. 호스트의 슬롯 데이터는 편집을 마칠 때 `state.itemIds`에 없는 것만 지운다. 빼자마자 지우면 실행 취소로 되살린 슬롯의 데이터가 사라진다 |
| 스택 | 위젯을 다른 위젯 가운데의 40% 영역에 놓으면 그 자리에서 넘겨 보는 스택이 되고, 아래에 점 인디케이터가 붙는다. 편집 중에 오른쪽 위 숫자 배지를 누르거나 TalkBack의 "스택 풀기" 동작으로 푼다. 코드에서는 `state.stack(a, b)`, `unstack(id)`, `stackMembers(id)`를 쓴다. `KineticaBoard(stackAutoAdvanceMs = 5_000)`을 주면 사용 모드에서 정한 시간마다 다음 위젯으로 넘어가고, 사용자가 직접 넘기면 그때부터 시간을 다시 잰다. XML에서는 `app:kinetica_stackAutoAdvanceMs`를 쓴다. 필수 위젯이 들어 있는 스택은 뺄 수 없다 |
| RTL | 아랍어나 히브리어처럼 레이아웃 방향이 RTL이면 보드 화면을 좌우로 뒤집는다. 처음 배치, 끌기, 분할 영역, 추가 버튼이 오른쪽 기준이 된다. 방향키와 TalkBack의 "왼쪽으로 이동"은 그대로 화면 왼쪽으로 옮긴다. 저장되는 배치는 방향과 상관없이 같다 |
| 키보드 | 편집 모드에서 위젯에 포커스를 두고 방향키로 옮긴다. Shift+방향키로 크기를 바꾸고, Delete로 뺀다 |
| 실행 취소 | 끌기, 크기 변경, 추가, 삭제가 각각 한 단계로 기록된다. 코드에서는 `state.history.undo()`와 `redo()`를 쓴다 |

## 저장과 화면 크기

- 배치는 기준 폭 1080 문서 단위로 저장한다. 약 393dp에 해당한다. 자유 배치는 가로 위치와 너비만 화면 폭 비율에 맞춰 늘리고, 격자 배치는 칸 단위로 저장한다.
  그래서 폴더블을 접었다 펴거나 화면을 회전해도 저장된 배치와 실행 취소 기록이 그대로 남는다.
- `toJson()`은 `SceneCodec` 장면 JSON에 저장 당시의 위젯 목록 키를 함께 남긴다. `create(..., saved)`는 저장본을 지금 목록에 맞춘다.
  목록에서 사라진 위젯은 지우고, 지난번 저장 뒤에 새로 생긴 `placedByDefault` 위젯은 빈 위치에 놓는다. 사용자가 뺀 위젯은 다시 넣지 않는다.
  읽을 수 없는 저장본이면 기본 배치로 시작한다.
- 처음 배치와 목록 맞추기는 실행 취소 기록에 남지 않는다.

## 넓은 화면과 폴더블

열 수가 고정된 격자는 펼친 폴드나 태블릿에서 칸이 화면 폭에 비례해 커진다. `columnsByWidth`로 폭에 맞는 열 수를 고른다. 기본값은 `null`이며, 이때는 열 수가 바뀌지 않는다.

```kotlin
// 칸이 160dp보다 좁아지지 않는 만큼 열을 늘린다. 폰 2열, 펼친 폴드 4열, 태블릿 최대 6열.
val arrangement = BoardArrangement.Grid(columns = 2, columnsByWidth = GridColumns.Adaptive(minCellWidthDp = 160f, maxColumns = 6))
// 폭 구간으로 정할 수도 있다. 600dp 미만은 columns(2), 600dp 이상 4, 840dp 이상 6.
val byBreakpoint = BoardArrangement.Grid(columns = 2, columnsByWidth = GridColumns.breakpoints(600f to 4, 840f to 6))
val state = rememberKineticaBoardState(catalog, storage, arrangement)
```

XML에서는 `app:kinetica_minCellWidth="160dp"`와 `app:kinetica_maxColumns="6"`을 주거나, 코드에서 `board.columnsByWidth = GridColumns.Adaptive(160f, 6)`을 넣는다. View에 준 값이 상태의 `columnsByWidth`보다 우선한다.

- 열 수마다 배치를 따로 기억한다. iOS와 One UI 홈 화면처럼 좁은 화면과 넓은 화면의 배치는 서로 영향을 주지 않는다. 넓은 화면에서 옮긴 위젯은 넓은 배치에만 반영되고, 접으면 좁은 배치가 그대로 보인다. 다시 펴면 넓은 화면에서 마지막으로 둔 배치로 돌아온다.
- 아직 편집하지 않은 열 수에서는 좁은 배치를 위에서 아래, 왼쪽에서 오른쪽으로 읽은 순서대로 흘려 넣어 보여 준다. 앞쪽 빈칸에 들어가는 위젯부터 채우는 dense 방식이므로, 큰 위젯 옆이나 아래에 빈칸이 남지 않게 빈칸이 줄어들 때만 순서를 바꾼다. 처음 펼친 화면이 이 결과이며, 실행 취소 기록에는 남지 않는다. 그 열 수에서 한 번 편집하면 그때 보이던 배치 전체를 기억하므로, 이후 좁은 배치를 바꿔도 넓은 배치는 따라 움직이지 않는다.
- 배치 하나를 열 수에 맞춰 매번 다시 흘리는 방식은 쓰지 않았다. 그렇게 하면 넓은 화면에서 일부러 비워 둔 칸이나 나란히 둔 위젯을 좁은 화면에서 나타낼 수 없어서, 접었다 펴면 사용자가 만든 배치가 달라진다.
- 위젯 추가, 삭제, 스택은 모든 배치에 함께 반영된다. 추가한 위젯은 지금 보이는 배치와 좁은 배치에서 각각 첫 빈칸에 놓인다. 스택은 열 수마다 대상 위젯의 자리를 물려받는다.
- `rectOf`, `place`, `moveBy`, `resizeBy`는 지금 보이는 열 수의 배치를 다룬다. 이 열 수는 `state.activeColumns`이고, `KineticaBoard`가 폭에 맞춰 정한다.
- 접거나 펴서 열 수가 바뀌는 순간 끌고 있던 위젯은 놓지 않고 끌기를 취소한다. 손을 떼고 다시 끌면 새 열 수에서 평소처럼 옮긴다.
- 저장 형식: 가장 좁은 열 수의 배치는 노드 위치로 저장하고, 다른 열 수의 칸은 노드 metadata `kinetica.cells`에 `{"4":[열,행,열 수,행 수]}` 형태로 남긴다. 열 수 규칙이 생기기 전의 저장본도 그대로 열리며, 이때 넓은 배치는 흘려 넣어 만든다. 반대로 새 저장본을 열 수 규칙이 없는 버전에서 열면 좁은 배치로 열린다.
- 가로 스크롤 컨테이너나 가로 LinearLayout의 `wrap_content`처럼 폭 제한 없이 재면 보드는 창 폭으로 그리고, 창 폭을 모르면 360dp로 그린다. 보드는 폭에 맞춰 배치하므로 폭을 정해 주는 편이 좋다.
- 자유 배치는 열 수 규칙 없이 가로 비율만 늘린다. 넓은 화면에서 위젯이 너무 커지지 않게 하려면 보드 폭을 720dp 정도로 제한해 가운데에 둔다.

## 위젯 상태

렌더러는 노드 id로 Compose 요소를 구분한다. 그래서 순서, 위치, 크기가 바뀌어도 입력 중인 텍스트, 스크롤 위치, `remember` 값 같은 위젯 안의 상태가 유지된다.

## 확인 상태

Galaxy Z Fold7 커버 화면에서 하드웨어 키보드 편집과 TalkBack 초점 이동을 확인했다. TalkBack의 사용자 정의 동작 메뉴는 adb로 넣은 터치로는 열 수 없어 Robolectric 테스트로만 검증했다.
실기기 측정값은 [성능](performance.md)에 있다.
