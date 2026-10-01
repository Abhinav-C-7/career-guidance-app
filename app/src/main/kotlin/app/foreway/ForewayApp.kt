package app.foreway

import android.app.Application
import app.foreway.data.ForewayData
import app.foreway.sync.ContentSyncWorker

class ForewayApp : Application() {

    /** One per process. Screens and workers reach the data layer through this. */
    lateinit var data: ForewayData
        private set

    override fun onCreate() {
        super.onCreate()
        data = ForewayData.create(this, BuildConfig.SERVER_URL, BuildConfig.PUBLISHABLE_KEY)
        ContentSyncWorker.schedule(this)
    }
}
