import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.TraceUtils
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.spock.Testcontainers
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.ListObjectsRequest
import software.amazon.awssdk.services.s3.model.ListObjectsResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import spock.lang.Shared

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor

@Testcontainers
class S3PutObjectTest extends AgentTestRunner {
  @Shared
  LocalStackContainer localStack = new LocalStackContainer()
    .withServices(LocalStackContainer.Service.S3)

  @Shared
  S3AsyncClient s3Client

  @Shared
  Random random = new Random()

  def setup() {
    s3Client = S3AsyncClient
      .builder()
      .endpointOverride(localStack.getEndpointOverride(LocalStackContainer.Service.S3))
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
        localStack.getAccessKey(), localStack.getSecretKey()
      )))
      .region(Region.of(localStack.getRegion()))
      .build()

    s3Client.createBucket({ b -> b.bucket("test") }).get()

    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()
  }

  def "Send large file"() {
    given:
    byte[] bytes = new byte[1024 * 1024 * 50]
    random.nextBytes(bytes)
    File file = File.createTempFile("test", "data")
    FileOutputStream out = new FileOutputStream(file)
    out.write(bytes)
    out.close()

    Executor executor = Executors.newFixedThreadPool(5)

    List<CompletableFuture<PutObjectResponse>> responses = []

    when:
    for (int i = 0; i < 20; i++) {
      TraceUtils.runUnderTrace("put-$i") {
        responses.add(s3Client.putObject(PutObjectRequest.builder().bucket("test").key("test-$i").build() as PutObjectRequest,
          AsyncRequestBody.fromFile(file)))
      }
    }
    responses.forEach {
      it.get()
    }

    and:
    while (((ThreadPoolExecutor) executor).queue.size() != 0) {
      sleep(10)
    }

    and:
    CompletableFuture<ListObjectsResponse> response
    TraceUtils.runUnderTrace("list") {
      response = s3Client.listObjects(ListObjectsRequest.builder().bucket("test").build() as ListObjectsRequest)
    }

    then:
    response.get().contents().findAll { it.key().startsWith("test-") }.size() == 20
    response.get().contents().any({ it.key() == "test-0" })
    response.get().contents().any({ it.key() == "test-19" })

    and:
    TEST_WRITER.size() == 21
  }
}
