package com.lifestreams.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        checkIntent(intent)
        window.decorView.postDelayed({
            WidgetUpdater.updateAll(applicationContext)
        }, 500)
    }

    private fun checkIntent(intent: Intent?) {
        val url = intent?.data?.toString() ?: return
        Log.d("dStream", "checkIntent url: $url")
        if (!url.startsWith("dstream://")) return
        intent.data = null
        getSharedPreferences("CapacitorStorage", MODE_PRIVATE)
            .edit()
            .putString("pending_auth_url", url)
            .apply()
        Log.d("dStream", "stored: $url")
        val escaped = url.replace("\\", "\\\\").replace("'", "\\'")
        bridge?.webView?.post {
            bridge?.webView?.evaluateJavascript(
                "if(typeof _handleAuthUrl==='function'){_handleAuthUrl('$escaped');}else{console.log('no handler');}",
                null
            )
        }
    }

    override fun onPause() {
        super.onPause()
        WidgetUpdater.updateAll(applicationContext)
    }
}
