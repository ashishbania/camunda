/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch;

import static io.camunda.operate.schema.templates.ListViewTemplate.*;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.constantScore;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.exists;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.json;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.match;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.matchAll;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.matchNone;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.not;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.or;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.stringTerms;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.wildcardQuery;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.opensearch.dsl.QueryDSL;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.VariablesQueryDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import org.apache.commons.lang3.ArrayUtils;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Conditional(OpensearchCondition.class)
@Component
public class OpenSearchQueryHelper {
  private static final String WILD_CARD = "*";

  @Autowired private OperateProperties operateProperties;

  @Autowired private DateTimeFormatter dateTimeFormatter;

  @Autowired(required = false)
  private PermissionsService permissionsService;

  public Query createProcessInstancesQuery(ListViewQueryDto query) {
    return constantScore(
        and(term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION), createQueryFragment(query)));
  }

  public Query createQueryFragment(ListViewQueryDto query) {
    return createQueryFragment(query, RequestDSL.QueryType.ALL);
  }

  public Query createQueryFragment(ListViewQueryDto query, RequestDSL.QueryType queryType) {
    return and(
        runningFinishedQuery(query, queryType),
        createRetriesLeftQuery(query),
        activityIdQuery(query, queryType),
        idsQuery(query),
        errorMessageQuery(query),
        dateRangeQuery(
            ListViewTemplate.START_DATE, query.getStartDateAfter(), query.getStartDateBefore()),
        dateRangeQuery(END_DATE, query.getEndDateAfter(), query.getEndDateBefore()),
        processDefinitionKeysQuery(query),
        bpmnProcessIdQuery(query),
        excludeIdsQuery(query),
        variablesQuery(query),
        batchOperationIdQuery(query),
        parentInstanceIdQuery(query),
        tenantIdQuery(query),
        readPermissionQuery()
        // TODO filter by tenants assigned to current user #4858
        );
  }

  private Query runningFinishedQuery(ListViewQueryDto query, RequestDSL.QueryType queryType) {
    boolean active = query.isActive();
    boolean incidents = query.isIncidents();
    boolean running = query.isRunning();

    boolean completed = query.isCompleted();
    boolean canceled = query.isCanceled();
    boolean finished = query.isFinished();

    if (!running && !finished) {
      // empty list should be returned
      return matchNone();
    }

    if (running && finished && active && incidents && completed && canceled) {
      // select all
      return null;
    }

    Query runningQuery = null;

    if (running && (active || incidents)) {
      // running query
      runningQuery = not(exists(END_DATE));

      Query activeQuery = query.isActive() ? term(INCIDENT, false) : null;
      Query incidentsQuery = query.isIncidents() ? term(INCIDENT, true) : null;

      if (query.getActivityId() == null && query.isActive() && query.isIncidents()) {
        // we request all running instances
      } else {
        // some of the queries may be null
        runningQuery = and(runningQuery, or(activeQuery, incidentsQuery));
      }
    }

    Query finishedQuery = null;

    if (finished && (completed || canceled)) {
      // add finished query
      finishedQuery = exists(END_DATE);

      Query completedQuery =
          query.isCompleted() ? term(STATE, ProcessInstanceState.COMPLETED.toString()) : null;
      Query canceledQuery =
          query.isCanceled() ? term(STATE, ProcessInstanceState.CANCELED.toString()) : null;

      if (query.getActivityId() == null && query.isCompleted() && query.isCanceled()) {
        // we request all finished instances
      } else {
        finishedQuery = and(finishedQuery, or(completedQuery, canceledQuery));
      }
    }

    final Query processInstanceQuery = or(runningQuery, finishedQuery);

    if (processInstanceQuery == null) {
      return matchNone();
    }

    return processInstanceQuery;
  }

  private Query createRetriesLeftQuery(ListViewQueryDto query) {
    if (query.isRetriesLeft()) {
      Query retriesLeftQuery = term(JOB_FAILED_WITH_RETRIES_LEFT, true);
      return QueryDSL.hasChildQuery(ACTIVITIES_JOIN_RELATION, retriesLeftQuery);
    }
    return null;
  }

  private Query activityIdQuery(String activityId, FlowNodeState state) {
    final Query query =
        and(
            term(ACTIVITY_STATE, state.name()),
            term(ACTIVITY_ID, activityId),
            state == FlowNodeState.COMPLETED
                ? term(ACTIVITY_TYPE, FlowNodeType.END_EVENT.name())
                : null);

    return QueryDSL.hasChildQuery(ACTIVITIES_JOIN_RELATION, query);
  }

  private Query activityIdIncidentQuery(String activityId) {
    final Query query =
        and(
            term(ACTIVITY_STATE, FlowNodeState.ACTIVE.name()),
            term(ACTIVITY_ID, activityId),
            exists(ERROR_MSG));

    return QueryDSL.hasChildQuery(ACTIVITIES_JOIN_RELATION, query);
  }

  private Query activityIdQuery(ListViewQueryDto query, RequestDSL.QueryType queryType) {
    if (!StringUtils.hasLength(query.getActivityId())) {
      return null;
    }

    return or(
        query.isActive() ? activityIdQuery(query.getActivityId(), FlowNodeState.ACTIVE) : null,
        query.isIncidents() ? activityIdIncidentQuery(query.getActivityId()) : null,
        query.isCompleted()
            ? activityIdQuery(query.getActivityId(), FlowNodeState.COMPLETED)
            : null,
        query.isCanceled()
            ? activityIdQuery(query.getActivityId(), FlowNodeState.TERMINATED)
            : null);
  }

  private Query idsQuery(ListViewQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getIds())) {
      return stringTerms(ListViewTemplate.ID, query.getIds());
    }
    return null;
  }

  private Query errorMessageQuery(ListViewQueryDto query) {
    String errorMessage = query.getErrorMessage();
    if (StringUtils.hasLength(errorMessage)) {
      if (errorMessage.contains(WILD_CARD)) {
        return QueryDSL.hasChildQuery(
            ACTIVITIES_JOIN_RELATION, wildcardQuery(ERROR_MSG, errorMessage.toLowerCase()));
      } else {
        return QueryDSL.hasChildQuery(
            ACTIVITIES_JOIN_RELATION, match(ERROR_MSG, errorMessage, Operator.And));
      }
    }
    return null;
  }

  private Query dateRangeQuery(String field, OffsetDateTime dateAfter, OffsetDateTime dateBefore) {
    if (dateAfter != null || dateBefore != null) {
      final RangeQuery.Builder rangeQueryBuilder = new RangeQuery.Builder().field(field);
      if (dateAfter != null) {
        rangeQueryBuilder.gte(json(dateTimeFormatter.format(dateAfter)));
      }
      if (dateBefore != null) {
        rangeQueryBuilder.lt(json(dateTimeFormatter.format(dateBefore)));
      }
      rangeQueryBuilder.format(operateProperties.getElasticsearch().getElsDateFormat());

      return rangeQueryBuilder.build()._toQuery();
    }
    return null;
  }

  private Query processDefinitionKeysQuery(ListViewQueryDto query) {
    return CollectionUtil.isNotEmpty(query.getProcessIds())
        ? stringTerms(ListViewTemplate.PROCESS_KEY, query.getProcessIds())
        : null;
  }

  private Query bpmnProcessIdQuery(ListViewQueryDto query) {
    if (!StringUtils.isEmpty(query.getBpmnProcessId())) {
      return and(
          term(ListViewTemplate.BPMN_PROCESS_ID, query.getBpmnProcessId()),
          query.getProcessVersion() != null
              ? term(ListViewTemplate.PROCESS_VERSION, query.getProcessVersion())
              : null);
    }
    return null;
  }

  private Query excludeIdsQuery(ListViewQueryDto query) {
    return CollectionUtil.isNotEmpty(query.getExcludeIds())
        ? not(stringTerms(ListViewTemplate.ID, query.getExcludeIds()))
        : null;
  }

  private Query variablesQuery(ListViewQueryDto query) {
    VariablesQueryDto variablesQuery = query.getVariable();
    // We consider the query as non-empty if it is not null and has either a value or values
    var nonEmptyQuery =
        variablesQuery != null
            && (StringUtils.hasLength(variablesQuery.getValue())
                || !ArrayUtils.isEmpty(variablesQuery.getValues()));
    if (nonEmptyQuery) {
      if (!StringUtils.hasLength(variablesQuery.getName())) {
        throw new InvalidRequestException("Variables query must provide not-null variable name.");
      }
      Query valueQuery =
          variablesQuery.getValue() != null
              ? term(VAR_VALUE, variablesQuery.getValue())
              : stringTerms(VAR_VALUE, Arrays.asList(variablesQuery.getValues()));
      return QueryDSL.hasChildQuery(
          VARIABLES_JOIN_RELATION, and(term(VAR_NAME, variablesQuery.getName()), valueQuery));
    }
    return null;
  }

  private Query batchOperationIdQuery(ListViewQueryDto query) {
    return query.getBatchOperationId() != null
        ? term(ListViewTemplate.BATCH_OPERATION_IDS, query.getBatchOperationId())
        : null;
  }

  private Query parentInstanceIdQuery(ListViewQueryDto query) {
    return query.getParentInstanceId() != null
        ? term(ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY, query.getParentInstanceId())
        : null;
  }

  private Query tenantIdQuery(ListViewQueryDto query) {
    return query.getTenantId() != null
        ? term(ListViewTemplate.TENANT_ID, query.getTenantId())
        : null;
  }

  private Query readPermissionQuery() {
    if (permissionsService == null) return null;
    var allowed = permissionsService.getProcessesWithPermission(IdentityPermission.READ);
    if (allowed == null) return null;
    return allowed.isAll()
        ? matchAll()
        : stringTerms(ListViewTemplate.BPMN_PROCESS_ID, allowed.getIds());
  }
}
