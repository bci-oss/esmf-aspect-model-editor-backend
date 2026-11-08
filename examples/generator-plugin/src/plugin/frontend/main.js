/*
 * Copyright (c) 2026 Robert Bosch Manufacturing Solutions GmbH
 *
 * See the AUTHORS file(s) distributed with this work for
 * additional information regarding authorship.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

/**
 * User interface of the example generator plugin.
 *
 * The editor creates the element, sets the property `host` (the plugin host API) and shows it in a dialog. The element
 * calls the generator `json` of the plugin backend with the open Aspect Model and saves the generated file.
 * `host.selectedElement` holds the URN of the model element selected in the editor, if any.
 */

const TEXTS = {
  en: {
    title: 'Generate as JSON description',
    model: 'Aspect Model',
    selection: 'Only the selected property',
    descriptions: 'Include descriptions',
    generate: 'Generate',
    generating: 'Generating...',
    cancel: 'Cancel',
    generated: 'The JSON description was saved.',
  },
  de: {
    title: 'Als JSON-Beschreibung generieren',
    model: 'Aspect Model',
    selection: 'Nur die ausgewählte Property',
    descriptions: 'Beschreibungen übernehmen',
    generate: 'Generieren',
    generating: 'Generiere...',
    cancel: 'Abbrechen',
    generated: 'Die JSON-Beschreibung wurde gespeichert.',
  },
  zh: {
    title: '生成 JSON 描述',
    model: 'Aspect Model',
    selection: '仅生成选中的属性',
    descriptions: '包含描述',
    generate: '生成',
    generating: '正在生成...',
    cancel: '取消',
    generated: 'JSON 描述已保存。',
  },
};

const STYLE = `
  :host { display: block; font: 14px/1.4 sans-serif; color: var(--text); min-width: 420px; }
  :host([theme='light']) { --text: #1b1b1b; --muted: #5f5f5f; --border: #c4c7c7; --accent: #007bc0; --warning: #a35b00; }
  :host([theme='dark']) { --text: #e8e8e8; --muted: #a4abb3; --border: #4f5358; --accent: #56b0ff; --warning: #ffcf00; }
  h2 { margin: 0 0 16px; font-size: 18px; }
  label { display: block; margin-bottom: 12px; color: var(--muted); }
  input[type='checkbox'] { display: inline; width: auto; margin: 0 8px 0 0; }
  .model { margin-bottom: 12px; }
  .model code { color: var(--text); }
  input { display: block; width: 100%; box-sizing: border-box; margin-top: 4px; padding: 6px; color: var(--text);
          background: transparent; border: 1px solid var(--border); border-radius: 4px; }
  ul { margin: 0 0 12px; padding-left: 20px; color: var(--warning); }
  .error { color: #e00; margin-bottom: 12px; }
  .actions { display: flex; justify-content: flex-end; gap: 8px; }
  button { padding: 6px 16px; border-radius: 4px; border: 1px solid var(--accent); background: transparent; color: var(--accent); cursor: pointer; }
  button.primary { background: var(--accent); color: #fff; }
  button:disabled { opacity: .5; cursor: default; }
`;

class ExampleGeneratorElement extends HTMLElement {
  /** The plugin host API, set by the editor before the element is connected. */
  host;

  connectedCallback() {
    const texts = TEXTS[this.host.language] ?? TEXTS.en;
    this.texts = texts;
    this.setAttribute('theme', this.host.theme);

    const model = this.host.model;
    const root = this.shadowRoot ?? this.attachShadow({mode: 'open'});
    root.innerHTML = `
      <style>${STYLE}</style>
      <h2>${texts.title}</h2>
      <div class="model">${texts.model}: <code data-test="generator-model"></code></div>
      <label ${this.host.selectedElement ? '' : 'hidden'}>
        <input data-test="generator-selection" type="checkbox" checked>${texts.selection}
        (<code data-test="generator-selected-element"></code>)
      </label>
      <label><input data-test="generator-descriptions" type="checkbox" checked>${texts.descriptions}</label>
      <div class="error" data-test="generator-error" hidden></div>
      <ul data-test="generator-warnings" hidden></ul>
      <div class="actions">
        <button data-test="generator-cancel" type="button">${texts.cancel}</button>
        <button data-test="generator-submit" type="button" class="primary">${texts.generate}</button>
      </div>`;

    // Values from the model are set as text, never as HTML.
    root.querySelector('[data-test="generator-model"]').textContent = `${model.namespace}:${model.version} / ${model.fileName}`;
    root.querySelector('[data-test="generator-selected-element"]').textContent = this.host.selectedElement ?? '';
    this.selectionInput = root.querySelector('[data-test="generator-selection"]');
    this.descriptionsInput = root.querySelector('[data-test="generator-descriptions"]');
    this.submitButton = root.querySelector('[data-test="generator-submit"]');
    this.errorBox = root.querySelector('[data-test="generator-error"]');
    this.warningList = root.querySelector('[data-test="generator-warnings"]');

    root.querySelector('[data-test="generator-cancel"]').addEventListener('click', () => this.host.close());
    this.submitButton.addEventListener('click', () => this.generate());
  }

  async generate() {
    this.showError(null);
    this.submitButton.disabled = true;
    this.submitButton.textContent = this.texts.generating;
    try {
      const options = {descriptions: String(this.descriptionsInput.checked)};
      const selectedElement = this.selectionInput.checked ? this.host.selectedElement : null;
      const result = await this.host.backend.generate('json', options, {selectedElement});
      for (const file of result.files) {
        await this.host.saveFile(file);
      }
      this.finish(result.warnings);
    } catch (error) {
      this.showError(error instanceof Error ? error.message : String(error));
    } finally {
      this.submitButton.disabled = false;
      this.submitButton.textContent = this.texts.generate;
    }
  }

  finish(warnings) {
    if (warnings.length) {
      this.warningList.hidden = false;
      this.warningList.replaceChildren(...warnings.map(warning => Object.assign(document.createElement('li'), {textContent: warning})));
      this.host.notify(this.texts.generated, 'warning');
    } else {
      this.host.notify(this.texts.generated, 'success');
      this.host.close();
    }
  }

  showError(message) {
    this.errorBox.hidden = !message;
    this.errorBox.textContent = message ?? '';
  }
}

if (!customElements.get('ame-plugin-example-generator')) {
  customElements.define('ame-plugin-example-generator', ExampleGeneratorElement);
}
