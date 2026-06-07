package com.eldraft.android.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.eldraft.android.ElDraftApplication
import com.eldraft.android.ui.auth.AuthViewModel
import com.eldraft.android.ui.profile.ProfileViewModel
import androidx.compose.ui.platform.LocalContext

/** Recupera la Application tipada desde el contexto de Compose. */
@Composable
fun appContainer(): ElDraftApplication =
    LocalContext.current.applicationContext as ElDraftApplication

/** Factory centralizado de los ViewModels de la Fase 1. */
fun elDraftViewModelFactory(app: ElDraftApplication): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { AuthViewModel(app.api, app.session, app.googleAuth) }
        initializer { ProfileViewModel(app.api, app.session) }
    }

@Composable
inline fun <reified VM : ViewModel> elDraftViewModel(): VM {
    val app = appContainer()
    return viewModel(factory = elDraftViewModelFactory(app))
}
