/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.v1_0.processors;

import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import javax.xml.parsers.SAXParserFactory;
import static org.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.camunda.operate.entities.ProcessEntity;
import org.camunda.operate.schema.indices.ProcessIndex;
import org.camunda.operate.schema.templates.ListViewTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.zeebeimport.ElasticsearchManager;
import org.camunda.operate.zeebeimport.util.XMLUtil;
import org.camunda.operate.zeebeimport.v1_0.record.value.deployment.DeployedProcessImpl;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource;

@Component
public class ProcessZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(ProcessZeebeRecordProcessor.class);

  private static final Charset CHARSET = StandardCharsets.UTF_8;

  private final static Set<String> STATES = new HashSet<>();
  static {
    STATES.add(ProcessIntent.CREATED.name());
  }

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private ElasticsearchManager elasticsearchManager;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ProcessIndex processIndex;

  @Autowired
  private SAXParserFactory saxParserFactory;

  @Autowired
  private XMLUtil xmlUtil;

  public void processDeploymentRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    final String intentStr = record.getIntent().name();

    if (STATES.contains(intentStr)) {
      DeployedProcessImpl recordValue = (DeployedProcessImpl)record.getValue();
      persistProcess(recordValue, bulkRequest);
    }

  }

  private void persistProcess(Process process, BulkRequest bulkRequest) throws PersistenceException {
    String resourceName = process.getResourceName();

    final ProcessEntity processEntity = createEntity(process);
    logger.debug("Process: key {}, bpmnProcessId {}", processEntity.getKey(), processEntity.getBpmnProcessId());

    try {
      updateFieldsInInstancesFor(processEntity, bulkRequest);

      bulkRequest.add(new IndexRequest(processIndex.getFullQualifiedName()).id(ConversionUtils.toStringOrNull(processEntity.getKey()))
          .source(objectMapper.writeValueAsString(processEntity), XContentType.JSON)
      );
    } catch (JsonProcessingException e) {
      logger.error("Error preparing the query to insert process", e);
      throw new PersistenceException(String.format("Error preparing the query to insert process [%s]", processEntity.getKey()), e);
    }
  }

  private void updateFieldsInInstancesFor(final ProcessEntity processEntity, BulkRequest bulkRequest) {
    List<Long> processInstanceKeys = elasticsearchManager.queryProcessInstancesWithEmptyProcessVersion(processEntity.getKey());
    for (Long processInstanceKey : processInstanceKeys) {
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.PROCESS_NAME, processEntity.getName());
      updateFields.put(ListViewTemplate.PROCESS_VERSION, processEntity.getVersion());
      UpdateRequest updateRequest = new UpdateRequest().index(listViewTemplate.getFullQualifiedName()).id(processInstanceKey.toString())
          .doc(updateFields)
          .retryOnConflict(UPDATE_RETRY_COUNT);
      bulkRequest.add(updateRequest);
    }
  }

  private ProcessEntity createEntity(Process process) {
    ProcessEntity processEntity = new ProcessEntity();

    processEntity.setId(String.valueOf(process.getProcessDefinitionKey()));
    processEntity.setKey(process.getProcessDefinitionKey());
    processEntity.setBpmnProcessId(process.getBpmnProcessId());
    processEntity.setVersion(process.getVersion());

    byte[] byteArray = process.getResource();

    String bpmn = new String(byteArray, CHARSET);
    processEntity.setBpmnXml(bpmn);

    String resourceName = process.getResourceName();
    processEntity.setResourceName(resourceName);

    final Optional<ProcessEntity> diagramData = xmlUtil.extractDiagramData(byteArray);
    if (diagramData.isPresent()) {
      processEntity.setName(diagramData.get().getName());
    }

    return processEntity;
  }

  private Map<String, DeploymentResource> resourceToMap(List<DeploymentResource> resources) {
    return resources.stream().collect(Collectors.toMap(DeploymentResource::getResourceName, Function.identity()));
  }

}
