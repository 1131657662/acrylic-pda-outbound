package com.acrylic.outbound.pda

import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper

/** Keep scheduler database work inside each Robolectric sandbox's lifetime. */
class PdaTestApplication : PdaApplication() {
    override fun onCreate() {
        val executor = SynchronousExecutor()
        WorkManagerTestInitHelper.initializeTestWorkManager(this, Configuration.Builder()
            .setExecutor(executor).setTaskExecutor(executor).build())
        super.onCreate()
    }
}
