# TODO
- Integration with SQS (message polling, message confirmation)
- Calling the callback URL (within notification service)
- Message deserialization from XML
- Gluing everything together and proper startup/shutdown
  - Have added orchestrator to call queue consumer, process messages, and send back confirmations for successful messages
  - Messages that are not processed successfully are logged and ignored, which means that SQS will eventually retry