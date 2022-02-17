/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Viewer} from './Viewer';

class DecisionViewer {
  #xml: string | null = null;
  #viewer: Viewer | null = null;
  #decisionViewId: string | null = null;

  render = async (
    container: HTMLElement,
    xml: string,
    decisionViewId: string
  ) => {
    if (this.#viewer === null) {
      this.#viewer = new Viewer('decision', {container});
    }

    if (this.#xml !== xml) {
      await this.#viewer.importXML(xml);
      this.#xml = xml;
    }

    if (this.#decisionViewId !== decisionViewId) {
      const view = this.#viewer.getViews().find((view) => {
        return view.id === decisionViewId;
      });
      if (view !== undefined) {
        this.#viewer.open(view);
        this.#decisionViewId = decisionViewId;
      }
    }
  };

  reset = () => {
    this.#xml = null;
    this.#decisionViewId = null;
    this.#viewer?.destroy();
    this.#viewer = null;
  };
}

export {DecisionViewer};
