package com.example.consumer

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.view.KDialogs
import com.naury.kinetica.ui.view.KPopupOptions
import com.naury.kinetica.ui.view.KSheets
import com.naury.kinetica.ui.view.KSurfaceView
import com.naury.kinetica.ui.view.KToasts

// Compose 코드 없이 XML 진입점만 쓰는 화면. 배포 artifact로 컴파일되는지 확인한다.
class XmlSurfaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.addView(KSurfaceView(this).apply { surfaceStyle = SurfaceStyles.Acrylic; addView(TextView(context).apply { text = "glass" }) })
        setContentView(root)
        val sheet = KSheets.show(this, TextView(this).apply { text = "sheet" }, KPopupOptions(title = "sheet"))
        sheet.setOnDismissListener { KToasts.show(this, "closed") }
        KDialogs.show(this, TextView(this).apply { text = "dialog" }).dismiss()
    }
}
