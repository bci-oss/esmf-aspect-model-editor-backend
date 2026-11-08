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

package org.eclipse.esmf.ame.services.plugins;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes the log of a plugin to the log of the editor backend. Plugins use {@link System.Logger}, so that they do not
 * depend on the logging library of the editor.
 */
final class PluginLogger implements System.Logger {
   private final Logger logger;

   PluginLogger( final String pluginId ) {
      logger = LoggerFactory.getLogger( "plugin." + pluginId );
   }

   @Override
   public String getName() {
      return logger.getName();
   }

   @Override
   public boolean isLoggable( final Level level ) {
      return switch ( level ) {
         case ALL, TRACE -> logger.isTraceEnabled();
         case DEBUG -> logger.isDebugEnabled();
         case INFO -> logger.isInfoEnabled();
         case WARNING -> logger.isWarnEnabled();
         case ERROR -> logger.isErrorEnabled();
         case OFF -> false;
      };
   }

   @Override
   public void log( final Level level, final ResourceBundle bundle, final String message, final Throwable thrown ) {
      if ( isLoggable( level ) ) {
         write( level, localize( bundle, message ), thrown );
      }
   }

   @Override
   public void log( final Level level, final ResourceBundle bundle, final String format, final Object... params ) {
      if ( isLoggable( level ) ) {
         final String pattern = localize( bundle, format );
         write( level, params == null || params.length == 0 ? pattern : MessageFormat.format( pattern, params ), null );
      }
   }

   private void write( final Level level, final String message, final Throwable thrown ) {
      switch ( level ) {
         case ALL, TRACE -> logger.trace( message, thrown );
         case DEBUG -> logger.debug( message, thrown );
         case INFO -> logger.info( message, thrown );
         case WARNING -> logger.warn( message, thrown );
         case ERROR -> logger.error( message, thrown );
         case OFF -> {
            // nothing to log
         }
      }
   }

   private static String localize( final ResourceBundle bundle, final String key ) {
      return bundle != null && key != null && bundle.containsKey( key ) ? bundle.getString( key ) : key;
   }
}
