/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.upscannotify.harness.aws

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.{Message, ReceiveMessageRequest, ReceiveMessageResult}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when

import java.net.URL
import java.util.Arrays.asList
import java.util.Date

object Mocks:
  def setup(mock: AmazonSQS, message: Message): AmazonSQS =
    when(mock.receiveMessage(any[ReceiveMessageRequest]))
      .thenReturn(ReceiveMessageResult().withMessages(asList(message)))
    mock

  def setup(mock: AmazonS3, bucketName: String, objectKey: String, metadata: ObjectMetadata, expirationUrl: URL): AmazonS3 =
    when(mock.getObjectMetadata(bucketName, objectKey))
      .thenReturn(metadata)
    when(mock.generatePresignedUrl(eqTo(bucketName), eqTo(objectKey), any[Date]))
      .thenReturn(expirationUrl)
    mock
