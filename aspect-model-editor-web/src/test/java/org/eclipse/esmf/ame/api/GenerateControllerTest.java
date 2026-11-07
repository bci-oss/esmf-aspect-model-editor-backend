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

package org.eclipse.esmf.ame.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.eclipse.esmf.ame.exceptions.GenerationException;
import org.eclipse.esmf.ame.exceptions.UriNotDefinedException;
import org.eclipse.esmf.ame.model.MockFileUpload;
import org.eclipse.esmf.ame.MediaTypeExtension;
import org.eclipse.esmf.ame.services.GenerateService;
import org.eclipse.esmf.aspectmodel.generator.diagram.DiagramGenerationConfig;
import org.eclipse.esmf.aspectmodel.generator.openapi.PagingOption;
import org.eclipse.esmf.aspectmodel.generator.sql.SqlGenerationConfig;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenerateControllerTest {

   private GenerateService generateService;
   private GenerateController controller;

   private static final String VALID_URI = "blob://Movement.ttl";

   @BeforeEach
   void setUp() {
      generateService = mock( GenerateService.class );
      controller = new GenerateController( generateService );
   }

   @Test
   void testGenerateHtmlSuccess() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      final byte[] expectedBytes = "<html>Documentation</html>".getBytes( StandardCharsets.UTF_8 );
      when( generateService.generateHtmlDocument( eq( upload ), any( URI.class ), eq( "en" ) ) ).thenReturn( expectedBytes );

      final HttpResponse<byte[]> response = controller.generateHtml( Optional.of( VALID_URI ), upload, "en" );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertArrayEquals( expectedBytes, response.body() );
   }

   @Test
   void testGenerateHtmlMissingUriThrowsUriNotDefinedException() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );

      final UriNotDefinedException ex = assertThrows( UriNotDefinedException.class,
            () -> controller.generateHtml( Optional.empty(), upload, "en" ) );
      assertEquals( 422, ex.getHttpStatusCode() );
   }

   @Test
   void testGenerateHtmlInvalidUriThrowsUriNotDefinedException() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );

      final UriNotDefinedException ex = assertThrows( UriNotDefinedException.class,
            () -> controller.generateHtml( Optional.of( "ht tp://bad uri" ), upload, "en" ) );
      assertEquals( 422, ex.getHttpStatusCode() );
      assertTrue( ex.getMessage().contains( "Invalid URI format" ) );
   }

   @Test
   void testJsonSchemaSuccess() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      when( generateService.jsonSchema( eq( upload ), any( URI.class ), eq( "en" ) ) ).thenReturn( "{\"type\":\"object\"}" );

      final HttpResponse<String> response = controller.jsonSchema( Optional.of( VALID_URI ), upload, "en" );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( "{\"type\":\"object\"}", response.body() );
   }

   @Test
   void testJsonSampleSuccess() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      when( generateService.sampleJSONPayload( eq( upload ), any( URI.class ) ) ).thenReturn( "{\"name\":\"Sample\"}" );

      final HttpResponse<Object> response = controller.jsonSample( Optional.of( VALID_URI ), upload );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( "{\"name\":\"Sample\"}", response.body() );
   }

   @Test
   void testGenerateAasxSuccess() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      when( generateService.generateAASXFile( eq( upload ), any( URI.class ) ) ).thenReturn( "aasx-content" );

      final HttpResponse<String> response = controller.generateAasx( Optional.of( VALID_URI ), upload );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( "aasx-content", response.body() );
   }

   @Test
   void testGenerateAasXmlSuccess() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      when( generateService.generateAasXmlFile( eq( upload ), any( URI.class ) ) ).thenReturn( "<xml>aas</xml>" );

      final HttpResponse<String> response = controller.generateAasXml( Optional.of( VALID_URI ), upload );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( "<xml>aas</xml>", response.body() );
   }

   @Test
   void testGenerateAasJsonSuccess() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      when( generateService.generateAasJsonFile( eq( upload ), any( URI.class ) ) ).thenReturn( "{\"aas\":\"data\"}" );

      final HttpResponse<String> response = controller.generateAasJson( Optional.of( VALID_URI ), upload );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( "{\"aas\":\"data\"}", response.body() );
   }

   @Test
   void testOpenApiSpecYamlSuccess() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      when( generateService.generateYamlOpenApiSpec( eq( upload ), any( URI.class ), any() ) ).thenReturn( "openapi: 3.0.0" );

      final HttpResponse<String> response = controller.openApiSpec(
            Optional.of( VALID_URI ), upload, "en", "yaml", "https://example.com",
            false, false, PagingOption.TIME_BASED_PAGING, false, false, false, false,
            "", "", ""
      );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( MediaType.APPLICATION_YAML, response.getContentType().map( MediaType::getName ).orElse( null ) );
      assertEquals( "openapi: 3.0.0", response.body() );
   }

   @Test
   void testOpenApiSpecJsonSuccess() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      when( generateService.generateJsonOpenApiSpec( eq( upload ), any( URI.class ), any() ) ).thenReturn( "{\"openapi\":\"3.0.0\"}" );

      final HttpResponse<String> response = controller.openApiSpec(
            Optional.of( VALID_URI ), upload, "en", "json", "https://example.com",
            false, false, PagingOption.TIME_BASED_PAGING, false, false, false, false,
            "", "", ""
      );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( MediaType.APPLICATION_JSON, response.getContentType().map( MediaType::getName ).orElse( null ) );
      assertEquals( "{\"openapi\":\"3.0.0\"}", response.body() );
   }

   @Test
   void testOpenApiSpecInvalidPropertiesThrowsGenerationException() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );

      final GenerationException ex = assertThrows( GenerationException.class, () -> controller.openApiSpec(
            Optional.of( VALID_URI ), upload, "en", "json", "https://example.com",
            false, false, PagingOption.TIME_BASED_PAGING, false, false, false, false,
            "/resource", "", "INVALID_JSON_NOT_AN_OBJECT"
      ) );

      assertEquals( 400, ex.getHttpStatusCode() );
      assertTrue( ex.getMessage().contains( "Invalid OpenAPI properties format" ) );
   }

   @Test
   void testAsyncApiSpecSingleFileSuccess() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      final byte[] yamlBytes = "asyncapi: 2.6.0".getBytes( StandardCharsets.UTF_8 );
      when( generateService.generateAsyncApiSpec( eq( upload ), any( URI.class ), eq( "en" ), eq( "yaml" ), any(), any(), eq( false ), eq( false ) ) )
            .thenReturn( yamlBytes );

      final HttpResponse<byte[]> response = controller.asyncApiSpec(
            Optional.of( VALID_URI ), upload, "en", "yaml", "app-id", "topic", false, false
      );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( MediaType.APPLICATION_YAML, response.getContentType().map( MediaType::getName ).orElse( null ) );
      assertArrayEquals( yamlBytes, response.body() );
   }

   @Test
   void testAsyncApiSpecZipPackageSuccess() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      final byte[] zipBytes = new byte[]{ 0x50, 0x4B, 0x03, 0x04 };
      when( generateService.generateAsyncApiSpec( eq( upload ), any( URI.class ), eq( "en" ), eq( "yaml" ), any(), any(), eq( false ), eq( true ) ) )
            .thenReturn( zipBytes );

      final HttpResponse<byte[]> response = controller.asyncApiSpec(
            Optional.of( VALID_URI ), upload, "en", "yaml", "app-id", "topic", false, true
      );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( MediaType.APPLICATION_ZIP, response.getContentType().map( MediaType::getName ).orElse( null ) );
      assertNotNull( response.getHeaders().get( "Content-Disposition" ) );
   }

   @Test
   void testGenerateSvgDiagramSuccess() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      final byte[] svg = "<svg/>".getBytes( StandardCharsets.UTF_8 );
      when( generateService.generateDiagram( eq( upload ), any( URI.class ), eq( "en" ), eq( DiagramGenerationConfig.Format.SVG ) ) )
            .thenReturn( svg );

      final HttpResponse<byte[]> response = controller.generateDiagram( Optional.of( VALID_URI ), upload, "en",
            DiagramGenerationConfig.Format.SVG );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertArrayEquals( svg, response.body() );
      assertEquals( MediaTypeExtension.IMAGE_SVG_VALUE, response.getContentType().map( MediaType::toString ).orElseThrow() );
   }

   @Test
   void testGeneratePngDiagramSetsPngContentType() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      when( generateService.generateDiagram( eq( upload ), any( URI.class ), eq( "en" ), eq( DiagramGenerationConfig.Format.PNG ) ) )
            .thenReturn( new byte[] { 1 } );

      final HttpResponse<byte[]> response = controller.generateDiagram( Optional.of( VALID_URI ), upload, "en",
            DiagramGenerationConfig.Format.PNG );

      assertEquals( MediaType.IMAGE_PNG, response.getContentType().map( MediaType::toString ).orElseThrow() );
   }

   @Test
   void testGenerateDiagramMissingUriThrowsUriNotDefinedException() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );

      assertThrows( UriNotDefinedException.class,
            () -> controller.generateDiagram( Optional.empty(), upload, "en", DiagramGenerationConfig.Format.SVG ) );
   }

   @Test
   void testGenerateSqlSuccess() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      when( generateService.generateSql( eq( upload ), any( URI.class ), eq( "en" ), eq( SqlGenerationConfig.Dialect.POSTGRESQL ) ) )
            .thenReturn( "CREATE TABLE movement" );

      final HttpResponse<String> response = controller.generateSql( Optional.of( VALID_URI ), upload, "en",
            SqlGenerationConfig.Dialect.POSTGRESQL );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( "CREATE TABLE movement", response.body() );
   }

   @Test
   void testGenerateJsonLdSuccess() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      when( generateService.generateJsonLd( eq( upload ), any( URI.class ) ) ).thenReturn( "{\"@context\":{}}" );

      final HttpResponse<String> response = controller.generateJsonLd( Optional.of( VALID_URI ), upload );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( "{\"@context\":{}}", response.body() );
   }

   @Test
   void testGenerateJavaClassesSuccess() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      final byte[] zip = { 0x50, 0x4B, 0x03, 0x04 };
      when( generateService.generateJavaClasses( eq( upload ), any( URI.class ), eq( "com.example" ), eq( true ), eq( false ) ) )
            .thenReturn( zip );

      final HttpResponse<byte[]> response = controller.generateJavaClasses( Optional.of( VALID_URI ), upload, "com.example", true, false );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertArrayEquals( zip, response.body() );
      assertEquals( MediaType.APPLICATION_ZIP, response.getContentType().map( MediaType::getName ).orElse( null ) );
      assertTrue( response.getHeaders().get( HttpHeaders.CONTENT_DISPOSITION ).contains( "java-classes.zip" ) );
   }

   @Test
   void testGenerateParquetPayloadSuccess() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );
      final byte[] parquet = "PAR1PAR1".getBytes( StandardCharsets.US_ASCII );
      when( generateService.generateParquetPayload( eq( upload ), any( URI.class ) ) ).thenReturn( parquet );

      final HttpResponse<byte[]> response = controller.generateParquetPayload( Optional.of( VALID_URI ), upload );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertArrayEquals( parquet, response.body() );
   }

   @Test
   void testGenerateParquetPayloadWithoutUriFails() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl" );

      assertThrows( UriNotDefinedException.class, () -> controller.generateParquetPayload( Optional.empty(), upload ) );
   }
}
