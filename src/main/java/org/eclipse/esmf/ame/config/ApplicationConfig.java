/*
 * Copyright (c) 2023 Robert Bosch Manufacturing Solutions GmbH
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

package org.eclipse.esmf.ame.config;

import java.util.Collections;
import java.util.List;

import org.eclipse.esmf.ame.exceptions.ResponseExceptionHandler;
import org.eclipse.esmf.ame.repository.strategy.LocalFolderResolverStrategy;
import org.eclipse.esmf.ame.repository.strategy.ModelResolverStrategy;
import org.eclipse.esmf.aspectmodel.shacl.constraint.JsConstraint;
import org.eclipse.esmf.aspectmodel.validation.services.AspectModelValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration file to return the conversion service of all the characteristic class converters
 */
@Configuration
@ComponentScan( basePackageClasses = ResponseExceptionHandler.class )
@EnableConfigurationProperties( ApplicationSettings.class )
public class ApplicationConfig implements WebMvcConfigurer {

   private final LocalFolderResolverStrategy localFolderStrategy;

   public ApplicationConfig( final LocalFolderResolverStrategy localFolderStrategy ) {
      this.localFolderStrategy = localFolderStrategy;
   }

   /**
    * mapping to a registry returns nothing
    */
   @Override
   public void addCorsMappings( final CorsRegistry registry ) {
      registry.addMapping( "/**" )
              .allowedMethods( "GET", "POST", "PUT", "DELETE" );
   }

   /**
    * FUnction to return AspectModelValidator bean to be managed by spring
    * controller
    *
    * @return AspectModelValidator
    */
   @Bean
   public AspectModelValidator getAspectModelValidator() {
      // Spring and GraalVM cannot launch Javascript engines at the moment, so this must be disabled for now.
      JsConstraint.setEvaluateJavaScript( false );

      return new AspectModelValidator();
   }

   @Bean
   public List<ModelResolverStrategy> modelStrategies() {
      return Collections.singletonList( localFolderStrategy );
   }
}