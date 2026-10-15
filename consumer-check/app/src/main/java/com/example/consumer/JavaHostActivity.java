package com.example.consumer;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.naury.kinetica.board.BoardStates;
import com.naury.kinetica.board.BoardStorage;
import com.naury.kinetica.board.BoardWidget;
import com.naury.kinetica.board.KineticaBoardState;
import com.naury.kinetica.board.KineticaBoardView;
import com.naury.kinetica.presets.EffectPresets;
import com.naury.kinetica.render.ViewEffects;
import com.naury.kinetica.ui.view.GlassDialogs;
import com.naury.kinetica.ui.view.GlassPopupHandle;
import com.naury.kinetica.ui.view.GlassPopupOptions;
import com.naury.kinetica.ui.view.GlassSheets;
import com.naury.kinetica.ui.view.GlassToasts;

import java.util.Arrays;
import java.util.List;

/** Java만 쓰는 호스트 화면. XML 진입점, ViewEffects, KineticaBoardView를 Java에서 부른다. */
public class JavaHostActivity extends AppCompatActivity {
    private int clicks = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);

        TextView status = new TextView(this);
        status.setText("눌림 0");
        root.addView(status);

        Button effect = new Button(this);
        effect.setText("효과 버튼");
        effect.setOnClickListener(v -> status.setText("눌림 " + (++clicks)));
        ViewEffects.apply(effect, EffectPresets.button, 12f);
        root.addView(effect);

        Button sheet = new Button(this);
        sheet.setText("자바 시트");
        sheet.setOnClickListener(v -> {
            TextView body = new TextView(this);
            body.setText("자바에서 연 시트");
            GlassPopupHandle handle = GlassSheets.show(this, body, new GlassPopupOptions(null, "자바 시트"));
            handle.setOnDismissListener(() -> status.setText("시트 닫힘"));
        });
        root.addView(sheet);

        Button dialog = new Button(this);
        dialog.setText("자바 다이얼로그");
        dialog.setOnClickListener(v -> {
            TextView body = new TextView(this);
            body.setText("자바에서 연 다이얼로그");
            GlassDialogs.show(this, body);
        });
        root.addView(dialog);

        Button toast = new Button(this);
        toast.setText("자바 토스트");
        toast.setOnClickListener(v -> GlassToasts.show(this, "자바 토스트"));
        root.addView(toast);

        List<BoardWidget> catalog = Arrays.asList(new BoardWidget("a", "에이"), new BoardWidget("b", "비"));
        KineticaBoardState state = BoardStates.obtain(this, catalog, BoardStorage.sharedPreferences(this, "java-host", "board"));
        KineticaBoardView board = new KineticaBoardView(this);
        board.setBoard(state, (id, context) -> {
            TextView widget = new TextView(context);
            widget.setText("자바 위젯 " + state.widgetKeyOf(id));
            return widget;
        });
        board.setOnLongPressListener(() -> board.setEditing(true));
        root.addView(board, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }
}
