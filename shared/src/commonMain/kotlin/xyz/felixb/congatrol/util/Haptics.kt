package xyz.felixb.congatrol.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A light tick for every tap that something acted on, anywhere below: set once at the root rather
 * than on each button, tab and card.
 *
 * It listens after everything else has had its turn. A tap that a button or anything else
 * clickable took comes by with its release consumed, a tap on nothing does not, and a press held
 * long enough to be a long press has its own, firmer feedback from whatever took it. Dialogs and
 * menus are windows of their own and stay silent.
 */
@Composable
fun Modifier.tapHaptics(): Modifier {
    val haptics = LocalHapticFeedback.current
    return pointerInput(haptics) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            var up: PointerInputChange? = null
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (up == null) {
                    val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUpIgnoreConsumed()) up = change
                }
            }
            val released = up ?: return@awaitEachGesture
            val moved = (released.position - down.position).getDistance()
            if (released.isConsumed && moved < viewConfiguration.touchSlop) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            }
        }
    }
}
