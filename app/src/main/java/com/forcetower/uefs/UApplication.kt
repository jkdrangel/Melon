/*
 * This file is part of the UNES Open Source Project.
 * UNES is licensed under the GNU GPLv3.
 *
 * Copyright (c) 2019.  João Paulo Sena <joaopaulo761@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.forcetower.uefs

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.forcetower.sagres.SagresNavigator
import com.forcetower.uefs.core.constants.Constants
import com.forcetower.uefs.core.injection.AppComponent
import com.forcetower.uefs.core.injection.AppInjection
import com.forcetower.uefs.core.storage.cookies.PrefsCookiePersistor
import com.forcetower.uefs.core.work.sync.SyncMainWorker
import com.forcetower.uefs.impl.AndroidBase64Encoder
import com.forcetower.uefs.service.NotificationHelper
import com.google.android.play.core.missingsplits.MissingSplitsManagerFactory
import com.jakewharton.threetenabp.AndroidThreeTen
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasActivityInjector
import dagger.android.HasBroadcastReceiverInjector
import dagger.android.HasServiceInjector
import dagger.android.support.HasSupportFragmentInjector
import timber.log.Timber
import javax.inject.Inject

/**
 * Representa o aplicativo por completo.
 *
 * Iniciar o aplicativo por qualquer meio irá iniciar os processos de injeção de dependençias
 */
class UApplication : Application(), HasActivityInjector, HasSupportFragmentInjector, HasBroadcastReceiverInjector, HasServiceInjector {
    @Inject
    lateinit var activityInjector: DispatchingAndroidInjector<Activity>
    @Inject
    lateinit var fragmentInjector: DispatchingAndroidInjector<Fragment>
    @Inject
    lateinit var receiverInjector: DispatchingAndroidInjector<BroadcastReceiver>
    @Inject
    lateinit var serviceInjector: DispatchingAndroidInjector<Service>
    @Inject
    lateinit var preferences: SharedPreferences

    lateinit var component: AppComponent

    @Volatile
    private var injected = false

    override fun onCreate() {
        if (MissingSplitsManagerFactory.create(this).disableAppIfMissingRequiredSplits()) {
            return
        }
        // O log timber só existe em build de debug
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // Injeta as dependências. Este é o ponto inicial
        injectApplicationIfNecessary()
        super.onCreate()
        setupDayNightTheme(this)
        AndroidThreeTen.init(this)
        // Redefine os trabalhos de sincronização
        defineWorker()
    }

    /**
     * Este método irá observar qual o tipo de sincronização selecionado e tentará cria apenas se ele
     * tiver sido apagado
     */
    private fun defineWorker() {
        val worker = preferences.getString("stg_sync_worker_type", "0")?.toIntOrNull() ?: 0
        val period = preferences.getString("stg_sync_frequency", "60")?.toIntOrNull() ?: 60
        when (worker) {
            0 -> SyncMainWorker.createWorker(this, period)
            1 -> Unit // SyncLinkedWorker.createWorker(period, false)
        }
    }

    /**
     * Cria o componente do Dagger.
     * Este processo pode ser simplicado se a classe extendesse de DaggerApplication
     */
    private fun createApplicationInjector() = AppInjection.create(this)

    /**
     * Injetar as dependencias!
     */
    private fun injectApplicationIfNecessary() {
        if (!injected) {
            synchronized(this) {
                if (!injected) {
                    component = createApplicationInjector()
                    component.inject(this)
                    if (!injected)
                        throw IllegalStateException("Attempt to inject the app has failed")
                }
            }
        }
    }

    /**
     * Marca aplicação como injetada
     */
    @Inject
    fun setInjected() {
        injected = true
    }

    /**
     * Inicializa o objeto de conexão com o Sagres
     */
    @Inject
    fun configureSagresNavigator() {
        val selected = preferences.getString(Constants.SELECTED_INSTITUTION_KEY, "UEFS") ?: "UEFS"
        SagresNavigator.initialize(PrefsCookiePersistor(this), selected, AndroidBase64Encoder())
    }

    /**
     * Cria/Apaga os canais de notificação
     */
    @Inject
    fun configureNotifications() {
        NotificationHelper(this).createChannels()
    }

    override fun activityInjector() = activityInjector
    override fun supportFragmentInjector() = fragmentInjector
    override fun broadcastReceiverInjector() = receiverInjector
    override fun serviceInjector() = serviceInjector

    companion object {
        fun setupDayNightTheme(context: Context) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val enabled = preferences.getBoolean("ach_night_mode_enabled", false)
            if (!enabled) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                preferences.edit()
                        .remove("ach_night_mode_enabled")
                        .remove("stg_night_mode")
                        .apply()
            } else {
                val mode = when (preferences.getString("stg_night_mode", "0")?.toIntOrNull() ?: 0) {
                    0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_NO
                }
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
    }
}