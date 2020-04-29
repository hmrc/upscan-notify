package harness.aws

import java.net.URL
import java.util.Arrays.asList
import java.util.Date

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.{Message, ReceiveMessageRequest, ReceiveMessageResult}
import org.mockito.ArgumentMatchersSugar._
import org.mockito.MockitoSugar._

object Mocks {
  def setup(mock: AmazonSQS, bucketName: String, objectKey: String, message: Message): AmazonSQS = {
    when(mock.receiveMessage(any[ReceiveMessageRequest]))
      .thenReturn(new ReceiveMessageResult().withMessages(asList(message)))
    mock
  }

  def setup(mock: AmazonS3, bucketName: String, objectKey: String, metadata: ObjectMetadata, expirationUrl: URL): AmazonS3 = {
    when(mock.getObjectMetadata(bucketName, objectKey)).thenReturn(metadata)
    when(mock.generatePresignedUrl(eqTo(bucketName), eqTo(objectKey), any[Date])).thenReturn(expirationUrl)
    mock
  }
}
