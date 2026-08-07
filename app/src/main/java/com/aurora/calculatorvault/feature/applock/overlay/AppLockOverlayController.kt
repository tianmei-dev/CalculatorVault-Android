package com.aurora.calculatorvault.feature.applock.overlay

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aurora.calculatorvault.feature.applock.domain.AppLockSessionManager
import com.aurora.calculatorvault.feature.calculator.domain.VaultUnlockUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppLockOverlayController(
    private val context: Context,
    private val vaultUnlockUseCase: VaultUnlockUseCase,
    private val sessionManager: AppLockSessionManager,
    private val scope: CoroutineScope,
    private val onUnlocked: (String) -> Unit = {},
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val password = CharArray(MAX_PASSWORD_LENGTH)
    private val _state = MutableStateFlow<AppLockOverlayState?>(null)
    private var passwordLength = 0
    private var overlayView: ComposeView? = null
    private var overlayOwner: OverlayViewTreeOwner? = null
    private var verifyJob: Job? = null

    val state = _state.asStateFlow()

    @Synchronized
    fun isShowing(): Boolean = overlayView != null

    @Synchronized
    fun currentTargetPackage(): String? = _state.value?.targetPackageName

    suspend fun show(
        targetPackageName: String,
        targetAppName: String?,
        detectedAtElapsed: Long,
    ): Boolean = withContext(Dispatchers.Main) {
        if (targetPackageName.isBlank()) return@withContext false
        if (isShowing()) {
            Log.d(TAG, "overlay already showing=$targetPackageName")
            return@withContext true
        }
        Log.d(TAG, "overlay show requested=$targetPackageName")
        wipePassword()
        _state.value = AppLockOverlayState(
            targetPackageName = targetPackageName,
            targetAppName = targetAppName,
        )
        val owner = OverlayViewTreeOwner().also { it.start() }
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            isClickable = true
            isFocusable = true
            setContent {
                val uiState by state.collectAsState()
                uiState?.let {
                    AppLockOverlayContent(
                        state = it,
                        onDigit = ::inputDigit,
                        onDelete = ::deleteDigit,
                        onClear = ::clearInput,
                        onConfirm = ::verifyPassword,
                    )
                }
            }
            setOnKeyListener { _, keyCode, event ->
                keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP
            }
        }
        return@withContext try {
            windowManager.addView(view, layoutParams())
            overlayView = view
            overlayOwner = owner
            val delay = SystemClock.elapsedRealtime() - detectedAtElapsed
            Log.d(TAG, "overlay shown=$targetPackageName")
            Log.d(TAG, "app lock overlay delay ≈ $delay ms")
            true
        } catch (error: WindowManager.BadTokenException) {
            Log.w(TAG, "overlay show failed=${error::class.java.simpleName}")
            owner.destroy()
            _state.value = null
            false
        } catch (error: SecurityException) {
            Log.w(TAG, "overlay show failed=${error::class.java.simpleName}")
            owner.destroy()
            _state.value = null
            false
        } catch (error: RuntimeException) {
            Log.w(TAG, "overlay show failed=${error::class.java.simpleName}")
            owner.destroy()
            _state.value = null
            false
        }
    }

    fun hide() {
        val owner = synchronized(this) {
            overlayOwner.also {
                overlayOwner = null
            }
        }
        val view = synchronized(this) {
            overlayView.also {
                overlayView = null
            }
        }
        verifyJob?.cancel()
        verifyJob = null
        wipePassword()
        _state.value = null
        view?.let {
            runCatching {
                windowManager.removeView(it)
                Log.d(TAG, "overlay removed")
            }.onFailure { error ->
                Log.w(TAG, "overlay remove failed=${error::class.java.simpleName}")
            }
        }
        owner?.destroy()
    }

    private fun inputDigit(digit: Int) {
        val current = _state.value ?: return
        if (current.verifying || digit !in 0..9 || passwordLength >= MAX_PASSWORD_LENGTH) return
        password[passwordLength++] = ('0'.code + digit).toChar()
        _state.value = current.copy(
            enteredLength = passwordLength,
            passwordIncorrect = false,
        )
    }

    private fun deleteDigit() {
        val current = _state.value ?: return
        if (current.verifying) return
        if (passwordLength > 0) password[--passwordLength] = NULL_CHAR
        _state.value = current.copy(
            enteredLength = passwordLength,
            passwordIncorrect = false,
        )
    }

    private fun clearInput() {
        val current = _state.value ?: return
        if (current.verifying) return
        wipePassword()
        _state.value = current.copy(
            enteredLength = 0,
            passwordIncorrect = false,
        )
    }

    private fun verifyPassword() {
        val current = _state.value ?: return
        if (current.verifying || passwordLength !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) return
        val candidate = password.copyOf(passwordLength)
        wipePassword()
        _state.value = current.copy(verifying = true, enteredLength = 0, passwordIncorrect = false)
        Log.d(TAG, "password verification started")
        verifyJob = scope.launch {
            val verified = try {
                vaultUnlockUseCase.verify(candidate)
            } catch (_: Exception) {
                false
            } finally {
                candidate.fill(NULL_CHAR)
            }
            withContext(Dispatchers.Main) {
                val latest = _state.value ?: return@withContext
                if (verified) {
                    Log.d(TAG, "password verification success")
                    sessionManager.markUnlocked(latest.targetPackageName)
                    Log.d(TAG, "temporary unlock granted=${latest.targetPackageName}")
                    onUnlocked(latest.targetPackageName)
                    hide()
                } else {
                    Log.d(TAG, "password verification failed")
                    _state.value = latest.copy(
                        enteredLength = 0,
                        verifying = false,
                        passwordIncorrect = true,
                    )
                }
            }
        }
    }

    private fun layoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SECURE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    private fun wipePassword() {
        password.fill(NULL_CHAR)
        passwordLength = 0
    }

    private class OverlayViewTreeOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry =
            savedStateRegistryController.savedStateRegistry

        fun start() {
            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private companion object {
        const val TAG = "CV_APPLOCK"
        const val MIN_PASSWORD_LENGTH = 4
        const val MAX_PASSWORD_LENGTH = 8
        const val NULL_CHAR = '\u0000'
    }
}
