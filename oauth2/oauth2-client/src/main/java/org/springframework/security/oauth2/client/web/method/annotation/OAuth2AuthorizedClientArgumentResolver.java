/*
 * Copyright 2004-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.security.oauth2.client.web.method.annotation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * An implementation of a {@link HandlerMethodArgumentResolver} that is capable of
 * resolving a method parameter to an argument value of type
 * {@link OAuth2AuthorizedClient}.
 *
 * <p>
 * For example: <pre>
 * &#64;Controller
 * public class MyController {
 *     &#64;GetMapping("/authorized-client")
 *     public String authorizedClient(@RegisteredOAuth2AuthorizedClient("login-client") OAuth2AuthorizedClient authorizedClient) {
 *         // do something with authorizedClient
 *     }
 * }
 * </pre>
 *
 * @author Joe Grandja
 * @since 5.1
 * @see RegisteredOAuth2AuthorizedClient
 */
public final class OAuth2AuthorizedClientArgumentResolver implements HandlerMethodArgumentResolver {

	private static final Authentication ANONYMOUS_AUTHENTICATION = new AnonymousAuthenticationToken("anonymous",
			"anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

	private SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
		.getContextHolderStrategy();

	private OAuth2AuthorizedClientManager authorizedClientManager;

	/**
	 * Constructs an {@code OAuth2AuthorizedClientArgumentResolver} using the provided
	 * parameters.
	 * @param authorizedClientManager the {@link OAuth2AuthorizedClientManager} which
	 * manages the authorized client(s)
	 * @since 5.2
	 */
	public OAuth2AuthorizedClientArgumentResolver(OAuth2AuthorizedClientManager authorizedClientManager) {
		Assert.notNull(authorizedClientManager, "authorizedClientManager cannot be null");
		this.authorizedClientManager = authorizedClientManager;
	}

	/**
	 * Constructs an {@code OAuth2AuthorizedClientArgumentResolver} using the provided
	 * parameters.
	 * @param clientRegistrationRepository the repository of client registrations
	 * @param authorizedClientRepository the repository of authorized clients
	 */
	public OAuth2AuthorizedClientArgumentResolver(ClientRegistrationRepository clientRegistrationRepository,
			OAuth2AuthorizedClientRepository authorizedClientRepository) {
		Assert.notNull(clientRegistrationRepository, "clientRegistrationRepository cannot be null");
		Assert.notNull(authorizedClientRepository, "authorizedClientRepository cannot be null");
		this.authorizedClientManager = new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository,
				authorizedClientRepository);
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> parameterType = parameter.getParameterType();
		return (OAuth2AuthorizedClient.class.isAssignableFrom(parameterType) && (AnnotatedElementUtils
			.findMergedAnnotation(parameter.getParameter(), RegisteredOAuth2AuthorizedClient.class) != null));
	}

	@NonNull
	@Override
	public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) {
		String clientRegistrationId = this.resolveClientRegistrationId(parameter);
		if (!StringUtils.hasLength(clientRegistrationId)) {
			throw new IllegalArgumentException("Unable to resolve the Client Registration Identifier. "
					+ "It must be provided via @RegisteredOAuth2AuthorizedClient(\"client1\") or "
					+ "@RegisteredOAuth2AuthorizedClient(registrationId = \"client1\").");
		}
		Authentication principal = this.securityContextHolderStrategy.getContext().getAuthentication();
		if (principal == null) {
			principal = ANONYMOUS_AUTHENTICATION;
		}
		HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		HttpServletResponse servletResponse = webRequest.getNativeResponse(HttpServletResponse.class);
		// @formatter:off
		OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
				.withClientRegistrationId(clientRegistrationId)
				.principal(principal)
				.attribute(HttpServletRequest.class.getName(), servletRequest)
				.attribute(HttpServletResponse.class.getName(), servletResponse)
				.build();
		// @formatter:on
		return this.authorizedClientManager.authorize(authorizeRequest);
	}

	private String resolveClientRegistrationId(MethodParameter parameter) {
		RegisteredOAuth2AuthorizedClient authorizedClientAnnotation = AnnotatedElementUtils
			.findMergedAnnotation(parameter.getParameter(), RegisteredOAuth2AuthorizedClient.class);
		Authentication principal = this.securityContextHolderStrategy.getContext().getAuthentication();
		if (StringUtils.hasLength(authorizedClientAnnotation.registrationId())) {
			return authorizedClientAnnotation.registrationId();
		}
		if (StringUtils.hasLength(authorizedClientAnnotation.value())) {
			return authorizedClientAnnotation.value();
		}
		if (principal != null && OAuth2AuthenticationToken.class.isAssignableFrom(principal.getClass())) {
			return ((OAuth2AuthenticationToken) principal).getAuthorizedClientRegistrationId();
		}
		return null;
	}

	/**
	 * Sets the {@link SecurityContextHolderStrategy} to use. The default action is to use
	 * the {@link SecurityContextHolderStrategy} stored in {@link SecurityContextHolder}.
	 *
	 * @since 5.8
	 */
	public void setSecurityContextHolderStrategy(SecurityContextHolderStrategy securityContextHolderStrategy) {
		Assert.notNull(securityContextHolderStrategy, "securityContextHolderStrategy cannot be null");
		this.securityContextHolderStrategy = securityContextHolderStrategy;
	}

}
