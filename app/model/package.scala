package model

import java.net.URL

case class Message(id: String, body: String, receiptHandle: String)
case class UploadedFile(url: URL, reference: String)

case class S3ObjectLocation(bucket: String, objectKey: String)
case class FileUploadEvent(location: S3ObjectLocation)
