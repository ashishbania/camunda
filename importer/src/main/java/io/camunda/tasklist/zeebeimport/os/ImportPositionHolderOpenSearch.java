/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.os;

import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.meta.ImportPositionEntity;
import io.camunda.tasklist.schema.indices.ImportPositionIndex;
import io.camunda.tasklist.util.Either;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.zeebeimport.ImportPositionHolder;
import io.camunda.tasklist.zeebeimport.ImportPositionHolderAbstract;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn("schemaStartup")
@Conditional(OpenSearchCondition.class)
public class ImportPositionHolderOpenSearch extends ImportPositionHolderAbstract
    implements ImportPositionHolder {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ImportPositionHolderOpenSearch.class);

  // this is the in-memory only storage

  @Qualifier("zeebeOsClient")
  @Autowired
  private OpenSearchClient osClient;

  public ImportPositionEntity getLatestLoadedPosition(String aliasTemplate, int partitionId)
      throws IOException {
    final Query query =
        OpenSearchUtil.joinWithAnd(
            new Query.Builder()
                .term(
                    t ->
                        t.field(ImportPositionIndex.ALIAS_NAME)
                            .value(FieldValue.of(aliasTemplate))),
            new Query.Builder()
                .term(
                    t ->
                        t.field(ImportPositionIndex.PARTITION_ID)
                            .value(FieldValue.of(partitionId))));

    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .query(query)
            .size(10)
            .index(List.of(importPositionType.getAlias()))
            .build();

    final SearchResponse<ImportPositionEntity> searchResponse =
        osClient.search(searchRequest, ImportPositionEntity.class);

    final List<Hit<ImportPositionEntity>> hits = searchResponse.hits().hits();

    ImportPositionEntity position =
        new ImportPositionEntity().setAliasName(aliasTemplate).setPartitionId(partitionId);

    for (Hit<ImportPositionEntity> hit : hits) {
      position = hit.source();
    }

    LOGGER.debug(
        "Latest loaded position for alias [{}] and partitionId [{}]: {}",
        aliasTemplate,
        partitionId,
        position);

    return position;
  }

  public Either<Throwable, Boolean> updateImportPositions(
      final Map<String, ImportPositionEntity> positions) {
    final var preparedBulkRequest = prepareBulkRequest(positions);

    if (preparedBulkRequest.isLeft()) {
      final var e = preparedBulkRequest.getLeft();
      return Either.left(e);
    }

    try {
      final var bulkRequest = preparedBulkRequest.get();

      withImportPositionTimer(
          () -> {
            OpenSearchUtil.processBulkRequest(osClient, bulkRequest);
            return null;
          });

      return Either.right(true);
    } catch (final Throwable e) {
      LOGGER.error("Error occurred while persisting latest loaded position", e);
      return Either.left(e);
    }
  }

  private Either<Exception, BulkRequest> prepareBulkRequest(
      final Map<String, ImportPositionEntity> positions) {
    final var bulkRequest = new BulkRequest.Builder();
    final ArrayList<BulkOperation> ops = new ArrayList<>();

    if (positions.size() > 0) {
      final var preparedUpdateRequests =
          positions.values().stream()
              .map(this::prepareUpdateRequest)
              .collect(Either.collectorFoldingLeft());

      if (preparedUpdateRequests.isLeft()) {
        final var e = preparedUpdateRequests.getLeft();
        return Either.left(e);
      }

      preparedUpdateRequests
          .get()
          .forEach(
              p -> {
                ops.add(
                    new BulkOperation.Builder()
                        .index(
                            IndexOperation.of(
                                io ->
                                    io.index(importPositionType.getFullQualifiedName())
                                        .document(p.doc())
                                        .id(p.id())))
                        .build());
              });
    }

    return Either.right(bulkRequest.operations(ops).build());
  }

  private Either<Exception, UpdateRequest> prepareUpdateRequest(
      final ImportPositionEntity position) {
    try {
      final var index = importPositionType.getFullQualifiedName();
      final var source = objectMapper.writeValueAsString(position);
      final var updateFields = new HashMap<String, Object>();

      updateFields.put(ImportPositionIndex.POSITION, position.getPosition());
      updateFields.put(ImportPositionIndex.FIELD_INDEX_NAME, position.getIndexName());
      updateFields.put(ImportPositionIndex.SEQUENCE, position.getSequence());

      final UpdateRequest updateRequest =
          new UpdateRequest.Builder()
              .index(index)
              .id(position.getId())
              .upsert(CommonUtils.getJsonObjectFromEntity(updateFields))
              .doc(CommonUtils.getJsonObjectFromEntity(position))
              .docAsUpsert(true)
              .build();

      return Either.right(updateRequest);

    } catch (final Exception e) {
      LOGGER.error(
          String.format(
              "Error occurred while preparing request to update processed position for %s",
              position.getAliasName()),
          e);
      return Either.left(e);
    }
  }
}
