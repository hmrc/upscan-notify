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

package config

import java.time.Clock

import akka.actor.ActorSystem
import cats.effect
import cats.effect.{IO, _}
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.sqs.AmazonSQS
import com.kenshoo.play.metrics.Metrics
import connectors.HttpNotificationService
import connectors.aws.{S3DownloadUrlGenerator, S3EventParser, S3FileManager, SqsQueueConsumer}
import javax.inject.Inject
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Environment}
import services._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext

class UpscanNotify @Inject()(
  configuration: Configuration,
  env: Environment,
  httpClient: HttpClient,
  sqsClient: AmazonSQS,
  s3Client: AmazonS3,
  auditConnector: AuditConnector,
  metrics: Metrics)(
  implicit actorSystem: ActorSystem,
  applicationLifecycle: ApplicationLifecycle,
  ec: ExecutionContext) {

  type F[A] = IO[A]

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)

  implicit val timer = IO.timer(ec)

  implicit val ioClock: effect.Clock[IO] = timer.clock

  lazy val serviceConfiguration: ServiceConfiguration = new PlayBasedServiceConfiguration(configuration, env)

  lazy val parser = new S3EventParser[F]()

  lazy val fileManager: FileManager[F] = new S3FileManager(s3Client)

  lazy val downloadUrlGenerator: DownloadUrlGenerator = new S3DownloadUrlGenerator(s3Client, serviceConfiguration)

  lazy val fileRetriever: FileNotificationDetailsRetriever[F] =
    new S3FileNotificationDetailsRetriever(fileManager, serviceConfiguration, downloadUrlGenerator)

  lazy val notificationService: NotificationService[F] = new HttpNotificationService(httpClient)

  lazy val auditingService: UpscanAuditingService = new UpscanAuditingService(auditConnector)

  lazy val successfulQueueConsumer: SuccessfulQueueConsumer[F] =
    new SqsQueueConsumer(sqsClient, serviceConfiguration.outboundSuccessfulQueueUrl) with SuccessfulQueueConsumer[F]

  lazy val quarantineQueueConsumer: QuarantineQueueConsumer[F] =
    new SqsQueueConsumer(sqsClient, serviceConfiguration.outboundQuarantineQueueUrl) with QuarantineQueueConsumer[F]

  lazy val successfulFileUploadProcessingJob: PollingJob[F] =
    new NotifyOnSuccessfulFileUploadMessageProcessingJob[F](
      successfulQueueConsumer,
      parser,
      fileRetriever,
      notificationService,
      metrics,
      auditingService,
      serviceConfiguration
    )

  lazy val quarantineFileUploadProcessingJob: PollingJob[F] =
    new NotifyOnQuarantineFileUploadMessageProcessingJob[F](
      quarantineQueueConsumer,
      parser,
      fileRetriever,
      notificationService,
      metrics,
      auditingService,
      serviceConfiguration
    )

  val continousPoller: ContinuousPoller = new ContinuousPoller(
    pollingJobs          = List(successfulFileUploadProcessingJob, quarantineFileUploadProcessingJob),
    serviceConfiguration = serviceConfiguration
  )

  val pollingComputation = continousPoller.run()

  val cancellationToken = pollingComputation.unsafeRunCancelable(_ => ())

  applicationLifecycle.addStopHook(() => cancellationToken.unsafeToFuture())

}
