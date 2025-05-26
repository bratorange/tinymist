package org.tinymist.intellij.lsp

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.redhat.devtools.lsp4ij.LanguageServerManager
import com.redhat.devtools.lsp4ij.services.LanguageServerService
import com.redhat.devtools.lsp4ij.utils.LSPUtils
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.tinymist.intellij.TinymistLanguageClient
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

abstract class LspIntegrationTestBase : BasePlatformTestCase() {

    protected lateinit var server: LanguageServer
    protected lateinit var textDocumentService: TextDocumentService
    protected lateinit var client: TinymistLanguageClient
    protected lateinit var projectPath: String


    override fun setUp() {
        super.setUp()
        projectPath = myFixture.project.basePath ?: throw IllegalStateException("Project path not found")

        // Configure a dummy file to ensure the server starts and we can fetch it.
        // myFixture.configureByText should trigger lsp4ij's EditorManager to send didOpen.
        val dummyFile = myFixture.configureByText("dummy.typ", """#let x = "hello"""")
        val virtualFile = dummyFile.virtualFile

        val languageServerManager = LanguageServerManager.getInstance(project)
        // Attempt to start servers if not already running. This is particularly important
        // if tests run in an environment where servers aren't auto-started.
        if (!languageServerManager.isAnyServerRunning) {
             languageServerManager.startServers(null)
        }


        waitForServerInitialization(project, virtualFile)

        val languageServerService = LanguageServerService.getInstance(project)
        val connectedServer = languageServerService.getLanguageServer(virtualFile)
        if (connectedServer == null) {
            val allServers = languageServerService.connectedServers
            val serverDetails = allServers.entries.joinToString { "${it.key.name} -> ${it.value?.javaClass?.name}" }
            val startedServerInstances = LanguageServerManager.getInstance(project).startedServerInstances
            val startedDetails = startedServerInstances.joinToString { "${it.project.name} for ${it.fileExtension} -> ${it.javaClass.name}" }
            throw IllegalStateException(
                "Language server not found for ${virtualFile.url} after initial setup and wait. " +
                "Connected servers: [$serverDetails]. Started server instances by manager: [$startedDetails]. " +
                "Check if Typst LSP server is registered, configured for '.typ' files, and started correctly."
            )
        }
        this.server = connectedServer
        this.textDocumentService = server.textDocumentService

        val clientProxy = languageServerService.getLanguageClient(virtualFile)
        if (clientProxy !is TinymistLanguageClient) {
            throw IllegalStateException("Client proxy is not an instance of TinymistLanguageClient. Actual: ${clientProxy?.javaClass?.name} for ${virtualFile.url}")
        }
        this.client = clientProxy
    }

    private fun waitForServerInitialization(project: Project, virtualFile: VirtualFile, timeoutSeconds: Long = 30) {
        val languageServerService = LanguageServerService.getInstance(project)
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds)
        var serverFound = false
        while (System.currentTimeMillis() < deadline) {
            ReadAction.run<Throwable> {
                if (languageServerService.getLanguageServer(virtualFile) != null && languageServerService.getLanguageClient(virtualFile) is TinymistLanguageClient) {
                    serverFound = true
                }
            }
            if (serverFound) return
            Thread.sleep(200) // Poll interval
        }
        // Final check after timeout
        val currentServer = ReadAction.compute<LanguageServer?, Throwable> { languageServerService.getLanguageServer(virtualFile) }
        val currentClient = ReadAction.compute<Any?, Throwable> { languageServerService.getLanguageClient(virtualFile) }

        if (currentServer == null) {
             val allServers = languageServerService.connectedServers
            val serverDetails = allServers.entries.joinToString { "${it.key.name} -> ${it.value?.javaClass?.name}" }
            val startedServerInstances = LanguageServerManager.getInstance(project).startedServerInstances
            val startedDetails = startedServerInstances.joinToString { "${it.project.name} for ${it.fileExtension} -> ${it.javaClass.name}" }
            throw IllegalStateException(
                "Timeout waiting for language server to initialize for ${virtualFile.url}. " +
                "Connected servers: [$serverDetails]. Started server instances by manager: [$startedDetails]."
            )
        }
        if (currentClient !is TinymistLanguageClient) {
             throw IllegalStateException("Timeout waiting for TinymistLanguageClient to initialize for ${virtualFile.url}. Actual client: ${currentClient?.javaClass?.name}")
        }
    }

    protected fun configureTypstFile(relativePath: String, content: String): PsiFile {
        // configureByText opens the file in an editor and should trigger lsp4ij's EditorManager
        // to send a didOpen notification, leading to server initialization for this file.
        val psiFile = myFixture.configureByText(relativePath, content)
        val virtualFile = psiFile.virtualFile
        
        // Ensure server is initialized for this specific file after opening.
        waitForServerInitialization(project, virtualFile)

        val languageServerService = LanguageServerService.getInstance(project)
        val currentServer = languageServerService.getLanguageServer(virtualFile)
        if (currentServer == null) {
            throw IllegalStateException("Language server not found for ${virtualFile.url} after configuration and wait.")
        }
        this.server = currentServer
        this.textDocumentService = server.textDocumentService

        val currentClient = languageServerService.getLanguageClient(virtualFile)
        if (currentClient !is TinymistLanguageClient) {
            throw IllegalStateException("TinymistLanguageClient not found for ${virtualFile.url} after configuration. Actual: ${currentClient?.javaClass?.name}")
        }
        this.client = currentClient
        
        // While lsp4ij's EditorManager should send didOpen upon file opening (e.g. via configureByText),
        // an explicit didOpen call here can be a safeguard or useful if we need to ensure
        // the server processes an open notification with specific content immediately,
        // especially if the test manipulates files in ways that might not perfectly mimic user interaction.
        // However, direct calls to textDocumentService.didOpen can be problematic if the server connection
        // isn't fully established or if lsp4ij expects to manage these notifications exclusively.
        // For now, we rely on EditorManager and `waitForServerInitialization`.
        // If explicit sending is needed, it would be:
        // sendDidOpenNotification(virtualFile, content)
        return psiFile
    }
    
    protected fun virtualFileToURI(virtualFile: VirtualFile): String {
        // Using VfsUtil.toUri is generally robust for converting IntelliJ VirtualFiles to file URIs.
        return VfsUtil.toUri(File(virtualFile.path))?.toString() ?: virtualFile.url.also {
            if (!it.startsWith("file://")) {
                // Log or handle if not a standard file URI, as LSP servers expect file:/// format
                System.err.println("Warning: VirtualFile URL '${it}' might not be a standard file URI for LSP.")
            }
        }
    }

    // --- LSP Interaction Helpers ---

    // This helper is for explicitly sending didOpen if needed.
    // Generally, lsp4ij's EditorManager handles this when a file is opened.
    protected fun sendDidOpenNotification(virtualFile: VirtualFile, content: String? = null) {
        val docContent = content ?: ReadAction.compute<String, Throwable> {
            FileDocumentManager.getInstance().getDocument(virtualFile)?.text ?: ""
        }
        val didOpenParams = DidOpenTextDocumentParams(
            TextDocumentItem(virtualFileToURI(virtualFile), "typst", 1, docContent) // Version is typically 1 for initial open
        )
        // Direct sending of didOpen via textDocumentService might bypass some lsp4ij logic.
        // It's usually better to rely on platform mechanisms (like opening file in editor) to trigger this.
        // This method is provided for cases where explicit control might be explored.
        // LSPUtils.getObjectDelegatingToPlaceholder(textDocumentService, DidOpenTextDocumentParams::class.java, listOf(null, project, null))
        //    .didOpen(didOpenParams) // This was a speculative call.
        // A more direct, though potentially problematic, call:
        textDocumentService.didOpen(didOpenParams)
    }

    protected fun didChange(virtualFile: VirtualFile, newContent: String) {
        val document: Document = ReadAction.compute<Document, Throwable> {
            FileDocumentManager.getInstance().getDocument(virtualFile)
        } ?: throw IllegalStateException("Document not found for ${virtualFile.url}")

        // lsp4ij's EditorManager should listen to document changes and send didChange.
        // Simulating user typing or direct document modification via myFixture.editor.document.setText()
        // is the preferred way to trigger this in tests.
        // This explicit helper is for cases where direct programmatic sending is desired.
        val version = ReadAction.compute<Int, Throwable> {
            // Version number for LSP should typically increment.
            // FileDocumentManager.getInstance().getDocument(virtualFile)?.modificationStamp could be a source,
            // but LSP versioning is managed by the client (IDE) and server.
            // lsp4ij's AbstractLanguageClient typically manages versioning.
            // For an explicit send, we'd need to fetch or guess the next version.
            // Let's assume lsp4ij's default client handles versioning if we modify through Document.
            // If sending directly, we must manage version.
            (LSPUtils.getDocumentVersion(virtualFile) ?: 0) + 1
        }

        val didChangeParams = DidChangeTextDocumentParams(
            VersionedTextDocumentIdentifier(virtualFileToURI(virtualFile), version),
            listOf(TextDocumentContentChangeEvent(newContent)) // Full content change
        )
        textDocumentService.didChange(didChangeParams)
    }
    
    protected fun completion(virtualFile: VirtualFile, position: Position): CompletableFuture<Either<List<CompletionItem>, CompletionList>>? {
        val params = CompletionParams(TextDocumentIdentifier(virtualFileToURI(virtualFile)), position)
        return textDocumentService.completion(params)
    }

    protected fun hover(virtualFile: VirtualFile, position: Position): CompletableFuture<Hover?>? {
        val params = HoverParams(TextDocumentIdentifier(virtualFileToURI(virtualFile)), position)
        return textDocumentService.hover(params)
    }

    protected fun definition(virtualFile: VirtualFile, position: Position): CompletableFuture<Either<List<Location>, List<LocationLink>>?>? {
        val params = DefinitionParams(TextDocumentIdentifier(virtualFileToURI(virtualFile)), position)
        return textDocumentService.definition(params)
    }

    protected fun references(virtualFile: VirtualFile, position: Position): CompletableFuture<List<Location>?>? {
        val params = ReferenceParams(TextDocumentIdentifier(virtualFileToURI(virtualFile)), position, ReferenceContext(false))
        return textDocumentService.references(params)
    }
    
    protected fun documentSymbol(virtualFile: VirtualFile): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>?>? {
        val params = DocumentSymbolParams(TextDocumentIdentifier(virtualFileToURI(virtualFile)))
        return textDocumentService.documentSymbol(params)
    }

    protected fun semanticTokensFull(virtualFile: VirtualFile): CompletableFuture<SemanticTokens?>? {
        val params = SemanticTokensParams(TextDocumentIdentifier(virtualFileToURI(virtualFile)))
        return textDocumentService.semanticTokensFull(params)
    }

    // --- Notification Capture ---
    protected fun getDiagnostics(virtualFile: VirtualFile, timeoutSeconds: Long = 10): List<Diagnostic> {
        val uri = virtualFileToURI(virtualFile)
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds)
        var lastDiagnostics: List<Diagnostic>? = null
        while (System.currentTimeMillis() < deadline) {
            // TinymistLanguageClient should be updated by lsp4ij's LanguageClientImpl upon receiving publishDiagnostics
            lastDiagnostics = client.getDiagnosticsForUri(uri) 
            // Check if diagnostics seem "final" or meet a condition. For many tests, any non-empty list might be enough.
            // Or, if expecting empty diagnostics, that would be the success condition.
            // This simplistic check returns on the first non-null set. Adjust if specific conditions are needed.
            if (lastDiagnostics != null) { // Could be empty list, which is valid.
                 return lastDiagnostics
            }
            Thread.sleep(200) // Poll interval
        }
        return lastDiagnostics ?: emptyList() // Return last known or empty if nothing received
    }


    override fun tearDown() {
        try {
            // lsp4ij's LanguageServerManager.disposeProjectServers / stopServers should handle
            // server shutdown when the test project is disposed by BasePlatformTestCase.
            // No explicit server.shutdown() or server.exit() should be needed here unless
            // there were issues with the automatic cleanup.
        } finally {
            super.tearDown()
        }
    }

    // Utility to get a position from line and character (0-indexed)
    protected fun position(line: Int, char: Int): Position = Position(line, char)
}
