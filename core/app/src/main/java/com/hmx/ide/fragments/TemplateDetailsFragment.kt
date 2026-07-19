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

package com.hmx.ide.fragments

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.TransitionManager
import com.hmx.ide.R
import com.hmx.ide.R.string
import com.hmx.ide.activities.MainActivity
import com.hmx.ide.adapters.TemplateWidgetsListAdapter
import com.hmx.ide.databinding.FragmentTemplateDetailsBinding
import com.hmx.ide.preferences.internal.GeneralPreferences
import com.hmx.ide.tasks.executeAsyncProvideError
import com.hmx.ide.templates.ProjectTemplateRecipeResult
import com.hmx.ide.templates.StringParameter
import com.hmx.ide.templates.Template
import com.hmx.ide.templates.base.util.getNewProjectName
import com.hmx.ide.templates.impl.ConstraintVerifier
import com.hmx.ide.utils.Environment
import com.hmx.ide.utils.TemplateRecipeExecutor
import com.hmx.ide.utils.DialogUtils
import com.hmx.ide.utils.flashError
import com.hmx.ide.utils.flashSuccess
import com.hmx.ide.viewmodel.MainViewModel
import org.slf4j.LoggerFactory
import java.io.File

/**
 * A fragment which shows a wizard-like interface for creating templates.
 *
 * @author Akash Yadav
 */
class TemplateDetailsFragment :
  FragmentWithBinding<FragmentTemplateDetailsBinding>(
    R.layout.fragment_template_details, FragmentTemplateDetailsBinding::bind) {

  private val viewModel by viewModels<MainViewModel>(
    ownerProducer = { requireActivity() })

  companion object {

    private val log = LoggerFactory.getLogger(TemplateDetailsFragment::class.java)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewModel.template.observe(viewLifecycleOwner) {
      binding.widgets.adapter = null
      viewModel.postTransition(viewLifecycleOwner) { bindWithTemplate(it) }
    }

    viewModel.creatingProject.observe(viewLifecycleOwner) {
      TransitionManager.beginDelayedTransition(binding.root)
      binding.progress.isVisible = it
      binding.finish.isEnabled = !it
      binding.previous.isEnabled = !it
    }

    binding.previous.setOnClickListener {
      viewModel.setScreen(MainViewModel.SCREEN_TEMPLATE_LIST)
    }

    binding.finish.setOnClickListener {
      viewModel.creatingProject.value = true
      val template = viewModel.template.value ?: run {
        viewModel.setScreen(MainViewModel.SCREEN_MAIN)
        return@setOnClickListener
      }

      val isValid = template.parameters.fold(true) { isValid, param ->
        if (param is StringParameter) {
          return@fold isValid && ConstraintVerifier.isValid(param.value,
            param.constraints)
        } else isValid
      }

      if (!isValid) {
        viewModel.creatingProject.value = false
        flashError(string.msg_invalid_project_details)
        return@setOnClickListener
      }

      val stringParams = template.parameters.filterIsInstance<StringParameter>()
      val projectNameParam = stringParams.firstOrNull { it.name == string.project_app_name }
      val saveLocationParam = stringParams.firstOrNull { it.name == string.wizard_save_location }

      val projectDir = projectNameParam?.let {
        val base = saveLocationParam?.value ?: Environment.PROJECTS_DIR.absolutePath
        File(base, it.value)
      }

      if (projectDir != null && projectDir.exists() && projectDir.listFiles()
          ?.isNotEmpty() == true
      ) {
        viewModel.creatingProject.value = false
        showProjectExistsDialog(template, projectNameParam!!)
        return@setOnClickListener
      }

      createProject(template)
    }

    binding.widgets.layoutManager = LinearLayoutManager(requireContext())
  }

  private fun createProject(template: Template<*>) {
    viewModel.creatingProject.value = true
    executeAsyncProvideError({
      template.recipe.execute(TemplateRecipeExecutor())
    }) { result, err ->

      viewModel.creatingProject.value = false
      if (result == null || err != null || result !is ProjectTemplateRecipeResult) {
        err?.printStackTrace()
        log.error("Failed to create project. result={}, err={}", result, err?.message)
        if (err != null) {
          flashError(err.cause?.message ?: err.message)
        } else {
          flashError(string.project_creation_failed)
        }
        return@executeAsyncProvideError
      }

      // Persist the created project so it shows up in Open Existing Project.
      GeneralPreferences.addRecentProject(result.data.projectDir.absolutePath)

      viewModel.setScreen(MainViewModel.SCREEN_MAIN)
      flashSuccess(string.project_created_successfully)

      viewModel.postTransition(viewLifecycleOwner) {
        // open the project
        (requireActivity() as MainActivity).openProject(result.data.projectDir)
      }
    }
  }

  private fun showProjectExistsDialog(template: Template<*>, projectNameParam: StringParameter) {
    val baseName = projectNameParam.value
    val suggested = getNewProjectName(Environment.PROJECTS_DIR.absolutePath, baseName)

    DialogUtils.newMaterialDialogBuilder(requireContext())
      .setTitle(string.title_project_exists)
      .setMessage(string.msg_project_exists)
      .setPositiveButton(string.action_rename_project) { _, _ ->
        projectNameParam.setValue(suggested)
        createProject(template)
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun bindWithTemplate(template: Template<*>?) {
    template ?: return

    binding.widgets.adapter = TemplateWidgetsListAdapter(template.widgets)
    binding.title.setText(template.templateName)
  }
}