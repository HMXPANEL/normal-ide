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

package com.hmx.ide.lsp.java.actions

import com.hmx.ide.actions.ActionItem
import com.hmx.ide.lsp.actions.IActionsMenuProvider
import com.hmx.ide.lsp.java.actions.common.CommentAction
import com.hmx.ide.lsp.java.actions.common.FindReferencesAction
import com.hmx.ide.lsp.java.actions.common.GoToDefinitionAction
import com.hmx.ide.lsp.java.actions.common.OrganizeImportsAction
import com.hmx.ide.lsp.java.actions.common.RemoveUnusedImportsAction
import com.hmx.ide.lsp.java.actions.common.UncommentAction
import com.hmx.ide.lsp.java.actions.diagnostics.AddImportAction
import com.hmx.ide.lsp.java.actions.diagnostics.AddThrowsAction
import com.hmx.ide.lsp.java.actions.diagnostics.AutoFixImportsAction
import com.hmx.ide.lsp.java.actions.diagnostics.CreateMissingMethodAction
import com.hmx.ide.lsp.java.actions.diagnostics.FieldToBlockAction
import com.hmx.ide.lsp.java.actions.diagnostics.ImplementAbstractMethodsAction
import com.hmx.ide.lsp.java.actions.diagnostics.RemoveClassAction
import com.hmx.ide.lsp.java.actions.diagnostics.RemoveMethodAction
import com.hmx.ide.lsp.java.actions.diagnostics.RemoveUnusedThrowsAction
import com.hmx.ide.lsp.java.actions.diagnostics.SuppressUncheckedWarningAction
import com.hmx.ide.lsp.java.actions.diagnostics.VariableToStatementAction
import com.hmx.ide.lsp.java.actions.generators.GenerateConstructorAction
import com.hmx.ide.lsp.java.actions.generators.GenerateMissingConstructorAction
import com.hmx.ide.lsp.java.actions.generators.GenerateSettersAndGettersAction
import com.hmx.ide.lsp.java.actions.generators.GenerateToStringMethodAction
import com.hmx.ide.lsp.java.actions.generators.OverrideSuperclassMethodsAction

/**
 * Java code actions.
 * @author Akash Yadav
 */
object JavaCodeActionsMenu : IActionsMenuProvider {

  override val actions: List<ActionItem> =
    listOf(
      CommentAction(),
      UncommentAction(),
      GoToDefinitionAction(),
      FindReferencesAction(),
      AddImportAction(),
      AutoFixImportsAction(),
      ImplementAbstractMethodsAction(),
      VariableToStatementAction(),
      FieldToBlockAction(),
      RemoveClassAction(),
      RemoveMethodAction(),
      RemoveUnusedThrowsAction(),
      CreateMissingMethodAction(),
      SuppressUncheckedWarningAction(),
      AddThrowsAction(),
      GenerateSettersAndGettersAction(),
      OverrideSuperclassMethodsAction(),
      GenerateMissingConstructorAction(),
      GenerateConstructorAction(),
      GenerateToStringMethodAction(),
      RemoveUnusedImportsAction(),
      OrganizeImportsAction()
    )
}
