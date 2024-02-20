/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.j5templates;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.property.OperateElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.util.TestUtil;
import java.io.IOException;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchContainerManager extends SearchContainerManager {

  protected static final Logger logger =
      LoggerFactory.getLogger(ElasticsearchContainerManager.class);

  private static final String OPEN_SCROLL_CONTEXT_FIELD = "open_contexts";
  // Path to find search statistics for all indexes
  private static final String PATH_SEARCH_STATISTICS =
      "/_nodes/stats/indices/search?filter_path=nodes.*.indices.search";

  protected final RestHighLevelClient esClient;

  public ElasticsearchContainerManager(
      @Qualifier("esClient") RestHighLevelClient esClient,
      OperateProperties operateProperties,
      SchemaManager schemaManager) {
    super(operateProperties, schemaManager);
    this.esClient = esClient;
  }

  @Override
  protected void updatePropertiesIndexPrefix() {
    operateProperties.getElasticsearch().setIndexPrefix(indexPrefix);
  }

  @Override
  protected boolean shouldCreateSchema() {
    return operateProperties.getElasticsearch().isCreateSchema();
  }

  protected boolean areIndicesCreated(String indexPrefix, int minCountOfIndices)
      throws IOException {
    GetIndexResponse response =
        esClient
            .indices()
            .get(
                new GetIndexRequest(indexPrefix + "*")
                    .indicesOptions(IndicesOptions.fromOptions(true, false, true, false)),
                RequestOptions.DEFAULT);
    String[] indices = response.getIndices();
    return indices != null && indices.length >= minCountOfIndices;
  }

  public void stopContainer() {
    // TestUtil.removeIlmPolicy(esClient);
    String indexPrefix = operateProperties.getElasticsearch().getIndexPrefix();
    TestUtil.removeAllIndices(esClient, indexPrefix);
    operateProperties
        .getElasticsearch()
        .setIndexPrefix(OperateElasticsearchProperties.DEFAULT_INDEX_PREFIX);

    assertThat(getIntValueForJSON(PATH_SEARCH_STATISTICS, OPEN_SCROLL_CONTEXT_FIELD, 0))
        .describedAs("There are too many open scroll contexts left.")
        .isLessThanOrEqualTo(15);
  }

  private int getIntValueForJSON(
      final String path, final String fieldname, final int defaultValue) {
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      Response response = esClient.getLowLevelClient().performRequest(new Request("GET", path));
      JsonNode jsonNode = objectMapper.readTree(response.getEntity().getContent());
      JsonNode field = jsonNode.findValue(fieldname);
      if (field != null) {
        return field.asInt(defaultValue);
      }
    } catch (Exception e) {
      logger.error("Couldn't retrieve json object from elasticsearch. Return Optional.Empty.", e);
    }

    return defaultValue;
  }
}
