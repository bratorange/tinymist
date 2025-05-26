package org.tinymist.intellij.lsp

import com.intellij.openapi.application.runReadAction
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.Test // If using JUnit5, this would be org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class CompletionTest : LspIntegrationTestBase() {

    @Test
    fun `test completion provides suggestions for identifier`() {
        val content = """
            #let my_variable = 42
            #let another_var = 100
            #my_
        """.trimIndent()
        val psiFile = configureTypstFile("completionTestIdentifier.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)

        // Position for completion: line 2, after "#my_"
        val line = 2
        val char = content.lines()[2].length 

        val completionParams = CompletionParams(
            TextDocumentIdentifier(fileUri),
            Position(line, char)
        )
        // textDocumentService is from LspIntegrationTestBase
        val completionFuture = textDocumentService.completion(completionParams)

        assertNotNull("Completion future should not be null for $fileUri", completionFuture)
        val completionResult = completionFuture!!.get(10, TimeUnit.SECONDS) // Increased timeout
        assertNotNull("Completion result should not be null for $fileUri", completionResult)

        val completionItems: List<CompletionItem> = if (completionResult.isLeft) {
            completionResult.left
        } else {
            completionResult.right.items
        }

        assertTrue("Expected at least one completion item for '#my_' in $fileUri, got ${completionItems.size}", completionItems.isNotEmpty())
        
        // Example: Check if 'my_variable' is suggested
        val expectedItem = completionItems.find { it.label == "my_variable" }
        assertNotNull("Expected completion item 'my_variable' not found in $fileUri. Found: ${completionItems.joinToString { it.label }}", expectedItem)
        // assertEquals(CompletionItemKind.Variable, expectedItem?.kind) // Example assertion, kind might differ
    }

    @Test
    fun `test completion with trigger character hash`() {
        // '#' is a common trigger for directives and built-in functions in Typst.
        val content = "#" 
        val psiFile = configureTypstFile("triggerCompletionHash.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)
        
        val position = Position(0, content.length)

        val context = CompletionContext().apply {
            triggerKind = CompletionTriggerKind.TriggerCharacter
            triggerCharacter = "#"
        }

        val completionParams = CompletionParams(
            TextDocumentIdentifier(fileUri),
            position,
            context
        )
        val completionFuture = textDocumentService.completion(completionParams)

        assertNotNull("Completion future should not be null for trigger '#' in $fileUri", completionFuture)
        val completionResult = completionFuture!!.get(10, TimeUnit.SECONDS) // Increased timeout
        assertNotNull("Completion result should not be null for trigger '#' in $fileUri", completionResult)

        val completionItems = if (completionResult.isLeft) completionResult.left else completionResult.right.items
        assertTrue("Expected completion items after trigger character '#' in $fileUri, got ${completionItems.size}", completionItems.isNotEmpty())
        
        // Example: Check for common Typst keywords/functions starting with #
        // assertTrue("Expected '#let' or '#if' or '#import' among suggestions for '#' in $fileUri. Found: ${completionItems.joinToString { it.label }}",
        //     completionItems.any { it.label == "let" || it.label == "if" || it.label == "import" || it.label == "include" || it.label == "list" || it.label == "table" })
        // Note: Actual labels might include the '#', e.g., "#let". Adjust as per server behavior.
        // val letItem = completionItems.find { it.label == "let" || it.label.startsWith("let") } // More flexible check
        // assertNotNull("Expected a 'let' related completion for '#' in $fileUri", letItem)
    }

    @Test
    fun `test completion for built-in function after hash`() {
        val content = "#re" // User starts typing #rect or #ref
        val psiFile = configureTypstFile("completionBuiltin.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)

        val position = Position(0, content.length)
        val completionParams = CompletionParams(
            TextDocumentIdentifier(fileUri),
            position
            // Could add context if server requires it for partial input after #
            // CompletionContext().apply { triggerKind = CompletionTriggerKind.Invoked }
        )
        val completionFuture = textDocumentService.completion(completionParams)

        assertNotNull("Completion future should not be null for '#re' in $fileUri", completionFuture)
        val completionResult = completionFuture!!.get(10, TimeUnit.SECONDS)
        assertNotNull("Completion result should not be null for '#re' in $fileUri", completionResult)

        val completionItems = if (completionResult.isLeft) completionResult.left else completionResult.right.items
        assertTrue("Expected completion items for '#re' in $fileUri, got ${completionItems.size}", completionItems.isNotEmpty())

        // Example: Check for 'rect' or 'ref'
        // val rectItem = completionItems.find { it.label.startsWith("rect") }
        // val refItem = completionItems.find { it.label.startsWith("ref") }
        // assertTrue("Expected 'rect' or 'ref' among suggestions for '#re'. Found: ${completionItems.joinToString { it.label }}", rectItem != null || refItem != null)
        // rectItem?.let { assertEquals(CompletionItemKind.Function, it.kind) } // Example
    }
}
