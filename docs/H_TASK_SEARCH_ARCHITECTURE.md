# H Task Search Architecture

Status: accepted product/engineering specification for the H lab repository. Implementation follows completion/stabilization of the reminders and current WhatsApp/Peach integration work.

## Goal

H must treat local search, travel/hotel search, price comparison, deal discovery, and long-running deep search as first-class tasks that can be created, monitored, paused, resumed, modified, and queried from both the Android app and WhatsApp.

H must never claim exhaustive internet coverage. Preferred wording is equivalent to: "This is the lowest price I could verify among the sources searched so far." Hidden member prices, app-only offers, personalized prices, private B2B agreements, and inaccessible sources are explicitly outside guaranteed coverage.

## Thin-client rule

Android remains a thin client. Do not add a large maps, hotel, scraping, or vendor SDK to the APK merely to support one source. Prefer backend integrations, HTTP APIs, server-side adapters, caching, and the existing shared networking stack. The app renders task state/results and opens external navigation/maps when requested.

APK size must be measured before and after implementation; unexplained growth is a regression.

## Shared architecture

All capabilities share one Task Engine rather than separate feature-specific engines.

Task Engine
- Intent / Task classifier
- Search Planner
- Source Adapters
- Normalization
- Verification
- Ranking
- Task Events / Audit log
- Results / Evidence
- Scheduler / Monitor
- Cache

Capabilities are lightweight task types/policies above this engine:
- LOCAL_SEARCH
- PLACE_RECOMMENDATION
- HOTEL_SEARCH
- PRICE_COMPARISON
- DEAL_SEARCH
- DEEP_SEARCH
- MONITOR / CONDITION_TASK

Source adapters must remain separable so new sources can be added without rewriting the core engine.

## Local search / route-aware recommendations

Example: "يا H شوف لي مطعم على طريق محايل يقدم رز بخاري وتقييماته عالية."

When location permission is available H may use the user's current position and/or route context. Results should be route-aware rather than merely nearby.

Ranking should consider at least:
- semantic/request match
- evidence that the requested item/service is actually offered
- rating
- review count
- distance
- detour from route
- estimated time impact
- open-now status
- data freshness / confidence

Do not rank a 5.0 place with 4 reviews above a 4.6 place with 1,800 reviews solely because of stars.

Structured place/map APIs are preferred for canonical place data. Deep Search is used only for missing details, such as verifying a menu item, unusual service, recent closure, or evidence unavailable from the structured result.

Returned result should include:
- place name
- concise reason for recommendation
- rating + review count
- distance / travel time / route detour when known
- open/closed state when verifiable
- confidence / verification caveat when needed
- external "Open location / Navigate" action

Do not build a full navigation engine into the APK.

## Hotel / travel price comparison

Example: "يا H شوف لي أرخص فندق قريب من الحرم من تاريخ X إلى Y."

Search plan should normalize first:
- target area / landmark
- check-in / check-out
- guests
- rooms
- maximum distance if supplied
- requested star/category constraints
- cancellation / meal preferences if supplied

Use a hybrid strategy:
1. Structured travel/pricing APIs where available.
2. Public web search.
3. Deep Search for additional sources and offer details.
4. Verification before ranking.

Possible sources include major OTAs, official hotel sites, local travel sites, smaller public sites, and other verifiable public sources. A small site must not be discarded solely for being unfamiliar; it must instead receive a trust/verification score.

Offers are comparable only after normalization. Verify as far as practical:
- same dates
- same guest/room count
- same room type or defensible equivalent
- taxes / fees included or normalized to total
- breakfast / meals
- cancellation policy
- payment conditions
- availability still live
- source trustworthiness

Never present a nominally lower price as "cheaper" when it is not comparable.

For ambiguous "cheap" requests, return separate best views where useful:
- Cheapest verified
- Best value
- Closest

## Price normalization

Store both raw and normalized offer representations. Suggested fields:
- source
- property / product identity
- raw price
- currency
- tax/fee state
- normalized final price
- room/offer class
- meals
- cancellation
- payment timing
- verification timestamp
- source URL / deep link
- confidence
- exclusion reason if rejected

## Long-running search / monitoring

Example: "دور لي على فندق حول الحرم أقل من 500 ريال، وإذا ما لقيت استمر إلى بكرة الساعة 6."

Create a persistent Task with a deadline and repeated search plan. It may:
- recheck prices
- discover new sources
- retain best verified price
- record price history
- emit an event when a better verified deal is found

Example task summary:
- Title: البحث عن فندق قرب الحرم
- State: Running
- Hotels checked: 63
- Offers checked: 287
- Best verified price: 462 SAR
- Last update: 7 minutes ago

Price improvements should be recorded, e.g. 462 -> 419 SAR.

Condition tasks are supported, e.g. "إذا نزل فندق 4 نجوم تحت 450 ريال علمني." They continue until condition match, deadline, explicit cancellation, or policy limit.

## Task controls from app and WhatsApp

Natural-language operations should modify the same task, not create duplicates when intent is clearly referential.

Examples:
- "وين وصل بحث الفندق؟" -> task status + current best result
- "وقف المهمة" -> Pause
- "كمل" -> Resume
- "لا أبي أبعد أكثر من 700 متر عن الحرم" -> update task constraints and rerank/replan
- "إذا نزل فندق 4 نجوم تحت 450 ريال علمني" -> convert/add monitor condition

## User-visible task events

Show concise operational events, never hidden chain-of-thought.

Examples:
- أبحث عن الفنادق ضمن 1 كم.
- وجدت 38 فندقًا.
- أفحص الأسعار.
- وجدت عرضًا أقل من السعر السابق.
- السعر لا يشمل الضريبة؛ تم تعديله للمقارنة.
- استبعدت العرض لأن التاريخ مختلف.
- وجدت عرضًا من موقع محلي وأتحقق منه.
- أفضل سعر متحقق منه حاليًا: 419 ريال.

The event log should expose actions, results, evidence state, and exclusions without revealing internal reasoning traces.

## Reliability rules

- Never invent place availability, hotel price, distance, rating, opening hours, route detour, or source trust.
- Time-sensitive claims require fresh verification.
- Keep provenance for each decisive result.
- Distinguish verified, partially verified, stale, and unverified data.
- If an offer cannot be normalized, show the caveat or exclude it from "cheapest" ranking.
- Do not claim "cheapest on the internet" unless exhaustive coverage can genuinely be proven (normally it cannot).
- Prefer: "أرخص سعر تمكنت من التحقق منه بين المصادر التي بحثت فيها حتى الآن."

## Cost and performance controls

- Cache normalized place/property/source metadata.
- Cache search results with freshness policies appropriate to the source.
- Re-query volatile prices more frequently than static metadata.
- Use progressive search: structured sources first, then expand only when needed.
- Bound concurrent adapters and retries.
- Deduplicate properties/offers across sources.
- Keep source-specific parsing and credentials server-side.

## Acceptance scenarios

H must ultimately support, from app or WhatsApp:
- "شوف لي مطعم ممتاز على طريقي."
- "شوف لي مطعم على طريق محايل يقدم رز بخاري وتقييماته عالية."
- "شوف لي أرخص فندق قريب الحرم."
- "دور لي على أفضل سعر حتى لو كان في موقع صغير."
- "راقب السعر وعلمني إذا نزل."
- "وين وصل بحث الفندق؟"
- "وقف المهمة."
- "كمل."
- "لا أبي أبعد أكثر من 700 متر عن الحرم."
- "أرسل لي الموقع وافتح الملاحة."

All must use the same persistent task model and remain controllable from both Android and WhatsApp.
