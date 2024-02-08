/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.zeebeimport.util.XMLUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static io.camunda.operate.schema.indices.ProcessIndex.BPMN_PROCESS_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProcessDefinitionDaoIT extends OperateSearchAbstractIT {

  @Autowired
  private ProcessDefinitionDao dao;

  @Autowired
  private ProcessIndex processIndex;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    List<ProcessDefinition> definitionData = new LinkedList<>();

    Map<String, String> xmlSourceMap = new HashMap<>();
    xmlSourceMap.put("demoProcess", "demoProcess_v_1.bpmn");
    xmlSourceMap.put("errorProcess", "errorProcess.bpmn");
    xmlSourceMap.put("complexProcess", "complexProcess_v_3.bpmn");

    definitionData.add(new ProcessDefinition().setKey(2251799813685249L).setTenantId(DEFAULT_TENANT_ID).setName("Demo process").setVersion(1)
        .setBpmnProcessId("demoProcess"));
    definitionData.add(new ProcessDefinition().setKey(2251799813685251L).setTenantId(DEFAULT_TENANT_ID).setName("Error process").setVersion(1)
        .setBpmnProcessId("errorProcess"));
    definitionData.add(new ProcessDefinition().setKey(2251799813685253L).setTenantId(DEFAULT_TENANT_ID).setName("Complex process").setVersion(1)
        .setBpmnProcessId("complexProcess"));

    for (ProcessDefinition data : definitionData) {
      Map<String, Object> entityMap = objectMapper.convertValue(data, new TypeReference<>() {
      });
      // XML source is not part of the model object and must be added manually in the entity map before writing
      String resourceXml = testResourceManager.readResourceFileContentsAsString(xmlSourceMap.get(data.getBpmnProcessId()));
      entityMap.put("bpmnXml", resourceXml);
      testSearchRepository.createOrUpdateDocument(processIndex.getFullQualifiedName(), entityMap);
    }

    searchContainerManager.refreshIndices("*operate-process*");
  }

  @Test
  public void shouldReturnProcessDefinitions() {
    Results<ProcessDefinition> processDefinitionResults = dao.search(new Query<>());

    assertThat(processDefinitionResults.getTotal()).isEqualTo(3);
    assertThat(processDefinitionResults.getItems()).extracting(BPMN_PROCESS_ID)
        .containsExactlyInAnyOrder("demoProcess", "errorProcess", "complexProcess");
  }

  @Test
  public void shouldReturnWhenByKey() {
    ProcessDefinition processDefinition = dao.byKey(2251799813685249L);

    assertThat(processDefinition.getBpmnProcessId()).isEqualTo("demoProcess");
    assertThat(processDefinition.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldThrowWhenKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byKey(1L));
  }

  @Test
  public void shouldReturnWhenXmlByKey() {
    String processDefinitionAsXml = dao.xmlByKey(2251799813685249L);

    assertThat(processDefinitionAsXml).contains("demoProcess");

    // Verify the returned string is xml
    try {
      final InputStream xmlInputStream = new ByteArrayInputStream(
          processDefinitionAsXml.getBytes(StandardCharsets.UTF_8));
      new XMLUtil().getSAXParserFactory().newSAXParser()
          .parse(xmlInputStream, new DefaultHandler());
    } catch (SAXException | IOException | ParserConfigurationException e) {
      fail(String.format("String '%s' should be of type xml", processDefinitionAsXml), e);
    }
  }

  @Test
  public void showThrowWhenXmlByKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.xmlByKey(1L));
  }

  @Test
  public void shouldFilterProcessDefinitions() {
    Results<ProcessDefinition> processDefinitionResults = dao.search(new Query<ProcessDefinition>()
        .setFilter(new ProcessDefinition().setBpmnProcessId("demoProcess")));

    assertThat(processDefinitionResults.getItems().get(0).getBpmnProcessId()).isEqualTo("demoProcess");
  }

  @Test
  public void shouldSortProcessDefinitionsDesc() {
    Results<ProcessDefinition> processDefinitionResults = dao.search(new Query<ProcessDefinition>()
        .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.DESC)));

    assertThat(processDefinitionResults.getTotal()).isEqualTo(3);
    assertThat(processDefinitionResults.getItems()).extracting(BPMN_PROCESS_ID)
        .containsExactly("errorProcess", "demoProcess", "complexProcess");
  }

  @Test
  public void shouldSortProcessDefinitionsAsc() {
    Results<ProcessDefinition> processDefinitionResults = dao.search(new Query<ProcessDefinition>()
        .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.ASC)));

    assertThat(processDefinitionResults.getTotal()).isEqualTo(3);
    assertThat(processDefinitionResults.getItems()).extracting(BPMN_PROCESS_ID)
        .containsExactly("complexProcess", "demoProcess", "errorProcess");
  }

  @Test
  public void shouldPageProcessDefinitions() {
    Results<ProcessDefinition> processDefinitionResults = dao.search(new Query<ProcessDefinition>()
        .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.DESC))
        .setSize(2));

    assertThat(processDefinitionResults.getTotal()).isEqualTo(3);
    assertThat(processDefinitionResults.getItems()).hasSize(2);

    assertThat(processDefinitionResults.getItems()).extracting(BPMN_PROCESS_ID)
        .containsExactly("errorProcess", "demoProcess");

    Object[] searchAfter = processDefinitionResults.getSortValues();
    assertThat(processDefinitionResults.getItems().get(1).getBpmnProcessId()).isEqualTo(searchAfter[0].toString());

    processDefinitionResults = dao.search(new Query<ProcessDefinition>()
        .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.DESC))
        .setSize(2).setSearchAfter(searchAfter));

    assertThat(processDefinitionResults.getTotal()).isEqualTo(3);
    assertThat(processDefinitionResults.getItems()).hasSize(1);

    assertThat(processDefinitionResults.getItems().get(0).getBpmnProcessId()).isEqualTo("complexProcess");
  }
}