package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class FreedTranslatorTest : TranslatorTest() {
    override fun onlyUseCommonLanguage(): Boolean {
        return true
    }

    override fun getTranslatorCode(): String {
        return FreedTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return FreedTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return FreedTranslator.supportedEngines.map { it.code }
    }
}
