package com.naury.kinetica.java;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.naury.kinetica.core.scene.SceneDocument;
import com.naury.kinetica.history.KineticaSession;
import org.junit.Test;

/** Java 호스트가 Kotlin 기본 인자 없이 열기·추가·이동·실행 취소·저장 형식 왕복을 할 수 있는지 확인한다. */
public class KineticaJavaTest {
    @Test
    public void javaHostCanEditUndoAndRoundTrip() {
        SceneDocument empty = Kinetica.emptyDocument("java-scene", 1080.0, 1920.0);
        KineticaSession session = Kinetica.openSession(empty);

        assertThat(Kinetica.add(session, Kinetica.rectangle("box", 10.0, 20.0, 100.0, 50.0, 0xFF4466FF))).isTrue();
        assertThat(Kinetica.moveBy(session, "box", 5.0, 0.0)).isTrue();
        assertThat(Kinetica.moveBy(session, "missing", 5.0, 0.0)).isFalse();
        assertThat(session.getHistory().undo()).isTrue();

        SceneDocument document = Kinetica.document(session);
        assertThat(Kinetica.decode(Kinetica.encode(document))).isEqualTo(document);
        session.close();
    }

    @Test
    public void invalidInputThrowsWithoutContent() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> Kinetica.decode("{\"secret\":"));
        assertThat(error.getMessage()).doesNotContain("secret");
        assertThrows(IllegalArgumentException.class, () -> Kinetica.rectangle("bad id", 0.0, 0.0, 1.0, 1.0, 0));
    }
}
