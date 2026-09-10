# H Architecture Contract

This document is the authoritative architecture contract for H. If existing or future code conflicts with this contract, the conflicting code must be removed or replaced while compatible features are preserved.

## Non-negotiable interpretation

- **H is the permanent, independent assistant/model identity.** The user talks to H, not to Gemini, Groq, OpenAI, OpenRouter, or any other provider.
- In the normal no-cost state, H may automatically consult approved free or near-free hidden helpers when the task needs external inference.
- Hidden free helpers are implementation details and must not appear in the ordinary provider UI.
- If the owner configures and enables a paid/BYOK provider, H still remains H, but **every AI inference request must use only that selected paid provider/model while it is enabled**.
- While an owner-paid/BYOK provider is enabled, H must not route any AI request to hidden free capacity, another paid provider, or a mixed candidate/verifier path. There is no automatic free fallback.
- If the selected paid provider becomes unavailable, rate-limited, blocked by the price guard, or its owner-defined limit is exhausted, AI inference fails closed on that route. H's deterministic cloud capabilities such as memory operations, reminders, tasks, identity, and other non-inference functions remain available where technically possible.
- H's free inference routing becomes eligible again only after the owner explicitly disables, disconnects, or removes the paid/BYOK route.
- No paid-provider state may overwrite H identity, memory, personality, or learning.
- After every provider invocation, execution returns to H automatically. Provider output is evidence/execution material integrated under H's own context, memory, learning, and verification boundaries.
- Durable learning belongs to H-owned storage. Provider chat history must never be treated as H's durable memory.
- No automatic purchase, upgrade, or charge is allowed. Cost-bearing providers require prior explicit owner configuration/permission.

## Google account vs durable storage contract

- The signed-in Google account is H's durable **owner identity and authorization binding** on Android. It is not the current storage bucket for H data.
- Durable H state currently lives in **H Cloud/Supabase** and is scoped server-side to the authenticated Google-linked H owner. This includes H-owned memory, aggregate learning, reminders/tasks, and other durable core state implemented by H Cloud.
- H data must not be described, measured, or implemented as consuming the user's personal Gmail/Google Drive quota unless a future storage migration explicitly moves that data to Google Drive.
- Google sign-in credentials or scopes used by the app do not by themselves make Google Drive the durable H storage provider.
- Android may retain only authentication/session material required to sign in, OS-required scheduling/registration metadata, and transient process/cache data. It must not become a second durable copy of H memory, learning, reminders, or files.
- The same server-side H state must remain usable from WhatsApp while the Android phone is offline, the app is closed, or the app is uninstalled. WhatsApp execution must never depend on the phone as a storage or synchronization relay.
- Moving durable H storage literally to Google Drive is a future **cloud-storage migration**, not a UI/account-toggle change. It requires moving the durable storage layer, ownership mapping, migration/integrity logic, and server-side authorization/access paths.
- If Google Drive becomes a future H storage target, H's backend must be able to access the owner's authorized Drive storage without requiring the Android device to be online, while preserving the same H identity, privacy boundaries, portability, and WhatsApp continuity.
- Google Drive is therefore a replaceable future cloud-storage provider behind H, not the definition of the Google-linked owner account.

## The 100-point H plan

1. Make H the primary assistant at all times in the app and WhatsApp.
2. Separate H from any particular AI provider.
3. Treat the app as an interface; H's durable core state lives in the cloud.
4. Build an independent H Cloud Core.
5. Bind H to the user's account, not to one device.
6. Store H identity, personality, and rules in H-owned cloud state.
7. Create short-term memory.
8. Create long-term memory.
9. Store preferences, people, places, and projects.
10. Store corrections the owner gives H.
11. Create semantic/RAG memory retrieval.
12. Compress and summarize old memory.
13. Prevent unimportant conversation/data accumulation.
14. Create an H Learning Engine.
15. Learn from usage and owner corrections.
16. Link new information with old information.
17. Update knowledge when facts change.
18. Create an Unknown Detector.
19. Record unknown answers as knowledge gaps.
20. Create a Learning Queue for unresolved questions.
21. Create a Research Engine for later investigation.
22. Create a Verification Engine.
23. Persist new knowledge only after suitable verification.
24. Run periodic learning cycles.
25. Reduce recurrence of previous mistakes.
26. Enable H to later answer questions it previously did not know.
27. Create an AI Router owned by H.
28. In the normal no-cost state, H uses only approved hidden free/near-free routes.
29. Select the best free helper according to task type when escalation is needed.
30. Switch automatically among free helpers when quotas are exhausted.
31. Monitor tokens, requests, and rate limits.
32. Create a Quota Manager.
33. Create Smart Failover across helper routes.
34. When a paid/BYOK provider is configured and enabled, H remains the primary assistant identity while that selected provider becomes H's exclusive AI inference route.
35. Route every AI inference request through the selected paid/BYOK provider/model while it is enabled; do not segment it to hard tasks only.
36. Do not mix another paid provider or hidden free helper into AI inference while the selected paid/BYOK route is enabled, and do not automatically fall back to free AI if it fails or reaches its limit.
37. When the owner explicitly disables, disconnects, or removes the paid/BYOK route, H automatically resumes H Core/free hidden routing.
38. H itself must not change when helpers/providers change.
39. Store H memory independently from Gemini, Groq, OpenAI, and other providers.
40. Create a Provider Registry for future helper providers.
41. Add a new provider without rebuilding H as a whole.
42. Create a multi-cloud architecture.
43. Maintain a primary H cloud.
44. Maintain a backup cloud.
45. Back up H memory periodically.
46. Monitor each cloud's limits.
47. Move automatically to backup cloud when required.
48. Warn before the last available free cloud capacity is exhausted.
49. Provide a flow for adding new cloud credentials.
50. Require only the necessary API Key / Project ID / URL / Token inputs.
51. H tests the connection automatically.
52. Register a cloud as ready only after validation succeeds.
53. Migrate data automatically when an old cloud is being retired/exhausted.
54. Provide a screen for cloud/helper health and status without exposing hidden implementation details unnecessarily.
55. Provide a complete “Move H” flow.
56. Allow H to be moved to another cloud even years later.
57. Move identity, memory, learning, reminders, settings, and files.
58. Verify migration completeness and prevent information loss.
59. Store large files in cloud/object storage.
60. Do not depend on phone storage for H's durable core data.
61. Keep only a small local cache when useful.
62. Compress images/audio to reduce cost/storage.
63. Convert audio to text when appropriate.
64. Do not retain raw files forever unless there is a durable need.
65. Create a smart cache to reduce AI calls.
66. Do not send the entire chat history with every request.
67. Send only memory/context relevant to the question.
68. In free mode, use stronger hidden free helpers only when the task needs them; in paid/BYOK mode, all AI inference stays on the selected paid provider.
69. Never transition automatically to a paid plan without owner permission.
70. Enforce limits that prevent surprise bills.
71. Keep H's identity and deterministic cloud functions operational even if an inference provider disappears; provider failure must not delete or replace H.
72. Add better free helpers when they become available for the normal no-cost state.
73. If a free helper provider stops working while H is in free mode, H moves to another available free route.
74. Use the same H across app and WhatsApp.
75. Use the same H-owned memory and personality across both.
76. Deleting the app must not delete H.
77. Changing phones must not delete H.
78. Signing in restores the same H state.
79. Let H develop its knowledge over time.
80. Do not depend on retraining a giant foundation model from scratch.
81. Evolve H through H-owned memory + learning + research + verification + replaceable external models.
82. During ongoing implementation, review the legacy `Malik05255/vpn` codebase where relevant.
83. Remove or replace code that conflicts with this architecture.
84. Preserve useful existing features that are compatible with it.
85. Implement progressively without bloating the app.
86. Test normal/free operation.
87. Test paid/BYOK operation and prove that every AI inference turn uses only the selected paid provider while enabled.
88. Test one free helper quota ending.
89. Test all free helper capacity ending.
90. Test adding a new helper provider.
91. Test primary-cloud failure.
92. Test transition to backup cloud.
93. Test uninstall/reinstall recovery.
94. Test sign-in on a new device.
95. Test that H remembers durable prior learning.
96. Test that H benefits from previous corrections/errors.
97. Test that WhatsApp and the app use the same H.
98. Test that a paid provider never changes H personality/identity.
99. Test that an enabled paid/BYOK provider failure, quota exhaustion, or price-guard block fails closed and never silently falls back to free AI; free routing resumes only after explicit owner disable/disconnect/removal.
100. **H is the constant; every model, provider, and cloud is a replaceable tool behind H.**

## UI contract for helper providers

- The normal AI-provider settings screen shows only user-managed external/BYOK/paid entries and the option to add one.
- Internal free/near-free helper names, API routes, quotas, and failover ordering remain hidden from ordinary UI.
- When a paid/BYOK provider is enabled, the UI must describe it as the exclusive AI inference route while enabled. It must not offer hard-tasks-only or automatic-free-fallback controls.
- An advanced H capacity control may allow the owner to let H remove/recreate internal helper route records. Removing those records must not delete H identity, memory, learning, reminders, settings, or cloud state.
- Re-enabling automatic internal capacity management must allow H to restore compatible hidden routes as if the implementation helper had been replaced, while H remains unchanged.

## Ephemeral media/file contract

- Images, audio, video, PDFs, long text, and other supported documents may enter H from either the Android app or WhatsApp.
- Cloud upload is an implementation option, **not a requirement**. Sustained no-cost operation takes priority over a particular upload path when H is operating in free mode.
- In free mode, H must select the least-expensive safe strategy that can satisfy the request: local processing first when practical; then compression/downsampling; then transcript/text/key-frame/chunk extraction; then bounded temporary cloud processing only when a verified no-cost route is available.
- H must never purchase storage, inference, OCR, transcription, bandwidth, or a paid plan automatically to process an attachment.
- If H is in free mode and no compliant no-cost strategy is available, H must preserve ordinary operation and ask for a smaller/trimmed/derived input instead of silently spending money.
- If an owner-paid/BYOK provider is enabled, any AI inference needed for supported attachment analysis must stay on that selected provider/model; H must not silently use free inference as a fallback. Local preprocessing may still be used for privacy, size, and efficiency.
- Audio and video accepted for temporary cloud processing are hard-limited to **180 seconds (3 minutes)** per item. Longer media must be trimmed, locally reduced to a derived representation, or rejected from cloud upload.
- Raw media and document originals are transient working data, not H memory. They must not enter durable memory, Learning Engine state, backups, or the future “Move H” payload.
- Temporary cloud objects must be private, owner-scoped, randomly named, short-lived, and deleted immediately after the processing attempt finishes. Cleanup must run after success, failure, timeout, or cancellation when the platform permits it; a short TTL sweeper is defense-in-depth only, never the primary retention mechanism.
- Only the minimum derived material required for the current request should be sent onward: relevant text chunks, a transcript, selected key frames, compressed image data, or another bounded representation.
- Derived content used only to answer the current turn is transient too. A fact extracted from an attachment becomes durable only when the owner explicitly asks H to remember/save it, and the normal H memory/privacy guard must approve it.
- App and WhatsApp attachment paths must implement the same policy so changing channels never changes H's privacy or cost guarantees.
