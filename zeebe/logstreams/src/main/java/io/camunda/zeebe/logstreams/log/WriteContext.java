/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.log;

public sealed interface WriteContext {
  static WriteContext userCommand() {
    return UserCommand.INSTANCE;
  }

  static WriteContext processingResult() {
    return ProcessingResult.INSTANCE;
  }

  static WriteContext interPartition() {
    return InterPartition.INSTANCE;
  }

  static WriteContext scheduled() {
    return Scheduled.INSTANCE;
  }

  static WriteContext internal() {
    return Internal.INSTANCE;
  }

  final class UserCommand implements WriteContext {
    private static final UserCommand INSTANCE = new UserCommand();
  }

  final class ProcessingResult implements WriteContext {
    private static final ProcessingResult INSTANCE = new ProcessingResult();
  }

  final class InterPartition implements WriteContext {
    private static final InterPartition INSTANCE = new InterPartition();
  }

  final class Scheduled implements WriteContext {
    private static final Scheduled INSTANCE = new Scheduled();
  }

  final class Internal implements WriteContext {
    private static final Internal INSTANCE = new Internal();
  }
}
