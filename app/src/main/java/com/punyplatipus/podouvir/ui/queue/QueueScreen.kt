package com.punyplatipus.podouvir.ui.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.punyplatipus.podouvir.R

@Composable
fun QueueScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.navigation_queue_placeholder))
    }
}
