package com.naury.kinetica.showcase

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * 쇼케이스 문구는 한국어로만 쓰여 있으므로, 기기 언어가 다르면 SDK 문구까지 한국어로 맞춰 한 화면에 두 언어가 섞이지 않게 한다.
 * 앱별 언어(설정의 앱 언어나 `cmd locale set-app-locales`)를 따로 정했으면 그 언어를 그대로 둔다. RTL 확인에 쓴다.
 */
internal fun showcaseContext(base: Context): Context {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val appLocales = base.getSystemService(LocaleManager::class.java)?.applicationLocales
        if (appLocales != null && !appLocales.isEmpty) return base
    }
    if (base.resources.configuration.locales[0].language == Locale.KOREAN.language) return base
    val config = Configuration(base.resources.configuration).apply { setLocale(Locale.KOREA) }
    return base.createConfigurationContext(config)
}
