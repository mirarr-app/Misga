package com.miss.ga.ui.util

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Ensures [CreationExtras] carries [ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
 * when hosted by a Navigation 3 entry decorator whose child owner omits it.
 */
@PublishedApi
internal fun withApplicationExtra(
    baseExtras: CreationExtras,
    application: Application
): CreationExtras {
    if (baseExtras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] != null) {
        return baseExtras
    }
    return MutableCreationExtras(baseExtras).apply {
        set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
    }
}

/**
 * Resolves an [AndroidViewModel] scoped to the current [LocalViewModelStoreOwner],
 * supplying the [Application] instance when the owner's default extras omit it.
 */
@Composable
inline fun <reified VM : AndroidViewModel> applicationViewModel(
    key: String? = null
): VM {
    val owner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
    }
    val application = LocalContext.current.applicationContext as? Application
    val baseExtras = (owner as? HasDefaultViewModelProviderFactory)
        ?.defaultViewModelCreationExtras
        ?: CreationExtras.Empty
    val extras = remember(baseExtras, application) {
        if (application != null) {
            withApplicationExtra(baseExtras, application)
        } else {
            baseExtras
        }
    }
    return viewModel(
        viewModelStoreOwner = owner,
        key = key,
        extras = extras
    )
}
