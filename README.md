<!--
Copyright (c) 2026 Robert Bosch Manufacturing Solutions GmbH

See the AUTHORS file(s) distributed with this work for
additional information regarding authorship.

This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
file, You can obtain one at https://mozilla.org/MPL/2.0/.

SPDX-License-Identifier: MPL-2.0
-->

# Aspect Model Editor backend

## Table of Contents

- [Introduction](#introduction)
- [Getting help](#getting-help)
- [Setup](#setup)
- [Build and run](#build-and-run)
- [Desktop integration](#desktop-integration)
- [External editors (VS Code)](#external-editors-vs-code)
- [Plugins](#plugins)
- [Release](#release)
- [Further documentation](#further-documentation)
- [License](#license)

## Introduction

This project is used as the backend for the Aspect Model Editor and interacts with the [ESMF-SDK](https://github.com/eclipse-esmf/esmf-sdk) to
create [SAMM](https://github.com/eclipse-esmf/esmf-semantic-aspect-meta-model) specific Aspect Models.

## Getting help

Are you having trouble with Aspect Model Editor backend? We want to help!

* Check the [developer documentation](https://eclipse-esmf.github.io)
* Check the SAMM [specification](https://eclipse-esmf.github.io/samm-specification/2.0.0/index.html)
* Having issues with the Aspect Model Editor backend? Open a [GitHub issue](https://github.com/eclipse-esmf/esmf-aspect-model-editor-backend/issues).

## Setup

* Download and install [JDK 25](https://github.com/adoptium/temurin25-binaries/releases)
* Download and Install [Maven](https://maven.apache.org/download.cgi)
* Environment Settings
    * Add "{JAVA_HOME}/bin" to PATH
    * Add "{MAVEN_HOME}/bin" to PATH
* Configure Maven Settings

> Note : Configure your IDE to use the new JDK and Maven installation.

## Build and run

```
mvn clean package
java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
     -jar aspect-model-editor-runtime/target/aspect-model-editor-runtime-DEV-SNAPSHOT.jar
```

The JVM options are the same as in the packaged app image of the desktop app. Without them, JDK 24+ prints warnings
when native libraries (`zstd-jni`) or `sun.misc.Unsafe` (GraalVM Truffle) are used. IntelliJ IDEA users can start the
backend with the shared run configuration `AME Backend` (`.run/AME Backend.run.xml`), which sets these options.

## Desktop integration

The desktop app of the [Aspect Model Editor](https://github.com/eclipse-esmf/esmf-aspect-model-editor) (Tauri) starts
the backend as a sidecar process and talks to it via REST on `127.0.0.1`. The following options control this contract:

| Option                                                   | Purpose                                                                                                                                   |
|----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `AME_AUTH_TOKEN` (env) / `ame.auth.token`                | When set, every request (except CORS preflights) must carry the header `X-AME-Token` with this value, otherwise `401` is returned. Unset during local development. |
| `-Dmicronaut.server.port=-1`                             | The operating system assigns a free port (no race between choosing and binding a port).                                                   |
| stdout line `AME_BACKEND_READY port=<port>`              | Printed once the HTTP server accepts connections; the desktop app reads the actual port from it.                                          |
| `-Dame.exit-on-stdin-eof=true`                           | The backend shuts down gracefully when its stdin is closed (works on all platforms, also on Windows without signals).                    |
| `-Dame.parent-pid=<pid>`                                 | The backend exits when the desktop app process is gone (e.g. after a crash).                                                              |
| `JAVA_TOOL_OPTIONS=-XX:SharedArchiveFile=<file> -XX:+AutoCreateSharedArchive` | Dynamic AppCDS archive created on the first graceful shutdown and reused afterwards (~30% faster startup). The jlink runtime is built with `--generate-cds-archive` for this. |

Endpoints with side effects (`/package/backup-workspace`, `/models/migrate-workspace`) only accept `POST`.

Example:

```
AME_AUTH_TOKEN=secret java -Dmicronaut.server.port=-1 -jar aspect-model-editor-runtime/target/aspect-model-editor-runtime-DEV-SNAPSHOT.jar
curl -H "X-AME-Token: secret" http://127.0.0.1:<port>/ame/api/models/storage-path
```

## External editors (VS Code)

Aspect Models can be edited in parallel in other editors, e.g. VS Code with the
[ESMF extension](https://marketplace.visualstudio.com/items?itemName=eclipse-esmf.semantic-models). The backend supports
this with two endpoints:

| Endpoint                                     | Purpose                                                                                                                                  |
|----------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `GET /models/location` (header `aspect-model-urn`, optional query `fileName`) | The file and the 1-based line and column an element is declared in (`{path, line, column}`), so that it can be opened at its position; `404` if it is not declared in the workspace. If several files of the namespace version declare the element, `fileName` (e.g. the file of the model shown in the editor) decides. |
| `GET /workspaces/events` (`text/event-stream`)     | Server-sent events about workspace files changed outside of the editor: `ready` on every (re)connect, `files-changed` with the `created`, `modified` and `removed` files, and a `heartbeat` every 15 seconds. |

The file watcher polls the writable local workspaces (not the read-only GitHub sources) every
`ame.workspace-watch.interval` (default `1s`) and compares size and modification time, and the content hash only if
these changed. Polling is used on purpose instead of `java.nio.file.WatchService`: it behaves the same on Windows, Linux
and macOS (where the JDK polls anyway), does not lock directories on Windows and needs no native libraries.

Changes made through the REST API are not reported: the watcher absorbs the state of the workspace after every
`POST`, `PUT`, `PATCH` or `DELETE` request. As a consequence, an external change saved while such a request is running
is not reported either. Changes made while a client is disconnected are not reported; clients refresh fully on `ready`.

As all endpoints, the event stream requires the token header (see above), so browsers have to read it with `fetch`
instead of `EventSource`.

## Plugins

Plugins extend the editor with backend functionality (Java) and a user interface (a Web Component), packed into a
`.zip` archive that users install in the settings of the editor. Installed plugins are stored in
`~/aspect-model-editor/plugins` and loaded when the backend starts, each with its own class loader that only shares the plugin API, the SLF4J API and the
Java runtime with the editor.

| Module / path                                                             | Content                                                                                         |
|---------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| [`aspect-model-editor-plugin-api`](aspect-model-editor-plugin-api)        | The API plugins implement (`AmePlugin`, `ModelImporter`, `ModelGenerator`, `PluginAction`, ...), without dependencies |
| `aspect-model-editor-service/.../services/plugins`                        | Installation, validation of `plugin.json`, storage and loading of plugins                      |
| `/ame/api/plugins`                                                        | REST endpoints to list, install, enable/disable and remove plugins and to run their importers, generators and actions |
| [`examples/import-plugin`](examples/import-plugin)                        | Example import plugin (JSON description → Aspect Model) with backend part, user interface and Maven assembly; sample input `samples/movement.json` |
| [`examples/generator-plugin`](examples/generator-plugin)                  | Example generator plugin (Aspect Model → JSON description) for menu and toolbar; sample model `samples/Movement.ttl` and its output `samples/Movement.description.json` |

Plugins contribute two kinds of commands: **importers** create new Aspect Models from external files
(`POST /plugins/{id}/importers/{importer}`, multipart) and **generators** create files from the current Aspect Model, which
they get read-only (`POST /plugins/{id}/generators/{generator}`, JSON). Plugins cannot register REST endpoints of their own;
additional backend logic is reachable through actions (`POST /plugins/{id}/actions/{action}`, any JSON). This keeps the
contract fixed, token-protected and independent of the backend framework.

The archive format, `plugin.json` and the host API of the user interface are described in the
[Plugins section of the editor README](https://github.com/eclipse-esmf/esmf-aspect-model-editor#plugins).

## Release

The workflow `.github/workflows/tagged_release.yml` (manually started with the release version) builds the backend as
[jpackage](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html) app image with its own Java runtime
and publishes it as GitHub release. The desktop app of the
[Aspect Model Editor](https://github.com/eclipse-esmf/esmf-aspect-model-editor) bundles these app images, so the backend
release must exist before the editor release with the same version is created.

1. `prepare` creates the branch `<major>.<minor>.x`, the tag `v<version>` and a draft release.
   Release candidates (e.g. `2.3.0-rc1`) become a pre-release.
2. `build` creates the app image on every platform and uploads it to the draft release:

   | Runner           | Release asset                                                        |
   |------------------|----------------------------------------------------------------------|
   | `ubuntu-latest`  | `ame-backend-v<version>-linux.tar.gz`                                |
   | `macos-15-intel` | `ame-backend-v<version>-mac-x64.tar.gz` (Intel)                      |
   | `macos-latest`   | `ame-backend-v<version>-mac-arm64.tar.gz` (Apple silicon)            |
   | `windows-latest` | `ame-backend-v<version>-win` (workflow artifact only, signed and uploaded by Jenkins) |

3. `publish` publishes the release and triggers the Jenkins job which signs the Windows app image.
   The Jenkins job is only triggered in the repository `eclipse-esmf`, so the workflow can be tested in a fork.

The Java runtime in the app image matches the processor architecture, therefore macOS is built separately for Intel and
Apple silicon.

## Further documentation

* [Workspace deletion and missing references](docs/workspace-and-references.adoc): reference-aware deletion of files,
  namespaces and the workspace, `ignoreMissing` and the `unresolvedElements` error responses, and how to run the Bruno tests

We are always looking forward to your contributions. For more details on how to contribute just take a look at the
[contribution guidelines](CONTRIBUTING.md). Please create an issue first before opening a pull request.

## License

SPDX-License-Identifier: MPL-2.0

This program and the accompanying materials are made available under the terms of the
[Mozilla Public License, v. 2.0](LICENSE).

The [Notice file](NOTICE.md) details contained third party materials.
