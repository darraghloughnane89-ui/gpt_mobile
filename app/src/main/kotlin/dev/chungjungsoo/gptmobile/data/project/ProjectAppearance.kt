package dev.chungjungsoo.gptmobile.data.project

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Work
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object ProjectIcons {
    const val FOLDER = "folder"
    const val WORK = "work"
    const val SCHOOL = "school"
    const val CODE = "code"
    const val SCIENCE = "science"
    const val BOOK = "book"

    val all = listOf(FOLDER, WORK, SCHOOL, CODE, SCIENCE, BOOK)

    @Composable
    fun image(key: String): ImageVector = when (key) {
        WORK -> Icons.Outlined.Work
        SCHOOL -> Icons.Outlined.School
        CODE -> Icons.Outlined.Code
        SCIENCE -> Icons.Outlined.Science
        BOOK -> Icons.Outlined.Book
        else -> Icons.Outlined.Folder
    }
}

object ProjectColors {
    val palette: List<Int> = listOf(
        0xFF6750A4.toInt(),
        0xFF006A6A.toInt(),
        0xFF8C4A32.toInt(),
        0xFF625B71.toInt(),
        0xFF7D5260.toInt(),
        0xFF006E1C.toInt()
    )

    fun color(argb: Int): Color = Color(argb)
}
