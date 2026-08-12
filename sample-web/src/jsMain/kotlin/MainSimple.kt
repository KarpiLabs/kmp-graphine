import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.web.renderComposable

private val darkScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    background = Color(0xFF1E1E1E),
    onBackground = Color(0xFFF0F0F0),
)

@Composable
private fun HelloWeb() {
    MaterialTheme(colorScheme = darkScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    "✅ KmpGraphine on Web",
                    fontSize = 32.sp,
                    color = Color(0xFF8AB4F8),
                )
                Text(
                    "Kotlin/JS compilation working!",
                    fontSize = 16.sp,
                    color = Color(0xFFF0F0F0),
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "One codebase, all platforms",
                    fontSize = 14.sp,
                    color = Color(0xFFB0BEC5),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

fun main() {
    renderComposable(rootElementId = "root") {
        HelloWeb()
    }
}

// For testing: alternative to simple demo
fun mainFull() {
    // TODO: call main from Main.kt when Skiko is fixed
}
