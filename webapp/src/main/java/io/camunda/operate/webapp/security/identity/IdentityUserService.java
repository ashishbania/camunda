/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.AbstractUserService;
import io.camunda.operate.webapp.security.Permission;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Profile(IDENTITY_AUTH_PROFILE)
public class IdentityUserService extends AbstractUserService<AbstractAuthenticationToken> {

  private final Identity identity;

  private final PermissionConverter permissionConverter;

  @Autowired
  public IdentityUserService(Identity identity, PermissionConverter permissionConverter) {
    this.identity = identity;
    this.permissionConverter = permissionConverter;
  }

  @Override
  public UserDto createUserDtoFrom(final AbstractAuthenticationToken authentication) {
    if (authentication instanceof IdentityAuthentication) {
      return new UserDto()
          .setUserId(((IdentityAuthentication) authentication).getId())
          .setDisplayName(authentication.getName())
          .setCanLogout(true)
          .setPermissions(((IdentityAuthentication) authentication).getPermissions())
          .setTenants(((IdentityAuthentication) authentication).getTenants());
    } else if (authentication instanceof JwtAuthenticationToken) {
      final AccessToken accessToken =
          identity
              .authentication()
              .verifyToken(((Jwt) authentication.getPrincipal()).getTokenValue());
      final List<Permission> permissions =
          accessToken.getPermissions().stream()
              .map(permissionConverter::convert)
              .collect(Collectors.toList());
      return new UserDto()
          .setUserId(authentication.getName())
          .setDisplayName(authentication.getName())
          .setCanLogout(true)
          .setPermissions(permissions);
    } else {
      return null;
    }
  }

  @Override
  public String getUserToken(final AbstractAuthenticationToken authentication) {
    throw new UnsupportedOperationException(
        "Get token is not supported for Identity authentication");
  }
}
