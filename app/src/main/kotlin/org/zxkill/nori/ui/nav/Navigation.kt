package org.zxkill.nori.ui.nav

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.zxkill.nori.R
import org.zxkill.nori.io.input.stt_popup.SttPopupActivity
import org.zxkill.nori.settings.MainSettingsScreen
import org.zxkill.nori.settings.SkillSettingsScreen
import org.zxkill.nori.ui.face.RobotFaceScreen

@Composable
fun Navigation() {
    val navController = rememberNavController()
    val backIcon = @Composable {
        IconButton(
            onClick = { navController.navigateUp() }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
    }

    // TODO this causes a crash when resuming a process from disk, the stacktrace is:
    //
    // java.lang.IllegalStateException: Restoring the Navigation back stack failed: destination
    //         186859275 cannot be found from the current destination ComposeNavGraph(0x0)
    //         startDestination={Destination(0x19acea6) route=org.zxkill.nori.ui.nav.Home}
    //     at androidx.navigation.NavController.onGraphCreated(NavController.kt:1365)
    //
    // this is caused by a bug in the type-safe Compose Navigation library:
    // https://issuetracker.google.com/issues/341801005
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            val context = LocalContext.current
            ScreenWithDrawer(
                onSettingsClick = { navController.navigate(MainSettings) },
                onSpeechToTextPopupClick = {
                    val intent = Intent(context, SttPopupActivity::class.java)
                    context.startActivity(intent)
                },
            ) {
                RobotFaceScreen(it)
            }
        }

        composable<MainSettings> {
            MainSettingsScreen(
                navigationIcon = backIcon,
                navigateToSkillSettings = { navController.navigate(SkillSettings) },
            )
        }

        composable<SkillSettings> {
            SkillSettingsScreen(navigationIcon = backIcon)
        }
    }
}

@Composable
fun ScreenWithDrawer(
    onSettingsClick: () -> Unit,
    onSpeechToTextPopupClick: () -> Unit,
    screen: @Composable (navigationIcon: @Composable () -> Unit) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                onSettingsClick = onSettingsClick,
                onSpeechToTextPopupClick = onSpeechToTextPopupClick,
                closeDrawer = {
                    scope.launch {
                        drawerState.close()
                    }
                }
            )
        },
    ) {
        screen {
            AppBarDrawerIcon(
                onDrawerClick = {
                    scope.launch {
                        drawerState.apply {
                            if (isClosed) open() else close()
                        }
                    }
                },
                isClosed = drawerState.isClosed,
            )
        }
    }
}
