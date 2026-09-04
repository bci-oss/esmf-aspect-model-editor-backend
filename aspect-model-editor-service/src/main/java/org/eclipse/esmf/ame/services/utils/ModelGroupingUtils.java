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

package org.eclipse.esmf.ame.services.utils;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.esmf.ame.exceptions.FileReadException;
import org.eclipse.esmf.ame.services.models.NamespaceModel;
import org.eclipse.esmf.ame.services.models.Version;
import org.eclipse.esmf.aspectmodel.AspectModelFile;
import org.eclipse.esmf.aspectmodel.loader.AspectModelLoader;
import org.eclipse.esmf.aspectmodel.resolver.AspectModelFileLoader;
import org.eclipse.esmf.aspectmodel.resolver.exceptions.ParserException;
import org.eclipse.esmf.aspectmodel.resolver.modelfile.RawAspectModelFile;
import org.eclipse.esmf.aspectmodel.urn.AspectModelUrn;
import org.eclipse.esmf.aspectmodel.validation.services.AspectModelValidator;
import org.eclipse.esmf.metamodel.ModelElement;
import org.eclipse.esmf.metamodel.vocabulary.SAMM;
import org.eclipse.esmf.metamodel.vocabulary.SAMMC;
import org.eclipse.esmf.metamodel.vocabulary.SAMME;
import org.eclipse.esmf.metamodel.vocabulary.SammNs;
import org.eclipse.esmf.samm.KnownVersion;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;

/**
 * A utility class for grouping model URIs by namespace and version.
 *
 * @param aspectModelLoader the loader for aspect models
 * @param aspectModelValidator the validator for aspect models
 */
public record ModelGroupingUtils( AspectModelLoader aspectModelLoader, AspectModelValidator aspectModelValidator ) {
   /**
    * Constructs a ModelGrouper with the given base model path.
    */
   public ModelGroupingUtils {
   }

   /**
    * Groups model URIs by namespace and version, setting the existing field as specified.
    *
    * @param uriStream a stream of model URIs
    * @return a map where the keys are namespaces and the values are lists of maps containing versions and their associated models
    */
   public Map<String, List<Version>> groupModelsByNamespaceAndVersion( final Stream<URI> uriStream ) {
      return this.groupModelsByNamespaceAndVersion( uriStream.map( File::new ).toList() );
   }

   /**
    * Groups model URIs by namespace and version, setting the existing field as specified.
    *
    * @param files a List of model Files
    * @return a map where the keys are namespaces and the values are lists of maps containing versions and their associated models
    */
   public Map<String, List<Version>> groupModelsByNamespaceAndVersion( final List<File> files ) {
      final List<NamespaceModel> allModels = loadAndExtractModels( files );
      final Map<String, List<NamespaceModel>> modelsByNamespace = groupByNamespace( allModels );

      return modelsByNamespace.entrySet().stream().sorted( Map.Entry.comparingByKey() ).collect(
            Collectors.toMap( Map.Entry::getKey, entry -> groupByVersion( entry.getValue() ), this::throwOnDuplicateKey,
                  LinkedHashMap::new ) );
   }

   private List<NamespaceModel> loadAndExtractModels( final List<File> files ) {
      return files.stream().map( this::loadModelWithVersion ).flatMap( entry -> extractModelsFromEntry( entry ) )
            .toList();
   }

   private Map.Entry<RawAspectModelFile, Optional<KnownVersion>> loadModelWithVersion( final File file ) {
      try {
         final RawAspectModelFile rawFile = AspectModelFileLoader.load( file );
         final Optional<KnownVersion> metaModelVersion = extractMetaModelVersion( rawFile );

         return new AbstractMap.SimpleEntry<>( rawFile, metaModelVersion );
      } catch ( final ParserException e ) {
         throw new FileReadException( String.format( "Failed to parse model file '%s': %s", file.getPath(), e.getMessage() ) );
      }
   }

   private Optional<KnownVersion> extractMetaModelVersion( final RawAspectModelFile rawFile ) {
      final String sammPrefix = rawFile.sourceModel().getNsPrefixMap().get( SammNs.SAMM.getShortForm() );

      if ( sammPrefix == null ) {
         final String bammPrefix = rawFile.sourceModel().getNsPrefixMap().get( "bamm" );
         throw new IllegalStateException( String.format(
               "The model uses an outdated BAMM definition '%s', which is no longer supported by the Aspect Model Editor. "
                     + "Please migrate your model to the current SAMM specification before reloading.", bammPrefix ) );
      }

      return AspectModelUrn.from( sammPrefix ).toJavaOptional().map( AspectModelUrn::getVersion )
            .flatMap( KnownVersion::fromVersionString );
   }

   private Stream<NamespaceModel> extractModelsFromEntry( final Map.Entry<RawAspectModelFile, Optional<KnownVersion>> entry ) {
      final RawAspectModelFile rawFile = entry.getKey();
      final String filename = extractFilename( rawFile );
      final KnownVersion version = entry.getValue().orElseThrow( () ->
            new IllegalStateException( String.format( "Valid SAMM meta-model version is required in model file '%s'", filename ) ) );

      final List<Resource> resources = collectMetaModelResources( version );
      final Resource firstNonBlankSubject = findFirstNonBlankSubject( rawFile.sourceModel(), resources, filename );

      final NamespaceModel model = new NamespaceModel( filename, AspectModelUrn.fromUrn( firstNonBlankSubject.getURI() ),
            version.toVersionString(), true );

      return Stream.of( model );
   }

   private String extractFilename( final RawAspectModelFile rawFile ) {
      return Path.of( rawFile.sourceUri() ).getFileName().toString();
   }

   private List<Resource> collectMetaModelResources( final KnownVersion version ) {
      final SAMM samm = new SAMM( version );
      final SAMMC sammc = new SAMMC( version );
      final SAMME samme = new SAMME( version, samm );

      return Stream.of(
            Stream.of( samm.Aspect(), samm.Property(), samm.Operation(), samm.Event(), samm.Entity(), samm.Value(), samm.Characteristic(),
                  samm.Constraint(), samm.AbstractEntity(), samm.AbstractProperty() ), samme.allEntities(), sammc.allCharacteristics(),
            sammc.allConstraints(), sammc.allCollections() ).flatMap( s -> s ).toList();
   }

   private Resource findFirstNonBlankSubject( final org.apache.jena.rdf.model.Model sourceModel, final List<Resource> resources,
         final String filename ) {
      return resources.stream().flatMap( resource -> sourceModel.listStatements( null, RDF.type, resource ).toList().stream() )
            .map( Statement::getSubject ).filter( subject -> !subject.isAnon() ).findFirst()
            .orElseThrow( () -> new IllegalStateException( "No non-blank subject found in " + filename ) );
   }

   private Map<String, List<NamespaceModel>> groupByNamespace( final List<NamespaceModel> models ) {
      return models.stream().collect( Collectors.groupingBy( model -> model.aspectModelUrn().getNamespaceMainPart() ) );
   }

   private Stream<ModelElement> extractModelElement( final AspectModelFile file, final boolean onlyAspectModels ) {

      final Optional<ModelElement> aspectElement = file.aspects().stream().map( ModelElement.class::cast ).findFirst();

      if ( onlyAspectModels ) {
         return aspectElement.stream();
      }

      return aspectElement.or( () -> findFirstNonAnonymousElement( file ) ).stream();
   }

   private Optional<ModelElement> findFirstNonAnonymousElement( final AspectModelFile file ) {
      return file.elements().stream().filter( element -> !element.isAnonymous() ).findAny();
   }

   private List<Version> groupByVersion( final List<NamespaceModel> models ) {
      final Map<AspectModelUrn, NamespaceModel> uniqueModels = removeDuplicateModels( models );
      final Map<String, List<NamespaceModel>> modelsByVersion = groupModelsByVersionString( uniqueModels );

      return modelsByVersion.entrySet().stream().sorted( Map.Entry.comparingByKey() ).map( this::createVersionEntry ).toList();
   }

   private Map<AspectModelUrn, NamespaceModel> removeDuplicateModels( final List<NamespaceModel> models ) {
      return models.stream()
            .collect( Collectors.toMap( NamespaceModel::aspectModelUrn, model -> model, ( existing, duplicate ) -> existing,
                  LinkedHashMap::new ) );
   }

   private Map<String, List<NamespaceModel>> groupModelsByVersionString( final Map<AspectModelUrn, NamespaceModel> uniqueModels ) {

      return uniqueModels.values().stream().collect( Collectors.groupingBy( model -> model.aspectModelUrn().getVersion() ) );
   }

   private Version createVersionEntry( final Map.Entry<String, List<NamespaceModel>> entry ) {
      final List<NamespaceModel> sortedModels = entry.getValue().stream().sorted( Comparator.comparing( NamespaceModel::name ) ).toList();
      return new Version( entry.getKey(), sortedModels );
   }

   private <T> T throwOnDuplicateKey( final T v1, final T v2 ) {
      throw new RuntimeException( String.format( "Duplicate key for values %s and %s", v1, v2 ) );
   }
}
