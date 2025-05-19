package org.tinymist.intellij.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.diagnostic.Logger
import dev.j_a.ide.lsp.api.LanguageServerManager
import javax.swing.JComponent

class TinymistSettingsConfigurable : Configurable {

    private var settingsPanel: TinymistSettingsPanel? = null
    private val settingsService = TinymistSettingsService.instance

    companion object {
        private val LOG = Logger.getInstance(TinymistSettingsConfigurable::class.java)
        // The j-a.dev library uses the LanguageServerDescriptor instance for identification,
        // so a static server ID string might not be directly used in the same way.
    }

    override fun getDisplayName(): String = "Tinymist LSP"

    override fun getHelpTopic(): String? = null

    override fun createComponent(): JComponent? {
        settingsPanel = TinymistSettingsPanel()
        return settingsPanel?.mainPanel
    }

    override fun isModified(): Boolean {
        return settingsPanel?.tinymistExecutablePath != settingsService.tinymistExecutablePath
    }

    override fun apply() {
        val currentSettingsPath = settingsService.state.tinymistExecutablePath
        val newPanelPath = settingsPanel?.tinymistExecutablePathField?.text ?: ""

        val pathChanged = currentSettingsPath != newPanelPath

        settingsService.state.tinymistExecutablePath = newPanelPath

        if (pathChanged) {
            LOG.info("Tinymist executable path changed. Old: '$currentSettingsPath', New: '$newPanelPath'. Requesting server restart for all projects.")

            val openProjects = ProjectManager.getInstance().openProjects
            if (openProjects.isEmpty()) {
                LOG.info("No open projects to restart Tinymist server for.")
                return
            }

            openProjects.forEach { project ->
                if (!project.isDisposed && project.isOpen) {
                    val lspManager = LanguageServerManager.getInstance(project)
                    // We need to find our specific server. The descriptor class can be used.
                    // Assuming TinymistServerDescriptor is the one associated with TinymistLanguageServerSupport.
                    val configurations = lspManager.getConfigurations(org.tinymist.intellij.lsp.TinymistLanguageServerSupport)
                    if (configurations.isNotEmpty()) {
                        configurations.forEach { config ->
                            LOG.info("Restarting Tinymist server for project: ${project.name} with descriptor: ${config.descriptor::class.java.simpleName}")
                            lspManager.restart(config.descriptor) // Restart using the descriptor
                        }
                    } else {
                        LOG.warn("No active Tinymist server configuration found for project: ${project.name}. Restart will not be automatically triggered. It might start on next file open.")
                    }
                }
            }
        }
    }

    override fun reset() {
        settingsPanel?.tinymistExecutablePath = settingsService.tinymistExecutablePath
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }
} 