package com.naury.kinetica.board;

import static com.google.common.truth.Truth.assertThat;

import android.widget.TextView;
import androidx.activity.ComponentActivity;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

/** Java 액티비티가 XML 보드를 설정하는 모양이 그대로 컴파일되고 동작하는지 확인한다. */
@RunWith(RobolectricTestRunner.class)
public class KineticaBoardViewJavaTest {
    @Test
    public void javaConfiguresBoard() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        List<BoardWidget> catalog = Arrays.asList(new BoardWidget("orders", "주문"), new BoardWidget("memo", "메모", 160f, 120f));
        KineticaBoardState state = BoardStates.obtain(activity, catalog, BoardStorage.sharedPreferences(activity, "home", "board"));
        KineticaBoardState again = BoardStates.obtain(activity, catalog, BoardStorage.sharedPreferences(activity, "home", "board"));

        KineticaBoardView board = new KineticaBoardView(activity);
        board.setBoard(state, (id, context) -> new TextView(context));
        board.setOnLongPressListener(() -> board.setEditing(true));
        board.setPermissions(new BoardPermissions(true, false));
        board.setColors(BoardColors.fromAccent(0xFF336699));
        board.setStackAutoAdvanceMs(4_000L);
        activity.setContentView(board);

        assertThat(again).isSameInstanceAs(state);
        assertThat(board.isEditing()).isFalse();
        assertThat(board.getPermissions().getResize()).isFalse();
    }
}
