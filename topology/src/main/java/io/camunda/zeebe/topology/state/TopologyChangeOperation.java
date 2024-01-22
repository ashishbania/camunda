/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.state;

import io.atomix.cluster.MemberId;
import java.util.List;

/**
 * An operation that changes the topology. The operation could be a member join or leave a cluster,
 * or a member join or leave partition.
 */
public sealed interface TopologyChangeOperation {

  MemberId memberId();

  record MemberJoinOperation(MemberId memberId) implements TopologyChangeOperation {}

  record MemberLeaveOperation(MemberId memberId) implements TopologyChangeOperation {}

  sealed interface PartitionChangeOperation extends TopologyChangeOperation {
    int partitionId();

    record PartitionJoinOperation(MemberId memberId, int partitionId, int priority)
        implements PartitionChangeOperation {}

    record PartitionLeaveOperation(MemberId memberId, int partitionId)
        implements PartitionChangeOperation {}

    record PartitionReconfigurePriorityOperation(MemberId memberId, int partitionId, int priority)
        implements PartitionChangeOperation {}

    // always run on the coordinator
    record PartitionOverwriteConfiguration(
        MemberId memberId, int partitionId, List<MemberId> newMembers)
        implements PartitionChangeOperation {}

    record ForcePartitionReconfigure(MemberId memberId, int partitionId)
        implements PartitionChangeOperation {}
  }
}
