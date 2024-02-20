/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.operate.entities.dmn.DecisionType;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DecisionInstance {

  // Used for index field search and sorting
  public static final String ID = DecisionInstanceTemplate.ID,
      KEY = DecisionInstanceTemplate.KEY,
      STATE = DecisionInstanceTemplate.STATE,
      EVALUATION_DATE = DecisionInstanceTemplate.EVALUATION_DATE,
      EVALUATION_FAILURE = DecisionInstanceTemplate.EVALUATION_FAILURE,
      PROCESS_DEFINITION_KEY = DecisionInstanceTemplate.PROCESS_DEFINITION_KEY,
      PROCESS_INSTANCE_KEY = DecisionInstanceTemplate.PROCESS_INSTANCE_KEY,
      DECISION_ID = DecisionInstanceTemplate.DECISION_ID,
      TENANT_ID = DecisionInstanceTemplate.TENANT_ID,
      DECISION_DEFINITION_ID = DecisionInstanceTemplate.DECISION_DEFINITION_ID,
      DECISION_NAME = DecisionInstanceTemplate.DECISION_NAME,
      DECISION_VERSION = DecisionInstanceTemplate.DECISION_VERSION,
      DECISION_TYPE = DecisionInstanceTemplate.DECISION_TYPE;

  private String id;
  private Long key;
  private DecisionInstanceState state;
  private String evaluationDate;
  private String evaluationFailure;
  private Long processDefinitionKey;
  private Long processInstanceKey;
  private String decisionId;
  private String decisionDefinitionId;
  private String decisionName;
  private Integer decisionVersion;
  private DecisionType decisionType;
  private String result;
  private List<DecisionInstanceInput> evaluatedInputs;
  private List<DecisionInstanceOutput> evaluatedOutputs;
  private String tenantId;

  public String getId() {
    return id;
  }

  public DecisionInstance setId(String id) {
    this.id = id;
    return this;
  }

  public Long getKey() {
    return key;
  }

  public DecisionInstance setKey(long key) {
    this.key = key;
    return this;
  }

  public DecisionInstanceState getState() {
    return state;
  }

  public DecisionInstance setState(DecisionInstanceState state) {
    this.state = state;
    return this;
  }

  public String getEvaluationDate() {
    return evaluationDate;
  }

  public DecisionInstance setEvaluationDate(String evaluationDate) {
    this.evaluationDate = evaluationDate;
    return this;
  }

  public String getEvaluationFailure() {
    return evaluationFailure;
  }

  public DecisionInstance setEvaluationFailure(String evaluationFailure) {
    this.evaluationFailure = evaluationFailure;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public DecisionInstance setProcessDefinitionKey(long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public DecisionInstance setProcessInstanceKey(long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public DecisionInstance setDecisionId(String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public DecisionInstance setDecisionDefinitionId(String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
    return this;
  }

  public String getDecisionName() {
    return decisionName;
  }

  public DecisionInstance setDecisionName(String decisionName) {
    this.decisionName = decisionName;
    return this;
  }

  public Integer getDecisionVersion() {
    return decisionVersion;
  }

  public DecisionInstance setDecisionVersion(int decisionVersion) {
    this.decisionVersion = decisionVersion;
    return this;
  }

  public DecisionType getDecisionType() {
    return decisionType;
  }

  public DecisionInstance setDecisionType(DecisionType decisionType) {
    this.decisionType = decisionType;
    return this;
  }

  public String getResult() {
    return result;
  }

  public DecisionInstance setResult(String result) {
    this.result = result;
    return this;
  }

  public List<DecisionInstanceInput> getEvaluatedInputs() {
    return evaluatedInputs;
  }

  public DecisionInstance setEvaluatedInputs(List<DecisionInstanceInput> evaluatedInputs) {
    this.evaluatedInputs = evaluatedInputs;
    return this;
  }

  public List<DecisionInstanceOutput> getEvaluatedOutputs() {
    return evaluatedOutputs;
  }

  public DecisionInstance setEvaluatedOutputs(List<DecisionInstanceOutput> evaluatedOutputs) {
    this.evaluatedOutputs = evaluatedOutputs;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public DecisionInstance setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DecisionInstance that = (DecisionInstance) o;
    return Objects.equals(id, that.id)
        && Objects.equals(key, that.key)
        && state == that.state
        && Objects.equals(evaluationDate, that.evaluationDate)
        && Objects.equals(evaluationFailure, that.evaluationFailure)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(decisionId, that.decisionId)
        && Objects.equals(decisionDefinitionId, that.decisionDefinitionId)
        && Objects.equals(decisionName, that.decisionName)
        && Objects.equals(decisionVersion, that.decisionVersion)
        && decisionType == that.decisionType
        && Objects.equals(result, that.result)
        && Objects.equals(evaluatedInputs, that.evaluatedInputs)
        && Objects.equals(evaluatedOutputs, that.evaluatedOutputs)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
        state,
        evaluationDate,
        evaluationFailure,
        processDefinitionKey,
        processInstanceKey,
        decisionId,
        decisionDefinitionId,
        decisionName,
        decisionVersion,
        decisionType,
        result,
        evaluatedInputs,
        evaluatedOutputs,
        tenantId);
  }

  @Override
  public String toString() {
    return "DecisionInstance{"
        + "id='"
        + id
        + '\''
        + ", key="
        + key
        + ", state="
        + state
        + ", evaluationDate='"
        + evaluationDate
        + '\''
        + ", evaluationFailure='"
        + evaluationFailure
        + '\''
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", decisionId='"
        + decisionId
        + '\''
        + ", decisionDefinitionId='"
        + decisionDefinitionId
        + '\''
        + ", decisionName='"
        + decisionName
        + '\''
        + ", decisionVersion="
        + decisionVersion
        + ", decisionType="
        + decisionType
        + ", result='"
        + result
        + '\''
        + ", evaluatedInputs="
        + evaluatedInputs
        + ", evaluatedOutputs="
        + evaluatedOutputs
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
