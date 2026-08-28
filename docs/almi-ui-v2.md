# ALMI UI v2

## Product principle
ALMI is a visual try-on studio, not an AI configuration app. The primary experience should feel closer to a camera/fashion tool than a developer console.

## Primary flow
1. Add a clear person photo.
2. Paste the exact product URL or upload the garment image.
3. ALMI reads the product page and prepares the garment.
4. One persistent action creates the try-on image.
5. The result becomes a full-screen experience.
6. Video is optional and starts only after the image succeeds.

## Information hierarchy
- Main screen: person, garment, create.
- Result screen: generated look, motion, optional video.
- Settings: language, appearance, AI engine, app information.
- AI engine: automatic or custom provider configuration.

Provider names, endpoints, model IDs, API keys and catalog diagnostics must never dominate the Studio screen.

## Visual identity
- Warm neutral background to keep photography visually dominant.
- Violet is the primary AI/action color.
- Coral is a playful supporting accent.
- Mint is reserved for completion/success states.
- Large rounded media surfaces and restrained elevation.
- ALMI mark: a minimal hanger/hook form with one playful spark.

## UX rules
- Always show what input is missing next to the primary create action.
- Never make video failure invalidate an already generated image.
- Product URL parsing should have an image-upload fallback in the same context.
- Prefer full-screen result presentation over modal dialogs.
- Keep Arabic RTL and English LTR equally functional.
- Technical settings stay outside the creative workflow.

## Responsive target
Phone-first. Content uses one column, 18dp horizontal page padding, 48–54dp primary controls, and large 4:5 / 2:3 media previews. The fixed create dock remains reachable with one hand.
