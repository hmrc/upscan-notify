/*
 * Copyright 2018 HM Revenue & Customs
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

package connectors.aws

import com.amazonaws.services.s3.AmazonS3
import javax.inject.Inject
import model.S3ObjectLocation
import org.apache.commons.io.IOUtils
import play.api.Logger
import services.{FileManager, ObjectMetadata, ObjectWithMetadata}

import scala.concurrent.Future
import scala.util.Try
import collection.JavaConverters._
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import util.logging.LoggingDetails

class S3FileManager @Inject()(s3Client: AmazonS3) extends FileManager {

  override def retrieveMetadata(objectLocation: S3ObjectLocation): Future[ObjectMetadata] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    Future {
      val metadata = s3Client.getObjectMetadata(objectLocation.bucket, objectLocation.objectKey)
      Logger.debug(s"Fetched metadata for objectKey: [${objectLocation.objectKey}].")
      ObjectMetadata(userMetadata = metadata.getUserMetadata.asScala.toMap, size = metadata.getContentLength)
    }
  }

  override def retrieveObject(objectLocation: S3ObjectLocation): Future[ObjectWithMetadata] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      s3Object <- Future(s3Client.getObject(objectLocation.bucket, objectLocation.objectKey))
      content <- Future.fromTry(Try(IOUtils.toString(s3Object.getObjectContent)))
    } yield {
      Logger.debug(s"Fetched object with metadata for objectKey: [${objectLocation.objectKey}].")

      ObjectWithMetadata(content, ObjectMetadata(
        userMetadata = s3Object.getObjectMetadata.getUserMetadata.asScala.toMap,
        size = s3Object.getObjectMetadata.getContentLength
      ))
    }
  }
}
