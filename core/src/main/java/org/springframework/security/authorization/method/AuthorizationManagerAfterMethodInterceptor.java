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

package org.springframework.security.authorization.method;

import java.util.function.Supplier;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.aop.Pointcut;
import org.springframework.core.log.LogMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.util.Assert;

/**
 * A {@link MethodInterceptor} which can determine if an {@link Authentication} has access
 * to the result of an {@link MethodInvocation} using an {@link AuthorizationManager}
 *
 * @author Evgeniy Cheban
 * @author Josh Cummings
 * @since 5.6
 */
public final class AuthorizationManagerAfterMethodInterceptor implements AuthorizationAdvisor {

	private Supplier<SecurityContextHolderStrategy> securityContextHolderStrategy = SecurityContextHolder::getContextHolderStrategy;

	private final Log logger = LogFactory.getLog(this.getClass());

	private final Pointcut pointcut;

	private final AuthorizationManager<MethodInvocationResult> authorizationManager;

	private final MethodAuthorizationDeniedHandler defaultHandler = new ThrowingMethodAuthorizationDeniedHandler();

	private int order;

	private AuthorizationEventPublisher eventPublisher = new NoOpAuthorizationEventPublisher();

	/**
	 * Creates an instance.
	 * @param pointcut the {@link Pointcut} to use
	 * @param authorizationManager the {@link AuthorizationManager} to use
	 */
	public AuthorizationManagerAfterMethodInterceptor(Pointcut pointcut,
			AuthorizationManager<MethodInvocationResult> authorizationManager) {
		Assert.notNull(pointcut, "pointcut cannot be null");
		Assert.notNull(authorizationManager, "authorizationManager cannot be null");
		this.pointcut = pointcut;
		this.authorizationManager = authorizationManager;
	}

	/**
	 * Creates an interceptor for the {@link PostAuthorize} annotation
	 * @return the interceptor
	 */
	public static AuthorizationManagerAfterMethodInterceptor postAuthorize() {
		return postAuthorize(new PostAuthorizeAuthorizationManager());
	}

	/**
	 * Creates an interceptor for the {@link PostAuthorize} annotation
	 * @param authorizationManager the {@link PostAuthorizeAuthorizationManager} to use
	 * @return the interceptor
	 */
	public static AuthorizationManagerAfterMethodInterceptor postAuthorize(
			PostAuthorizeAuthorizationManager authorizationManager) {
		AuthorizationManagerAfterMethodInterceptor interceptor = new AuthorizationManagerAfterMethodInterceptor(
				AuthorizationMethodPointcuts.forAnnotations(PostAuthorize.class), authorizationManager);
		interceptor.setOrder(AuthorizationInterceptorsOrder.POST_AUTHORIZE.getOrder());
		return interceptor;
	}

	/**
	 * Creates an interceptor for the {@link PostAuthorize} annotation
	 * @param authorizationManager the {@link AuthorizationManager} to use
	 * @return the interceptor
	 * @since 6.0
	 */
	public static AuthorizationManagerAfterMethodInterceptor postAuthorize(
			AuthorizationManager<MethodInvocationResult> authorizationManager) {
		AuthorizationManagerAfterMethodInterceptor interceptor = new AuthorizationManagerAfterMethodInterceptor(
				AuthorizationMethodPointcuts.forAnnotations(PostAuthorize.class), authorizationManager);
		interceptor.setOrder(AuthorizationInterceptorsOrder.POST_AUTHORIZE.getOrder());
		return interceptor;
	}

	/**
	 * Determine if an {@link Authentication} has access to the {@link MethodInvocation}
	 * using the {@link AuthorizationManager}.
	 * @param mi the {@link MethodInvocation} to check
	 * @throws AccessDeniedException if access is not granted
	 */
	@Override
	public @Nullable Object invoke(MethodInvocation mi) throws Throwable {
		Object result;
		try {
			result = mi.proceed();
		}
		catch (AuthorizationDeniedException ex) {
			if (this.authorizationManager instanceof MethodAuthorizationDeniedHandler handler) {
				return handler.handleDeniedInvocation(mi, ex);
			}
			return this.defaultHandler.handleDeniedInvocation(mi, ex);
		}
		return attemptAuthorization(mi, result);
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	/**
	 * Use this {@link AuthorizationEventPublisher} to publish the
	 * {@link AuthorizationManager} result.
	 * @param eventPublisher
	 * @since 5.7
	 */
	public void setAuthorizationEventPublisher(AuthorizationEventPublisher eventPublisher) {
		Assert.notNull(eventPublisher, "eventPublisher cannot be null");
		this.eventPublisher = eventPublisher;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Pointcut getPointcut() {
		return this.pointcut;
	}

	@Override
	public Advice getAdvice() {
		return this;
	}

	@Override
	public boolean isPerInstance() {
		return true;
	}

	/**
	 * Sets the {@link SecurityContextHolderStrategy} to use. The default action is to use
	 * the {@link SecurityContextHolderStrategy} stored in {@link SecurityContextHolder}.
	 *
	 * @since 5.8
	 */
	public void setSecurityContextHolderStrategy(SecurityContextHolderStrategy strategy) {
		this.securityContextHolderStrategy = () -> strategy;
	}

	private @Nullable Object attemptAuthorization(MethodInvocation mi, @Nullable Object result) {
		this.logger.debug(LogMessage.of(() -> "Authorizing method invocation " + mi));
		MethodInvocationResult object = new MethodInvocationResult(mi, result);
		AuthorizationResult authorizationResult = this.authorizationManager.authorize(this::getAuthentication, object);
		if (authorizationResult != null) {
			this.eventPublisher.publishAuthorizationEvent(this::getAuthentication, object, authorizationResult);
		}
		if (authorizationResult != null && !authorizationResult.isGranted()) {
			this.logger.debug(LogMessage.of(() -> "Failed to authorize " + mi + " with authorization manager "
					+ this.authorizationManager + " and authorizationResult " + authorizationResult));
			return handlePostInvocationDenied(object, authorizationResult);
		}
		this.logger.debug(LogMessage.of(() -> "Authorized method invocation " + mi));
		return result;
	}

	private @Nullable Object handlePostInvocationDenied(MethodInvocationResult mi, AuthorizationResult result) {
		if (this.authorizationManager instanceof MethodAuthorizationDeniedHandler deniedHandler) {
			return deniedHandler.handleDeniedInvocationResult(mi, result);
		}
		return this.defaultHandler.handleDeniedInvocationResult(mi, result);
	}

	private Authentication getAuthentication() {
		Authentication authentication = this.securityContextHolderStrategy.get().getContext().getAuthentication();
		if (authentication == null) {
			throw new AuthenticationCredentialsNotFoundException(
					"An Authentication object was not found in the SecurityContext");
		}
		return authentication;
	}

}
