package com.eipna.centsation.habit.ui

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.eipna.centsation.habit.ui.theme.SuperCenterTheme

object HabitViewBinder {
    @JvmStatic
    fun bind(composeView: ComposeView, owner: ViewModelStoreOwner) {
        val viewModel = ViewModelProvider(owner)[HabitViewModel::class.java]
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                SuperCenterTheme {
                    HabitDashboardScreen(
                        viewModel = viewModel,
                        onBackClicked = null,
                        showTopBar = false
                    )
                }
            }
        }
    }
}
