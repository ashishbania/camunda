/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.AddMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.CancelChangeRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ForceOverwriteTopologyRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ScaleRequest;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinator;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinator.TopologyChangeRequest;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinator.TopologyChangeResult;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.function.Function;

/**
 * Handles the requests for the topology management. This is expected be running on the coordinator
 * node.
 */
public final class TopologyManagementRequestsHandler implements TopologyManagementApi {

  private final TopologyChangeCoordinator coordinator;
  private final ConcurrencyControl executor;

  public TopologyManagementRequestsHandler(
      final TopologyChangeCoordinator coordinator, final ConcurrencyControl executor) {
    this.coordinator = coordinator;
    this.executor = executor;
  }

  @Override
  public ActorFuture<TopologyChangeResponse> addMembers(final AddMembersRequest addMembersRequest) {
    return handleRequest(
        addMembersRequest.dryRun(), new AddMembersTransformer(addMembersRequest.members()));
  }

  @Override
  public ActorFuture<TopologyChangeResponse> removeMembers(
      final RemoveMembersRequest removeMembersRequest) {
    return handleRequest(
        removeMembersRequest.dryRun(),
        new RemoveMembersTransformer(removeMembersRequest.members()));
  }

  @Override
  public ActorFuture<TopologyChangeResponse> joinPartition(
      final JoinPartitionRequest joinPartitionRequest) {
    return handleRequest(
        joinPartitionRequest.dryRun(),
        ignore ->
            Either.right(
                List.of(
                    new PartitionJoinOperation(
                        joinPartitionRequest.memberId(),
                        joinPartitionRequest.partitionId(),
                        joinPartitionRequest.priority()))));
  }

  @Override
  public ActorFuture<TopologyChangeResponse> leavePartition(
      final LeavePartitionRequest leavePartitionRequest) {

    return handleRequest(
        leavePartitionRequest.dryRun(),
        ignore ->
            Either.right(
                List.of(
                    new PartitionLeaveOperation(
                        leavePartitionRequest.memberId(), leavePartitionRequest.partitionId()))));
  }

  @Override
  public ActorFuture<TopologyChangeResponse> reassignPartitions(
      final ReassignPartitionsRequest reassignPartitionsRequest) {
    return handleRequest(
        reassignPartitionsRequest.dryRun(),
        new PartitionReassignRequestTransformer(reassignPartitionsRequest.members()));
  }

  @Override
  public ActorFuture<TopologyChangeResponse> scaleMembers(final ScaleRequest scaleRequest) {
    return handleRequest(
        scaleRequest.dryRun(), new ScaleRequestTransformer(scaleRequest.members()));
  }

  @Override
  public ActorFuture<ClusterTopology> cancelTopologyChange(
      final CancelChangeRequest changeRequest) {
    return coordinator.cancelChange(changeRequest.changeId());
  }

  @Override
  public ActorFuture<ClusterTopology> forceOverwriteTopology(
      final ForceOverwriteTopologyRequest forceRequest) {

    return coordinator.forceOverwriteTopology(forceRequest.memberIdsToRemove());
  }

  @Override
  public ActorFuture<ClusterTopology> getTopology() {
    return coordinator.getTopology();
  }

  private ActorFuture<TopologyChangeResponse> handleRequest(
      final boolean dryRun, final TopologyChangeRequest request) {
    final ActorFuture<TopologyChangeResponse> responseFuture = executor.createFuture();
    final Function<TopologyChangeRequest, ActorFuture<TopologyChangeResult>> handler;
    if (dryRun) {
      handler = coordinator::simulateOperations;
    } else {
      handler = coordinator::applyOperations;
    }
    executor.run(
        () ->
            handler
                .apply(request)
                .onComplete(
                    (result, error) -> {
                      if (error == null) {
                        final var changeStatus =
                            new TopologyChangeResponse(
                                result.changeId(),
                                result.currentTopology().members(),
                                result.finalTopology().members(),
                                result.operations());
                        responseFuture.complete(changeStatus);
                      } else {
                        responseFuture.completeExceptionally(error);
                      }
                    }));
    return responseFuture;
  }
}
