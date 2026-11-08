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
 * User interface of the example import plugin.
 *
 * The editor creates the element, sets the property `host` (the plugin host API) and shows it in a dialog. The element
 * uploads a JSON description to the importer `json` of the plugin backend and opens the created Aspect Model.
 */

const TEXTS = {
  en: {
    title: 'Import from JSON description',
    file: 'JSON description',
    namespace: 'Namespace (optional, overrides the file)',
    import: 'Import',
    cancel: 'Cancel',
    importing: 'Importing...',
    exists: 'A model with this name already exists in the workspace. Opening it replaces nothing until you save.',
    imported: 'The Aspect Model was imported.',
  },
  de: {
    title: 'Aus JSON-Beschreibung importieren',
    file: 'JSON-Beschreibung',
    namespace: 'Namespace (optional, überschreibt die Datei)',
    import: 'Importieren',
    cancel: 'Abbrechen',
    importing: 'Importiere...',
    exists: 'Im Workspace gibt es bereits ein Modell mit diesem Namen. Es wird erst beim Speichern ersetzt.',
    imported: 'Das Aspect Model wurde importiert.',
  },
  zh: {
    title: '从 JSON 描述导入',
    file: 'JSON 描述',
    namespace: '命名空间（可选，覆盖文件中的值）',
    import: '导入',
    cancel: '取消',
    importing: '正在导入...',
    exists: '工作区中已存在同名模型。保存前不会替换任何内容。',
    imported: 'Aspect Model 已导入。',
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

class ExampleImportElement extends HTMLElement {
  /** The plugin host API, set by the editor before the element is connected. */
  host;

  connectedCallback() {
    const texts = TEXTS[this.host.language] ?? TEXTS.en;
    this.texts = texts;
    this.setAttribute('theme', this.host.theme);

    const root = this.shadowRoot ?? this.attachShadow({mode: 'open'});
    root.innerHTML = `
      <style>${STYLE}</style>
      <h2>${texts.title}</h2>
      <label>${texts.file}<input data-test="import-file" type="file" accept=".json,application/json"></label>
      <label>${texts.namespace}<input data-test="import-namespace" type="text" placeholder="org.example"></label>
      <div class="error" data-test="import-error" hidden></div>
      <ul data-test="import-warnings" hidden></ul>
      <div class="actions">
        <button data-test="import-cancel" type="button">${texts.cancel}</button>
        <button data-test="import-submit" type="button" class="primary" disabled>${texts.import}</button>
      </div>`;

    this.fileInput = root.querySelector('[data-test="import-file"]');
    this.namespaceInput = root.querySelector('[data-test="import-namespace"]');
    this.submitButton = root.querySelector('[data-test="import-submit"]');
    this.errorBox = root.querySelector('[data-test="import-error"]');
    this.warningList = root.querySelector('[data-test="import-warnings"]');

    root.querySelector('[data-test="import-cancel"]').addEventListener('click', () => this.host.close());
    this.fileInput.addEventListener('change', () => (this.submitButton.disabled = !this.fileInput.files?.length));
    this.submitButton.addEventListener('click', () => this.importModel());
  }

  async importModel() {
    const file = this.fileInput.files?.[0];
    if (!file) {
      return;
    }

    this.showError(null);
    this.submitButton.disabled = true;
    this.submitButton.textContent = this.texts.importing;
    try {
      const namespace = this.namespaceInput.value.trim();
      const result = await this.host.backend.importModel('json', file, namespace ? {namespace} : {});
      const [model] = result.models;

      const existing = await this.host.backend.action('existing-models');
      const warnings = [...result.warnings];
      if (existing.some(f => f.namespace === model.namespace && f.version === model.version && f.fileName === model.fileName)) {
        warnings.push(this.texts.exists);
      }

      await this.host.workspace.openModel(model);
      this.finish(warnings);
    } catch (error) {
      this.showError(error instanceof Error ? error.message : String(error));
    } finally {
      this.submitButton.disabled = false;
      this.submitButton.textContent = this.texts.import;
    }
  }

  finish(warnings) {
    if (warnings.length) {
      this.warningList.hidden = false;
      this.warningList.replaceChildren(...warnings.map(warning => Object.assign(document.createElement('li'), {textContent: warning})));
      this.host.notify(this.texts.imported, 'warning');
    } else {
      this.host.notify(this.texts.imported, 'success');
      this.host.close();
    }
  }

  showError(message) {
    this.errorBox.hidden = !message;
    this.errorBox.textContent = message ?? '';
  }
}

if (!customElements.get('ame-plugin-example-import')) {
  customElements.define('ame-plugin-example-import', ExampleImportElement);
}
