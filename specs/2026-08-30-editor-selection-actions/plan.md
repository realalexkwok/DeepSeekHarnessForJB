# Item 11 remainder — editor-selection context actions: plan

1. Spec files (this directory).
2. `ComposerRequests` project service: a tiny bus from editor actions to the
   chat composer (action + prompt text).
3. Three `AnAction`s (Ask/Explain/Fix) + `plugin.xml` editor-popup group with
   selection-gated enablement.
4. Composer wiring: consume the bus (set action tab, prefill input, focus);
   enable the Fix menu item; add the FIX prompt instruction.
5. Tests: PromptAssembly FIX case; full suite + buildPlugin.
