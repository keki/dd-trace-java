package com.datadog.profiling.mlt;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * A sampler trace per-thread context wrapper. For now the context is completely determined by the
 * 'traceId' but it can be extended with more dimensions if necessary.
 */
@Builder
public final class SamplerContext {
  @Builder.Default @Getter @NonNull private Thread thread = Thread.currentThread();

  @Getter @NonNull private String traceId;
}