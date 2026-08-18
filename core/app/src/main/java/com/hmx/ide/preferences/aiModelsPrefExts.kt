package com.hmx.ide.preferences

import android.content.Intent
import com.hmx.ide.activities.AIModelsActivity
import com.hmx.ide.resources.R

internal val aiModelsPreference =
  SimpleClickablePreference(
    key = "idepref_ai_models",
    title = R.string.idepref_ai_models_title,
    summary = R.string.idepref_ai_models_summary,
    icon = R.drawable.ic_ai_chat
  ) {
    it.context.startActivity(Intent(it.context, AIModelsActivity::class.java))
    true
  }
