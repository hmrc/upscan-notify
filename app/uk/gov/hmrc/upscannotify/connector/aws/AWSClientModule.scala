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

import org.apache.pekko.actor.ActorSystem
import play.api.inject.{Binding, Module}
import play.api.Logger
import play.api.{Configuration, Environment}
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.*
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, AwsCredentialsProvider, AwsSessionCredentials, ContainerCredentialsProvider, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.s3.S3AsyncClient
import uk.gov.hmrc.upscannotify.config.ServiceConfiguration
import uk.gov.hmrc.upscannotify.service.{DownloadUrlGenerator, FileNotificationDetailsRetriever, MessageParser}

import javax.inject.{Inject, Provider}

class AWSClientModule extends Module:
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[SecretsManagerClient            ].toProvider[SecretsManagerClientProvider],
      bind[AwsCredentialsProvider          ].toProvider[ProviderOfAwsCredentials],
      bind[SqsAsyncClient                  ].toProvider[SqsClientProvider],
      bind[S3AsyncClient                   ].toProvider[S3ClientProvider],
      bind[FileNotificationDetailsRetriever].to[S3FileNotificationDetailsRetriever],
      bind[MessageParser                   ].to[S3EventParser],
      bind[DownloadUrlGenerator            ].to[S3DownloadUrlGenerator]
    )

class ProviderOfAwsCredentials @Inject()(
  configuration       : ServiceConfiguration,
  secretsManagerClient: SecretsManagerClient
) extends Provider[AwsCredentialsProvider]:
  private val logger: Logger = Logger(getClass)
  override def get(): AwsCredentialsProvider =
    StaticCredentialsProvider.create:
      configuration.sessionToken match
        case Some(sessionToken) =>
          AwsSessionCredentials.create(configuration.accessKeyId, configuration.secretAccessKey, sessionToken)
        case None =>
          try
            val request = GetSecretValueRequest.builder()
              .secretId(configuration.secretArn)
              .build()

            val secretResponse = secretsManagerClient.getSecretValue(request)
            val creds = Json.parse(secretResponse.secretString()).as[RetrievedCredentials](RetrievedCredentials.reads)

            AwsBasicCredentials.create(creds.accessKeyId, creds.secretAccessKey)
          catch
            case e: Exception =>
              logger.error(s"Failed to retrieve AWS credentials from Secrets Manager: ${e.getMessage}", e)
              throw e

class SqsClientProvider @Inject()(
  configuration      : ServiceConfiguration,
  credentialsProvider: AwsCredentialsProvider,
  actorSystem        : ActorSystem
) extends Provider[SqsAsyncClient]:
  override def get(): SqsAsyncClient =
    val client =
      SqsAsyncClient.builder()
        .region(Region.of(configuration.awsRegion))
        .credentialsProvider(credentialsProvider)
        .build()
    actorSystem.registerOnTermination(client.close())
    client

class S3ClientProvider @Inject()(
  configuration      : ServiceConfiguration,
  credentialsProvider: AwsCredentialsProvider,
  actorSystem        : ActorSystem
) extends Provider[S3AsyncClient]:
  override def get(): S3AsyncClient =
    val client =
      S3AsyncClient.builder()
        .region(Region.of(configuration.awsRegion))
        .credentialsProvider(credentialsProvider)
        .build()
    actorSystem.registerOnTermination(client.close())
    client

class SecretsManagerClientProvider @Inject()(
  configuration: ServiceConfiguration,
  actorSystem  : ActorSystem
) extends Provider[SecretsManagerClient]:
  override def get(): SecretsManagerClient =
    val containerCredentials = ContainerCredentialsProvider.builder().build()
    val client =
      SecretsManagerClient.builder()
        .credentialsProvider(containerCredentials)
        .region(Region.of(configuration.awsRegion))
        .build()
    actorSystem.registerOnTermination(client.close())
    client

final case class RetrievedCredentials(accessKeyId: String, secretAccessKey: String):
  override def toString(): String = s"RetrievedCredentials($accessKeyId, REDACTED)"

object RetrievedCredentials:
  val reads: Reads[RetrievedCredentials] =
    ( (__ \ "accessKeyId"    ).read[String]
    ~ (__ \ "secretAccessKey").read[String]
    )(apply)
