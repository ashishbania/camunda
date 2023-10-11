/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';
import {Paths} from 'modules/Routes';
import {
  mockIncidentsByError,
  mockIncidentsByProcess,
  mockStatistics,
  mockResponses,
} from '../mocks/dashboard.mocks';

test.describe('dashboard page', () => {
  for (const theme of ['light', 'dark']) {
    test(`empty page - ${theme}`, async ({page, commonPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          statistics: {
            running: 0,
            active: 0,
            withIncidents: 0,
          },
          incidentsByError: [],
          incidentsByProcess: [],
        }),
      );

      await page.goto(Paths.dashboard(), {
        waitUntil: 'networkidle',
      });

      await expect(page).toHaveScreenshot();
    });

    test(`error page - ${theme}`, async ({page, commonPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(/^.*\/api.*$/i, mockResponses({}));

      await page.goto(Paths.dashboard(), {
        waitUntil: 'networkidle',
      });

      await expect(page).toHaveScreenshot();
    });

    test(`filled with data - ${theme}`, async ({page, commonPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          statistics: mockStatistics,
          incidentsByError: mockIncidentsByError,
          incidentsByProcess: mockIncidentsByProcess,
        }),
      );

      await page.goto(Paths.dashboard(), {
        waitUntil: 'networkidle',
      });

      await expect(page).toHaveScreenshot();
    });

    test(`expanded rows - ${theme}`, async ({page, commonPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          statistics: mockStatistics,
          incidentsByError: mockIncidentsByError,
          incidentsByProcess: mockIncidentsByProcess,
        }),
      );

      await page.goto(Paths.dashboard(), {
        waitUntil: 'networkidle',
      });

      const expandInstancesByProcessRow = page
        .getByTestId('instances-by-process')
        .getByRole('button', {
          name: /expand current row/i,
        })
        .nth(0);

      expect(expandInstancesByProcessRow).toBeEnabled();

      await expandInstancesByProcessRow.click();

      await expect(
        page.getByText(/order process – 136 instances in version 2/i),
      ).toBeVisible();

      const expandIncidentsByErrorRow = page
        .getByTestId('incident-byError')
        .getByRole('button', {
          name: /expand current row/i,
        })
        .nth(0);

      await expandIncidentsByErrorRow.click();

      await expect(page.getByText(/complexprocess – version 2/i)).toBeVisible();

      await expect(page).toHaveScreenshot();
    });
  }
});