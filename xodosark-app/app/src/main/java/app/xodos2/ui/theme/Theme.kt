package app.xodos2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Dark theme; primary — deep brand purple (#310236) matching the XoDos logo. */
@Composable
fun xodos2Theme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,          
            surface = Color.Black,             
            surfaceVariant = Color(0xFF333333), 
            primary = Color(0xFF7502D1),       
            onPrimary = Color.White,           
            onBackground = Color.White,       
            onSurface = Color.White,         
        ),
        content = content
    )
}