package org.tinymist.intellij.lsp

import com.intellij.openapi.application.runReadAction
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.Test // If using JUnit5, this would be org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class DocumentSymbolTest : LspIntegrationTestBase() {

    @Test
    fun `test documentSymbol returns hierarchical symbols`() {
        val content = """
            #let myVar = 10
            #let myFunction(arg) = {
              #let innerVar = arg + 1
              innerVar
            }
            #let anotherTopLevel = "text"
        """.trimIndent()
        val psiFile = configureTypstFile("documentSymbolTest.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)

        val symbolFuture = textDocumentService.documentSymbol(DocumentSymbolParams(
            TextDocumentIdentifier(fileUri)
        ))

        assertNotNull("DocumentSymbol future should not be null", symbolFuture)
        val symbolResult = symbolFuture!!.get(5, TimeUnit.SECONDS) // Adjust timeout
        assertNotNull("DocumentSymbol result should not be null", symbolResult)
        assertTrue("Expected DocumentSymbol result to not be empty. Got ${symbolResult.size} items.", symbolResult.isNotEmpty())

        // Process DocumentSymbol (hierarchical) or SymbolInformation (flat list)
        val documentSymbols: List<DocumentSymbol>
        // val symbolInformation: List<SymbolInformation> // Declared for completeness if needed

        if (symbolResult.first().isLeft) { // List<SymbolInformation>
            // symbolInformation = symbolResult.map { it.left }
            // This case is less ideal for testing hierarchy, but check basic properties
            // Fail if we strictly expect hierarchical symbols for this test
            fail("Should have received DocumentSymbols for hierarchical test, but got SymbolInformation. This indicates the server might not support hierarchical document symbols or returned a flat list.")
            // For SymbolInformation, you'd check names, kinds, locations
            // val mainFuncInfo = symbolInformation.find { it.name == "myFunction" }
            // assertNotNull(mainFuncInfo)
            // assertEquals(SymbolKind.Function, mainFuncInfo!!.kind)

        } else { // List<DocumentSymbol>
            documentSymbols = symbolResult.map { it.right }
            
            // Expecting myVar, myFunction, anotherTopLevel at the top level
            assertEquals("Expected 3 top-level symbols, got: ${documentSymbols.joinToString { it.name }}", 3, documentSymbols.size) 

            val myVarSymbol = documentSymbols.find { it.name == "myVar" }
            assertNotNull("Top-level symbol 'myVar' not found", myVarSymbol)
            assertEquals(SymbolKind.Variable, myVarSymbol!!.kind) // Or Constant, Field, depending on server
            // Example range, adjust to actual server output:
            // #let myVar = 10 (line 0, char 5-10 for "myVar")
            // assertEquals(Range(Position(0,5), Position(0,10)), myVarSymbol.selectionRange)


            val myFunctionSymbol = documentSymbols.find { it.name == "myFunction" }
            assertNotNull("Top-level symbol 'myFunction' not found", myFunctionSymbol)
            assertEquals(SymbolKind.Function, myFunctionSymbol!!.kind)
            // Example range for "myFunction":
            // #let myFunction(arg) = { (line 1, char 5-15 for "myFunction")
            // assertEquals(Range(Position(1,5), Position(1,15)), myFunctionSymbol.selectionRange)


            // Check for nested symbols if applicable (e.g., innerVar inside myFunction)
            assertNotNull("myFunction should have children symbols", myFunctionSymbol.children)
            assertTrue("myFunction children symbols should not be empty. Found: ${myFunctionSymbol.children?.joinToString { it.name }}", myFunctionSymbol.children!!.isNotEmpty())

            val innerVarSymbol = myFunctionSymbol.children!!.find { it.name == "innerVar" }
            assertNotNull("Nested symbol 'innerVar' under 'myFunction' not found. Children: ${myFunctionSymbol.children?.joinToString { it.name }}", innerVarSymbol)
            assertEquals(SymbolKind.Variable, innerVarSymbol!!.kind) // Or Constant, Field
            // Example range for "innerVar":
            // #let innerVar = arg + 1 (line 2, char 7-15 for "innerVar", relative to parent or absolute to document)
            // This range is particularly tricky and depends on how server reports it (full range vs selection range, absolute vs relative)
            // assertEquals(Range(Position(2,7), Position(2,15)), innerVarSymbol.selectionRange)

            val anotherTopLevelSymbol = documentSymbols.find { it.name == "anotherTopLevel" }
            assertNotNull("Top-level symbol 'anotherTopLevel' not found", anotherTopLevelSymbol)
            assertEquals(SymbolKind.Variable, anotherTopLevelSymbol!!.kind) // Or String, Constant
        }
    }

    @Test
    fun `test documentSymbol on empty file returns empty list`() {
        val content = ""
        val psiFile = configureTypstFile("documentSymbolEmpty.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)

        val symbolFuture = textDocumentService.documentSymbol(DocumentSymbolParams(
            TextDocumentIdentifier(fileUri)
        ))
        assertNotNull("DocumentSymbol future should not be null for empty file", symbolFuture)
        val symbolResult = symbolFuture!!.get(5, TimeUnit.SECONDS)
        
        assertNotNull("Result should not be null even for empty file", symbolResult)
        assertTrue("Expected empty list of symbols for empty file, got ${symbolResult!!.size} items.", symbolResult!!.isEmpty())
    }
}
