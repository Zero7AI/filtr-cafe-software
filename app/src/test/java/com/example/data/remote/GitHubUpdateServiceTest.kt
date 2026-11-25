package com.example.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateServiceTest {

    private val service = GitHubUpdateService.getInstance()

    @Test
    fun `isNewerVersion returns true for strictly newer semver and prefixed tags`() {
        assertTrue(service.isNewerVersion("2.2.0", "2.1"))
        assertTrue(service.isNewerVersion("v2.2", "2.1"))
        assertTrue(service.isNewerVersion("V3.0.0", "2.9.9"))
        assertTrue(service.isNewerVersion("release-2.2", "2.1"))
        assertTrue(service.isNewerVersion("2.1.1", "2.1"))
        assertTrue(service.isNewerVersion("2.10.0", "2.2.0"))
        assertTrue(service.isNewerVersion("2.2.0-rc1", "2.1.5"))
    }

    @Test
    fun `isNewerVersion prevents downgrades and returns false for equal versions`() {
        assertFalse(service.isNewerVersion("2.1", "2.1"))
        assertFalse(service.isNewerVersion("v2.1.0", "2.1"))
        assertFalse(service.isNewerVersion("2.1.0+build42", "2.1.0"))
        assertFalse(service.isNewerVersion("2.0", "2.1"))
        assertFalse(service.isNewerVersion("v1.9.9", "2.1"))
        assertFalse(service.isNewerVersion("2.1", "2.1.1"))
    }

    @Test
    fun `isNewerVersion returns false for invalid empty or non numeric tags`() {
        assertFalse(service.isNewerVersion("", "2.1"))
        assertFalse(service.isNewerVersion("   ", "2.1"))
        assertFalse(service.isNewerVersion("2.2", ""))
        assertFalse(service.isNewerVersion("latest", "2.1"))
        assertFalse(service.isNewerVersion("v-alpha", "2.1"))
        assertFalse(service.isNewerVersion("2.2", "unknown"))
    }

    @Test
    fun `isAllowedGitHubHttpsUrl allows https GitHub and githubusercontent hosts`() {
        assertTrue(
            GitHubUpdateService.isAllowedGitHubHttpsUrl(
                "https://github.com/Zero7AI/filtr-cafe-software/releases/download/v2.2/filtr-billing-v2.2.apk"
            )
        )
        assertTrue(
            GitHubUpdateService.isAllowedGitHubHttpsUrl(
                "https://api.github.com/repos/Zero7AI/filtr-cafe-software/releases/assets/12345"
            )
        )
        assertTrue(
            GitHubUpdateService.isAllowedGitHubHttpsUrl(
                "https://objects.githubusercontent.com/github-production-release-asset-123/filtr.apk"
            )
        )
        assertTrue(
            GitHubUpdateService.isAllowedGitHubHttpsUrl(
                "  HTTPS://GITHUB.COM/Zero7AI/filtr-cafe-software/releases/latest  "
            )
        )
    }

    @Test
    fun `isAllowedGitHubHttpsUrl rejects insecure http untrusted hosts and malformed urls`() {
        assertFalse(GitHubUpdateService.isAllowedGitHubHttpsUrl("http://github.com/Zero7AI/repo/app.apk"))
        assertFalse(GitHubUpdateService.isAllowedGitHubHttpsUrl("http://api.github.com/repos/Zero7AI/repo"))
        assertFalse(GitHubUpdateService.isAllowedGitHubHttpsUrl("http://objects.githubusercontent.com/app.apk"))

        assertFalse(GitHubUpdateService.isAllowedGitHubHttpsUrl("https://evil.com/filtr.apk"))
        assertFalse(GitHubUpdateService.isAllowedGitHubHttpsUrl("https://github.com.evil.com/filtr.apk"))
        assertFalse(GitHubUpdateService.isAllowedGitHubHttpsUrl("https://githubusercontent.com/filtr.apk"))
        assertFalse(GitHubUpdateService.isAllowedGitHubHttpsUrl("https://evilgithubusercontent.com/filtr.apk"))
        assertFalse(GitHubUpdateService.isAllowedGitHubHttpsUrl("https://gist.github.com/Zero7AI/app.apk"))

        assertFalse(GitHubUpdateService.isAllowedGitHubHttpsUrl(""))
        assertFalse(GitHubUpdateService.isAllowedGitHubHttpsUrl("   "))
        assertFalse(GitHubUpdateService.isAllowedGitHubHttpsUrl("ftp://github.com/app.apk"))
        assertFalse(GitHubUpdateService.isAllowedGitHubHttpsUrl("file:///tmp/app.apk"))
        assertFalse(GitHubUpdateService.isAllowedGitHubHttpsUrl("https:///missing-host"))
        assertFalse(GitHubUpdateService.isAllowedGitHubHttpsUrl("not a valid uri ://"))
    }
}
