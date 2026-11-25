package com.miss.ga

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.miss.ga.ui.util.withApplicationExtra
import com.miss.ga.ui.viewmodel.BlockedSendersViewModel
import com.miss.ga.ui.viewmodel.ConversationsViewModel
import com.miss.ga.ui.viewmodel.FilterStudioViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class NavViewModelFactoryTest {

    class TestAndroidViewModel(application: Application) : AndroidViewModel(application)

    private class FakeNavEntryOwner : ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)
        private val controller = SavedStateRegistryController.create(this).apply {
            performAttach()
        }
        override val viewModelStore = ViewModelStore()
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        init {
            enableSavedStateHandles()
        }
    }

    @Test
    fun savedStateFactoryFailsWithoutApplicationExtraAndSucceedsWithHelper() {
        val owner = FakeNavEntryOwner()
        val navDecoratorExtras = MutableCreationExtras().apply {
            set(SAVED_STATE_REGISTRY_OWNER_KEY, owner)
            set(VIEW_MODEL_STORE_OWNER_KEY, owner)
            set(ViewModelProvider.VIEW_MODEL_KEY, "filter_studio_key")
        }
        val factory = SavedStateViewModelFactory()

        // Mirrors ViewModelStoreNavEntryDecorator's default CreationExtras omitting APPLICATION_KEY
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(TestAndroidViewModel::class.java, navDecoratorExtras)
        }

        val app = Application()
        val fixedExtras = withApplicationExtra(navDecoratorExtras, app)
        val created = factory.create(TestAndroidViewModel::class.java, fixedExtras)

        assertSame(app, created.getApplication<Application>())
        assertSame(owner, fixedExtras[SAVED_STATE_REGISTRY_OWNER_KEY])
        assertSame(owner, fixedExtras[VIEW_MODEL_STORE_OWNER_KEY])
        assertEquals("filter_studio_key", fixedExtras[ViewModelProvider.VIEW_MODEL_KEY])
    }

    @Test
    fun withApplicationExtraPreservesExistingApplication() {
        val existingApp = Application()
        val otherApp = Application()
        val base = MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, existingApp)
        }

        val result = withApplicationExtra(base, otherApp)

        assertSame(base, result)
        assertSame(existingApp, result[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
    }

    @Test
    fun navScreenAndroidViewModelsExposeApplicationConstructor() {
        val viewModelClasses = listOf(
            FilterStudioViewModel::class.java,
            BlockedSendersViewModel::class.java,
            ConversationsViewModel::class.java
        )
        for (vmClass in viewModelClasses) {
            vmClass.getConstructor(Application::class.java)
        }
    }
}
