# Item 17c — permission FIFO queue + question-card audit: requirements

- Permission asks serialize FIFO (Kilo's pending LinkedHashMap semantics): the
  active dialog finishes before the next queued ask opens; the bridge keeps
  answering each request with its own outcome.
- Question/plan dialogs already follow Kilo's keyed-card essentials (header
  title, options, approve label, fail-closed dismiss); the full inline-card
  migration (Kilo QuestionView/DialogView cards inside the transcript) rides
  the future JList-based transcript rewrite — recorded, not duplicated here.
