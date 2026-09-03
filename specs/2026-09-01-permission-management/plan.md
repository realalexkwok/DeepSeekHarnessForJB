# Item 24 (replanned) — permission management: plan

1. Spec files (this directory).
2. Pure PermissionRules engine (levels + patterns, decide()) + unit tests.
3. PermissionSettings (PropertiesComponent persistence: auto-approve flag +
   rules JSON).
4. DshRuntimeService.answerApproval: auto-approve short-circuit, rule-engine
   short-circuit, approval listeners + per-ask latch (10 min watchdog).
5. Model: PermissionRow (add/resolve) + pure test; panel: inline permission
   card widget (header, tool/reason, Reject/Allow once, inline rules section)
   + shield toggle in the composer tab row; remove PermissionDialog usage.
6. Settings: permissions section (per-tool levels + pattern table).
7. Full suite + buildPlugin + artifact report.
