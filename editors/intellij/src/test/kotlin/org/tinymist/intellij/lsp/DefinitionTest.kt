package org.tinymist.intellij.lsp

import com.intellij.openapi.application.runReadAction
import org.eclipse.lsp4j.* // Import Range here
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.Test // If using JUnit5, this would be org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class DefinitionTest : LspIntegrationTestBase() {

    @Test
    fun `test definition on local variable reference jumps to definition`() {
        val content = """
            #let myVar = "value"
            #let another = myVar
        """.trimIndent()
        val psiFile = configureTypstFile("definitionTest.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)

        // Position for definition request: on 'myVar' in the second line (line 1, char #let another = |myVar| <- here )
        // Content line 1: "#let another = myVar"
        // Index of 'm' in 'myVar' is 14
        val requestPosition = Position(1, 14) 

        val definitionFuture = textDocumentService.definition(DefinitionParams(
            TextDocumentIdentifier(fileUri),
            requestPosition
        ))

        assertNotNull("Definition future should not be null", definitionFuture)
        val definitionResult = definitionFuture!!.get(5, TimeUnit.SECONDS) 
        assertNotNull("Definition result should not be null", definitionResult)

        assertTrue("Definition result should contain at least one location. Left: ${definitionResult.isLeft}, Right: ${definitionResult.isRight}",
            (definitionResult.isLeft && definitionResult.left.isNotEmpty()) ||
            (definitionResult.isRight && definitionResult.right.isNotEmpty())
        )

        val location: Location = if (definitionResult.isLeft) {
            definitionResult.left.first()
        } else {
            val locationLink = definitionResult.right.first()
            // For LocationLink, targetUri is the definition, targetSelectionRange is what to select there.
            assertEquals("Definition should be in the same file", fileUri, locationLink.targetUri)
            Location(locationLink.targetUri, locationLink.targetSelectionRange) 
        }

        assertEquals("Definition should be in the same file (URI check)", fileUri, location.uri)
        
        // Expected definition position: 'myVar' in the first line (line 0)
        // Content line 0: "#let myVar = \"value\""
        // 'm' of myVar is at char 5. 'myVar' has length 5.
        // So range is (0,5) to (0,10)
        val expectedRange = Range(Position(0, 5), Position(0, 10)) 
        assertEquals("Definition range does not match. Expected: $expectedRange, Actual: ${location.range}", expectedRange, location.range)
    }

    @Test
    fun `test definition on function reference jumps to definition`() {
        val content = """
            #let myFunction(x) = x * 2
            #myFunction(5)
        """.trimIndent()
        val psiFile = configureTypstFile("definitionFuncTest.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)

        // Position for definition request: on 'myFunction' in the second line (line 1)
        // Content line 1: "#myFunction(5)"
        // 'm' of myFunction is at char 1
        val requestPosition = Position(1, 1) 

        val definitionFuture = textDocumentService.definition(DefinitionParams(
            TextDocumentIdentifier(fileUri),
            requestPosition
        ))
        assertNotNull("Definition future should not be null", definitionFuture)
        val definitionResult = definitionFuture!!.get(5, TimeUnit.SECONDS)
        assertNotNull("Definition result should not be null", definitionResult)

        assertTrue("Definition result should contain at least one location. Left: ${definitionResult.isLeft}, Right: ${definitionResult.isRight}",
            (definitionResult.isLeft && definitionResult.left.isNotEmpty()) ||
            (definitionResult.isRight && definitionResult.right.isNotEmpty())
        )

        val location: Location = if (definitionResult.isLeft) {
            definitionResult.left.first()
        } else {
            val locationLink = definitionResult.right.first()
            assertEquals("Definition should be in the same file", fileUri, locationLink.targetUri)
            Location(locationLink.targetUri, locationLink.targetSelectionRange)
        }
        
        assertEquals("Definition should be in the same file (URI check)", fileUri, location.uri)
        // Expected definition: 'myFunction' in the first line (line 0)
        // Content line 0: "#let myFunction(x) = x * 2"
        // 'm' of myFunction is at char 5. 'myFunction' has length 10
        // So range is (0,5) to (0,15)
        val expectedRange = Range(Position(0, 5), Position(0, 15))
        assertEquals("Definition range does not match. Expected: $expectedRange, Actual: ${location.range}", expectedRange, location.range)
    }

    @Test
    fun `test definition on undefined symbol returns empty or null`() {
        val content = "#undefinedVar"
        val psiFile = configureTypstFile("definitionUndefined.typ", content)
        val virtualFile = runReadAction { psiFile.virtualFile }
        val fileUri = virtualFileToURI(virtualFile)

        // Position for definition request: on 'undefinedVar'
        // Content line 0: "#undefinedVar"
        // 'u' of undefinedVar is at char 1
        val requestPosition = Position(0, 1)

        val definitionFuture = textDocumentService.definition(DefinitionParams(
            TextDocumentIdentifier(fileUri),
            requestPosition
        ))
        assertNotNull("Definition future should not be null",definitionFuture)
        val definitionResult = definitionFuture!!.get(5, TimeUnit.SECONDS)
        
        // A server should return null or an empty list if no definition is found.
        if (definitionResult != null) {
            assertTrue("Expected no definition for undefined symbol. Result: $definitionResult",
                (definitionResult.isLeft && definitionResult.left.isEmpty()) ||
                (definitionResult.isRight && definitionResult.right.isEmpty()))
        } else {
            // This case is also valid (server returns null for no definition)
            assertNull("Definition result should be null or have empty lists for undefined symbol", definitionResult)
        }
    }
}
