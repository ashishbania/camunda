/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;

import io.camunda.operate.util.ConversionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProcessEntity extends OperateZeebeEntity<ProcessEntity> {

  private String name;
  private int version;
  private String bpmnProcessId;
  private String bpmnXml;
  private String resourceName;
  private List<ProcessFlowNodeEntity> flowNodes = new ArrayList<>();
  private String tenantId = DEFAULT_TENANT_ID;
  ;

  public String getName() {
    return name;
  }

  @Override
  public ProcessEntity setId(String id) {
    super.setId(id);
    setKey(ConversionUtils.toLongOrNull(id));
    return this;
  }

  public ProcessEntity setName(String name) {
    this.name = name;
    return this;
  }

  public int getVersion() {
    return version;
  }

  public ProcessEntity setVersion(int version) {
    this.version = version;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getBpmnXml() {
    return bpmnXml;
  }

  public ProcessEntity setBpmnXml(String bpmnXml) {
    this.bpmnXml = bpmnXml;
    return this;
  }

  public String getResourceName() {
    return resourceName;
  }

  public ProcessEntity setResourceName(String resourceName) {
    this.resourceName = resourceName;
    return this;
  }

  public List<ProcessFlowNodeEntity> getFlowNodes() {
    if (flowNodes == null) {
      flowNodes = new ArrayList<>();
    }
    return flowNodes;
  }

  public ProcessEntity setFlowNodes(List<ProcessFlowNodeEntity> flowNodes) {
    this.flowNodes = flowNodes;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ProcessEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ProcessEntity that = (ProcessEntity) o;
    return version == that.version
        && Objects.equals(name, that.name)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(bpmnXml, that.bpmnXml)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(flowNodes, that.flowNodes)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), name, version, bpmnProcessId, bpmnXml, resourceName, flowNodes, tenantId);
  }

  @Override
  public String toString() {
    return "ProcessEntity{"
        + "name='"
        + name
        + '\''
        + ", version="
        + version
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", bpmnXml='"
        + bpmnXml
        + '\''
        + ", resourceName='"
        + resourceName
        + '\''
        + ", flowNodes="
        + flowNodes
        + ", tenantId='"
        + tenantId
        + '\''
        + "} "
        + super.toString();
  }
}
