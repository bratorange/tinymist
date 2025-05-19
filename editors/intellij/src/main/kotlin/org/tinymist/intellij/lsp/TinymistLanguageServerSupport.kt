package org.tinymist.intellij.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.j_a.ide.lsp.api.BaseLanguageServerSupport
import dev.j_a.ide.lsp.api.LanguageServerSupport

object TinymistLanguageServerSupport : BaseLanguageServerSupport(
    "org.tinymist.intellij.lsp.Tinymist", // A unique ID for this server type
    "Tinymist Language Server" // A user-friendly name
) {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LanguageServerSupport.LanguageServerStarter
    ) {
        if (file.extension == "typ") {
            serverStarter.ensureStarted(TinymistServerDescriptor(project))
        }
    }
} 