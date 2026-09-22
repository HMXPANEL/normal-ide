/*
 *  This file is part of HMX IDE.
 *
 *  HMX IDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  HMX IDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with HMX IDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.hmx.ide.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.provider.DocumentsContractCompat
import androidx.core.provider.DocumentsContractCompat.buildDocumentUriUsingTree
import androidx.core.provider.DocumentsContractCompat.getTreeDocumentId
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hmx.ide.databinding.LayoutOpenProjectSheetBinding
import com.hmx.ide.preferences.internal.GeneralPreferences
import com.hmx.ide.resources.R.string
import com.hmx.ide.utils.DialogUtils
import com.hmx.ide.utils.Environment
import com.hmx.ide.utils.ProjectValidator
import com.hmx.ide.utils.flashError
import com.hmx.ide.adapters.RecentProjectsAdapter
import java.io.File

/**
 * Bottom sheet shown when the user taps "Open existing project".
 *
 * Shows recent projects (with instant search) and an "Import Project" button that
 * launches Android's Storage Access Framework folder picker.
 */
class OpenProjectSheet : BottomSheetDialogFragment() {

  private var binding: LayoutOpenProjectSheetBinding? = null
  private val adapter by lazy { RecentProjectsAdapter(::onProjectClicked) }

  companion object {
    const val TAG = "ide.openproject.sheet"
    private val ALLOWED_AUTHORITIES =
      setOf(BaseFragment.ANDROID_DOCS_AUTHORITY, BaseFragment.ANDROIDIDE_DOCS_AUTHORITY)
  }

  private val pickDirectory =
    registerForActivityResult(StartActivityForResult()) { result ->
      val uri = result.data?.data ?: return@registerForActivityResult
      val context = requireContext()
      val picked = DocumentFile.fromTreeUri(context, uri) ?: run {
        flashError(string.err_invalid_data_by_intent)
        return@registerForActivityResult
      }
      if (!picked.exists()) {
        flashError(string.msg_picked_isnt_dir)
        return@registerForActivityResult
      }

      val docUri = buildDocumentUriUsingTree(uri, getTreeDocumentId(uri)!!)!!
      val docId = DocumentsContractCompat.getDocumentId(docUri)!!
      val authority = docUri.authority

      if (!ALLOWED_AUTHORITIES.contains(authority)) {
        flashError(getString(string.err_authority_not_allowed, authority))
        return@registerForActivityResult
      }

      val dir = if (authority == BaseFragment.ANDROIDIDE_DOCS_AUTHORITY) {
        File(docId)
      } else {
        val split = docId.split(':')
        if ("primary" != split[0]) {
          flashError(getString(string.msg_select_from_primary_storage))
          return@registerForActivityResult
        }
        File(android.os.Environment.getExternalStorageDirectory(), split[1])
      }

      if (!dir.exists() || !dir.isDirectory) {
        flashError(string.err_invalid_data_by_intent)
        return@registerForActivityResult
      }

      // Persist URI permission so the folder stays accessible across restarts.
      runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }

      onDirectoryPicked(dir)
    }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return LayoutOpenProjectSheetBinding.inflate(inflater, container, false)
      .also { binding = it }
      .root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding!!.recentProjects.adapter = adapter
    refreshList("")

    binding!!.importProject.setOnClickListener {
      try {
        pickDirectory.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
      } catch (e: Exception) {
        requireActivity().flashError(getString(string.msg_dir_picker_failed, e.message))
      }
    }

    binding!!.searchInput.addTextChangedListener(object : android.text.TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        refreshList(s?.toString().orEmpty())
      }

      override fun afterTextChanged(s: android.text.Editable?) {}
    })
  }

  private fun refreshList(query: String) {
    val recent = GeneralPreferences.recentProjects
      .filter { it.isNotBlank() }
      .filter { File(it).exists() }
      .toSet()

    val scanned = scanProjectsDir()
      .filter { matches(it, query) }
      .map { it to (it in recent) }

    adapter.submit(scanned)
    binding!!.emptyView.visibility = if (scanned.isEmpty()) View.VISIBLE else View.GONE
    binding!!.emptyView.setText(
      if (query.isBlank()) string.msg_no_recent_projects else string.msg_no_projects_match
    )
  }

  private fun scanProjectsDir(): List<String> {
    val dir = Environment.PROJECTS_DIR
    if (!dir.isDirectory) return emptyList()
    return dir.listFiles()
      ?.filter { it.isDirectory && ProjectValidator.isSupportedProject(it) }
      ?.map { it.absolutePath }
      ?.sortedBy { it.lowercase() }
      .orEmpty()
  }

  private fun matches(path: String, query: String): Boolean {
    if (query.isBlank()) return true
    val lower = query.lowercase()
    val file = File(path)
    return file.name.lowercase().contains(lower) || path.lowercase().contains(lower)
  }

  private fun onProjectClicked(file: File) {
    if (!ProjectValidator.isSupportedProject(file)) {
      showUnsupportedDialog()
      return
    }
    GeneralPreferences.addRecentProject(file.absolutePath)
    dismiss()
    openProject(file)
  }

  private fun onDirectoryPicked(dir: File) {
    if (!ProjectValidator.isSupportedProject(dir)) {
      showUnsupportedDialog()
      return
    }
    GeneralPreferences.addRecentProject(dir.absolutePath)
    dismiss()
    openProject(dir)
  }

  private fun showUnsupportedDialog() {
    DialogUtils.newMaterialDialogBuilder(requireContext())
      .setTitle(string.title_unsupported_project)
      .setMessage(string.msg_unsupported_project)
      .setPositiveButton(android.R.string.ok, null)
      .show()
  }

  private fun openProject(root: File) {
    (requireActivity() as com.hmx.ide.activities.MainActivity).openProject(root)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    binding = null
  }
}
