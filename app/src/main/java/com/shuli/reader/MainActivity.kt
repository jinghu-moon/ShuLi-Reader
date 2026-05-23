package com.shuli.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.shuli.reader.feature.bookshelf.BookshelfScreen
import com.shuli.reader.feature.bookshelf.BookshelfViewModel
import com.shuli.reader.ui.theme.ShuLiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as ShuLiApplication).appContainer
        val bookshelfViewModel = BookshelfViewModel(appContainer.bookRepository)

        setContent {
            ShuLiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BookshelfScreen(
                        viewModel = bookshelfViewModel,
                        onNavigateToReader = { bookId ->
                            // TODO: 导航到阅读器
                        },
                    )
                }
            }
        }
    }
}
