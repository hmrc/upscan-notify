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

package services

import javax.inject.Inject
import config.ServiceConfiguration
import model._
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, Json}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import util.logging.LoggingDetails

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class S3FileNotificationDetailsRetriever @Inject()(
  fileManager: FileManager,
  config: ServiceConfiguration,
  downloadUrlGenerator: DownloadUrlGenerator)
    extends FileNotificationDetailsRetriever {

  override def retrieveUploadedFileDetails(objectLocation: S3ObjectLocation): Future[UploadedFile] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      metadata <- fileManager.retrieveReadyMetadata(objectLocation)
      downloadUrl = downloadUrlGenerator.generate(objectLocation)
    } yield {
      val retrieved =
        UploadedFile(
          metadata.callbackUrl,
          FileReference(objectLocation.objectKey),
          downloadUrl,
          metadata.size,
          metadata.uploadDetails,
          metadata.requestContext
        )
      Logger.debug(
        s"Retrieved file with callbackUrl: [${retrieved.callbackUrl}], for objectKey: [${objectLocation.objectKey}].")
      retrieved
    }
  }

  override def retrieveQuarantinedFileDetails(objectLocation: S3ObjectLocation): Future[QuarantinedFile] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      quarantineFile <- fileManager.retrieveFailedObject(objectLocation)
    } yield {
      val retrieved =
        QuarantinedFile(
          quarantineFile.metadata.callbackUrl,
          FileReference(objectLocation.objectKey),
          parseContents(quarantineFile.content),
          quarantineFile.metadata.requestContext
        )
      Logger.debug(
        s"Retrieved quarantined file with callbackUrl: [${retrieved.callbackUrl}], for objectKey: [${objectLocation.objectKey}].")
      retrieved
    }
  }

  private def parseContents(contents: String): ErrorDetails = {
    def unknownError(): ErrorDetails = ErrorDetails("UNKNOWN", contents)

    Try(Json.parse(contents)) match {
      case Success(json) =>
        json.validate[ErrorDetails] match {
          case JsSuccess(details, _) => details
          case _: JsError            => unknownError()
        }
      case Failure(_) => unknownError()
    }
  }
}
