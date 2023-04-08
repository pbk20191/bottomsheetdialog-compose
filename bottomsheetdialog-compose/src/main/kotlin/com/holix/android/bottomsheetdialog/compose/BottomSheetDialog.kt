package com.holix.android.bottomsheetdialog.compose

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.os.Build
import android.view.*
import androidx.activity.addCallback
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.annotation.Px
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.view.WindowCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.*


/**
 * Properties used to customize [BottomSheetDialog].
 *
 * @property dismissOnBackPress whether the dialog can be dismissed by pressing the back button.
 * If true, pressing the back button will call onDismissRequest.
 * @property dismissOnClickOutside whether the dialog can be dismissed by clicking outside the
 * dialog's bounds. If true, clicking outside the dialog will call onDismissRequest.
 * @property dismissWithAnimation [BottomSheetDialog.setDismissWithAnimation]
 * @property securePolicy Policy for setting [WindowManager.LayoutParams.FLAG_SECURE] on the
 * dialog's window.
 * @property navigationBarColor  Color to apply to the navigationBar.
 */
@Immutable
data class BottomSheetDialogProperties constructor(
    val dismissOnBackPress: Boolean = true,
    val dismissOnClickOutside: Boolean = true,
    val dismissWithAnimation: Boolean = false,
    val enableEdgeToEdge: Boolean = false,
    val securePolicy: SecureFlagPolicy = SecureFlagPolicy.Inherit,
    val navigationBarProperties: NavigationBarProperties = NavigationBarProperties(),
    val behaviorProperties: BottomSheetBehaviorProperties = BottomSheetBehaviorProperties()
) {

    @Deprecated("Use NavigationBarProperties(color = navigationBarColor) instead")
    constructor(
        dismissOnBackPress: Boolean = true,
        dismissOnClickOutside: Boolean = true,
        dismissWithAnimation: Boolean = false,
        securePolicy: SecureFlagPolicy = SecureFlagPolicy.Inherit,
        navigationBarColor: Color
    ) : this(
        dismissOnBackPress,
        dismissOnClickOutside,
        dismissWithAnimation,
        false,
        securePolicy,
        NavigationBarProperties(color = navigationBarColor)
    )

}

/**
 * Properties used to set [com.google.android.material.bottomsheet.BottomSheetBehavior].
 *
 * @see [com.google.android.material.bottomsheet.BottomSheetBehavior]
 */
@Immutable
data class BottomSheetBehaviorProperties(
    val state: State = State.Collapsed,
    val maxSize: DpSize = DpSize.Unspecified,
    val isDraggable: Boolean = true,
    @IntRange(from = 0)
    val expandedOffset: Int = 0,
    @FloatRange(from = 0.0, to = 1.0, fromInclusive = false, toInclusive = false)
    val halfExpandedRatio: Float = 0.5F,
    val isHideable: Boolean = true,
    val peekHeight: Dp = Dp.Unspecified,
    val isFitToContents: Boolean = true,
    val skipCollapsed: Boolean = false,
    val isGestureInsetBottomIgnored: Boolean = false
) {


    internal fun measureMaxSize(density: Density): IntSize {
        if (maxSize.isUnspecified)
            return IntSize(-1, -1)
        return with(density) {
            val width = maxSize.width.takeIf { it.isSpecified }?.roundToPx() ?: -1
            val height = maxSize.height.takeIf { it.isSpecified }?.roundToPx() ?: -1
            IntSize(width, height)
        }
    }

    @Px
    internal fun measurePeekHeight(density: Density): Int {
       return with(density) {
           if (peekHeight.isSpecified) {
               peekHeight.roundToPx()
           } else {
               PEEK_HEIGHT_AUTO
           }
       }
    }

    @Immutable
    enum class State {
        @Stable
        Expanded,
        @Stable
        HalfExpanded,
        @Stable
        Collapsed
    }

}

/**
 * Properties used to customize navigationBar.

 * @param color The **desired** [Color] to set. This may require modification if running on an
 * API level that only supports white navigation bar icons. Additionally this will be ignored
 * and [Color.Transparent] will be used on API 29+ where gesture navigation is preferred or the
 * system UI automatically applies background protection in other navigation modes.
 * @param darkIcons Whether dark navigation bar icons would be preferable.
 * @param navigationBarContrastEnforced Whether the system should ensure that the navigation
 * bar has enough contrast when a fully transparent background is requested. Only supported on
 * API 29+.
 * @param transformColorForLightContent A lambda which will be invoked to transform [color] if
 * dark icons were requested but are not available. Defaults to applying a black scrim.
 *
 * Inspired by [Accompanist SystemUiController](https://github.com/google/accompanist/blob/main/systemuicontroller/src/main/java/com/google/accompanist/systemuicontroller/SystemUiController.kt)
 */

@Immutable
data class NavigationBarProperties(
    val color: Color = Color.Unspecified,
    val darkIcons: Boolean = color.luminance() > 0.5f,
    val navigationBarContrastEnforced: Boolean = true,
)

/**
 * Opens a bottomsheet dialog with the given content.
 *
 * The dialog is visible as long as it is part of the composition hierarchy.
 * In order to let the user dismiss the BottomSheetDialog, the implementation of [onDismissRequest] should
 * contain a way to remove to remove the dialog from the composition hierarchy.
 *
 * Example usage:
 *
 * @sample com.holix.android.bottomsheetdialogcomposedemo.MainActivity
 *
 * @param onDismissRequest Executes when the user tries to dismiss the dialog.
 * @param properties [BottomSheetDialogProperties] for further customization of this dialog's behavior.
 * @param content The content to be displayed inside the dialog.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BottomSheetDialog(
    onDismissRequest: () -> Unit,
    properties: BottomSheetDialogProperties = BottomSheetDialogProperties(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val context = LocalContext.current
    val composition = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val dialogId = rememberSaveable { UUID.randomUUID() }
    val dialog = remember(view, density, context) {
        BottomSheetDialogWrapper(
            onDismissRequest,
            properties,
            view,
            layoutDirection,
            density,
            dialogId,
            context
        ).apply {
            setContent(composition) {
                BottomSheetDialogLayout(
                    Modifier
                        .nestedScroll(rememberNestedScrollInteropConnection())
                        .scrollable(state = rememberScrollableState{ 0f }, orientation = Orientation.Vertical)
                        .semantics { dialog() },
                ) {
                    currentContent()
                }
            }
        }
    }

    DisposableEffect(dialog) {
        dialog.show()
        onDispose {
            dialog.dismiss()
            dialog.disposeComposition()
        }
    }

    SideEffect {
        dialog.updateParameters(
            onDismissRequest = onDismissRequest,
            properties = properties,
            layoutDirection = layoutDirection,
            density = density
        )
    }
}



@Suppress("ViewConstructor")
private class BottomSheetDialogLayout(
    context: Context,
    override val window: Window
) : AbstractComposeView(context), DialogWindowProvider {
    private var content: @Composable () -> Unit by mutableStateOf({})

    override var shouldCreateCompositionOnAttachedToWindow: Boolean = false
        private set

    fun setContent(parent: CompositionContext, content: @Composable () -> Unit) {
        setParentCompositionContext(parent)
        this.content = content
        shouldCreateCompositionOnAttachedToWindow = true
        createComposition()
    }

    @Composable
    override fun Content() {
        content()
    }
}

private class BottomSheetDialogWrapper(
    private var onDismissRequest: () -> Unit,
    private var properties: BottomSheetDialogProperties,
    private val composeView: View,
    layoutDirection: LayoutDirection,
    density: Density,
    dialogId: UUID,
    context: Context
) : BottomSheetDialog(context),
    ViewRootForInspector {
    private val bottomSheetDialogLayout: BottomSheetDialogLayout

    private val bottomSheetCallbackForAnimation: BottomSheetCallback = object : BottomSheetCallback() {
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
        }

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == STATE_HIDDEN) {
                onDismissRequest()
            }
        }
    }

    private val maxSupportedElevation = 30.dp

    override val subCompositionView: AbstractComposeView get() = bottomSheetDialogLayout

    // to control insets
    private val windowInsetsController = window?.let {
        WindowCompat.getInsetsController(it, it.decorView)
    }
    private var navigationBarDarkContentEnabled: Boolean
        get() = windowInsetsController?.isAppearanceLightNavigationBars == true
        set(value) {
            windowInsetsController?.isAppearanceLightNavigationBars = value
        }

    private var isNavigationBarContrastEnforced: Boolean
        get() = Build.VERSION.SDK_INT >= 29 && window?.isNavigationBarContrastEnforced == true
        set(value) {
            if (Build.VERSION.SDK_INT >= 29) {
                window?.isNavigationBarContrastEnforced = value
            }
        }

    init {
        val window = window ?: error("Dialog has no window")
        window.requestFeature(Window.FEATURE_NO_TITLE)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        bottomSheetDialogLayout = BottomSheetDialogLayout(context, window).apply {
            // Set unique id for AbstractComposeView. This allows state restoration for the state
            // defined inside the Dialog via rememberSaveable()
            setTag(androidx.compose.ui.R.id.compose_view_saveable_id_tag, "Dialog:$dialogId")
            // Enable children to draw their shadow by not clipping them
            clipChildren = false
            // Allocate space for elevation
            with(density) { elevation = maxSupportedElevation.toPx() }
            // Simple outline to force window manager to allocate space for shadow.
            // Note that the outline affects clickable area for the dismiss listener. In case of
            // shapes like circle the area for dismiss might be to small (rectangular outline
            // consuming clicks outside of the circle).
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, result: Outline) {
                    result.setRect(0, 0, view.width, view.height)
                    // We set alpha to 0 to hide the view's shadow and let the composable to draw
                    // its own shadow. This still enables us to get the extra space needed in the
                    // surface.
                    result.alpha = 0f
                }
            }
        }

        /**
         * Disables clipping for [this] and all its descendant [ViewGroup]s until we reach a
         * [BottomSheetDialogLayout] (the [ViewGroup] containing the Compose hierarchy).
         */
        fun ViewGroup.disableClipping() {
            clipChildren = false
            if (this is BottomSheetDialogLayout) return
            for (i in 0 until childCount) {
                (getChildAt(i) as? ViewGroup)?.disableClipping()
            }
        }

        // Turn of all clipping so shadows can be drawn outside the window
        (window.decorView as? ViewGroup)?.disableClipping()
        setContentView(bottomSheetDialogLayout)
        ViewTreeLifecycleOwner.set(bottomSheetDialogLayout, ViewTreeLifecycleOwner.get(composeView))
        ViewTreeViewModelStoreOwner.set(bottomSheetDialogLayout, ViewTreeViewModelStoreOwner.get(composeView))
        bottomSheetDialogLayout.setViewTreeOnBackPressedDispatcherOwner(this)
        bottomSheetDialogLayout.setViewTreeSavedStateRegistryOwner(
            composeView.findViewTreeSavedStateRegistryOwner()
        )
        // Initial setup
        updateParameters(onDismissRequest, properties, layoutDirection, density)

        onBackPressedDispatcher.addCallback(this) {
            if (properties.dismissOnBackPress) {
                cancel()
            }
        }
    }

    private fun setLayoutDirection(layoutDirection: LayoutDirection) {
        bottomSheetDialogLayout.layoutDirection = when (layoutDirection) {
            LayoutDirection.Ltr -> android.util.LayoutDirection.LTR
            LayoutDirection.Rtl -> android.util.LayoutDirection.RTL
        }
    }

    // TODO(b/159900354): Make the Android Dialog full screen and the scrim fully transparent

    fun setContent(parentComposition: CompositionContext, children: @Composable () -> Unit) {
        bottomSheetDialogLayout.setContent(parentComposition, children)
    }

    private fun setSecurePolicy(securePolicy: SecureFlagPolicy) {
        val secureFlagEnabled =
            securePolicy.shouldApplySecureFlag(composeView.isFlagSecureEnabled())
        window!!.setFlags(
            if (secureFlagEnabled) {
                WindowManager.LayoutParams.FLAG_SECURE
            } else {
                WindowManager.LayoutParams.FLAG_SECURE.inv()
            },
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    private fun setNavigationBarProperties(properties: NavigationBarProperties) {
        with(properties) {
            navigationBarDarkContentEnabled = darkIcons
            isNavigationBarContrastEnforced = navigationBarContrastEnforced

            window?.navigationBarColor = when {
                darkIcons && windowInsetsController?.isAppearanceLightNavigationBars != true -> {
                    // If we're set to use dark icons, but our windowInsetsController call didn't
                    // succeed (usually due to API level), we instead transform the color to maintain
                    // contrast
                    Color(0f, 0f, 0f, 0.3f).compositeOver(color)
                }
                else -> color
            }.toArgb()
        }
    }

    override fun setDismissWithAnimation(dismissWithAnimation: Boolean) {
        super.setDismissWithAnimation(dismissWithAnimation)
        if (dismissWithAnimation) {
            behavior.addBottomSheetCallback(bottomSheetCallbackForAnimation)
        } else {
            behavior.removeBottomSheetCallback(bottomSheetCallbackForAnimation)
        }
    }

    private fun setBehaviorProperties(behaviorProperties: BottomSheetBehaviorProperties, density: Density) {
        this.behavior.state = when (behaviorProperties.state) {
            BottomSheetBehaviorProperties.State.Expanded -> STATE_EXPANDED
            BottomSheetBehaviorProperties.State.Collapsed -> STATE_COLLAPSED
            BottomSheetBehaviorProperties.State.HalfExpanded -> STATE_HALF_EXPANDED
        }
        val maxSize = behaviorProperties.measureMaxSize(density)
        this.behavior.maxWidth = maxSize.width
        this.behavior.maxHeight = maxSize.height
        this.behavior.isDraggable = behaviorProperties.isDraggable
        this.behavior.expandedOffset = behaviorProperties.expandedOffset
        this.behavior.halfExpandedRatio = behaviorProperties.halfExpandedRatio
        this.behavior.isHideable = behaviorProperties.isHideable
        this.behavior.peekHeight = behaviorProperties.measurePeekHeight(density)
        this.behavior.isFitToContents = behaviorProperties.isFitToContents
        this.behavior.skipCollapsed = behaviorProperties.skipCollapsed
        this.behavior.isGestureInsetBottomIgnored = behaviorProperties.isGestureInsetBottomIgnored
    }

    fun updateParameters(
        onDismissRequest: () -> Unit,
        properties: BottomSheetDialogProperties,
        layoutDirection: LayoutDirection,
        density: Density
    ) {
        this.onDismissRequest = onDismissRequest
        this.properties = properties
        setSecurePolicy(properties.securePolicy)
        setLayoutDirection(layoutDirection)
        setCanceledOnTouchOutside(properties.dismissOnClickOutside)
        setNavigationBarProperties(properties.navigationBarProperties)
        setBehaviorProperties(properties.behaviorProperties, density)
        dismissWithAnimation = properties.dismissWithAnimation
    }

    fun disposeComposition() {
        bottomSheetDialogLayout.disposeComposition()
    }

    override fun cancel() {
        if (properties.dismissWithAnimation) {
            // call setState(STATE_HIDDEN) -> onDismissRequest will be called in BottomSheetCallback
            super.cancel()
        } else {
            // dismiss with window animation
            onDismissRequest()
        }
    }
}

@Composable
private fun BottomSheetDialogLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val width = placeables.maxByOrNull { it.width }?.width ?: constraints.minWidth
        val height = placeables.maxByOrNull { it.height }?.height ?: constraints.minHeight
        layout(width, height) {
            placeables.forEach { it.placeRelative(0, 0) }
        }
    }
}

fun View.isFlagSecureEnabled(): Boolean {
    val windowParams = rootView.layoutParams as? WindowManager.LayoutParams
    if (windowParams != null) {
        return (windowParams.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
    }
    return false
}

fun SecureFlagPolicy.shouldApplySecureFlag(isSecureFlagSetOnParent: Boolean): Boolean {
    return when (this) {
        SecureFlagPolicy.SecureOff -> false
        SecureFlagPolicy.SecureOn -> true
        SecureFlagPolicy.Inherit -> isSecureFlagSetOnParent
    }
}
