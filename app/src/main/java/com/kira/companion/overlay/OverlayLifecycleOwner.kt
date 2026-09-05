package com.kira.companion.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * A view added directly to [android.view.WindowManager] (as our overlay bubble is) has no
 * Activity/Fragment to provide lifecycle, ViewModelStore or SavedStateRegistry — but
 * Compose's `ComposeView` requires all three to be set on the view tree before its
 * content can be attached. This class supplies minimal, manually-driven implementations
 * scoped to the overlay service's own lifetime.
 */
class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun performRestore() {
        savedStateRegistryController.performRestore(null)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}
