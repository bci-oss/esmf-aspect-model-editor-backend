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

package org.eclipse.esmf.ame.services.plugins.testplugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.esmf.ame.plugin.api.AmePlugin;
import org.eclipse.esmf.ame.plugin.api.GeneratedFile;
import org.eclipse.esmf.ame.plugin.api.GenerationResult;
import org.eclipse.esmf.ame.plugin.api.GenerationSource;
import org.eclipse.esmf.ame.plugin.api.ImportResult;
import org.eclipse.esmf.ame.plugin.api.ImportSource;
import org.eclipse.esmf.ame.plugin.api.ImportedModel;
import org.eclipse.esmf.ame.plugin.api.ModelGenerator;
import org.eclipse.esmf.ame.plugin.api.ModelImporter;
import org.eclipse.esmf.ame.plugin.api.PluginAction;
import org.eclipse.esmf.ame.plugin.api.PluginContext;
import org.eclipse.esmf.ame.plugin.api.PluginException;

/**
 * A plugin for the tests. It is packed into a JAR at test time and loaded by the plugin class loader, so it must not use
 * anything but the Java runtime and the plugin API.
 */
public class EchoPlugin implements AmePlugin {
   private PluginContext context;

   @Override
   public void start( final PluginContext context ) throws PluginException {
      this.context = context;
      write( "started", context.pluginId() + " " + context.pluginVersion() );
   }

   @Override
   public void stop() {
      try {
         write( "stopped", "true" );
      } catch ( final PluginException e ) {
         throw new IllegalStateException( e );
      }
   }

   @Override
   public List<PluginAction> actions() {
      return List.of(
            new SimpleAction( "echo", payload -> payload ),
            new SimpleAction( "reject", payload -> {
               throw new PluginException( "Rejected " + payload );
            } ),
            new SimpleAction( "crash", payload -> {
               throw new IllegalStateException( "boom" );
            } ),
            new SimpleAction( "visible-classes", payload -> Stream.of( payload.replace( "\"", "" ).split( "," ) )
                  .map( name -> name + "=" + isVisible( name ) ).collect( Collectors.joining( "," ) ) ),
            new SimpleAction( "context-class-loader",
                  payload -> String.valueOf( Thread.currentThread().getContextClassLoader() == EchoPlugin.class.getClassLoader() ) ) );
   }

   @Override
   public List<ModelImporter> importers() {
      return List.of( new ModelImporter() {
         @Override
         public String id() {
            return "upper";
         }

         @Override
         public ImportResult importModel( final ImportSource source ) {
            final String name = source.fileName().replaceFirst( "\\.[^.]*$", "" );
            return new ImportResult(
                  List.of( new ImportedModel( "org.example", "1.0.0", name + ".ttl",
                        new String( source.content(), StandardCharsets.UTF_8 ).toUpperCase( Locale.ROOT ) ) ),
                  source.options().entrySet().stream().map( option -> option.getKey() + "=" + option.getValue() ).sorted().toList() );
         }
      } );
   }

   @Override
   public List<ModelGenerator> generators() {
      return List.of(
            new SimpleGenerator( "summary", source -> {
               final String summary = source.namespace() + ":" + source.version() + " " + source.fileName() + " "
                     + source.selectedElement().orElse( "-" ) + " " + source.options() + "\n" + source.content();
               return new GenerationResult(
                     List.of( new GeneratedFile( source.fileName().replaceFirst( "\\.ttl$", ".txt" ), "text/plain",
                           summary.getBytes( StandardCharsets.UTF_8 ) ) ),
                     List.of( "generated" ) );
            } ),
            new SimpleGenerator( "escape", source -> new GenerationResult(
                  List.of( new GeneratedFile( "../outside.txt", "text/plain", new byte[0] ) ), List.of() ) ) );
   }

   private void write( final String fileName, final String content ) throws PluginException {
      try {
         Files.writeString( context.dataDirectory().resolve( fileName ), content );
      } catch ( final IOException e ) {
         throw new PluginException( "Could not write " + fileName, e );
      }
   }

   private static boolean isVisible( final String className ) {
      try {
         Class.forName( className, false, EchoPlugin.class.getClassLoader() );
         return true;
      } catch ( final ClassNotFoundException e ) {
         return false;
      }
   }

   private record SimpleAction( String id, Handler handler ) implements PluginAction {
      @Override
      public String handle( final String payload ) throws PluginException {
         return handler.handle( payload );
      }
   }

   private record SimpleGenerator( String id, Generation generation ) implements ModelGenerator {
      @Override
      public GenerationResult generate( final GenerationSource source ) throws PluginException {
         return generation.generate( source );
      }
   }

   @FunctionalInterface
   private interface Generation {
      GenerationResult generate( GenerationSource source ) throws PluginException;
   }

   @FunctionalInterface
   private interface Handler {
      String handle( String payload ) throws PluginException;
   }
}
