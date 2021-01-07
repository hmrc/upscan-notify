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

package connectors.aws

import javax.inject.{Inject, Provider}
import com.amazonaws.auth.{AWSCredentialsProvider, AWSStaticCredentialsProvider, BasicAWSCredentials, BasicSessionCredentials}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import config.ServiceConfiguration
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import services.{DownloadUrlGenerator, FileManager, MessageParser}

class AWSClientModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[AWSCredentialsProvider].toProvider[ProviderOfAWSCredentials],
      bind[AmazonSQS].toProvider[SqsClientProvider],
      bind[AmazonS3].toProvider[S3ClientProvider],
      bind[FileManager].to[S3FileManager],
      bind[MessageParser].to[S3EventParser],
      bind[DownloadUrlGenerator].to[S3DownloadUrlGenerator]
    )

}

class ProviderOfAWSCredentials @Inject()(configuration: ServiceConfiguration) extends Provider[AWSCredentialsProvider] {
  override def get(): AWSCredentialsProvider =
    new AWSStaticCredentialsProvider(configuration.sessionToken match {
      case Some(sessionToken) =>
        new BasicSessionCredentials(configuration.accessKeyId, configuration.secretAccessKey, sessionToken)
      case None =>
        new BasicAWSCredentials(configuration.accessKeyId, configuration.secretAccessKey)
    })
}

class SqsClientProvider @Inject()(configuration: ServiceConfiguration, credentialsProvider: AWSCredentialsProvider) extends Provider[AmazonSQS] {
  override def get(): AmazonSQS =
    AmazonSQSClientBuilder
      .standard()
      .withRegion(configuration.awsRegion)
      .withCredentials(credentialsProvider)
      .build()
}

class S3ClientProvider @Inject()(configuration: ServiceConfiguration, credentialsProvider: AWSCredentialsProvider)
    extends Provider[AmazonS3] {

  override def get(): AmazonS3 =
    AmazonS3ClientBuilder
      .standard()
      .withRegion(configuration.awsRegion)
      .withCredentials(credentialsProvider)
      .build()

}
