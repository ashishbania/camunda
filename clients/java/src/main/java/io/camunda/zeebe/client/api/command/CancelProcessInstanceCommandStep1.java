/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.client.api.command;

import io.camunda.zeebe.client.api.response.CancelProcessInstanceResponse;

public interface CancelProcessInstanceCommandStep1
    extends FinalCommandStep<CancelProcessInstanceResponse> {
  // the place for new optional parameters

  /**
   * @param tenantId the tenant id of the process instance to cancel
   * @return the builder for this command
   */
  CancelProcessInstanceCommandStep1 tenantId(String tenantId);
}
