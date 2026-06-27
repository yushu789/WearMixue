package site.unclefish.wearmixue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import site.unclefish.wearmixue.ui.WearMixueApp
import site.unclefish.wearmixue.ui.theme.WearMixueTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WearMixueTheme {
                WearMixueApp()
            }
        }
    }
}
