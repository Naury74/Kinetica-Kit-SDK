# 31 — 호스트 위젯 보드 (kinetica-board-compose)

호스트 앱이 자기 화면에 넣는 모듈로, 앱 사용자가 앱의 위젯을 자유롭게 옮기고, 크기를 바꾸고, 빼거나 추가할 수 있게 한다.
스튜디오(`KineticaEditor`)가 디자이너와 운영자를 위한 도구라면, 보드는 앱 사용자가 앱 안에서 바로 쓰는 편집 기능이다.

## 연동

```kotlin
val catalog = listOf(
    BoardWidget("sales", "오늘 매출", widthDp = 176f, heightDp = 120f),
    BoardWidget("chart", "주간 매출", widthDp = 360f, heightDp = 170f, columnSpan = 2),
    BoardWidget("memo", "메모", placedByDefault = false),          // 처음에는 놓이지 않고 추가 메뉴에만 나온다
)
// 배치와 실행 취소 기록은 ViewModel에 있어 화면을 회전해도 남고, 화면이 가려질 때(ON_STOP)마다 저장한다.
// 저장본을 읽지 못했으면(state.restoreFailed) 사용자가 배치를 바꾸기 전까지 저장본을 덮어쓰지 않는다.
val state = rememberKineticaBoardState(catalog, BoardStorage.sharedPreferences(context, "home", "board"))

KineticaBoard(state, Modifier.fillMaxSize(), editing = editing, permissions = BoardPermissions(remove = false)) { id ->
    when (state.widgetKeyOf(id)) { "sales" -> SalesCard(vm); "chart" -> SalesChart(vm); "memo" -> Memo() }   // 호스트의 Composable을 그대로 쓴다
}
```

XML 화면에서는 `KineticaBoardView`의 `setBoard(state) { id, context -> View }`, `isEditing`, `setOnLongPressListener`를 쓴다.
위젯 키는 `state.widgetKeyOf(id)`로 얻는다. XML 속성으로는 `app:kinetica_editing`, `app:kinetica_stackAutoAdvanceMs`, `app:kinetica_accentColor`, `app:kinetica_widgetCornerRadius`가 있고,
View에 id가 있으면 화면을 회전해도 편집 모드가 유지된다. Java에서는 `KineticaBoardState.create(catalog)`로 상태를 만든다.

편집 테두리와 크기 조절 핸들의 모서리 반지름은 기본 16dp다. 위젯 카드의 모서리 반지름과 다르면 테두리가 카드 모서리와 어긋나 보이므로
`KineticaBoard(widgetCornerRadius = 20.dp)`(View는 `widgetCornerRadiusDp`, XML은 `app:kinetica_widgetCornerRadius`)로 카드와 같게 맞춘다.

XML 화면의 상태는 `BoardStates.obtain(activity, catalog, storage)`로 얻는다. Compose의 `rememberKineticaBoardState`와 마찬가지로
화면을 회전해도 배치와 실행 취소 기록이 남고, 화면이 가려질 때(ON_STOP)마다 저장하며, 화면이 끝나면 알아서 닫힌다. `close()`를 직접 부르지 않아도 된다.
한 화면에 보드가 둘 이상이면 마지막 인자 `key`(ViewModel 이름)를 보드마다 다르게 준다.

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

`BoardViewFactory`는 슬롯마다 View를 한 번만 만든다. 매출처럼 실시간으로 바뀌는 값은 호스트가 만든 View를 들고 있다가 갱신하거나, View 안에서 데이터를 구독해 반영한다.

## 동작

| 상황 | 동작 |
| --- | --- |
| 사용 모드 | 위젯은 저장된 위치에서 평소처럼 동작한다. 화면보다 길면 세로로 스크롤된다. `onLongPress`를 주면 길게 눌러 편집을 시작할 수 있고, 짧은 탭과 스크롤은 위젯이 그대로 받는다 |
| 편집 모드 | 드래그로 옮기면 다른 위젯의 변, 간격에 맞는 위치, 보드 가장자리에 스냅된다. 오른쪽 아래 핸들로 크기를 바꾸고, 왼쪽 위 표시로 빼고, 오른쪽 아래 버튼으로 추가한다. 위젯 안쪽은 터치에 반응하지 않는다 |
| 자유 배치 분할 | 손가락이 화면 왼쪽이나 오른쪽 가장자리 40dp 안에 들어가면 화면 절반에, 모서리에 들어가면 4분의 1에 붙는다. 놓기 전에 들어갈 위치를 미리 보여 준다 |
| 스냅 레이아웃 | Windows 11처럼 위젯을 위쪽 가장자리로 끌면 레이아웃 선택 패널이 뜬다(반반, 2:1, 1:2, 3등분, 위아래, 큰 칸+작은 두 칸, 4분할). 손가락을 레이아웃의 칸 위에 올리면 그 위치를 미리 보여 주고, 놓으면 그 칸에 들어간다. 패널 아래로 내려가면 패널이 닫힌다 |
| 겹침 | 놓은 위젯과 겹치는 위젯은 겹치지 않을 때까지 아래로 밀린다. 밀린 위젯이 다른 위젯과 겹치면 그 위젯도 이어서 밀린다 |
| 격자 배치 | 열 수, 간격, 칸 높이 비율로 정한 칸 단위로 배치한다. 가장 가까운 칸에 맞추고, 겹치면 아래로 민다 |
| 처음 배치 | 위에서부터, 같은 줄이면 왼쪽부터 첫 빈 위치에 놓는다. 넓은 화면에서는 옆으로 먼저 채운다 |
| 자동 스크롤 | 드래그하는 손가락이 보이는 영역의 위나 아래 끝에 머물면 보드가 스크롤되고 위젯도 따라간다. 끝은 분할 영역 바로 안쪽의 48dp 띠와 그 바깥을 말한다. 보드는 지금 내용 아래로 한 화면까지만 늘어난다 |
| 접근성 | 편집 모드에서 위젯마다 TalkBack 동작을 제공한다. "위로 이동", "아래로 이동", "왼쪽으로 이동", "오른쪽으로 이동"(8dp, 격자에서는 한 칸), "크기 키우기", "크기 줄이기", "보드에서 삭제"가 있다 |
| 여러 개 | `BoardWidget(multiple = true)`이면 같은 위젯을 여러 번 추가할 수 있다. 슬롯 id는 `memo`, `memo.2`…처럼 붙고 `content(id)`로 받으며, 위젯 키는 `state.widgetKeyOf(id)`로 얻는다. 뺀 슬롯의 id는 저장했다가 다시 열어도 재사용하지 않으므로, 호스트가 슬롯 id 기준으로 저장한 데이터가 새 슬롯에 섞이지 않는다. 슬롯 데이터는 편집을 마칠 때 `state.itemIds`에 없는 것을 지운다. 빼자마자 지우면 실행 취소로 되살린 슬롯의 데이터가 사라진다 |
| 스택 | 위젯을 다른 위젯의 가운데 40% 영역에 놓으면 그 위치에서 넘겨 보는 스택이 된다(아래에 점 인디케이터 표시). 편집 중에 오른쪽 위 숫자 배지나 TalkBack의 "스택 풀기" 동작으로 푼다. 코드로는 `state.stack(a, b)`, `unstack(id)`, `stackMembers(id)`를 쓴다. `KineticaBoard(stackAutoAdvanceMs = 5_000)`(XML은 `app:kinetica_stackAutoAdvanceMs`)를 주면 사용 모드에서 정한 시간마다 다음 위젯으로 넘어가고, 사용자가 직접 넘기면 그때부터 시간을 다시 잰다. 필수 위젯이 들어 있는 스택은 뺄 수 없다 |
| RTL | 아랍어나 히브리어처럼 레이아웃 방향이 RTL이면 보드 화면을 좌우로 뒤집는다. 처음 배치, 드래그, 분할 영역, 추가 버튼이 오른쪽 기준이 되고, 방향키와 TalkBack의 "왼쪽으로"는 화면 왼쪽으로 옮긴다. 저장되는 배치는 방향과 상관없이 같다 |
| 키보드 | 편집 모드에서 위젯에 포커스를 두고 방향키로 옮기고, Shift+방향키로 크기를 바꾸고, Delete로 뺀다 |
| 실행 취소 | 드래그, 크기 변경, 추가, 삭제가 각각 한 단계로 기록된다. `state.history.undo()/redo()` |

## 저장과 화면 크기

- 배치는 기준 폭 1080 문서 단위(약 393dp)로 저장한다. 자유 배치는 가로 위치와 너비만 화면 폭 비율에 맞춰 늘리고, 격자 배치는 칸 단위로 저장한다.
  그래서 폴더블을 접었다 펴거나 화면을 회전해도 저장된 배치와 실행 취소 기록이 그대로 남는다.
- `toJson()`은 장면 JSON(`SceneCodec`)에 저장 당시의 위젯 목록 키를 함께 남긴다. `create(..., saved)`는 저장본을 현재 목록에 맞춘다.
  목록에 없는 위젯은 지우고, 지난번 저장 뒤에 새로 생긴 `placedByDefault` 위젯은 빈 위치에 놓으며, 사용자가 뺀 위젯은 다시 넣지 않는다.
  읽을 수 없는 저장본이면 기본 배치로 시작한다.
- 처음 배치와 목록 맞추기는 실행 취소 기록에 남지 않는다.

## 위젯 상태

렌더러는 노드 id로 Compose 요소를 구분하므로 순서, 위치, 크기가 바뀌어도 위젯 안의 상태(입력 중인 텍스트, 스크롤 위치, `remember` 값)가 유지된다.

## 남은 것

실기기에서 TalkBack과 하드웨어 키보드를 확인해야 한다.
