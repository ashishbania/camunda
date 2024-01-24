/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.ClusterTopologyManager;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ForceOverwriteTopologyRequest;
import io.camunda.zeebe.topology.api.TopologyRequestFailedException;
import io.camunda.zeebe.topology.api.TopologyRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.topology.api.TopologyRequestFailedException.InvalidRequest;
import io.camunda.zeebe.topology.api.TopologyRequestFailedException.OperationNotAllowed;
import io.camunda.zeebe.topology.changes.TopologyChangeAppliers.OperationApplier;
import io.camunda.zeebe.topology.state.ClusterChangePlan;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.CompletedChange;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.ForcePartitionReconfigure;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyChangeCoordinatorImpl implements TopologyChangeCoordinator {
  private static final Logger LOG = LoggerFactory.getLogger(TopologyChangeCoordinatorImpl.class);
  private final ClusterTopologyManager clusterTopologyManager;
  private final ConcurrencyControl executor;

  public TopologyChangeCoordinatorImpl(
      final ClusterTopologyManager clusterTopologyManager, final ConcurrencyControl executor) {
    this.clusterTopologyManager = clusterTopologyManager;
    this.executor = executor;
  }

  @Override
  public ActorFuture<ClusterTopology> getTopology() {
    return clusterTopologyManager.getClusterTopology();
  }

  @Override
  public ActorFuture<TopologyChangeResult> applyOperations(final TopologyChangeRequest request) {
    return applyOrDryRun(false, request);
  }

  @Override
  public ActorFuture<TopologyChangeResult> simulateOperations(final TopologyChangeRequest request) {
    return applyOrDryRun(true, request);
  }

  @Override
  public ActorFuture<ClusterTopology> cancelChange(final long changeId) {
    final ActorFuture<ClusterTopology> future = executor.createFuture();
    executor.run(
        () ->
            clusterTopologyManager.updateClusterTopology(
                clusterTopology -> {
                  if (!validateCancel(changeId, clusterTopology, future)) {
                    return clusterTopology;
                  }
                  final var completedOperation =
                      clusterTopology
                          .pendingChanges()
                          .map(ClusterChangePlan::completedOperations)
                          .orElse(List.of());
                  final var cancelledOperations =
                      clusterTopology
                          .pendingChanges()
                          .map(ClusterChangePlan::pendingOperations)
                          .orElse(List.of());
                  LOG.warn(
                      "Cancelling topology change '{}'. Following operations have been already applied: {}. Following pending operations won't be applied: {}",
                      changeId,
                      completedOperation,
                      cancelledOperations);
                  final var cancelledTopology = clusterTopology.cancelPendingChanges();
                  future.complete(cancelledTopology);
                  return cancelledTopology;
                }));
    return future;
  }

  @Override
  public ActorFuture<TopologyChangeResult> forceOverwriteTopology(
      final ForceOverwriteTopologyRequest request) {
    final ActorFuture<TopologyChangeResult> future = executor.createFuture();
    executor.run(() -> forcerOverwriteTopologyInternal(request.memberIdsToRemove(), future));
    return future;
  }

  private void forcerOverwriteTopologyInternal(
      final List<MemberId> memberIdsToRemove, final ActorFuture<TopologyChangeResult> future) {
    clusterTopologyManager
        .getClusterTopology()
        .onComplete(
            (currentClusterTopology, errorOnGettingTopology) -> {
              if (errorOnGettingTopology != null) {
                LOG.error("Failed to overwrite topology", errorOnGettingTopology);
                failFuture(future, errorOnGettingTopology);
                return;
              }
              if (currentClusterTopology.isUninitialized()) {
                LOG.error("Cannot overwrite topology. The topology is not initialized.");
                failFuture(future, new RuntimeException("Uninitialized topology"));
                return;
              }
              if (currentClusterTopology.hasPendingChanges()) {
                LOG.error(
                    "Cannot overwrite topology. Another topology change [{}] is in progress.",
                    currentClusterTopology);
                failFuture(future, new ConcurrentModificationException("fail"));
                return;
              }
              var newTopology = currentClusterTopology;
              for (final MemberId idToRemove : memberIdsToRemove) {
                newTopology = newTopology.updateMember(idToRemove, member -> null);
              }

              final List<TopologyChangeOperation> operations = new ArrayList<>();

              final var partitions =
                  newTopology.members().values().stream()
                      .flatMap(memberState -> memberState.partitions().keySet().stream())
                      .collect(Collectors.toSet());
              for (final var partition : partitions) {
                final var members =
                    newTopology.members().entrySet().stream()
                        .filter(member -> member.getValue().hasPartition(partition))
                        .map(Entry::getKey)
                        .collect(Collectors.toSet())
                        .stream()
                        .toList();
                for (int i = 0; i < members.size() - 1; i++) {
                  operations.add(new ForcePartitionReconfigure(members.get(i), partition, false));
                }
                operations.add(new ForcePartitionReconfigure(members.getLast(), partition, true));
              }

              final var newTopologyWithChanges = newTopology.startTopologyChange(operations);

              clusterTopologyManager
                  .updateClusterTopology(
                      clusterTopology ->
                          newTopologyWithChanges) // TODO: verify no concurrent update
                  .onComplete(
                      (updatedTopology, errorOnUpdatingTopology) -> {
                        if (errorOnUpdatingTopology != null) {
                          LOG.error("Failed to overwrite topology", errorOnUpdatingTopology);
                          failFuture(future, errorOnUpdatingTopology);
                        } else {
                          LOG.info("Overwritten topology. The new topology is {}", updatedTopology);
                          future.complete(
                              new TopologyChangeResult(
                                  updatedTopology,
                                  updatedTopology,
                                  0,
                                  updatedTopology.pendingChanges().get().pendingOperations()));
                        }
                      });
            });
  }

  private ActorFuture<TopologyChangeResult> applyOrDryRun(
      final boolean dryRun, final TopologyChangeRequest request) {
    final ActorFuture<TopologyChangeResult> future = executor.createFuture();
    executor.run(
        () ->
            clusterTopologyManager
                .getClusterTopology()
                .onComplete(
                    (currentClusterTopology, errorOnGettingTopology) -> {
                      if (errorOnGettingTopology != null) {
                        failFuture(future, errorOnGettingTopology);
                        return;
                      }
                      final var generatedOperations = request.operations(currentClusterTopology);
                      if (generatedOperations.isLeft()) {
                        failFuture(future, generatedOperations.getLeft());
                        return;
                      }

                      applyOrDryRunOnTopology(
                          dryRun, currentClusterTopology, generatedOperations.get(), future);
                    },
                    executor));
    return future;
  }

  private void applyOrDryRunOnTopology(
      final boolean dryRun,
      final ClusterTopology currentClusterTopology,
      final List<TopologyChangeOperation> operations,
      final ActorFuture<TopologyChangeResult> future) {
    if (operations.isEmpty()) {
      // No operations to apply
      future.complete(
          new TopologyChangeResult(
              currentClusterTopology,
              currentClusterTopology,
              currentClusterTopology.lastChange().map(CompletedChange::id).orElse(0L),
              operations));
      return;
    }

    final ActorFuture<ClusterTopology> validation =
        validateTopologyChangeRequest(currentClusterTopology, operations);

    validation.onComplete(
        (simulatedFinalTopology, validationError) -> {
          if (validationError != null) {
            failFuture(future, validationError);
            return;
          }

          // Validation was successful. If it's not a dry-run, apply the changes.
          final ActorFuture<ClusterTopology> applyFuture = executor.createFuture();
          if (dryRun) {
            applyFuture.complete(currentClusterTopology.startTopologyChange(operations));
          } else {
            applyTopologyChange(
                operations, currentClusterTopology, simulatedFinalTopology, applyFuture);
          }

          applyFuture.onComplete(
              (clusterTopologyWithPendingChanges, error) -> {
                if (error == null) {
                  final long changeId =
                      clusterTopologyWithPendingChanges
                          .pendingChanges()
                          .map(ClusterChangePlan::id)
                          .orElse(0L); // No changes, this should not happen because
                  // operations are not empty

                  future.complete(
                      new TopologyChangeResult(
                          currentClusterTopology, simulatedFinalTopology, changeId, operations));
                } else {
                  failFuture(future, error);
                }
              });
        });
  }

  private ActorFuture<ClusterTopology> validateTopologyChangeRequest(
      final ClusterTopology currentClusterTopology,
      final List<TopologyChangeOperation> operations) {

    final ActorFuture<ClusterTopology> validationFuture = executor.createFuture();

    if (currentClusterTopology.isUninitialized()) {
      failFuture(
          validationFuture,
          new OperationNotAllowed(
              "Cannot apply topology change. The topology is not initialized."));
    } else if (currentClusterTopology.hasPendingChanges()) {
      failFuture(
          validationFuture,
          new ConcurrentModificationException(
              String.format(
                  "Cannot apply topology change. Another topology change [%s] is in progress.",
                  currentClusterTopology)));
    } else {
      // simulate applying changes to validate the operations
      final var topologyChangeSimulator =
          new TopologyChangeAppliersImpl(
              new NoopPartitionChangeExecutor(), new NoopTopologyMembershipChangeExecutor());
      final var topologyWithPendingOperations =
          currentClusterTopology.startTopologyChange(operations);

      // Simulate applying the operations. The resulting topology will be the expected final
      // topology. If the sequence of operations is not valid, the simulation fails.
      simulateTopologyChange(
          topologyWithPendingOperations, topologyChangeSimulator, validationFuture);
    }
    return validationFuture;
  }

  private void applyTopologyChange(
      final List<TopologyChangeOperation> operations,
      final ClusterTopology currentClusterTopology,
      final ClusterTopology simulatedFinalTopology,
      final ActorFuture<ClusterTopology> future) {
    executor.run(
        () ->
            clusterTopologyManager
                .updateClusterTopology(
                    clusterTopology -> {
                      if (!clusterTopology.equals(currentClusterTopology)) {
                        throw new ConcurrentModificationException(
                            "Topology changed while applying the change. Please retry.");
                      }
                      return clusterTopology.startTopologyChange(operations);
                    })
                .onComplete(
                    (topologyWithPendingOperations, errorOnUpdatingTopology) -> {
                      if (errorOnUpdatingTopology != null) {
                        failFuture(future, errorOnUpdatingTopology);
                        return;
                      }
                      LOG.debug(
                          "Applying the topology change has started. The resulting topology will be {}",
                          simulatedFinalTopology);
                      future.complete(topologyWithPendingOperations);
                    }));
  }

  private void simulateTopologyChange(
      final ClusterTopology updatedTopology,
      final TopologyChangeAppliersImpl topologyChangeSimulator,
      final ActorFuture<ClusterTopology> simulationCompleted) {
    if (!updatedTopology.hasPendingChanges()) {
      simulationCompleted.complete(updatedTopology);
      return;
    }

    final var operation = updatedTopology.nextPendingOperation();
    final OperationApplier applier = topologyChangeSimulator.getApplier(operation);
    final var result = applier.init(updatedTopology);
    if (result.isLeft()) {
      failFuture(simulationCompleted, new InvalidRequest(result.getLeft()));
      return;
    }

    final var initializedChanges = updatedTopology.updateMember(operation.memberId(), result.get());

    applier
        .apply()
        .onComplete(
            (stateUpdater, error) -> {
              if (error != null) {
                failFuture(simulationCompleted, new InvalidRequest(error));
                return;
              }
              final var newTopology =
                  initializedChanges.advanceTopologyChange(operation.memberId(), stateUpdater);

              simulateTopologyChange(newTopology, topologyChangeSimulator, simulationCompleted);
            });
  }

  private void failFuture(final ActorFuture<?> future, final Throwable error) {
    LOG.warn("Failed to handle topology request", error);
    if (error instanceof TopologyRequestFailedException) {
      future.completeExceptionally(error);
    } else {
      future.completeExceptionally(new TopologyRequestFailedException.InternalError(error));
    }
  }

  private boolean validateCancel(
      final long changeId,
      final ClusterTopology currentClusterTopology,
      final ActorFuture<ClusterTopology> future) {
    if (currentClusterTopology.isUninitialized()) {
      failFuture(
          future,
          new InvalidRequest(
              "Cannot cancel change " + changeId + " because the topology is not initialized"));
      return false;
    }
    if (!currentClusterTopology.hasPendingChanges()) {
      failFuture(
          future,
          new InvalidRequest(
              "Cannot cancel change " + changeId + " because no change is in progress"));
      return false;
    }

    final var clusterChangePlan = currentClusterTopology.pendingChanges().orElseThrow();
    if (clusterChangePlan.id() != changeId) {
      failFuture(
          future,
          new InvalidRequest(
              "Cannot cancel change " + changeId + " because it is not the current change"));
      return false;
    }
    return true;
  }
}
