package ro.florin.bateriexperia

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/** Salvează o măsurătoare la aproximativ 15 minute, chiar dacă aplicația nu e deschisă. */
class LogJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        try {
            SampleStore(this).append(BatteryReader.read(this))
        } catch (e: Exception) {
            // Dacă o măsurătoare eșuează, următoarea o va relua.
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_ID = 4711

        fun schedule(context: Context) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (js.getPendingJob(JOB_ID) != null) return
            val info = JobInfo.Builder(JOB_ID, ComponentName(context, LogJobService::class.java))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build()
            js.schedule(info)
        }
    }
}
