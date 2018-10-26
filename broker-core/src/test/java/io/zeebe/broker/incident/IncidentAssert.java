/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.incident;

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.exporter.record.Assertions;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.IncidentRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;

public class IncidentAssert {

  public static void assertIOMappingIncidentWithNoData(
      long workflowInstanceKey,
      Record<WorkflowInstanceRecordValue> followUpEvent,
      Record<IncidentRecordValue> incidentRecord) {
    assertIOMappingIncidentWithNoData(
        workflowInstanceKey, "failingTask", followUpEvent, incidentRecord);
  }

  public static void assertIOMappingIncidentWithNoData(
      long workflowInstanceKey,
      String activityId,
      Record<WorkflowInstanceRecordValue> followUpEvent,
      Record<IncidentRecordValue> incidentRecord) {
    assertIncidentRecordValue(
        ErrorType.IO_MAPPING_ERROR.name(),
        "No data found for query $.foo.",
        workflowInstanceKey,
        activityId,
        followUpEvent,
        incidentRecord);
  }

  public static void assertIncidentRecordValue(
      String errorType,
      String errorMsg,
      String activityId,
      Record<IncidentRecordValue> incidentRecord) {
    final IncidentRecordValue incidentEventValue = incidentRecord.getValue();
    Assertions.assertThat(incidentEventValue)
        .hasErrorType(errorType)
        .hasErrorMessage(errorMsg)
        .hasActivityId(activityId);
  }

  public static void assertIncidentRecordValue(
      String errorType,
      String errorMsg,
      long workflowInstanceKey,
      String activityId,
      Record<WorkflowInstanceRecordValue> followUpEvent,
      Record<IncidentRecordValue> incidentResolvedEvent) {
    assertIncidentRecordValue(
        errorType,
        errorMsg,
        workflowInstanceKey,
        activityId,
        followUpEvent.getKey(),
        incidentResolvedEvent);
  }

  public static void assertIncidentRecordValue(
      String errorType,
      String errorMsg,
      long workflowInstanceKey,
      String activityId,
      long activityInstanceKey,
      Record<IncidentRecordValue> incidentResolvedEvent) {
    assertIncidentRecordValue(
        errorType,
        errorMsg,
        workflowInstanceKey,
        activityId,
        activityInstanceKey,
        -1,
        incidentResolvedEvent);
  }

  public static void assertIncidentRecordValue(
      String errorType,
      String errorMsg,
      long workflowInstanceKey,
      String activityId,
      long activityInstanceKey,
      long jobKey,
      Record<IncidentRecordValue> incidentResolvedEvent) {
    final IncidentRecordValue incidentEventValue = incidentResolvedEvent.getValue();
    Assertions.assertThat(incidentEventValue)
        .hasErrorType(errorType)
        .hasErrorMessage(errorMsg)
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasActivityId(activityId)
        .hasActivityInstanceKey(activityInstanceKey)
        .hasJobKey(jobKey);
  }

  public static void assertIncidentContainErrorDetails(Record<IncidentRecordValue> incidentEvent) {
    assertIncidentContainErrorDetails(
        incidentEvent,
        "Processing failed, since mapping will result in a non map object (json object).");
  }

  public static void assertIncidentContainErrorDetails(
      Record<IncidentRecordValue> incidentEvent, String errorMsg) {
    assertIncidentContainErrorDetails(incidentEvent, ErrorType.IO_MAPPING_ERROR.name(), errorMsg);
  }

  public static void assertIncidentContainErrorDetails(
      Record<IncidentRecordValue> incidentEvent, String errorType, String errorMsg) {
    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(errorType)
        .hasErrorMessage(errorMsg);
  }

  public static void assertIncidentOfStandaloneJob(
      Record<IncidentRecordValue> incidentEvent, long jobKey) {
    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.JOB_NO_RETRIES.name())
        .hasErrorMessage("No more retries left.")
        .hasBpmnProcessId("")
        .hasWorkflowInstanceKey(-1)
        .hasActivityId("")
        .hasActivityInstanceKey(-1)
        .hasJobKey(jobKey);
  }
}
