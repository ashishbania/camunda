/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch;

import static io.camunda.operate.store.opensearch.client.sync.OpenSearchRetryOperation.UPDATE_RETRY_COUNT;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.script;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.stringTerms;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.ExceptionHelper.withPersistenceException;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.OperationStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchOperationStore implements OperationStore {
  private static final Logger logger = LoggerFactory.getLogger(OpensearchOperationStore.class);
  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private OperationTemplate operationTemplate;

  @Autowired private BatchOperationTemplate batchOperationTemplate;

  @Autowired private BeanFactory beanFactory;

  @Override
  public Map<String, String> getIndexNameForAliasAndIds(String alias, Collection<String> ids) {
    return richOpenSearchClient.doc().getIndexNames(alias, ids);
  }

  @Override
  public List<OperationEntity> getOperationsFor(
      Long zeebeCommandKey,
      Long processInstanceKey,
      Long incidentKey,
      OperationType operationType) {
    if (processInstanceKey == null && zeebeCommandKey == null) {
      throw new OperateRuntimeException(
          "Wrong call to search for operation. Not enough parameters.");
    }

    final Query zeebeCommandKeyQ =
        zeebeCommandKey != null ? term(OperationTemplate.ZEEBE_COMMAND_KEY, zeebeCommandKey) : null;
    final Query processInstanceKeyQ =
        processInstanceKey != null
            ? term(OperationTemplate.PROCESS_INSTANCE_KEY, processInstanceKey)
            : null;
    final Query incidentKeyQ =
        incidentKey != null ? term(OperationTemplate.INCIDENT_KEY, incidentKey) : null;
    final Query operationTypeQ =
        operationType != null ? term(OperationTemplate.TYPE, operationType.name()) : null;
    final Query query =
        and(
            zeebeCommandKeyQ,
            processInstanceKeyQ,
            incidentKeyQ,
            operationTypeQ,
            stringTerms(
                OperationTemplate.STATE,
                List.of(OperationState.SENT.name(), OperationState.LOCKED.name())));
    var searchRequestBuilder =
        searchRequestBuilder(operationTemplate.getAlias()).query(query).size(1);

    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, OperationEntity.class);
  }

  @Override
  public String add(BatchOperationEntity batchOperationEntity) throws PersistenceException {
    var indexRequestBuilder =
        RequestDSL.<BatchOperationEntity>indexRequestBuilder(
                batchOperationTemplate.getFullQualifiedName())
            .id(batchOperationEntity.getId())
            .document(batchOperationEntity);

    withPersistenceException(() -> richOpenSearchClient.doc().index(indexRequestBuilder));

    return batchOperationEntity.getId();
  }

  @Override
  public void update(OperationEntity operation, boolean refreshImmediately)
      throws PersistenceException {
    final Function<Exception, String> errorMessageSupplier =
        e ->
            String.format(
                "Error preparing the query to update operation [%s] for process instance id [%s]",
                operation.getId(), operation.getProcessInstanceKey());
    var updateRequestBuilder =
        RequestDSL.<OperationEntity, Void>updateRequestBuilder(
                operationTemplate.getFullQualifiedName())
            .id(operation.getId())
            .doc(operation)
            .retryOnConflict(UPDATE_RETRY_COUNT);

    if (refreshImmediately) {
      updateRequestBuilder.refresh(Refresh.True);
    }

    withPersistenceException(
        () -> richOpenSearchClient.doc().update(updateRequestBuilder, errorMessageSupplier));
  }

  @Override
  public void updateWithScript(
      String index, String id, String script, Map<String, Object> parameters) {
    final Function<Exception, String> errorMessageSupplier =
        e ->
            String.format(
                "Exception occurred, while executing update request with script for operation [%s]",
                id);
    var updateRequestBuilder =
        RequestDSL.<Void, Void>updateRequestBuilder(index)
            .id(id)
            .script(script(script, parameters))
            .retryOnConflict(UPDATE_RETRY_COUNT);

    richOpenSearchClient.doc().update(updateRequestBuilder, errorMessageSupplier);
  }

  @Override
  public BatchRequest newBatchRequest() {
    return beanFactory.getBean(BatchRequest.class);
  }
}
