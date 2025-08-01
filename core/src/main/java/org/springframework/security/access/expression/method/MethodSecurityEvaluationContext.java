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

package org.springframework.security.access.expression.method;

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.NullUnmarked;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.parameters.DefaultSecurityParameterNameDiscoverer;

/**
 * Internal security-specific EvaluationContext implementation which lazily adds the
 * method parameter values as variables (with the corresponding parameter names) if and
 * when they are required.
 *
 * @author Luke Taylor
 * @author Daniel Bustamante
 * @author Evgeniy Cheban
 * @since 3.0
 */
class MethodSecurityEvaluationContext extends MethodBasedEvaluationContext {

	/**
	 * Intended for testing. Don't use in practice as it creates a new parameter resolver
	 * for each instance. Use the constructor which takes the resolver, as an argument
	 * thus allowing for caching.
	 */
	MethodSecurityEvaluationContext(Authentication user, MethodInvocation mi) {
		this(user, mi, new DefaultSecurityParameterNameDiscoverer());
	}

	@NullUnmarked // FIXME: rootObject in MethodBasedEvaluationContext is non-null
					// (probably needs changed) but StandardEvaluationContext is Nullable
	MethodSecurityEvaluationContext(Authentication user, MethodInvocation mi,
			ParameterNameDiscoverer parameterNameDiscoverer) {
		super(mi.getThis(), getSpecificMethod(mi), mi.getArguments(), parameterNameDiscoverer);
	}

	MethodSecurityEvaluationContext(MethodSecurityExpressionOperations root, MethodInvocation mi,
			ParameterNameDiscoverer parameterNameDiscoverer) {
		super(root, getSpecificMethod(mi), mi.getArguments(), parameterNameDiscoverer);
	}

	private static Method getSpecificMethod(MethodInvocation mi) {
		Class<?> targetClass = (mi.getThis() != null) ? AopProxyUtils.ultimateTargetClass(mi.getThis()) : null;
		return AopUtils.getMostSpecificMethod(mi.getMethod(), targetClass);
	}

}
