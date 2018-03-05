# TODO
- Gluing everything together and proper startup/shutdown
  - Have added orchestrator to call queue consumer, process messages, and send back confirmations for successful messages
  - Messages that are not processed successfully are logged and ignored, which means that SQS will eventually retry
- Need to configure deadletter queue
- Build service to generate presigned URL for download, and call this service for contents of notification callback body