package org.tinymist.intellij.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.j_a.ide.lsp.api.descriptor.CommandLineLanguageServerDescriptor
import org.tinymist.intellij.settings.TinymistSettingsService
import java.io.File

class TinymistServerDescriptor(project: Project) :
    CommandLineLanguageServerDescriptor(project, TinymistLanguageServerSupport, "Tinymist") {

    companion object {
        private val LOG = Logger.getInstance(TinymistServerDescriptor::class.java)
        private const val TINYMIST_EXECUTABLE_NAME = "tinymist"
        // TODO: Consider how to handle DEV_FALLBACK_PATH or remove it.
        // For now, let's assume it's defined elsewhere or we remove it for simplicity.
        // If it's crucial, it needs to be properly defined or sourced.
        // private const val DEV_FALLBACK_PATH = "/path/to/your/dev/tinymist" // Example
    }

    override fun isSupported(file: VirtualFile): Boolean {
        return file.extension == "typ"
    }

    private fun findExecutablePath(): String? {
        val configuredPath = TinymistSettingsService.instance.tinymistExecutablePath
        if (configuredPath.isNotBlank()) {
            val configFile = File(configuredPath)
            if (configFile.exists() && configFile.isFile && configFile.canExecute()) {
                LOG.info("Using configured Tinymist executable path: $configuredPath")
                return configuredPath
            }
            LOG.warn("Configured Tinymist path is invalid or not executable: $configuredPath. Trying PATH.")
        }

        val pathExecutable = findExecutableOnPath(TINYMIST_EXECUTABLE_NAME)
        if (pathExecutable != null) {
            LOG.info("Found Tinymist executable on PATH: $pathExecutable")
            return pathExecutable
        }

        LOG.warn("Tinymist executable not found in settings or on PATH.")
        // LOG.warn("Tinymist executable not found. Trying development fallback.")
        // val devFallbackFile = File(DEV_FALLBACK_PATH)
        // if (devFallbackFile.exists() && devFallbackFile.isFile && devFallbackFile.canExecute()) {
        //     LOG.warn("Using DEVELOPMENT FALLBACK Tinymist executable path: $DEV_FALLBACK_PATH. Please configure the path in settings.")
        //     return DEV_FALLBACK_PATH
        // }
        // LOG.error("Tinymist executable not found. Please configure the path in 'Settings -> Tools -> Tinymist LSP' or ensure it's on your PATH. Development fallback also failed: $DEV_FALLBACK_PATH")
        return null
    }

    private fun findExecutableOnPath(name: String): String? {
        val systemPath = System.getenv("PATH")
        val pathDirs = systemPath?.split(File.pathSeparatorChar) ?: emptyList()
        for (dir in pathDirs) {
            val file = File(dir, name)
            if (file.exists() && file.isFile && file.canExecute()) {
                return file.absolutePath
            }
        }
        if (System.getProperty("os.name").lowercase().contains("win")) {
            for (dir in pathDirs) {
                val file = File(dir, "$name.exe")
                if (file.exists() && file.isFile && file.canExecute()) {
                    return file.absolutePath
                }
            }
        }
        return null
    }

    override fun createCommandLine(): GeneralCommandLine {
        val executablePath = findExecutablePath()
            ?: throw IllegalStateException("Tinymist executable not found. Please configure it in settings or ensure it's on PATH.")
        return GeneralCommandLine(executablePath, "lsp")
    }

    override fun getInitializationOptions(): Any? {
        val backgroundPreviewOpts = mapOf(
            "enabled" to true
        )
        val previewOpts = mapOf(
            "background" to backgroundPreviewOpts
        )
        return mutableMapOf<String, Any>(
            "preview" to previewOpts,
            "semanticTokens" to mapOf<String, Any>(),
            "completion" to mapOf<String, Any>(),
            "lint" to mapOf<String, Any>()
        )
    }
} 