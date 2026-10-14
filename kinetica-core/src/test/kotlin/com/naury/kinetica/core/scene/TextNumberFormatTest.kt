package com.naury.kinetica.core.scene

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.id
import org.junit.Test

class TextNumberFormatTest {
    @Test
    fun `천 단위·소수 자릿수·앞뒤 글자·음수를 기기 언어와 상관없이 같은 모양으로 쓴다`() {
        assertThat(TextNumberFormat(suffix = "원").format(1_284_000.0)).isEqualTo("1,284,000원")
        assertThat(TextNumberFormat(prefix = "$", decimals = 2).format(1234.5)).isEqualTo("$1,234.50")
        assertThat(TextNumberFormat(decimals = 0).format(2.5)).isEqualTo("3")
        assertThat(TextNumberFormat(grouping = false).format(-1234.25)).isEqualTo("-1234.25")
        assertThat(TextNumberFormat(suffix = "%").format(12.0)).isEqualTo("12%")
        assertThat(TextNumberFormat().format(Double.NaN)).isEqualTo("")
    }

    @Test
    fun `숫자 데이터를 묶은 글자는 형식대로 보이고 글자 데이터는 그대로이며 잘못된 형식은 검증에서 걸린다`() {
        val sales = SceneNode(
            id("sales"),
            NodeContent.Text(Size(300.0, 60.0), "-", numberFormat = TextNumberFormat(suffix = "원")),
            bindings = mapOf(BoundProperty.Text to "sales"),
        )
        val scene = document(listOf(sales))

        fun shown(value: DataValue) =
            (Bindings.apply(scene, SceneData.of("sales" to value)).node(id("sales"))!!.content as NodeContent.Text).text

        assertThat(shown(DataValue.Number(1_284_000.0))).isEqualTo("1,284,000원")
        assertThat(shown(DataValue.Text("준비 중"))).isEqualTo("준비 중")
        val bad = sales.copy(content = (sales.content as NodeContent.Text).copy(numberFormat = TextNumberFormat(decimals = 9)))
        assertThat(SceneValidator().validate(document(listOf(bad))).map { it.code }).contains(ViolationCode.InvalidMetadata)
    }
}
