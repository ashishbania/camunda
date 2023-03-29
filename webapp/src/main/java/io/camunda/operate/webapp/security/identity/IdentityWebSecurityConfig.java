/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import static io.camunda.operate.webapp.security.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.OperateURIs.API;
import static io.camunda.operate.webapp.security.OperateURIs.AUTH_WHITELIST;
import static io.camunda.operate.webapp.security.OperateURIs.PUBLIC_API;
import static io.camunda.operate.webapp.security.OperateURIs.ROOT;

import io.camunda.operate.webapp.security.BaseWebConfigurer;
import io.camunda.operate.webapp.security.oauth2.IdentityOAuth2WebConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.stereotype.Component;

@Profile(IDENTITY_AUTH_PROFILE)
@EnableWebSecurity
@Component("webSecurityConfig")
public class IdentityWebSecurityConfig extends BaseWebConfigurer {

  @Autowired
  protected IdentityOAuth2WebConfigurer oAuth2WebConfigurer;

  @Override
  protected void applySecurityFilterSettings(final HttpSecurity http) throws Exception {
    http.csrf((csrf) -> csrf.disable())
      .authorizeRequests((authorize) -> {
        authorize
          .requestMatchers(AUTH_WHITELIST).permitAll()
          .requestMatchers(API, PUBLIC_API, ROOT).authenticated();
      })
      .exceptionHandling((handling) -> {
        handling.authenticationEntryPoint(this::failureHandler);
      });
  }

  @Override
  protected void applyOAuth2Settings(final HttpSecurity http) throws Exception {
    oAuth2WebConfigurer.configure(http);
  }

}
