package com.polyalerts.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/** Opens the real Polymarket page in an in-app Chrome Custom Tab. */
fun openOnPolymarket(context: Context, url: String) {
    val intent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
    runCatching { intent.launchUrl(context, Uri.parse(url)) }
}
