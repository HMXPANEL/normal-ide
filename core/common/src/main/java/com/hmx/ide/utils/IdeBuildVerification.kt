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

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/**
 * IDE-native helper that verifies whether the running APK is an official AndroidIDE or F-Droid
 * build by comparing its signing certificate digest against the known official digests.
 */
object IdeBuildVerification {

  /** SHA-256 digest of the official AndroidIDE signing certificate. */
  private const val APK_RELEASE_ANDROIDIDE_SIGNING_CERTIFICATE_SHA256_DIGEST =
    "2DF2CBC1468CCB89DAD1733DC8E027BFF35EEEFA58C9EF35A5518A5D57912007"

  /** SHA-256 digest of the F-Droid signing certificate. */
  private const val APK_RELEASE_FDROID_SIGNING_CERTIFICATE_SHA256_DIGEST =
    "0E0E8D2836F926EF04E82D2AAD79589E214DC634ED9BE49EEAF10B89F8958F4C"

  /**
   * Get the SHA-256 digest of the signing certificate for the IDE package, or `null` on failure.
   */
  @JvmStatic
  fun getSigningCertificateSHA256DigestForPackage(context: Context): String? {
    return try {
      val packageInfo =
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
      val signature = packageInfo.signatures?.firstOrNull() ?: return null
      MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).let { digest ->
        digest.joinToString("") { "%02x".format(it) }
      }
    } catch (_: Throwable) {
      null
    }
  }

  /** Map a signing certificate digest to a release identifier ("AndroidIDE", "F-Droid", ...). */
  @JvmStatic
  fun getAPKRelease(signingCertificateSHA256Digest: String?): String {
    if (signingCertificateSHA256Digest == null) return "null"
    return when (signingCertificateSHA256Digest.uppercase()) {
      APK_RELEASE_ANDROIDIDE_SIGNING_CERTIFICATE_SHA256_DIGEST -> "AndroidIDE"
      APK_RELEASE_FDROID_SIGNING_CERTIFICATE_SHA256_DIGEST -> "F-Droid"
      else -> "Unknown"
    }
  }

  /** Whether the running APK is an official AndroidIDE or F-Droid build. */
  @JvmStatic
  fun isOfficialBuild(context: Context): Boolean {
    val digest = getSigningCertificateSHA256DigestForPackage(context) ?: return false
    val release = getAPKRelease(digest)
    return release == "AndroidIDE" || release == "F-Droid"
  }
}
