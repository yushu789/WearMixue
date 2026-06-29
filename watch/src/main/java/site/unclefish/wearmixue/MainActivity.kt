package site.unclefish.wearmixue

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import site.unclefish.wearmixue.ui.WearMixueApp
import site.unclefish.wearmixue.ui.theme.WearMixueTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Force a dark appearance for the system bars so they match the dark app on phones.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            WearMixueTheme {
                WearMixueApp()
            }
        }
    }
}
