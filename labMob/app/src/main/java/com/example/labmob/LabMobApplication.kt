package com.example.labmob

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

class LabMobApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LabMobApplication)
            modules(module {
                single { BackendApi() }
                single { GoldRateRepository() }
                viewModel { HuntGameViewModel(get(), get()) }
            })
        }
    }
}
