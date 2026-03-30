package com.lifestreams.app

import android.os.Bundle
import com.getcapacitor.BridgeActivity

/**
 * Main Activity — extends Capacitor's BridgeActivity so the HTML app runs inside it.
 * On every resume (app comes to foreground) we refresh widgets so they stay current.
 */
class MainActivity : BridgeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        // Small delay to let Capacitor flush any pending storage writes
        window.decorView.postDelayed({
            WidgetUpdater.updateAll(applicationContext)
        }, 500)
    }

    override fun onPause() {
        super.onPause()
        // Also update when leaving — data may have changed
        WidgetUpdater.updateAll(applicationContext)
    }
}
