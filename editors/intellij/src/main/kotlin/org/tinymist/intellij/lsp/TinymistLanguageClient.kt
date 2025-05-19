package org.tinymist.intellij.lsp

import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.PublishDiagnosticsParams
// import org.eclipse.lsp4j.jsonrpc.services.JsonNotification // This might need a new way to be handled
import com.intellij.openapi.diagnostic.Logger
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.ShowMessageRequestParams
import java.util.concurrent.CompletableFuture

// TODO: Re-evaluate how to integrate this client with j-a.dev LSP library.
// The previous inheritance from LanguageClientImpl (lsp4ij) is no longer valid.
// Custom notification handling and message interception need a new approach.
class TinymistLanguageClient(
    private val project: Project // Retain project if needed for future context
) {

    companion object {
        private val LOG = Logger.getInstance(TinymistLanguageClient::class.java)
    }

    // This method was an override. Its functionality needs to be reintegrated if possible.
    fun customPublishDiagnostics(diagnostics: PublishDiagnosticsParams) {
        val newDiagnostics = diagnostics.diagnostics.map { originalDiagnostic ->
            val originalMessage = originalDiagnostic.message
            val newMessage = originalMessage.replace("\n", "<br>")

            val codeAsString: String? = when {
                originalDiagnostic.code == null -> null
                originalDiagnostic.code.isLeft -> originalDiagnostic.code.left
                originalDiagnostic.code.isRight -> originalDiagnostic.code.right.toString()
                else -> null
            }

            val newDiagnostic = Diagnostic(
                originalDiagnostic.range,
                newMessage,
                originalDiagnostic.severity,
                originalDiagnostic.source,
                codeAsString
            )

            originalDiagnostic.relatedInformation?.let { newDiagnostic.relatedInformation = it }
            originalDiagnostic.tags?.let { newDiagnostic.tags = it }
            originalDiagnostic.codeDescription?.let { newDiagnostic.codeDescription = it }
            originalDiagnostic.data?.let { newDiagnostic.data = it }

            newDiagnostic
        }
        // How to send this back or intercept the original call needs to be determined
        // with the j-a.dev library.
        LOG.info("Custom publishDiagnostics called. URI: ${diagnostics.uri}, Count: ${newDiagnostics.size}")
        // super.publishDiagnostics(PublishDiagnosticsParams(diagnostics.uri, newDiagnostics)) // This was the old call
    }

    // @JsonNotification("tinymist/document") // TODO: Find new way to handle custom notifications
    fun handleDocument(params: Any?) {
        System.err.println("TinymistLanguageClient: Received tinymist/document with params: ${'$'}params")
    }

    // @JsonNotification("tinymist/documentOutline") // TODO: Find new way to handle custom notifications
    fun handleDocumentOutline(params: Any?) {
        LOG.info("Received tinymist/documentOutline notification with params: ${'$'}params")
    }

    // This method was an override. Its functionality needs to be reintegrated if possible.
    fun customShowMessageRequest(params: ShowMessageRequestParams): CompletableFuture<MessageActionItem?> {
        LOG.warn("Received showMessageRequest from server. Type: ${params.type}, Message: '${params.message}'. Actions: ${params.actions}. Suppressing UI and returning null action item.")
        return CompletableFuture.completedFuture(null)
    }
}
