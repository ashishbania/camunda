/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

type OperationsMock = {
  RETRY: OperationEntity;
  CANCEL: OperationEntity;
  EDIT: OperationEntity;
  DELETE_PROCESS_INSTANCE: OperationEntity;
  DELETE_PROCESS_DEFINITION: OperationEntity;
  DELETE_DECISION_DEFINITION: OperationEntity;
  MODIFY: OperationEntity;
};

const OPERATIONS: OperationsMock = {
  RETRY: {
    id: 'b42fd629-73b1-4709-befb-7ccd900fb18d',
    type: 'RESOLVE_INCIDENT',
    endDate: null,
    operationsTotalCount: 2,
    operationsFinishedCount: 1,
    instancesCount: 1,
    name: null,
    startDate: '2021-02-20T18:31:18.625+0100',
    sortValues: ['1613842299289', '1613842278625'],
  },
  CANCEL: {
    id: '393ad666-d7f0-45c9-a679-ffa0ef82f88a',
    type: 'CANCEL_PROCESS_INSTANCE',
    endDate: '2020-02-06T14:56:17.932+0100',
    operationsTotalCount: 2,
    operationsFinishedCount: 2,
    instancesCount: 1,
    name: null,
    startDate: '2021-02-20T18:31:18.625+0100',
    sortValues: ['1613842299289', '1613842278625'],
  },
  EDIT: {
    id: 'df325d44-6a4c-4428-b017-24f923f1d052',
    type: 'UPDATE_VARIABLE',
    endDate: '2020-02-06T14:56:17.932+0100',
    operationsTotalCount: 4,
    operationsFinishedCount: 4,
    instancesCount: 1,
    name: null,
    startDate: '2021-02-20T18:31:18.625+0100',
    sortValues: ['1613842299289', '1613842278625'],
  },
  DELETE_PROCESS_INSTANCE: {
    id: 'df325d44-6a4c-4428-b017-24f923f1d052',
    type: 'DELETE_PROCESS_INSTANCE',
    endDate: '2020-02-06T14:56:17.932+0100',
    operationsTotalCount: 4,
    operationsFinishedCount: 4,
    instancesCount: 1,
    name: null,
    startDate: '2021-02-20T18:31:18.625+0100',
    sortValues: ['1613842299289', '1613842278625'],
  },
  MODIFY: {
    id: 'df325d44-6a4c-4428-b017-24f923f1d052',
    type: 'MODIFY_PROCESS_INSTANCE',
    endDate: '2020-02-06T14:56:17.932+0100',
    operationsTotalCount: 4,
    operationsFinishedCount: 4,
    instancesCount: 1,
    name: null,
    startDate: '2021-02-20T18:31:18.625+0100',
    sortValues: ['1613842299289', '1613842278625'],
  },
  DELETE_PROCESS_DEFINITION: {
    id: '5de66f22-a438-40f8-a89c-904g2dgfjm28',
    name: 'ProcessDefinitionA - version 1',
    type: 'DELETE_PROCESS_DEFINITION',
    startDate: '2020-02-06T14:56:17.932+0100',
    endDate: '2023-02-16T14:23:45.306+0100',
    instancesCount: 1,
    operationsTotalCount: 1,
    operationsFinishedCount: 1,
  },
  DELETE_DECISION_DEFINITION: {
    id: '5de66f22-a438-40f8-a89c-fn298fn23988',
    name: 'DecisionDefinitionA - version 1',
    type: 'DELETE_DECISION_DEFINITION',
    startDate: '2023-02-16T14:23:45.306+0100',
    endDate: null,
    instancesCount: 23,
    operationsTotalCount: 23,
    operationsFinishedCount: 10,
  },
};

const mockProps = {
  onInstancesClick: jest.fn(),
};

export {OPERATIONS, mockProps};