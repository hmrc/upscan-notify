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

import com.amazonaws.auth.{AWSCredentialsProvider, AWSStaticCredentialsProvider, BasicAWSCredentials, BasicSessionCredentials}
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, AwsCredentialsProvider, AwsSessionCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import uk.gov.hmrc.upscannotify.config.ServiceConfiguration
import uk.gov.hmrc.upscannotify.service.{DownloadUrlGenerator, FileManager, MessageParser}

import javax.inject.{Inject, Provider}

class AWSClientModule extends Module:
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[AWSCredentialsProvider].toProvider[OldProviderOfAWSCredentials],
      bind[AwsCredentialsProvider].toProvider[ProviderOfAwsCredentials],
      bind[AmazonSQS             ].toProvider[SqsClientProvider],
      bind[S3AsyncClient         ].toProvider[S3ClientProvider],
      bind[FileManager           ].to[S3FileManager],
      bind[MessageParser         ].to[S3EventParser],
      bind[DownloadUrlGenerator  ].to[S3DownloadUrlGenerator]
    )

class OldProviderOfAWSCredentials @Inject()(configuration: ServiceConfiguration) extends Provider[AWSCredentialsProvider]:
  override def get(): AWSCredentialsProvider =
    AWSStaticCredentialsProvider(
      configuration.sessionToken match
        case Some(sessionToken) =>
          BasicSessionCredentials(configuration.accessKeyId, configuration.secretAccessKey, sessionToken)
        case None =>
          BasicAWSCredentials(configuration.accessKeyId, configuration.secretAccessKey)
    )

class ProviderOfAwsCredentials @Inject()(configuration: ServiceConfiguration) extends Provider[AwsCredentialsProvider]:
  override def get(): AwsCredentialsProvider =
    StaticCredentialsProvider.create:
      configuration.sessionToken match
        case Some(sessionToken) =>
          AwsSessionCredentials.create(configuration.accessKeyId, configuration.secretAccessKey, sessionToken)
        case None =>
          AwsBasicCredentials.create(configuration.accessKeyId, configuration.secretAccessKey)

class SqsClientProvider @Inject()(
  configuration      : ServiceConfiguration,
  credentialsProvider: AWSCredentialsProvider
) extends Provider[AmazonSQS]:
  override def get(): AmazonSQS =
    AmazonSQSClientBuilder
      .standard()
      .withRegion(configuration.awsRegion)
      .withCredentials(credentialsProvider)
      .build()

class S3ClientProvider @Inject()(
  configuration      : ServiceConfiguration,
  credentialsProvider: AwsCredentialsProvider
) extends Provider[S3AsyncClient]:
  override def get(): S3AsyncClient =
    S3AsyncClient.builder()
      .region(Region.of(configuration.awsRegion))
      .credentialsProvider(credentialsProvider)
      .build()
