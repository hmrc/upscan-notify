/*
 * Copyright 2019 HM Revenue & Customs
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

package connectors

import java.time.{Clock, Instant}

import javax.inject.Inject
import model._
import play.api.Logger
import play.api.libs.json.Writes
import services.NotificationService
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import util.logging.LoggingDetails

import scala.concurrent.{ExecutionContext, Future}

class HttpNotificationService @Inject()(httpClient: HttpClient, clock: Clock) extends NotificationService {

  override def notifySuccessfulCallback(uploadedFile: FileProcessingDetails[SucessfulResult]): Future[Seq[Checkpoint]] =
    makeCallback(
      ReadyCallbackBody(
        reference   = uploadedFile.reference,
        downloadUrl = uploadedFile.result.downloadUrl,
        uploadDetails = UploadDetails(
          fileName        = uploadedFile.result.fileName,
          fileMimeType    = uploadedFile.result.fileMimeType,
          uploadTimestamp = uploadedFile.result.uploadTimestamp,
          checksum        = uploadedFile.result.checksum
        )
      ),
      uploadedFile,
      "File ready"
    )

  override def notifyFailedCallback(
    quarantinedFile: FileProcessingDetails[QuarantinedResult]): Future[Seq[Checkpoint]] =
    makeCallback(
      FailedCallbackBody(reference = quarantinedFile.reference, failureDetails = quarantinedFile.result.error),
      quarantinedFile,
      "File failed")

  private def makeCallback[T, M <: ProcessingResult](
    callback: T,
    metadata: FileProcessingDetails[M],
    notificationType: String)(implicit writes: Writes[T]): Future[Seq[Checkpoint]] = {

    implicit val ld = LoggingDetails.fromFileReference(metadata.reference)

    for (WithTimeMeasurement(measurement, httpResult) <- timed(
                                                          httpClient.POST[T, HttpResponse](
                                                            metadata.callbackUrl.toString,
                                                            callback))) yield {
      Logger.info(
        s"""$notificationType notification sent to service with callbackUrl: [${metadata.callbackUrl}].
           | Response status was: [${httpResult.status}].""".stripMargin
      )
      collectExecutionTimeMetadata(measurement)
    }
  }

  private def collectExecutionTimeMetadata(timeMeasurement: TimeMeasurement): Seq[Checkpoint] =
    Seq(
      Checkpoint("x-amz-meta-upscan-notify-callback-started", timeMeasurement.start),
      Checkpoint("x-amz-meta-upscan-notify-callback-ended", timeMeasurement.end)
    )

  case class TimeMeasurement(start: Instant, end: Instant)
  case class WithTimeMeasurement[T](timeMeasurement: TimeMeasurement, result: T)

  private def timed[T](f: => Future[T])(implicit ec: ExecutionContext): Future[WithTimeMeasurement[T]] = {

    val startTime = clock.instant()
    f.map { result =>
      val endTime = clock.instant()
      WithTimeMeasurement(TimeMeasurement(startTime, endTime), result)
    }
  }

}
