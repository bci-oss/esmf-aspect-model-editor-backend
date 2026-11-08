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

package org.eclipse.esmf.ame.examples.generator;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.esmf.ame.plugin.api.GeneratedFile;
import org.eclipse.esmf.ame.plugin.api.GenerationResult;
import org.eclipse.esmf.ame.plugin.api.GenerationSource;
import org.eclipse.esmf.ame.plugin.api.ModelGenerator;
import org.eclipse.esmf.ame.plugin.api.PluginException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.vocabulary.RDF;

/**
 * Generates a JSON description of the Aspect of the open Aspect Model, the format the example import plugin reads.
 * <p>
 * The model is parsed with the plugin's own Apache Jena. Properties whose data type the format does not support are skipped
 * with a warning. If a Property is selected in the editor, only that Property is exported. The option
 * {@value #DESCRIPTIONS_OPTION} set to {@code false} leaves out the descriptions.
 * </p>
 */
class JsonDescriptionGenerator implements ModelGenerator {
   static final String DESCRIPTIONS_OPTION = "descriptions";

   private static final ObjectMapper MAPPER = new ObjectMapper();
   private static final String META_MODEL = "urn:samm:org.eclipse.esmf.samm:meta-model:";
   private static final List<String> TYPES = List.of( "string", "boolean", "int", "long", "float", "double", "dateTime" );
   private static final Map<String, String> PREDEFINED_TYPES = Map.of( "Text", "string", "Boolean", "boolean", "Timestamp", "dateTime" );

   @Override
   public String id() {
      return "json";
   }

   @Override
   public GenerationResult generate( final GenerationSource source ) throws PluginException {
      final Model model = parse( source.content(), source.fileName() );
      final Resource aspect = findAspect( model );
      final Samm samm = new Samm( model, aspect.getPropertyResourceValue( RDF.type ).getURI() );
      final boolean descriptions = !"false".equalsIgnoreCase( source.options().get( DESCRIPTIONS_OPTION ) );

      final List<String> warnings = new ArrayList<>();
      final ArrayNode properties = describeProperties( exportedProperties( aspect, samm, source.selectedElement() ), samm, descriptions,
            warnings );
      final ObjectNode aspectJson = MAPPER.createObjectNode().put( "name", aspect.getLocalName() );
      description( aspect, samm, descriptions ).ifPresent( text -> aspectJson.put( "description", text ) );
      aspectJson.set( "properties", properties );
      final ObjectNode root = MAPPER.createObjectNode().put( "namespace", source.namespace() ).put( "version", source.version() );
      root.set( "aspect", aspectJson );

      return new GenerationResult( List.of( new GeneratedFile( aspect.getLocalName() + ".description.json", "application/json",
            write( root ) ) ), warnings );
   }

   /**
    * @return the Properties of the Aspect, or only the selected one if a Property of the Aspect is selected
    */
   private static List<Resource> exportedProperties( final Resource aspect, final Samm samm, final Optional<String> selectedElement ) {
      final List<Resource> aspectProperties = properties( aspect, samm );
      return selectedElement
            .filter( urn -> aspectProperties.stream().anyMatch( property -> property.getURI().equals( urn ) ) )
            .map( urn -> aspectProperties.stream().filter( property -> property.getURI().equals( urn ) ).toList() )
            .orElse( aspectProperties );
   }

   private static ArrayNode describeProperties( final List<Resource> properties, final Samm samm, final boolean descriptions,
         final List<String> warnings ) {
      final ArrayNode json = MAPPER.createArrayNode();
      for ( final Resource property : properties ) {
         final Optional<String> type = descriptionType( property, samm );
         if ( type.isEmpty() ) {
            warnings.add( "Skipped the property '" + property.getLocalName() + "': its data type is not supported by the JSON description." );
            continue;
         }
         final ObjectNode propertyJson = json.addObject().put( "name", property.getLocalName() ).put( "type", type.get() );
         description( property, samm, descriptions ).ifPresent( text -> propertyJson.put( "description", text ) );
      }
      return json;
   }

   private static Model parse( final String turtle, final String fileName ) throws PluginException {
      try {
         return RDFParser.source( new ByteArrayInputStream( turtle.getBytes( StandardCharsets.UTF_8 ) ) ).lang( Lang.TURTLE )
               .base( "inmemory:" + fileName ).toModel();
      } catch ( final RuntimeException e ) {
         throw new PluginException( "The Aspect Model could not be read: " + e.getMessage(), e );
      }
   }

   private static Resource findAspect( final Model model ) throws PluginException {
      final List<Resource> aspects = model.listStatements( null, RDF.type, (RDFNode) null ).toList().stream()
            .filter( statement -> statement.getObject().isURIResource() )
            .filter( statement -> statement.getResource().getURI().startsWith( META_MODEL )
                  && statement.getResource().getURI().endsWith( "#Aspect" ) )
            .map( Statement::getSubject )
            .filter( Resource::isURIResource )
            .toList();
      if ( aspects.size() != 1 ) {
         throw new PluginException( "The JSON description supports Aspect Models with exactly one Aspect, but this model contains " + aspects.size()
               + "." );
      }
      return aspects.getFirst();
   }

   private static List<Resource> properties( final Resource aspect, final Samm samm ) {
      final Resource list = aspect.getPropertyResourceValue( samm.properties );
      if ( list == null ) {
         return List.of();
      }
      // An entry is either the Property or a blank node referencing it with samm:property, e.g. for optional Properties
      return list.as( RDFList.class ).asJavaList().stream()
            .filter( RDFNode::isResource )
            .map( RDFNode::asResource )
            .map( entry -> entry.isAnon() ? entry.getPropertyResourceValue( samm.property ) : entry )
            .filter( property -> property != null && property.isURIResource() )
            .toList();
   }

   private static Optional<String> descriptionType( final Resource property, final Samm samm ) {
      final Resource characteristic = property.getPropertyResourceValue( samm.characteristic );
      if ( characteristic == null ) {
         return Optional.empty();
      }
      if ( characteristic.isURIResource() && characteristic.getURI().startsWith( samm.characteristicNamespace ) ) {
         return Optional.ofNullable( PREDEFINED_TYPES.get( characteristic.getLocalName() ) );
      }
      final Resource dataType = characteristic.getPropertyResourceValue( samm.dataType );
      if ( dataType == null || !dataType.isURIResource() || !dataType.getURI().startsWith( XSDDatatype.XSD + "#" ) ) {
         return Optional.empty();
      }
      return Optional.of( dataType.getLocalName() ).filter( TYPES::contains );
   }

   private static Optional<String> description( final Resource element, final Samm samm, final boolean enabled ) {
      if ( !enabled ) {
         return Optional.empty();
      }
      final List<Statement> descriptions = element.listProperties( samm.description ).toList();
      return descriptions.stream().filter( statement -> "en".equals( statement.getLanguage() ) ).findFirst()
            .or( () -> descriptions.stream().findFirst() )
            .map( Statement::getString );
   }

   private static byte[] write( final ObjectNode root ) throws PluginException {
      try {
         return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString( root ).getBytes( StandardCharsets.UTF_8 );
      } catch ( final JsonProcessingException e ) {
         throw new PluginException( "The JSON description could not be written.", e );
      }
   }

   /**
    * The SAMM vocabulary in the version the model uses.
    */
   private static final class Samm {
      private final Property properties;
      private final Property property;
      private final Property characteristic;
      private final Property dataType;
      private final Property description;
      private final String characteristicNamespace;

      private Samm( final Model model, final String aspectType ) {
         final String namespace = aspectType.substring( 0, aspectType.length() - "Aspect".length() );
         properties = model.createProperty( namespace, "properties" );
         property = model.createProperty( namespace, "property" );
         characteristic = model.createProperty( namespace, "characteristic" );
         dataType = model.createProperty( namespace, "dataType" );
         description = model.createProperty( namespace, "description" );
         characteristicNamespace = namespace.replace( ":meta-model:", ":characteristic:" );
      }
   }
}
