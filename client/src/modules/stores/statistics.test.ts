/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {statisticsStore} from './statistics';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {waitFor} from 'modules/testing-library';
import {processInstancesStore} from './processInstances';
import {
  mockProcessXML,
  groupedProcessesMock,
  createInstance,
} from 'modules/testUtils';
import {statistics} from 'modules/mocks/statistics';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessCoreStatistics} from 'modules/mocks/api/processInstances/fetchProcessCoreStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockServer} from 'modules/mock-server/node';
import {rest} from 'msw';

const mockInstance = createInstance({id: '2251799813685625'});

describe('stores/statistics', () => {
  beforeEach(() => {
    // mock for initial fetch when statistics store is initialized
    mockFetchProcessCoreStatistics().withSuccess(statistics);
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
    statisticsStore.reset();
    processInstancesStore.reset();
  });

  it('should reset state', async () => {
    await statisticsStore.fetchStatistics();
    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    statisticsStore.reset();
    expect(statisticsStore.state.running).toBe(0);
    expect(statisticsStore.state.active).toBe(0);
    expect(statisticsStore.state.withIncidents).toBe(0);
    expect(statisticsStore.state.status).toBe('initial');
  });

  it('should fetch statistics with error', async () => {
    mockFetchProcessCoreStatistics().withServerError();

    expect(statisticsStore.state.status).toBe('initial');

    await statisticsStore.fetchStatistics();

    expect(statisticsStore.state.status).toBe('error');
    expect(statisticsStore.state.running).toBe(0);
    expect(statisticsStore.state.active).toBe(0);
    expect(statisticsStore.state.withIncidents).toBe(0);
  });

  it('should fetch statistics with success', async () => {
    expect(statisticsStore.state.status).toBe('initial');

    await statisticsStore.fetchStatistics();
    expect(statisticsStore.state.status).toBe('fetched');
    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);
  });

  it('should fetch statistics', async () => {
    expect(statisticsStore.state.status).toBe('initial');
    statisticsStore.fetchStatistics();

    expect(statisticsStore.state.status).toBe('first-fetch');
    await waitFor(() => {
      expect(statisticsStore.state.running).toBe(1087);
    });
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);
  });

  it('should start polling on init', async () => {
    jest.useFakeTimers();
    statisticsStore.init();
    jest.runOnlyPendingTimers();
    await waitFor(() => expect(statisticsStore.state.status).toBe('fetched'));

    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    mockFetchProcessCoreStatistics().withSuccess({
      running: 1088,
      active: 211,
      withIncidents: 878,
    });
    jest.runOnlyPendingTimers();

    await waitFor(() => expect(statisticsStore.state.running).toBe(1088));
    expect(statisticsStore.state.active).toBe(211);
    expect(statisticsStore.state.withIncidents).toBe(878);

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should fetch statistics depending on completed operations', async () => {
    jest.useFakeTimers();

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchProcessInstances().withSuccess({
      processInstances: [{...mockInstance, hasActiveOperation: true}],
      totalCount: 1,
    });

    statisticsStore.init();

    jest.runOnlyPendingTimers();
    await waitFor(() => expect(statisticsStore.state.status).toBe('fetched'));

    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched'),
    );

    // mock for next poll

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [{...mockInstance}],
            totalCount: 1,
          }),
        ),
      ),
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [{...mockInstance}],
            totalCount: 2,
          }),
        ),
      ),
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.json({
            ...statistics,
          }),
        ),
      ),
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.json({
            ...statistics,
            running: 1088,
          }),
        ),
      ),
    );

    jest.runOnlyPendingTimers();

    await waitFor(() => expect(statisticsStore.state.running).toBe(1088));
    await waitFor(() =>
      expect(processInstancesStore.state.filteredProcessInstancesCount).toBe(2),
    );
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    statisticsStore.fetchStatistics();

    await waitFor(() => expect(statisticsStore.state.running).toBe(1087));

    mockFetchProcessCoreStatistics().withSuccess({
      ...statistics,
      running: 1000,
    });

    eventListeners.online();

    await waitFor(() => expect(statisticsStore.state.running).toBe(1000));

    window.addEventListener = originalEventListener;
  });
});