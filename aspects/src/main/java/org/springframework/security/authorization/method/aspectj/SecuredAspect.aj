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
package org.springframework.security.authorization.method.aspectj;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.access.annotation.Secured;

/**
 * Concrete AspectJ aspect using Spring Security @Secured annotation.
 *
 * <p>
 * When using this aspect, you <i>must</i> annotate the implementation class
 * (and/or methods within that class), <i>not</i> the interface (if any) that
 * the class implements. AspectJ follows Java's rule that annotations on
 * interfaces are <i>not</i> inherited. This will vary from Spring AOP.
 *
 * @author Mike Wiesner
 * @author Luke Taylor
 * @author Josh Cummings
 * @since 5.8
 */
public aspect SecuredAspect extends AbstractMethodInterceptorAspect {

	/**
	 * Matches the execution of any public method in a type with the Secured
	 * annotation, or any subtype of a type with the Secured annotation.
	 */
	private pointcut executionOfAnyPublicMethodInAtSecuredType() :
			execution(public * ((@Secured *)+).*(..)) && @this(Secured);

	/**
	 * Matches the execution of any method with the Secured annotation.
	 */
	private pointcut executionOfSecuredMethod() :
			execution(* *(..)) && @annotation(Secured);

	protected pointcut executionOfAnnotatedMethod() :
			executionOfAnyPublicMethodInAtSecuredType() ||
			executionOfSecuredMethod();
}
