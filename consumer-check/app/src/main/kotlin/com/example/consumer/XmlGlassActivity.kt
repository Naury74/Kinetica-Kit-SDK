package com.example.consumer

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.view.GlassDialogs
import com.naury.kinetica.ui.view.GlassPopupOptions
import com.naury.kinetica.ui.view.GlassSheets
import com.naury.kinetica.ui.view.GlassSurfaceView
import com.naury.kinetica.ui.view.GlassToasts

// Compose 코드 없이 XML 진입점만 쓰는 화면. 배포 artifact로 컴파일되는지 확인한다.
class XmlGlassActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.addView(GlassSurfaceView(this).apply { glassStyle = GlassStyles.Acrylic; addView(TextView(context).apply { text = "glass" }) })
        setContentView(root)
        val sheet = GlassSheets.show(this, TextView(this).apply { text = "sheet" }, GlassPopupOptions(title = "sheet"))
        sheet.setOnDismissListener { GlassToasts.show(this, "closed") }
        GlassDialogs.show(this, TextView(this).apply { text = "dialog" }).dismiss()
    }
}
