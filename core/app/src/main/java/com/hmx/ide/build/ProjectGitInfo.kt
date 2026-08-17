package com.hmx.ide.build

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

/**
 * Reads repository coordinates from the local git repository using JGit.
 * Nothing is hardcoded; owner/repo/branch are derived from the configured
 * remote and the current HEAD.
 */
object ProjectGitInfo {

  fun detect(projectDir: File): RepoInfo? {
    if (!File(projectDir, ".git").exists()) return null
    val git = Git.open(projectDir)
    return try {
      val branch = git.repository.branch
      val remotes = git.remoteList().call()
      val origin = remotes.firstOrNull { it.name == "origin" } ?: remotes.firstOrNull()
        ?: return null
      val remoteUrl = origin.urIs.firstOrNull()?.toString() ?: return null
      val (owner, repo) = parseOwnerRepo(remoteUrl) ?: return null
      RepoInfo(owner = owner, repo = repo, branch = branch, remoteUrl = remoteUrl)
    } finally {
      git.close()
    }
  }

  private fun parseOwnerRepo(url: String): Pair<String, String>? {
    // https://github.com/owner/repo.git  OR  git@github.com:owner/repo.git
    val cleaned = url.removeSuffix(".git")
    val m = Regex("""(?:https?://|git@)(?:[^/:@]+[:/]+)([^/]+)/(.+)$""").find(cleaned)
    if (m != null) {
      val owner = m.groupValues[1]
      val repo = m.groupValues[2]
      if (owner.isNotBlank() && repo.isNotBlank()) return owner to repo
    }
    return null
  }

  fun commitAndPush(
    projectDir: File,
    token: String,
    message: String,
  ): Boolean {
    val git = Git.open(projectDir)
    return try {
      git.add().addFilepattern(".").call()

      val status = git.status().call()
      val hasChanges = status.hasUncommittedChanges() ||
        status.untracked.isNotEmpty() ||
        status.missing.isNotEmpty()
      if (!hasChanges) {
        // Nothing to commit; still ensure the ref is pushed.
        push(git, token)
        return true
      }

      git.commit().setMessage(message).call()
      push(git, token)
      true
    } catch (e: Exception) {
      false
    } finally {
      git.close()
    }
  }

  private fun push(git: Git, token: String) {
    val cp = UsernamePasswordCredentialsProvider(token, "")
    git.push().setRemote("origin").setCredentialsProvider(cp).call()
  }
}
