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

package org.eclipse.esmf.ame.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.esmf.ame.exceptions.GenerationException;
import org.eclipse.esmf.ame.model.MockFileUpload;
import org.eclipse.esmf.aspectmodel.generator.diagram.DiagramGenerationConfig;
import org.eclipse.esmf.aspectmodel.generator.openapi.OpenApiSchemaGenerationConfig;
import org.eclipse.esmf.aspectmodel.generator.openapi.PagingOption;
import org.eclipse.esmf.aspectmodel.generator.sql.SqlGenerationConfig;

import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLFactory;

@MicronautTest
class GenerateServiceTest {
   @Inject
   private GenerateService generateService;

   private static final Path resourcesPath = Path.of( "src", "test", "resources", "services" );
   private static final Path eclipseTestPath = Path.of( resourcesPath.toString(), "org.eclipse.esmf.example", "1.0.0" );

   private final String model = "Movement.ttl";

   @Test
   void testAspectModelHtmlDocumentation() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final byte[] payload = generateService.generateHtmlDocument( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ),
            "en" );

      assertTrue( new String( payload, StandardCharsets.UTF_8 ).contains( "<!doctype html>" ) );
   }

   @Test
   void testAspectModelJsonSample() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final String payload = generateService.sampleJSONPayload( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ) );

      final ObjectMapper mapper = new ObjectMapper();
      final JsonNode actual = mapper.readTree( payload );

      // Verify keys exist at root level
      assertTrue( actual.has( "isMoving" ) );
      assertTrue( actual.has( "position" ) );
      assertTrue( actual.has( "speed" ) );
      assertTrue( actual.has( "speedLimitWarning" ) );

      // Verify nested keys in position object
      final JsonNode position = actual.get( "position" );
      assertTrue( position.has( "altitude" ) );
      assertTrue( position.has( "latitude" ) );
      assertTrue( position.has( "longitude" ) );
   }

   @Test
   void testAspectModelJsonSchema() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final String payload = generateService.jsonSchema( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ), "en-EN" );

      assertTrue( payload.contains( "#/components/schemas/Coordinate" ) );
   }

   @Test
   void testAspectModelJsonOpenApiSpecWithoutResourcePath() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final OpenApiSchemaGenerationConfig config = new OpenApiSchemaGenerationConfig( Locale.forLanguageTag( "en" ), false, false,
            "https://test.com", null, null, null, "", new ObjectMapper().createObjectNode(), PagingOption.TIME_BASED_PAGING, false, false,
            false, false, false, null );

      final String payload = generateService.generateJsonOpenApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ),
            config );

      assertTrue( payload.contains( "\"openapi\" : \"3.0.3\"" ) );
      assertTrue( payload.contains( "\"version\" : \"v1\"" ) );
      assertTrue( payload.contains( "\"title\" : \"movement\"" ) );
      assertTrue( payload.contains( "\"url\" : \"https://test.com/api/v1\"" ) );
   }

   @Test
   void testAspectModelJsonOpenApiSpecWithResourcePath() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final ObjectMapper objectMapper = new ObjectMapper();
      final ObjectNode jsonProperties = (ObjectNode) objectMapper.readTree( """
            {
              "resourceId": {
                "name": "resourceId",
                "in": "path",
                "description": "An example resource Id.",
                "required": true,
                "schema": {
                  "type": "string"
                }
              }
            }
            """ );

      final OpenApiSchemaGenerationConfig config = new OpenApiSchemaGenerationConfig( Locale.forLanguageTag( "en" ), false, false,
            "https://test.com", null, null, null, "/resource/{resourceId}", jsonProperties, PagingOption.TIME_BASED_PAGING, false, false,
            false, false, false, null );

      final String payload = generateService.generateJsonOpenApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ),
            config );

      assertTrue( payload.contains( "\"openapi\" : \"3.0.3\"" ) );
      assertTrue( payload.contains( "\"version\" : \"v1\"" ) );
      assertTrue( payload.contains( "\"title\" : \"movement\"" ) );
      assertTrue( payload.contains( "\"url\" : \"https://test.com/api/v1\"" ) );
      assertTrue( payload.contains( "\"/resource/{resourceId}\"" ) );
      assertTrue( payload.contains( "\"name\" : \"resourceId\"" ) );
      assertTrue( payload.contains( "\"in\" : \"path\"" ) );
   }

   @Test
   void testAspectModelJsonOpenApiSpecWithWrongResourcePathProperties() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final ObjectMapper objectMapper = new ObjectMapper();
      final ObjectNode jsonProperties = (ObjectNode) objectMapper.readTree( """
            {
              "wrongId": {
                "name": "wrongId",
                "in": "path",
                "description": "An example resource Id.",
                "required": true,
                "schema": {
                  "type": "string"
                }
              }
            }
            """ );

      final OpenApiSchemaGenerationConfig config = new OpenApiSchemaGenerationConfig( Locale.forLanguageTag( "en" ), false, false,
            "https://test.com", null, null, null, "/resource/{resourceId}", jsonProperties, PagingOption.TIME_BASED_PAGING, false, false,
            false, false, false, null );

      assertThrows( GenerationException.class,
            () -> generateService.generateJsonOpenApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ), config ) );
   }

   @Test
   void testAspectModelJsonOpenApiSpecWithIncludeCrud() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final OpenApiSchemaGenerationConfig config = new OpenApiSchemaGenerationConfig( Locale.forLanguageTag( "en" ), false, false,
            "https://test.com", null, null, null, "", new ObjectMapper().createObjectNode(), PagingOption.TIME_BASED_PAGING, false, true,
            false, false, false, null );

      final String payload = generateService.generateJsonOpenApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ),
            config );

      assertTrue( payload.contains( "\"openapi\" : \"3.0.3\"" ) );
      assertTrue( payload.contains( "\"version\" : \"v1\"" ) );
      assertTrue( payload.contains( "\"title\" : \"movement\"" ) );
      assertTrue( payload.contains( "\"url\" : \"https://test.com/api/v1\"" ) );
      assertTrue( payload.contains( "\"get\" : " ) );
      assertTrue( payload.contains( "\"post\" : " ) );
      assertTrue( payload.contains( "\"patch\" : " ) );
      assertTrue( payload.contains( "\"put\" : " ) );
   }

   @Test
   void testAspectModelJsonOpenApiSpecWithIncludePost() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final OpenApiSchemaGenerationConfig config = new OpenApiSchemaGenerationConfig( Locale.forLanguageTag( "en" ), false, false,
            "https://test.com", null, null, null, "", new ObjectMapper().createObjectNode(), PagingOption.TIME_BASED_PAGING, false, false,
            true, false, false, null );

      final String payload = generateService.generateJsonOpenApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ),
            config );

      assertTrue( payload.contains( "\"openapi\" : \"3.0.3\"" ) );
      assertTrue( payload.contains( "\"version\" : \"v1\"" ) );
      assertTrue( payload.contains( "\"title\" : \"movement\"" ) );
      assertTrue( payload.contains( "\"url\" : \"https://test.com/api/v1\"" ) );
      assertTrue( payload.contains( "\"get\" : " ) );
      assertTrue( payload.contains( "\"post\" : " ) );
      assertFalse( payload.contains( "\"patch\" : " ) );
      assertFalse( payload.contains( "\"put\" : " ) );
   }

   @Test
   void testAspectModelJsonOpenApiSpecWithIncludePatch() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final OpenApiSchemaGenerationConfig config = new OpenApiSchemaGenerationConfig( Locale.forLanguageTag( "en" ), false, false,
            "https://test.com", null, null, null, "", new ObjectMapper().createObjectNode(), PagingOption.TIME_BASED_PAGING, false, false,
            false, false, true, null );

      final String payload = generateService.generateJsonOpenApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ),
            config );

      assertTrue( payload.contains( "\"openapi\" : \"3.0.3\"" ) );
      assertTrue( payload.contains( "\"version\" : \"v1\"" ) );
      assertTrue( payload.contains( "\"title\" : \"movement\"" ) );
      assertTrue( payload.contains( "\"url\" : \"https://test.com/api/v1\"" ) );
      assertTrue( payload.contains( "\"get\" : " ) );
      assertFalse( payload.contains( "\"post\" : " ) );
      assertTrue( payload.contains( "\"patch\" : " ) );
      assertFalse( payload.contains( "\"put\" : " ) );
   }

   @Test
   void testAspectModelJsonOpenApiSpecWithIncludePut() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final OpenApiSchemaGenerationConfig config = new OpenApiSchemaGenerationConfig( Locale.forLanguageTag( "en" ), false, false,
            "https://test.com", null, null, null, "", new ObjectMapper().createObjectNode(), PagingOption.TIME_BASED_PAGING, false, false,
            false, true, false, null );

      final String payload = generateService.generateJsonOpenApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ),
            config );

      assertTrue( payload.contains( "\"openapi\" : \"3.0.3\"" ) );
      assertTrue( payload.contains( "\"version\" : \"v1\"" ) );
      assertTrue( payload.contains( "\"title\" : \"movement\"" ) );
      assertTrue( payload.contains( "\"url\" : \"https://test.com/api/v1\"" ) );
      assertTrue( payload.contains( "\"get\" : " ) );
      assertFalse( payload.contains( "\"post\" : " ) );
      assertFalse( payload.contains( "\"patch\" : " ) );
      assertTrue( payload.contains( "\"put\" : " ) );
   }

   @Test
   void testAspectModelYamlOpenApiSpecWithoutResourcePath() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final OpenApiSchemaGenerationConfig config = new OpenApiSchemaGenerationConfig( Locale.forLanguageTag( "en" ), false, false,
            "https://test.com", null, null, null, "", new ObjectMapper( new YAMLFactory() ).createObjectNode(),
            PagingOption.TIME_BASED_PAGING, false, false, false, false, false, null );

      final String payload = generateService.generateYamlOpenApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ),
            config );

      assertTrue( payload.contains( "openapi: 3.0.3" ) );
      assertTrue( payload.contains( "title: movement" ) );
      assertTrue( payload.contains( "version: v1" ) );
      assertTrue( payload.contains( "url: https://test.com/api/v1" ) );
   }

   @Test
   void testAspectModelYamlOpenApiSpecWithResourcePath() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final ObjectMapper objectMapper = new ObjectMapper( new YAMLFactory() );

      final ObjectNode yamlProperties = (ObjectNode) objectMapper.readTree( """
               resourceId:
                       name: resourceId
                       in: path
                       description: An example resource Id.
                       required: true
                       schema:
                         type: string
            """ );

      final OpenApiSchemaGenerationConfig config = new OpenApiSchemaGenerationConfig( Locale.forLanguageTag( "en" ), false, false,
            "https://test.com", null, null, null, "/resource/{resourceId}", yamlProperties, PagingOption.TIME_BASED_PAGING, false, false,
            false, false, false, null );

      final String payload = generateService.generateYamlOpenApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ),
            config );

      assertTrue( payload.contains( "openapi: 3.0.3" ) );
      assertTrue( payload.contains( "title: movement" ) );
      assertTrue( payload.contains( "version: v1" ) );
      assertTrue( payload.contains( "url: https://test.com/api/v1" ) );
      assertTrue( payload.contains( "/resource/{resourceId}" ) );
      assertTrue( payload.contains( "name: resourceId" ) );
      assertTrue( payload.contains( "in: path" ) );
   }

   @Test
   void testAspectModelYamlOpenApiSpecWithWrongResourcePathProperties() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final ObjectMapper objectMapper = new ObjectMapper( new YAMLFactory() );

      final ObjectNode yamlProperties = (ObjectNode) objectMapper.readTree( """
               wrongId:
                       name: wrongId
                       in: path
                       description: An example resource Id.
                       required: true
                       schema:
                         type: string
            """ );

      final OpenApiSchemaGenerationConfig config = new OpenApiSchemaGenerationConfig( Locale.forLanguageTag( "en" ), false, false,
            "https://test.com", null, null, null, "/resource/{resourceId}", yamlProperties, PagingOption.TIME_BASED_PAGING, false, false,
            false, false, false, null );

      assertThrows( GenerationException.class,
            () -> generateService.generateYamlOpenApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ), config ) );
   }

   @Test
   void testAspectModelYamlOpenApiSpecWithIncludeCrud() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final OpenApiSchemaGenerationConfig config = new OpenApiSchemaGenerationConfig( Locale.forLanguageTag( "en" ), false, false,
            "https://test.com", null, null, null, "", new ObjectMapper( new YAMLFactory() ).createObjectNode(),
            PagingOption.TIME_BASED_PAGING, false, true, false, false, false, null );

      final String payload = generateService.generateYamlOpenApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ),
            config );

      assertTrue( payload.contains( "openapi: 3.0.3" ) );
      assertTrue( payload.contains( "title: movement" ) );
      assertTrue( payload.contains( "version: v1" ) );
      assertTrue( payload.contains( "url: https://test.com/api/v1" ) );
      assertTrue( payload.contains( "get:" ) );
      assertTrue( payload.contains( "post:" ) );
      assertTrue( payload.contains( "patch:" ) );
      assertTrue( payload.contains( "put:" ) );
   }

   @Test
   void testAspectModelYamlOpenApiSpecWithIncludePost() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final OpenApiSchemaGenerationConfig config = new OpenApiSchemaGenerationConfig( Locale.forLanguageTag( "en" ), false, false,
            "https://test.com", null, null, null, "", new ObjectMapper( new YAMLFactory() ).createObjectNode(),
            PagingOption.TIME_BASED_PAGING, false, false, true, false, false, null );

      final String payload = generateService.generateYamlOpenApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ),
            config );

      assertTrue( payload.contains( "openapi: 3.0.3" ) );
      assertTrue( payload.contains( "title: movement" ) );
      assertTrue( payload.contains( "version: v1" ) );
      assertTrue( payload.contains( "url: https://test.com/api/v1" ) );
      assertTrue( payload.contains( "get:" ) );
      assertTrue( payload.contains( "post:" ) );
      assertFalse( payload.contains( "patch:" ) );
      assertFalse( payload.contains( "put:" ) );
   }

   @Test
   void testAspectModelYamlOpenApiSpecWithIncludePatch() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final OpenApiSchemaGenerationConfig config = new OpenApiSchemaGenerationConfig( Locale.forLanguageTag( "en" ), false, false,
            "https://test.com", null, null, null, "", new ObjectMapper( new YAMLFactory() ).createObjectNode(),
            PagingOption.TIME_BASED_PAGING, false, false, false, false, true, null );

      final String payload = generateService.generateYamlOpenApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ),
            config );

      assertTrue( payload.contains( "openapi: 3.0.3" ) );
      assertTrue( payload.contains( "title: movement" ) );
      assertTrue( payload.contains( "version: v1" ) );
      assertTrue( payload.contains( "url: https://test.com/api/v1" ) );
      assertTrue( payload.contains( "get:" ) );
      assertFalse( payload.contains( "post:" ) );
      assertTrue( payload.contains( "patch:" ) );
      assertFalse( payload.contains( "put:" ) );
   }

   @Test
   void testAspectModelYamlOpenApiSpecWithIncludePut() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final OpenApiSchemaGenerationConfig config = new OpenApiSchemaGenerationConfig( Locale.forLanguageTag( "en" ), false, false,
            "https://test.com", null, null, null, "", new ObjectMapper( new YAMLFactory() ).createObjectNode(),
            PagingOption.TIME_BASED_PAGING, false, false, false, true, false, null );

      final String payload = generateService.generateYamlOpenApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ),
            config );

      assertTrue( payload.contains( "openapi: 3.0.3" ) );
      assertTrue( payload.contains( "title: movement" ) );
      assertTrue( payload.contains( "version: v1" ) );
      assertTrue( payload.contains( "url: https://test.com/api/v1" ) );
      assertTrue( payload.contains( "get:" ) );
      assertFalse( payload.contains( "post:" ) );
      assertFalse( payload.contains( "patch:" ) );
      assertTrue( payload.contains( "put:" ) );
   }

   @Test
   void testAspectModelAASX() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final String payload = generateService.generateAASXFile( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ) );

      assertTrue( payload.contains( "aasx" ) );
   }

   @Test
   void testAspectModelAASXml() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final String payload = generateService.generateAasXmlFile( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ) );

      assertTrue( payload.contains( "<?xml version='1.0' encoding='UTF-8'?>" ) );
      assertTrue( payload.contains( "https://admin-shell.io/aas/3/1" ) );
   }

   @Test
   void testAspectModelAASJson() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final String payload = generateService.generateAasJsonFile( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ) );

      assertTrue( payload.contains( "{" ) );
      assertTrue( payload.contains( "}" ) );
      assertTrue( payload.contains( "assetAdministrationShells" ) );
      assertTrue( payload.contains( "submodels" ) );
   }

   @Test
   void testAspectModelJsonAsyncApiSpec() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final String payload = new String(
            generateService.generateAsyncApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ), "en", "json",
                  "application:id", "foo/bar", false, false ), StandardCharsets.UTF_8 );

      assertTrue( payload.contains( "\"asyncapi\" : \"3.1.0\"" ) );
      assertTrue( payload.contains( "\"id\" : \"application:id\"" ) );
      assertTrue( payload.contains( "\"title\" : \"movement MQTT API\"" ) );
      assertTrue( payload.contains( "\"version\" : \"v1\"" ) );
      assertTrue( payload.contains( "\"defaultContentType\" : \"application/json\"" ) );
      assertTrue( payload.contains( "\"address\" : \"foo/bar\"" ) );
   }

   @Test
   void testAspectModelJsonAsyncApiSpecWithSeparateFiles() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final byte[] payload = generateService.generateAsyncApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ), "en",
            "json", "application:id", "foo/bar", false, true );

      assertTrue( isValidZipFile( payload ) );
   }

   @Test
   void testAspectModelYAMLAsyncApiSpec() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final String payload = new String(
            generateService.generateAsyncApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ), "en", "yaml",
                  "application:id", "foo/bar", false, false ), StandardCharsets.UTF_8 );

      assertTrue( payload.contains( "asyncapi: 3.1.0" ) );
      assertTrue( payload.contains( "id: application:id" ) );
      assertTrue( payload.contains( "title: movement MQTT API" ) );
      assertTrue( payload.contains( "version: v1" ) );
      assertTrue( payload.contains( "defaultContentType: application/json" ) );
      assertTrue( payload.contains( "address: foo/bar" ) );
   }

   @Test
   void testAspectModelYAMLAsyncApiSpecWithSeparateFiles() throws IOException {
      final Path storagePath = Path.of( eclipseTestPath.toString(), model );
      final byte[] testModelForService = Files.readAllBytes( storagePath );
      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.ttl", testModelForService,
            MediaType.of( MediaType.MULTIPART_FORM_DATA ) );

      final byte[] payload = generateService.generateAsyncApiSpec( mockedZipFile, URI.create( "blob:///" + toUriPath( storagePath ) ), "en",
            "yaml", "application:id", "foo/bar", false, true );

      assertTrue( isValidZipFile( payload ) );
   }

   private boolean isValidZipFile( final byte[] payload ) {
      try ( final ZipInputStream zis = new ZipInputStream( new ByteArrayInputStream( payload ) ) ) {
         return zis.getNextEntry() != null;
      } catch ( final IOException e ) {
         return false;
      }
   }

   @Test
   void testAspectModelSvgDiagram() throws IOException {
      final byte[] payload = generateService.generateDiagram( movementUpload(), movementUri(), "en", DiagramGenerationConfig.Format.SVG );

      final String svg = new String( payload, StandardCharsets.UTF_8 );
      assertTrue( svg.contains( "<svg" ) );
      assertTrue( svg.contains( "Movement" ) );
   }

   @Test
   void testAspectModelPngDiagram() throws IOException {
      final byte[] payload = generateService.generateDiagram( movementUpload(), movementUri(), "en", DiagramGenerationConfig.Format.PNG );

      final byte[] pngSignature = { (byte) 0x89, 'P', 'N', 'G' };
      assertArrayEquals( pngSignature, Arrays.copyOf( payload, pngSignature.length ) );
   }

   @Test
   void testAspectModelPostgreSql() throws IOException {
      final String sql = generateService.generateSql( movementUpload(), movementUri(), "en", SqlGenerationConfig.Dialect.POSTGRESQL );

      assertTrue( sql.contains( "CREATE TABLE" ) );
      assertTrue( sql.contains( "movement" ) );
   }

   @Test
   void testAspectModelDatabricksSql() throws IOException {
      final String sql = generateService.generateSql( movementUpload(), movementUri(), "en", SqlGenerationConfig.Dialect.DATABRICKS );

      assertTrue( sql.contains( "CREATE TABLE" ) );
      assertTrue( sql.contains( "movement" ) );
   }

   @Test
   void testAspectModelJsonLd() throws IOException {
      final String jsonLd = generateService.generateJsonLd( movementUpload(), movementUri() );

      final JsonNode actual = new ObjectMapper().readTree( jsonLd );
      assertTrue( actual.has( "@context" ) );
   }

   @Test
   void testAspectModelJavaClassesWithDerivedPackageName() throws IOException {
      final byte[] zip = generateService.generateJavaClasses( movementUpload(), movementUri(), "", false, true );

      final Set<String> entries = zipEntryNames( zip );
      assertTrue( entries.contains( "org/eclipse/esmf/example/Movement.java" ), entries::toString );
      assertTrue( entries.stream().noneMatch( entry -> entry.contains( "/Meta" ) ), entries::toString );
   }

   @Test
   void testAspectModelJavaClassesWithStaticMetaModel() throws IOException {
      final byte[] zip = generateService.generateJavaClasses( movementUpload(), movementUri(), "com.example.movement", true, false );

      final Set<String> entries = zipEntryNames( zip );
      assertTrue( entries.contains( "com/example/movement/Movement.java" ), entries::toString );
      assertTrue( entries.contains( "com/example/movement/MetaMovement.java" ), entries::toString );
   }

   @Test
   void testAspectModelJavaClassesWithInvalidPackageName() {
      assertThrows( GenerationException.class,
            () -> generateService.generateJavaClasses( movementUpload(), movementUri(), "com.1example", false, true ) );
   }

   @Test
   void testAspectModelParquetPayload() throws IOException {
      final byte[] parquet = generateService.generateParquetPayload( movementUpload(), movementUri() );

      final byte[] magic = "PAR1".getBytes( StandardCharsets.US_ASCII );
      assertArrayEquals( magic, Arrays.copyOfRange( parquet, 0, magic.length ) );
      assertArrayEquals( magic, Arrays.copyOfRange( parquet, parquet.length - magic.length, parquet.length ) );
   }

   private Set<String> zipEntryNames( final byte[] zip ) throws IOException {
      final Set<String> names = new HashSet<>();
      try ( final ZipInputStream zis = new ZipInputStream( new ByteArrayInputStream( zip ) ) ) {
         for ( ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry() ) {
            names.add( entry.getName() );
         }
      }
      return names;
   }

   private CompletedFileUpload movementUpload() throws IOException {
      final byte[] content = Files.readAllBytes( Path.of( eclipseTestPath.toString(), model ) );
      return MockFileUpload.create( "TestArchive.ttl", content, MediaType.of( MediaType.MULTIPART_FORM_DATA ) );
   }

   private URI movementUri() {
      return URI.create( "blob:///" + toUriPath( Path.of( eclipseTestPath.toString(), model ) ) );
   }

   public String toUriPath( final Path path ) {
      String uriPath = path.toString();
      if ( System.getProperty( "os.name" ).toLowerCase().contains( "win" ) ) {
         uriPath = uriPath.replace( "\\", "/" );
      }
      return uriPath;
   }
}
