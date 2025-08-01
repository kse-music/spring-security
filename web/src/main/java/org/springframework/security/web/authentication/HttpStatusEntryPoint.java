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

package org.springframework.security.web.authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.util.Assert;

/**
 * An {@link AuthenticationEntryPoint} that sends a generic {@link HttpStatus} as a
 * response. Useful for JavaScript clients which cannot use Basic authentication since the
 * browser intercepts the response.
 *
 * @author Rob Winch
 * @since 4.0
 */
public final class HttpStatusEntryPoint implements AuthenticationEntryPoint {

	private final HttpStatus httpStatus;

	/**
	 * Creates a new instance.
	 * @param httpStatus the HttpStatus to set
	 */
	public HttpStatusEntryPoint(HttpStatus httpStatus) {
		Assert.notNull(httpStatus, "httpStatus cannot be null");
		this.httpStatus = httpStatus;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) {
		response.setStatus(this.httpStatus.value());
	}

}
