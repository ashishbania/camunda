/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.rest;

import static org.mockito.Mockito.when;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.data.OperateDateTimeFormatter;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.reader.DecisionInstanceReader;
import io.camunda.operate.webapp.rest.DecisionInstanceRestService;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      DecisionInstanceRestService.class,
      OperateProperties.class,
      OperateProfileService.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      OperateProperties.class
    })
public class DecisionInstanceRestServiceIT extends OperateAbstractIT {

  @MockBean private DecisionInstanceReader decisionInstanceReader;

  @MockBean private PermissionsService permissionsService;

  @Test
  public void testDecisionInstanceFailsWhenNoPermissions() throws Exception {
    // given
    String decisionInstanceId = "instanceId";
    String bpmnDecisionId = "decisionId";
    // when
    when(decisionInstanceReader.getDecisionInstance(decisionInstanceId))
        .thenReturn(new DecisionInstanceDto().setDecisionId(bpmnDecisionId));
    when(permissionsService.hasPermissionForDecision(bpmnDecisionId, IdentityPermission.READ))
        .thenReturn(false);
    MvcResult mvcResult =
        getRequestShouldFailWithNoAuthorization(getDecisionInstanceByIdUrl(decisionInstanceId));
    // then
    assertErrorMessageContains(mvcResult, "No read permission for decision instance");
  }

  @Test
  public void testDecisionInstanceDrdFailsWhenNoPermissions() throws Exception {
    // given
    String decisionInstanceId = "instanceId";
    String bpmnDecisionId = "decisionId";
    // when
    when(decisionInstanceReader.getDecisionInstance(decisionInstanceId))
        .thenReturn(new DecisionInstanceDto().setDecisionId(bpmnDecisionId));
    when(permissionsService.hasPermissionForDecision(bpmnDecisionId, IdentityPermission.READ))
        .thenReturn(false);
    MvcResult mvcResult =
        getRequestShouldFailWithNoAuthorization(getDecisionInstanceDrdByIdUrl(decisionInstanceId));
    // then
    assertErrorMessageContains(mvcResult, "No read permission for decision instance");
  }

  public String getDecisionInstanceByIdUrl(String id) {
    return DecisionInstanceRestService.DECISION_INSTANCE_URL + "/" + id;
  }

  public String getDecisionInstanceDrdByIdUrl(String id) {
    return DecisionInstanceRestService.DECISION_INSTANCE_URL + "/" + id + "/drd-data";
  }
}
