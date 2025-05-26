package org.tinymist.intellij.lsp

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiDocumentManager
import org.eclipse.lsp4j.DiagnosticSeverity
import org.junit.Test // If using JUnit5, this would be org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class DocumentSyncTest : LspIntegrationTestBase() {

    @Test
    fun `test documentOpen publishes diagnostics`() {
        val content = "#invalid" // Simple Typst content that should generate a diagnostic
        val psiFile = configureTypstFile("test.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)

        // configureTypstFile in LspIntegrationTestBase should handle server initialization
        // and the didOpen notification implicitly through lsp4ij's mechanisms.

        // Wait for diagnostics to be published.
        // LspIntegrationTestBase.getDiagnostics attempts to wait/poll.
        val diagnostics = getDiagnostics(virtualFile, timeoutSeconds = 10) // Increased timeout

        assertNotNull("Diagnostics should be published for $fileUri", diagnostics)
        assertTrue("Expected at least one diagnostic for invalid content in $fileUri, got ${diagnostics.size}", diagnostics.isNotEmpty())

        val diagnostic = diagnostics.first()
        // assertEquals("Typst", diagnostic.source) // Assuming 'Typst' is the source, this might vary
        // Add more assertions based on expected diagnostic for "#invalid"
        // e.g., check message, range, severity
        // assertTrue(diagnostic.message.contains("expected expression")) // Example, adjust to actual message
        // assertEquals(DiagnosticSeverity.Error, diagnostic.severity)
    }

    @Test
    fun `test documentChange updates diagnostics`() {
        val initialContent = "Hello" // Content that should ideally produce no diagnostics
        val psiFile = configureTypstFile("testChange.typ", initialContent)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)
        
        // Wait for initial diagnostics processing.
        val initialDiagnostics = getDiagnostics(virtualFile, timeoutSeconds = 5)
        // Depending on the server, "Hello" might be valid or not.
        // For this test, we are more interested in the *change* leading to new diagnostics.
        // assertTrue("Expected no diagnostics for initial valid content in $fileUri, got ${initialDiagnostics.size}", initialDiagnostics.isEmpty())


        // Change content to something that triggers a diagnostic
        val changedContent = "#invalid" // Invalid Typst content
        
        // Use myFixture.saveText to simulate user editing and saving the file.
        // This should trigger IntelliJ's document change events, which lsp4ij listens to.
        myFixture.saveText(virtualFile, changedContent)
        // Ensure document is committed
        PsiDocumentManager.getInstance(project).commitAllDocuments()


        // lsp4ij's EditorManager should detect the document change and send a didChange notification.
        // If this proves unreliable, explicit textDocumentService.didChange() can be called using
        // the helper in LspIntegrationTestBase.

        // Wait for new diagnostics to be published after the change.
        val updatedDiagnostics = getDiagnostics(virtualFile, timeoutSeconds = 10) // Increased timeout

        assertNotNull("Updated diagnostics should not be null for $fileUri", updatedDiagnostics)
        // Allow for initialDiagnostics to be non-empty if "Hello" itself causes informational diagnostics
        if (initialDiagnostics.all { it.severity == DiagnosticSeverity.Hint || it.severity == DiagnosticSeverity.Information }) {
            // If initial diagnostics were only hints/info, new errors/warnings are significant
            assertTrue("Expected new error/warning diagnostics after change in $fileUri, got ${updatedDiagnostics.size}", 
                updatedDiagnostics.any { it.severity == DiagnosticSeverity.Error || it.severity == DiagnosticSeverity.Warning })
        } else {
            // Generic check if counts differ or content of diagnostics differ significantly
             assertNotEquals(initialDiagnostics.size, updatedDiagnostics.size, "Diagnostics count should change or content differ for $fileUri. Initial: ${initialDiagnostics.size}, Updated: ${updatedDiagnostics.size}")
        }
        
        val errorDiagnostic = updatedDiagnostics.firstOrNull { it.severity == DiagnosticSeverity.Error || it.severity == DiagnosticSeverity.Warning }
        assertNotNull("Expected at least one error or warning diagnostic for '#invalid' in $fileUri", errorDiagnostic)
        // assertEquals("Typst", errorDiagnostic.source) // Assuming 'Typst' is the source
        // Add more assertions for the new diagnostic
        // assertTrue(errorDiagnostic.message.contains("expected expression")) // Example
    }
}
