/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.entities.ProcessEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.StringJoiner;

public class FormResponse {

  @Schema(description = "The unique identifier of the embedded form within one process.")
  private String id;

  @Schema(
      description =
          "Reference to process definition (renamed equivalent of `Form.processDefinitionId` field).")
  private String processDefinitionKey;

  @Schema(description = "The title of the form.")
  private String title;

  @Schema(description = "The form content.")
  private String schema;

  @Schema(
      description =
          "The version field is null in the case of an embedded form, while it represents the deployed form's version in other scenarios.",
      format = "int64")
  private Long version;

  @Schema(description = "The tenant ID associated with the form.")
  private String tenantId;

  @Schema(
      description =
          "Indicates whether the deployed form is deleted or not on Zeebe. This field is false by default, in the case of an embedded form.")
  private Boolean isDeleted;

  public String getId() {
    return id;
  }

  public FormResponse setId(String id) {
    this.id = id;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public FormResponse setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getTitle() {
    return title;
  }

  public FormResponse setTitle(String processName) {
    this.title = processName;
    return this;
  }

  public String getSchema() {
    return schema;
  }

  public FormResponse setSchema(String schema) {
    this.schema = schema;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public FormResponse setVersion(Long version) {
    this.version = version;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public FormResponse setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public Boolean getIsDeleted() {
    return isDeleted;
  }

  public FormResponse setIsDeleted(Boolean isDeleted) {
    this.isDeleted = isDeleted;
    return this;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", FormResponse.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("processDefinitionKey='" + processDefinitionKey + "'")
        .add("title='" + title + "'")
        .add("schema='" + schema + "'")
        .add("version='" + version + "'")
        .add("tenantId='" + tenantId + "'")
        .add("isDeleted='" + isDeleted + "'")
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FormResponse that = (FormResponse) o;
    return Objects.equals(id, that.id)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(title, that.title)
        && Objects.equals(schema, that.schema)
        && Objects.equals(version, that.version)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(isDeleted, that.isDeleted);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, processDefinitionKey, title, schema, version, tenantId, isDeleted);
  }

  public static FormResponse fromFormEntity(FormEntity form) {
    return new FormResponse()
        .setId(form.getBpmnId())
        .setProcessDefinitionKey(form.getProcessDefinitionId())
        .setSchema(form.getSchema())
        .setVersion(form.getVersion())
        .setTenantId(form.getTenantId())
        .setIsDeleted(form.getIsDeleted());
  }

  public static FormResponse fromFormEntity(FormEntity form, ProcessEntity processEntity) {
    return new FormResponse()
        .setId(form.getBpmnId())
        .setProcessDefinitionKey(form.getProcessDefinitionId())
        .setTitle(
            processEntity.getName() != null
                ? processEntity.getName()
                : processEntity.getBpmnProcessId())
        .setSchema(form.getSchema())
        .setVersion(form.getVersion())
        .setTenantId(form.getTenantId())
        .setIsDeleted(form.getIsDeleted());
  }
}