/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.upscannotify.connector.aws

import play.api.Logging
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, HeadObjectRequest}
import uk.gov.hmrc.play.http.logging.Mdc
import uk.gov.hmrc.upscannotify.model.S3ObjectLocation

import javax.inject.Inject
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}

// TOFO S3FileManagerSpec and UrlExpirationIntegrationSpec have been disabled for now, they can be adjusted accordingly
class S3FileManager @Inject()(
  s3Client: S3AsyncClient
)(using
  ExecutionContext
) extends Logging:

  def getObjectMetadata(objectLocation: S3ObjectLocation): Future[S3ObjectMetadata] =
    val request =
      HeadObjectRequest
        .builder()
        .bucket(objectLocation.bucket)
        .key(objectLocation.objectKey)
    logger.info(s"getObjectMetadata($objectLocation)")
    Mdc
      .preservingMdc:
        s3Client
          .headObject(request.build())
          .asScala
      .map: response =>
        S3ObjectMetadata(
          objectLocation,
          response.metadata.asScala.toMap,
          response.lastModified,
          response.contentLength
        )

  def getObject(objectLocation: S3ObjectLocation): Future[(S3ObjectMetadata, String)] =
    val request =
      GetObjectRequest
        .builder()
        .bucket(objectLocation.bucket)
        .key(objectLocation.objectKey)
    Mdc
      .preservingMdc:
        s3Client
          .getObject(request.build(), AsyncResponseTransformer.toBytes())
          .asScala
      .map: in =>
        (S3ObjectMetadata(
           objectLocation,
           in.response.metadata.asScala.toMap,
           in.response.lastModified,
           in.response.contentLength
         ),
         in.asUtf8String
        )
