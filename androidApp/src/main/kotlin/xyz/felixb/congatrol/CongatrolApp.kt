package xyz.felixb.congatrol

import android.app.Application
import xyz.felixb.congatrol.di.initKoinAndroid

class CongatrolApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoinAndroid(this)
    }
}
