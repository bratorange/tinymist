package org.tinymist.intellij.lsp

import dev.j_a.ide.lsp.api.RegisterLanguageServerSupportActivity

class TinymistRegisterLanguageServerActivity :
    RegisterLanguageServerSupportActivity(TinymistLanguageServerSupport) 