package com.polyalerts.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.State
import kotlinx.coroutines.flow.StateFlow

/** Lifecycle-aware StateFlow collection for Compose. */
@Composable
fun <T> StateFlow<T>.collectAsStateSafe(): State<T> = collectAsStateWithLifecycle()
