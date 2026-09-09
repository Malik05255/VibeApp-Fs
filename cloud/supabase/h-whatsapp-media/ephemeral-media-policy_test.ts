import {
  chooseEphemeralMediaStrategy,
  maxRawCloudMediaDurationMs,
} from "./ephemeral-media-policy.ts";

Deno.test("app prefers local derivation even when free cloud exists", () => {
  const decision = chooseEphemeralMediaStrategy({
    kind: "video",
    source: "app",
    sizeBytes: 7 * 1024 * 1024,
    durationMs: 120_000,
  }, {
    localDerivation: true,
    remoteReference: false,
    inlineFreeHelper: true,
    temporaryCloudFree: true,
  });

  if (!decision.allowed || decision.strategy !== "local_derived") {
    throw new Error(`unexpected strategy: ${decision.strategy}`);
  }
  if (decision.rawCloudUploadAllowed) throw new Error("local derivation allowed raw upload");
});

Deno.test("raw audio and video over three minutes never go to cloud", () => {
  for (const kind of ["audio", "video"] as const) {
    const decision = chooseEphemeralMediaStrategy({
      kind,
      source: "whatsapp",
      sizeBytes: 2 * 1024 * 1024,
      durationMs: maxRawCloudMediaDurationMs() + 1,
    }, {
      localDerivation: false,
      remoteReference: true,
      inlineFreeHelper: true,
      temporaryCloudFree: true,
    });

    if (decision.allowed || decision.reason !== "raw_audio_video_over_180_seconds") {
      throw new Error(`${kind} raw cloud duration guard failed`);
    }
  }
});

Deno.test("long media can continue when it is reduced locally first", () => {
  const decision = chooseEphemeralMediaStrategy({
    kind: "audio",
    source: "app",
    sizeBytes: 30 * 1024 * 1024,
    durationMs: 15 * 60_000,
  }, {
    localDerivation: true,
    remoteReference: false,
    inlineFreeHelper: false,
    temporaryCloudFree: false,
  });

  if (!decision.allowed || decision.strategy !== "local_derived") {
    throw new Error("local transcript path should remain available for long media");
  }
});

Deno.test("source short-lived reference beats duplicate H cloud storage", () => {
  const decision = chooseEphemeralMediaStrategy({
    kind: "image",
    source: "whatsapp",
    sizeBytes: 500_000,
  }, {
    localDerivation: false,
    remoteReference: true,
    inlineFreeHelper: true,
    temporaryCloudFree: true,
  });

  if (!decision.allowed || decision.strategy !== "remote_reference") {
    throw new Error("remote source reference was not preferred");
  }
});

Deno.test("strictly free inline processing beats temporary object storage", () => {
  const decision = chooseEphemeralMediaStrategy({
    kind: "pdf",
    source: "whatsapp",
    sizeBytes: 2 * 1024 * 1024,
  }, {
    localDerivation: false,
    remoteReference: false,
    inlineFreeHelper: true,
    temporaryCloudFree: true,
  });

  if (!decision.allowed || decision.strategy !== "inline_free_helper") {
    throw new Error("inline free helper was not preferred");
  }
  if (decision.rawCloudUploadAllowed) throw new Error("inline processing marked raw H upload allowed");
});

Deno.test("temporary cloud is only last verified no-cost option", () => {
  const decision = chooseEphemeralMediaStrategy({
    kind: "image",
    source: "app",
    sizeBytes: 1_000_000,
  }, {
    localDerivation: false,
    remoteReference: false,
    inlineFreeHelper: false,
    temporaryCloudFree: true,
  });

  if (!decision.allowed || decision.strategy !== "temporary_cloud") {
    throw new Error("verified free temporary cloud should be usable as last resort");
  }
  if (!decision.rawCloudUploadAllowed || !decision.mustDeleteTemporaryRaw) {
    throw new Error("temporary raw lifecycle flags are wrong");
  }
});

Deno.test("no verified no-cost route refuses instead of paying", () => {
  const decision = chooseEphemeralMediaStrategy({
    kind: "image",
    source: "app",
    sizeBytes: 1_000_000,
  }, {
    localDerivation: false,
    remoteReference: false,
    inlineFreeHelper: false,
    temporaryCloudFree: false,
  });

  if (decision.allowed || decision.strategy !== "reject") {
    throw new Error("policy silently allowed a non-free fallback");
  }
});

Deno.test("text requires derivation/chunking instead of raw storage", () => {
  const decision = chooseEphemeralMediaStrategy({
    kind: "text",
    source: "whatsapp",
    sizeBytes: 100_000,
  }, {
    localDerivation: false,
    remoteReference: false,
    inlineFreeHelper: true,
    temporaryCloudFree: true,
  });

  if (decision.allowed || decision.reason !== "text_requires_local_chunk_derivation") {
    throw new Error("raw text was allowed without chunk derivation");
  }
});
