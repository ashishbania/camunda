/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.DraftTaskVariableEntity;
import io.camunda.tasklist.entities.FlowNodeInstanceEntity;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.entities.TaskVariableEntity;
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.DraftVariableStore;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.store.VariableStore.FlowNodeTree;
import io.camunda.tasklist.store.VariableStore.GetVariablesRequest;
import io.camunda.tasklist.store.VariableStore.VariableMap;
import io.camunda.tasklist.util.CollectionUtil;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.tasklist.webapp.es.TaskValidator;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariableService {

  private static final Logger LOGGER = LoggerFactory.getLogger(VariableService.class);
  private static final String ABSENT_PARENT_ID = "-1";

  @Autowired private TaskStore taskStore;
  @Autowired private VariableStore variableStore;
  @Autowired private DraftVariableStore draftVariableStore;
  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private TaskValidator taskValidator;
  @Autowired private ObjectMapper objectMapper;

  public void persistDraftTaskVariables(String taskId, List<VariableInputDTO> draftTaskVariables) {
    try {
      final TaskEntity task = taskStore.getTask(taskId);
      taskValidator.validateCanPersistDraftTaskVariables(task);
      validateVariableInputs(draftTaskVariables);

      // drop all current draft variables
      final long deletedDraftVariablesCount = draftVariableStore.deleteAllByTaskId(taskId);
      LOGGER.debug(
          "'{}' draft task variables associated with task id '{}' were deleted",
          deletedDraftVariablesCount,
          task);

      if (CollectionUtils.isEmpty(draftTaskVariables)) {
        return;
      }

      final Map<String, VariableEntity> currentOriginalVariables = new HashMap<>();
      getRuntimeVariablesByRequest(GetVariablesRequest.createFrom(task))
          .forEach(originalVar -> currentOriginalVariables.put(originalVar.getName(), originalVar));

      final int variableSizeThreshold = tasklistProperties.getImporter().getVariableSizeThreshold();

      final Map<String, DraftTaskVariableEntity> toPersist = new HashMap<>();
      draftTaskVariables.forEach(
          draftVariable -> {
            if (currentOriginalVariables.containsKey(draftVariable.getName())) {
              final VariableEntity variableEntity =
                  currentOriginalVariables.get(draftVariable.getName());
              // Persist new draft variables based on the input if value `name` is the same as
              // original and `value` property is different
              if (!variableEntity.getFullValue().equals(draftVariable.getValue())) {
                toPersist.put(
                    draftVariable.getName(),
                    DraftTaskVariableEntity.createFrom(
                        // draft variable will have the same Id as original variable
                        variableEntity.getId(),
                        task,
                        draftVariable.getName(),
                        draftVariable.getValue(),
                        variableSizeThreshold));
              }
            } else {
              toPersist.put(
                  draftVariable.getName(),
                  DraftTaskVariableEntity.createFrom(
                      task,
                      draftVariable.getName(),
                      draftVariable.getValue(),
                      variableSizeThreshold));
            }
          });

      draftVariableStore.createOrUpdate(toPersist.values());
    } catch (NotFoundException e) {
      throw new NotFoundApiException("Task not found", e);
    }
  }

  private void validateVariableInputs(Collection<VariableInputDTO> variable) {
    variable.stream()
        .map(VariableInputDTO::getValue)
        .forEach(
            value -> {
              try {
                objectMapper.readValue(value, Object.class);
              } catch (IOException e) {
                throw new InvalidRequestException(e.getMessage(), e);
              }
            });
  }

  public void persistTaskVariables(
      String taskId, List<VariableInputDTO> changedVariables, boolean withDraftVariableValues) {
    // take current runtime variables values and
    final TaskEntity task = taskStore.getTask(taskId);
    final List<VariableEntity> taskVariables =
        getRuntimeVariablesByRequest(GetVariablesRequest.createFrom(task));

    final Map<String, TaskVariableEntity> finalVariablesMap = new HashMap<>();
    taskVariables.forEach(
        variable ->
            finalVariablesMap.put(
                variable.getName(), TaskVariableEntity.createFrom(taskId, variable)));

    if (withDraftVariableValues) {
      // update/append with draft variables
      draftVariableStore
          .getVariablesByTaskIdAndVariableNames(taskId, Collections.emptyList())
          .forEach(
              draftTaskVariable ->
                  finalVariablesMap.put(
                      draftTaskVariable.getName(),
                      TaskVariableEntity.createFrom(taskId, draftTaskVariable)));
    }

    // update/append with variables passed for task completion
    for (VariableInputDTO var : changedVariables) {
      finalVariablesMap.put(
          var.getName(),
          TaskVariableEntity.createFrom(
              task.getTenantId(),
              taskId,
              var.getName(),
              var.getValue(),
              tasklistProperties.getImporter().getVariableSizeThreshold()));
    }
    variableStore.persistTaskVariables(finalVariablesMap.values());
  }

  /** Deletes all draft variables associated with the task by {@code taskId}. */
  public void deleteDraftTaskVariables(String taskId) {
    draftVariableStore.deleteAllByTaskId(taskId);
  }

  private List<VariableEntity> getRuntimeVariablesByRequest(
      GetVariablesRequest getVariablesRequest) {
    final List<GetVariablesRequest> requests = Collections.singletonList(getVariablesRequest);
    final Map<String, List<VariableEntity>> runtimeVariablesPerTaskId =
        getRuntimeVariablesPerTaskId(requests);
    if (runtimeVariablesPerTaskId.size() > 0) {
      return runtimeVariablesPerTaskId.values().iterator().next();
    } else {
      return new ArrayList<>();
    }
  }

  private List<VariableEntity> getRuntimeVariablesDTOPerTaskId(
      final List<GetVariablesRequest> requests) {
    final Map<String, List<VariableEntity>> variablesByTaskIds =
        getRuntimeVariablesPerTaskId(requests);
    if (variablesByTaskIds.size() > 0) {
      return variablesByTaskIds.values().iterator().next();
    } else {
      return new ArrayList<>();
    }
  }

  private Map<String, List<VariableEntity>> getRuntimeVariablesPerTaskId(
      List<GetVariablesRequest> requests) {

    if (requests == null || requests.size() == 0) {
      return new HashMap<>();
    }

    // build flow node trees (for each process instance)
    final Map<String, FlowNodeTree> flowNodeTrees = buildFlowNodeTrees(requests);

    // build local variable map  (for each flow node instance)
    final List<String> flowNodeInstanceIds =
        flowNodeTrees.values().stream()
            .flatMap(f -> f.getFlowNodeInstanceIds().stream())
            .collect(Collectors.toList());
    final Map<String, VariableMap> variableMaps =
        buildVariableMaps(
            flowNodeInstanceIds,
            requests.stream()
                .map(GetVariablesRequest::getVarNames)
                .flatMap(x -> x == null ? null : x.stream())
                .collect(toList()),
            requests
                .get(0)
                .getFieldNames()); // we assume here that all requests has the same list of  fields

    return buildResponse(flowNodeTrees, variableMaps, requests);
  }

  /**
   * Builds lists of variables taking into account nested scopes.
   *
   * @param flowNodeTrees
   * @param variableMaps
   * @param requests
   * @return list of variables per each taskId
   */
  private Map<String, List<VariableEntity>> buildResponse(
      final Map<String, FlowNodeTree> flowNodeTrees,
      final Map<String, VariableMap> variableMaps,
      final List<GetVariablesRequest> requests) {

    final Map<String, List<VariableEntity>> response = new HashMap<>();

    for (GetVariablesRequest req : requests) {
      final FlowNodeTree flowNodeTree = flowNodeTrees.get(req.getProcessInstanceId());

      final VariableMap resultingVariableMap = new VariableMap();

      accumulateVariables(
          resultingVariableMap, variableMaps, flowNodeTree, req.getFlowNodeInstanceId());

      response.put(
          req.getTaskId(),
          resultingVariableMap.entrySet().stream()
              .sorted(Entry.comparingByKey())
              .map(e -> e.getValue())
              .collect(Collectors.toList()));
    }
    return response;
  }

  @NotNull
  private Collector<Entry<String, VariableEntity>, ArrayList<VariableDTO>, ArrayList<VariableDTO>>
      getVariableDTOListCollector() {
    return Collector.of(
        ArrayList::new,
        (list, entry) -> list.add(VariableDTO.createFrom(entry.getValue())),
        (list1, list2) -> {
          list1.addAll(list2);
          return list1;
        });
  }

  private void accumulateVariables(
      VariableMap resultingVariableMap,
      final Map<String, VariableMap> variableMaps,
      final FlowNodeTree flowNodeTree,
      final String flowNodeInstanceId) {
    final VariableMap m = variableMaps.get(flowNodeInstanceId);
    if (m != null) {
      resultingVariableMap.putAll(m);
    }
    final String parentFlowNodeId =
        flowNodeTree != null ? flowNodeTree.getParent(flowNodeInstanceId) : null;
    if (parentFlowNodeId != null && !parentFlowNodeId.equals(ABSENT_PARENT_ID)) {
      accumulateVariables(resultingVariableMap, variableMaps, flowNodeTree, parentFlowNodeId);
    }
  }

  /**
   * Builds variable map for each flow node instance id: "local" variables for each flow node
   * instance.
   *
   * @param flowNodeInstanceIds
   * @return
   */
  private Map<String, VariableMap> buildVariableMaps(
      List<String> flowNodeInstanceIds, List<String> varNames, Set<String> fieldNames) {
    // get list of all variables
    final List<VariableEntity> variables =
        variableStore.getVariablesByFlowNodeInstanceIds(flowNodeInstanceIds, varNames, fieldNames);

    return variables.stream()
        .collect(groupingBy(VariableEntity::getScopeFlowNodeId, getVariableMapCollector()));
  }

  @NotNull
  private Collector<VariableEntity, VariableMap, VariableMap> getVariableMapCollector() {
    return Collector.of(
        VariableMap::new,
        (map, var) -> map.put(var.getName(), var),
        (map1, map2) -> {
          map1.putAll(map2);
          return map1;
        });
  }

  /**
   * Builds flow node tree for each requested process instance id.
   *
   * @param requests
   * @return map of flow node trees per process instance id
   */
  private Map<String, FlowNodeTree> buildFlowNodeTrees(List<GetVariablesRequest> requests) {
    final List<String> processInstanceIds =
        CollectionUtil.map(requests, GetVariablesRequest::getProcessInstanceId);
    // get all flow node instances for all process instance ids
    final List<FlowNodeInstanceEntity> flowNodeInstances =
        variableStore.getFlowNodeInstances(processInstanceIds);

    final Map<String, FlowNodeTree> flowNodeTrees = new HashMap<>();
    for (FlowNodeInstanceEntity flowNodeInstance : flowNodeInstances) {
      getFlowNodeTree(flowNodeTrees, flowNodeInstance.getProcessInstanceId())
          .setParent(flowNodeInstance.getId(), flowNodeInstance.getParentFlowNodeId());
    }
    return flowNodeTrees;
  }

  private FlowNodeTree getFlowNodeTree(
      Map<String, FlowNodeTree> flowNodeTrees, String processInstanceId) {
    if (flowNodeTrees.get(processInstanceId) == null) {
      flowNodeTrees.put(processInstanceId, new FlowNodeTree());
    }
    return flowNodeTrees.get(processInstanceId);
  }

  public List<VariableSearchResponse> getVariableSearchResponses(
      String taskId, List<String> variableNames) {

    final TaskEntity task = taskStore.getTask(taskId);
    final List<GetVariablesRequest> requests =
        Collections.singletonList(
            VariableStore.GetVariablesRequest.createFrom(task)
                .setVarNames(variableNames)
                .setFieldNames(Collections.emptySet()));

    final List<VariableSearchResponse> vars = new ArrayList<>();
    switch (task.getState()) {
      case CREATED -> {
        final Map<String, VariableEntity> nameToOriginalVariables = new HashMap<>();
        getRuntimeVariablesDTOPerTaskId(requests)
            .forEach(
                originalVar -> nameToOriginalVariables.put(originalVar.getName(), originalVar));
        final Map<String, DraftTaskVariableEntity> nameToDraftVariable = new HashMap<>();
        draftVariableStore
            .getVariablesByTaskIdAndVariableNames(taskId, variableNames)
            .forEach(draftVar -> nameToDraftVariable.put(draftVar.getName(), draftVar));

        nameToOriginalVariables.forEach(
            (name, originalVar) -> {
              if (nameToDraftVariable.containsKey(name)) {
                vars.add(
                    VariableSearchResponse.createFrom(originalVar, nameToDraftVariable.get(name)));
              } else {
                vars.add(VariableSearchResponse.createFrom(originalVar));
              }
            });

        // creating variable responses for draft variables without original values
        CollectionUtils.removeAll(nameToDraftVariable.keySet(), nameToOriginalVariables.keySet())
            .forEach(
                draftVariableName ->
                    vars.add(
                        VariableSearchResponse.createFrom(
                            nameToDraftVariable.get(draftVariableName))));
      }
      case COMPLETED -> {
        final Map<String, List<TaskVariableEntity>> variablesByTaskIds =
            variableStore.getTaskVariablesPerTaskId(requests);
        if (variablesByTaskIds.size() > 0) {
          vars.addAll(
              variablesByTaskIds.values().iterator().next().stream()
                  .map(VariableSearchResponse::createFrom)
                  .toList());
        }
      }
      default -> {}
    }

    return vars.stream().sorted(Comparator.comparing(VariableSearchResponse::getName)).toList();
  }

  public List<VariableDTO> getVariables(
      String taskId, List<String> variableNames, final Set<String> fieldNames) {
    final TaskEntity task = taskStore.getTask(taskId);
    final List<GetVariablesRequest> requests =
        Collections.singletonList(
            GetVariablesRequest.createFrom(task)
                .setVarNames(variableNames)
                .setFieldNames(fieldNames));

    final List<VariableDTO> vars = new ArrayList<>();
    switch (task.getState()) {
      case CREATED -> vars.addAll(
          VariableDTO.createFrom(getRuntimeVariablesDTOPerTaskId(requests)));
      case COMPLETED -> {
        final Map<String, List<TaskVariableEntity>> variablesByTaskIds =
            variableStore.getTaskVariablesPerTaskId(requests);
        if (variablesByTaskIds.size() > 0) {
          vars.addAll(
              variablesByTaskIds.values().iterator().next().stream()
                  .map(VariableDTO::createFrom)
                  .toList());
        }
      }
      default -> {}
    }

    vars.sort(Comparator.comparing(VariableDTO::getName));
    return vars;
  }

  public List<List<VariableDTO>> getVariables(List<GetVariablesRequest> requests) {
    final Map<TaskState, List<GetVariablesRequest>> groupByStates =
        requests.stream().collect(groupingBy(GetVariablesRequest::getState));

    final List<List<VariableDTO>> response = new ArrayList<>();

    final Map<String, List<VariableEntity>> varsForActive =
        getRuntimeVariablesPerTaskId(groupByStates.get(TaskState.CREATED));

    final Map<String, List<TaskVariableEntity>> varsForCompleted =
        variableStore.getTaskVariablesPerTaskId(groupByStates.get(TaskState.COMPLETED));

    for (GetVariablesRequest req : requests) {
      final List<VariableDTO> vars = new ArrayList<>();
      switch (req.getState()) {
        case CREATED:
          vars.addAll(
              VariableDTO.createFrom(
                  varsForActive.getOrDefault(req.getTaskId(), new ArrayList<>())));
          break;
        case COMPLETED:
          vars.addAll(
              varsForCompleted.getOrDefault(req.getTaskId(), new ArrayList<>()).stream()
                  .map(VariableDTO::createFrom)
                  .toList());
          break;
        default:
          break;
      }
      vars.sort(Comparator.comparing(VariableDTO::getName));
      response.add(vars);
    }
    return response;
  }

  public VariableDTO getVariable(final String variableId, final Set<String> fieldNames) {
    try {
      // 1st search in runtime variables
      final VariableEntity runtimeVariable =
          variableStore.getRuntimeVariable(variableId, fieldNames);
      return VariableDTO.createFrom(runtimeVariable);
    } catch (NotFoundException ex) {
      // then in task variables (for completed tasks)
      try {
        // 2nd search in runtime variables
        final TaskVariableEntity taskVariable =
            variableStore.getTaskVariable(variableId, fieldNames);
        return VariableDTO.createFrom(taskVariable);
      } catch (NotFoundException ex2) {
        throw new NotFoundApiException(String.format("Variable with id %s not found.", variableId));
      }
    }
  }

  public VariableResponse getVariableResponse(final String variableId) {
    try {
      // 1st search in runtime variables
      final VariableEntity runtimeVariable =
          variableStore.getRuntimeVariable(variableId, Collections.emptySet());
      final VariableResponse variableResponse = VariableResponse.createFrom(runtimeVariable);
      draftVariableStore.getById(variableId).ifPresent(variableResponse::addDraft);
      return variableResponse;
    } catch (NotFoundException ex) {
      // 2nd then search in draft task variables
      return draftVariableStore
          .getById(variableId)
          .map(VariableResponse::createFrom)
          .orElseGet(
              () -> {
                try {
                  // 3rd search in task variables (for completed tasks)
                  final TaskVariableEntity taskVariable =
                      variableStore.getTaskVariable(variableId, Collections.emptySet());
                  return VariableResponse.createFrom(taskVariable);
                } catch (NotFoundException ex2) {
                  throw new NotFoundApiException(
                      String.format("Variable with id %s not found.", variableId));
                }
              });
    }
  }
}
