package com.example.suicanfcreader

import android.app.Activity
import android.graphics.Color.TRANSPARENT
<<<<<<< HEAD
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
=======
import androidx.compose.foundation.layout.Row
>>>>>>> origin/main
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
<<<<<<< HEAD
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
=======
import androidx.compose.ui.platform.LocalView
>>>>>>> origin/main
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.rememberNavController
import com.example.suicanfcreader.model.AppThemeMode
import com.example.suicanfcreader.navigation.SuicaNFCReaderNavigation
import com.example.suicanfcreader.ui.theme.SuicaNFCReaderTheme
import com.example.suicanfcreader.viewModel.TopScreenViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuicaNFCReaderApp(viewModel: TopScreenViewModel) {
    val themeMode = viewModel.themeMode.observeAsState(AppThemeMode.AMOLED)
    val accentColorHex = viewModel.accentColorHex.observeAsState("#8AD7C8")

    SuicaNFCReaderTheme(themeMode = themeMode.value, accentColorHex = accentColorHex.value) {
        val navController = rememberNavController()
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        ApplySystemBars(themeMode.value)

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("suicanfc kd") },
                    actions = {
                        ThemeMenu(
                            selectedMode = themeMode.value,
                            onThemeSelected = viewModel::setThemeMode
                        )
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
        ) { innerPadding ->
            SuicaNFCReaderNavigation(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
                viewModel
            )
        }
    }
}

@Composable
private fun ThemeMenu(
    selectedMode: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
<<<<<<< HEAD
        PaletteIcon()
=======
        Text(
            text = "色",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
>>>>>>> origin/main
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        AppThemeMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedMode == mode,
                            onClick = null
                        )
                        Text(mode.label)
                    }
                },
                onClick = {
                    onThemeSelected(mode)
                    expanded = false
                }
            )
        }
    }
}

@Composable
<<<<<<< HEAD
private fun PaletteIcon() {
    val base = MaterialTheme.colorScheme.onBackground
    val cutout = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = Modifier.size(24.dp)) {
        drawOval(
            color = base,
            topLeft = Offset(size.width * 0.08f, size.height * 0.12f),
            size = Size(size.width * 0.78f, size.height * 0.72f)
        )
        drawCircle(
            color = cutout,
            radius = size.minDimension * 0.16f,
            center = Offset(size.width * 0.64f, size.height * 0.62f)
        )
        drawCircle(primary, radius = size.minDimension * 0.07f, center = Offset(size.width * 0.33f, size.height * 0.35f))
        drawCircle(secondary, radius = size.minDimension * 0.07f, center = Offset(size.width * 0.52f, size.height * 0.31f))
        drawCircle(tertiary, radius = size.minDimension * 0.07f, center = Offset(size.width * 0.39f, size.height * 0.55f))
    }
}

@Composable
=======
>>>>>>> origin/main
private fun ApplySystemBars(themeMode: AppThemeMode) {
    val view = LocalView.current
    val lightBars = themeMode == AppThemeMode.WHITE

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = TRANSPARENT
        window.navigationBarColor = TRANSPARENT
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        WindowInsetsControllerCompat(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }
}
