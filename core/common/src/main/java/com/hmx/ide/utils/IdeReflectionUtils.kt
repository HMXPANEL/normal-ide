/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.hmx.ide.utils

import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * IDE-native reflection helpers.
 *
 * Used to bypass hidden API reflection restrictions and to read hidden fields/methods.
 */
object IdeReflectionUtils {

  @JvmStatic
  private var hiddenApiRestrictionsBypassed = Build.VERSION.SDK_INT < Build.VERSION_CODES.P

  /** Bypass Android hidden API reflection restrictions (no-op below Android P). */
  @JvmStatic
  fun bypassHiddenAPIReflectionRestrictions() {
    if (!hiddenApiRestrictionsBypassed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      try {
        HiddenApiBypass.addHiddenApiExemptions("")
      } catch (_: Throwable) {
        // best effort; some devices block the bypass
      }
      hiddenApiRestrictionsBypassed = true
    }
  }

  @JvmStatic
  fun areHiddenAPIReflectionRestrictionsBypassed(): Boolean = hiddenApiRestrictionsBypassed

  @JvmStatic
  fun getDeclaredField(clazz: Class<*>, fieldName: String): Field? = try {
    clazz.getDeclaredField(fieldName).apply { isAccessible = true }
  } catch (_: Throwable) {
    null
  }

  @JvmStatic
  fun getDeclaredMethod(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): Method? =
    try {
      clazz.getDeclaredMethod(methodName, *parameterTypes).apply { isAccessible = true }
    } catch (_: Throwable) {
      null
    }

  @JvmStatic
  fun invokeMethod(method: Method, obj: Any?, vararg args: Any?): Any? = try {
    method.isAccessible = true
    method.invoke(obj, *args)
  } catch (_: Throwable) {
    null
  }
}
