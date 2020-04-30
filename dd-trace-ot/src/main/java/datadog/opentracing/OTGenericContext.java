package datadog.opentracing;

import datadog.trace.core.DDSpanContext;
import io.opentracing.SpanContext;
import java.util.Map;
import java.util.Objects;

class OTGenericContext implements SpanContext {
  private final DDSpanContext delegate;

  OTGenericContext(final DDSpanContext delegate) {
    this.delegate = delegate;
  }

  @Override
  public String toTraceId() {
    return delegate.getTraceId().toString();
  }

  @Override
  public String toSpanId() {
    return delegate.getSpanId().toString();
  }

  @Override
  public Iterable<Map.Entry<String, String>> baggageItems() {
    return delegate.baggageItems();
  }

  DDSpanContext getDelegate() {
    return delegate;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final OTGenericContext that = (OTGenericContext) o;
    return delegate.equals(that.delegate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(delegate);
  }
}