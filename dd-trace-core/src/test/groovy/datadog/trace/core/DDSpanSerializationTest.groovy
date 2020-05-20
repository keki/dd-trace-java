package datadog.trace.core

import com.squareup.moshi.Moshi
import datadog.trace.api.DDTags
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import org.msgpack.core.MessagePack
import org.msgpack.core.buffer.ArrayBufferInput
import org.msgpack.core.buffer.ArrayBufferOutput
import org.msgpack.value.ValueType

import static datadog.trace.core.serialization.JsonFormatWriter.SPAN_ADAPTER
import static datadog.trace.core.serialization.MsgpackFormatWriter.MSGPACK_WRITER

class DDSpanSerializationTest extends DDSpecification {

  def "serialize spans with sampling #samplingPriority"() throws Exception {
    setup:
    def jsonAdapter = new Moshi.Builder().build().adapter(Map)

    final Map<String, Number> metrics = ["_sampling_priority_v1": 1]
    if (samplingPriority == PrioritySampling.UNSET) {  // RateByServiceSampler sets priority
      metrics.put("_dd.agent_psr", 1.0d)
    }

    Map<String, Object> expected = [
      service  : "service",
      name     : "operation",
      resource : "operation",
      trace_id : DDId.from(1),
      span_id  : DDId.from(2),
      parent_id: DDId.ZERO,
      start    : 100000,
      duration : 33000,
      type     : spanType,
      error    : 0,
      metrics  : metrics,
      meta     : [
        "a-baggage"         : "value",
        "k1"                : "v1",
        (DDTags.THREAD_NAME): Thread.currentThread().getName(),
        (DDTags.THREAD_ID)  : String.valueOf(Thread.currentThread().getId()),
      ],
    ]

    def writer = new ListWriter()
    def tracer = CoreTracer.builder().writer(writer).build()
    final DDSpanContext context =
      new DDSpanContext(
        DDId.from(1),
        DDId.from(2),
        DDId.ZERO,
        "service",
        "operation",
        null,
        samplingPriority,
        null,
        ["a-baggage": "value"],
        false,
        spanType,
        ["k1": "v1"],
        PendingTrace.create(tracer, DDId.from(1)),
        tracer,
        [:])

    DDSpan span = DDSpan.create(100L, context)

    span.finish(133L)

    def actualTree = jsonAdapter.fromJson(SPAN_ADAPTER.toJson(span))
    def expectedTree = jsonAdapter.fromJson(jsonAdapter.toJson(expected))
    expect:
    actualTree == expectedTree

    where:
    samplingPriority              | spanType
    PrioritySampling.SAMPLER_KEEP | null
    PrioritySampling.UNSET        | "some-type"
  }

  def "serialize trace/span with id #value as int"() {
    setup:
    def writer = new ListWriter()
    def tracer = CoreTracer.builder().writer(writer).build()
    def context = new DDSpanContext(
      value,
      value,
      DDId.ZERO,
      "fakeService",
      "fakeOperation",
      "fakeResource",
      PrioritySampling.UNSET,
      null,
      Collections.emptyMap(),
      false,
      spanType,
      Collections.emptyMap(),
      PendingTrace.create(tracer, DDId.from(1)),
      tracer,
      [:])
    def span = DDSpan.create(0, context)
    def buffer = new ArrayBufferOutput()
    def packer = MessagePack.newDefaultPacker(buffer)
    MSGPACK_WRITER.writeDDSpan(span, packer)
    packer.flush()
    byte[] bytes = buffer.toByteArray()
    def unpacker = MessagePack.newDefaultUnpacker(new ArrayBufferInput(bytes))
    int size = unpacker.unpackMapHeader()

    expect:
    for (int i = 0; i < size; i++) {
      String key = unpacker.unpackString()

      switch (key) {
        case "trace_id":
        case "span_id":
          assert unpacker.nextFormat.valueType == ValueType.INTEGER
          assert unpacker.unpackBigInteger() == value
          break
        default:
          unpacker.unpackValue()
      }
    }

    where:
    value                                           | spanType
    DDId.ZERO                                       | null
    DDId.from(1)                                    | "some-type"
    DDId.from(8223372036854775807)                  | null
    DDId.from(Long.MAX_VALUE - 1)                   | "some-type"
    DDId.from(-1)                                   | "some-type"
  }
}
