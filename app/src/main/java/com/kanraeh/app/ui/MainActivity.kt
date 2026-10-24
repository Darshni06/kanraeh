package com.kanraeh.app.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.kanraeh.app.core.Graph
import com.kanraeh.app.privacy.AppLock
import com.kanraeh.app.theme.KanraehTheme
import com.kanraeh.app.theme.dimmed
import com.kanraeh.app.ui.screens.LockScreen

/** MODULE: ui - single activity. Lock gate -> (attack mode | four tabs). */
class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(false)
    private lateinit var vm: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[MainViewModel::class.java]
        locked = Graph.settings.lockEnabled
        applySecure()
        intent?.getStringExtra("open")?.let { vm.pendingOpen.value = it }
        setContent { Root() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("open")?.let { vm.pendingOpen.value = it }
    }

    override fun onStop() { super.onStop(); if (Graph.settings.lockEnabled) locked = true }

    fun applySecure() {
        if (Graph.settings.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    @Composable
    private fun Root() {
        val pal by vm.palette.collectAsState()
        val attack by vm.openAttack.collectAsState()
        KanraehTheme(if (attack != null) pal.dimmed() else pal) {
            Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                when {
                    locked -> {
                        androidx.compose.runtime.LaunchedEffect(Unit) { AppLock.prompt(this@MainActivity, { locked = false }) }
                        LockScreen { AppLock.prompt(this@MainActivity, { locked = false }) }
                    }
                    attack != null -> AttackRoute(vm)
                    else -> Tabs()
                }
            }
        }
    }

    @Composable
    private fun Tabs() {
        var tab by remember { mutableStateOf(0) }
        val names = listOf("Today", "My body", "Patterns", "Me")
        val icons = listOf("\u2600", "\u25CE", "\u2736", "\u263A")
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {
                when (tab) {
                    0 -> TodayRoute(vm)
                    1 -> BodyRoute(vm)
                    2 -> PatternsRoute(vm)
                    else -> MeRoute(vm, this@MainActivity)
                }
            }
            NavigationBar {
                names.forEachIndexed { i, n ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(icons[i]) }, label = { Text(n) })
                }
            }
        }
    }
}
