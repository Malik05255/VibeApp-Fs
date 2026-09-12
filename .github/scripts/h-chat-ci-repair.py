from pathlib import Path

path = Path("app/src/main/kotlin/com/malik/lmai/feature/agent/loop/ProviderAgentGatewayRouter.kt")
text = path.read_text(encoding="utf-8")
old_ctor = """    private val openRouterCredentialStore: OpenRouterCredentialStore,\n    private val hAppMediaPreprocessor: HAppMediaPreprocessor,\n    private val mohammedAssistantContext: HAssistantContext,\n"""
new_ctor = """    private val openRouterCredentialStore: OpenRouterCredentialStore,\n    private val mohammedAssistantContext: HAssistantContext,\n    private val hAppMediaPreprocessor: HAppMediaPreprocessor? = null,\n"""
old_prepare = """        val mediaPreparedRequest = try {\n            hAppMediaPreprocessor.prepare(request)\n"""
new_prepare = """        val mediaPreparedRequest = try {\n            hAppMediaPreprocessor?.prepare(request) ?: request\n"""
if text.count(old_ctor) != 1:
    raise SystemExit("constructor pattern mismatch")
if text.count(old_prepare) != 1:
    raise SystemExit("prepare pattern mismatch")
text = text.replace(old_ctor, new_ctor, 1).replace(old_prepare, new_prepare, 1)
path.write_text(text, encoding="utf-8")
print("ProviderAgentGatewayRouter backward-compatible media injection repaired")
