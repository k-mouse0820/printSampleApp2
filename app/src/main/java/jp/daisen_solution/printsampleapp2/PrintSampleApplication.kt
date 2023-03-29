package jp.daisen_solution.printsampleapp2

import android.app.Application
import com.beardedhen.androidbootstrap.TypefaceProvider

class PrintSampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TypefaceProvider.registerDefaultIconSets()
    }
}