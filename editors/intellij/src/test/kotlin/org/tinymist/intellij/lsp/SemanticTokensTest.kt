package org.tinymist.intellij.lsp

import com.intellij.openapi.application.runReadAction
import org.eclipse.lsp4j.*
import org.junit.Test // If using JUnit5, this would be org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class SemanticTokensTest : LspIntegrationTestBase() {

    @Test
    fun `test semanticTokensFull provides tokens`() {
        val content = """
            #let myVar = 10
            #let myFunction(name) = {
              // This is a comment
              #let greeting = "Hello, " + name
              greeting
            }
            #myFunction("World")
            // Another comment
        """.trimIndent()
        val psiFile = configureTypstFile("semanticTokensTest.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)

        val tokensFuture = textDocumentService.semanticTokensFull(SemanticTokensParams(
            TextDocumentIdentifier(fileUri)
        ))

        assertNotNull("SemanticTokensFull future should not be null", tokensFuture)
        val tokensResult = tokensFuture!!.get(5, TimeUnit.SECONDS) // Adjust timeout
        assertNotNull("SemanticTokensFull result should not be null", tokensResult)
        
        assertNotNull("Tokens data should not be null", tokensResult!!.data)
        assertTrue("Expected semantic tokens data to not be empty for non-empty file. Got ${tokensResult.data.size} integers.", tokensResult.data.isNotEmpty())

        // Further assertions require knowledge of the server's SemanticTokensLegend
        // and how it encodes tokens.
        // For example, if we know the legend and expected tokens:
        // val legend = serverCapabilities.semanticTokensProvider.legend // Need to get this from server init
        // val decodedTokens = decodeSemanticTokens(tokensResult.data, legend)
        // assertNotNull(decodedTokens.find { it.type == "variable" && it.text == "myVar" })
        // assertNotNull(decodedTokens.find { it.type == "function" && it.text == "myFunction" })

        println("Received ${tokensResult.data.size} token integers for semanticTokensTest.typ. Result ID: ${tokensResult.resultId}")
        // For now, just assert that *some* tokens are returned.
        // Detailed validation will require decoding logic based on the server's legend.
    }

    @Test
    fun `test semanticTokensFull on empty file returns empty or null tokens`() {
        val content = ""
        val psiFile = configureTypstFile("semanticTokensEmpty.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)

        val tokensFuture = textDocumentService.semanticTokensFull(SemanticTokensParams(
            TextDocumentIdentifier(fileUri)
        ))
        assertNotNull("SemanticTokensFull future should not be null for empty file", tokensFuture)
        val tokensResult = tokensFuture!!.get(5, TimeUnit.SECONDS)
        
        // A server can return null for semantic tokens if the file is empty or no tokens are applicable.
        // Or it can return a SemanticTokens object with a null or empty data list.
        if (tokensResult != null) {
            assertTrue("Expected no token data (null or empty list) for empty file, but got ${tokensResult.data?.size ?: "null data list"} integers.", 
                        tokensResult.data == null || tokensResult.data.isEmpty())
        } else {
            // This is also an acceptable response from the server for an empty file.
            assertNull("SemanticTokens result can be null for empty file, this is acceptable.", tokensResult)
        }
        println("Received result for empty file: ${if (tokensResult == null) "null" else "data size: " + (tokensResult.data?.size ?: "null")}")
    }
}
