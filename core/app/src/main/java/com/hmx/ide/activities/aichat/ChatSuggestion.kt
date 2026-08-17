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

package com.hmx.ide.activities.aichat

/**
 * A quick action shown above the chat input.
 *
 * A suggestion only carries a [label] (chip text) and a [prompt] (text inserted into the existing
 * message input). It never performs an AI call itself — the standard send flow is reused, so all
 * providers behave identically.
 *
 * @property requiresProjectContext when `true`, the chip is hidden while no project index is
 *   available, because the prompt would otherwise have nothing to operate on.
 */
data class ChatSuggestion(
  val label: String,
  val prompt: String,
  val requiresProjectContext: Boolean = false,
) {

  companion object {

    /** The default chip set. Order is the display order. */
    @JvmStatic
    val DEFAULTS: List<ChatSuggestion> = listOf(
      ChatSuggestion(
        label = "Explain this",
        prompt = "Explain what this code does.",
      ),
      ChatSuggestion(
        label = "Fix errors",
        prompt = "Find and fix the errors in this project.",
        requiresProjectContext = true,
      ),
      ChatSuggestion(
        label = "Optimize",
        prompt = "Suggest performance optimizations for this code.",
      ),
      ChatSuggestion(
        label = "Generate tests",
        prompt = "Generate unit tests for this code.",
      ),
      ChatSuggestion(
        label = "Refactor",
        prompt = "Refactor this code for readability and maintainability.",
      ),
      ChatSuggestion(
        label = "Find bugs",
        prompt = "Review this code and list potential bugs.",
        requiresProjectContext = true,
      ),
      ChatSuggestion(
        label = "Search project",
        prompt = "Search the project for ",
        requiresProjectContext = true,
      ),
      ChatSuggestion(
        label = "Generate documentation",
        prompt = "Generate documentation for this code.",
      ),
    )

    /**
     * Returns the chips that are usable in the current state.
     *
     * @param hasProjectContext whether a project index is available.
     */
    @JvmStatic
    fun forState(hasProjectContext: Boolean): List<ChatSuggestion> =
      if (hasProjectContext) DEFAULTS else DEFAULTS.filterNot { it.requiresProjectContext }
  }
}
