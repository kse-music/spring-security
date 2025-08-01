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

package org.springframework.security.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.log.LogMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.util.Assert;

/**
 * Simple implementation of <tt>RedirectStrategy</tt> which is the default used throughout
 * the framework.
 *
 * @author Luke Taylor
 * @author Mark Chesney
 * @since 3.0
 */
public class DefaultRedirectStrategy implements RedirectStrategy {

	protected final Log logger = LogFactory.getLog(getClass());

	private boolean contextRelative;

	private HttpStatus statusCode = HttpStatus.FOUND;

	/**
	 * Redirects the response to the supplied URL.
	 * <p>
	 * If <tt>contextRelative</tt> is set, the redirect value will be the value after the
	 * request context path. Note that this will result in the loss of protocol
	 * information (HTTP or HTTPS), so will cause problems if a redirect is being
	 * performed to change to HTTPS, for example.
	 */
	@Override
	public void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url) throws IOException {
		String redirectUrl = calculateRedirectUrl(request.getContextPath(), url);
		redirectUrl = response.encodeRedirectURL(redirectUrl);
		if (this.logger.isDebugEnabled()) {
			this.logger.debug(LogMessage.format("Redirecting to %s", redirectUrl));
		}
		if (this.statusCode == HttpStatus.FOUND) {
			response.sendRedirect(redirectUrl);
		}
		else {
			response.setHeader(HttpHeaders.LOCATION, redirectUrl);
			response.setStatus(this.statusCode.value());
			response.getWriter().flush();
		}
	}

	protected String calculateRedirectUrl(String contextPath, String url) {
		if (!UrlUtils.isAbsoluteUrl(url)) {
			if (isContextRelative()) {
				return url;
			}
			return contextPath + url;
		}
		// Full URL, including http(s)://
		if (!isContextRelative()) {
			return url;
		}
		Assert.isTrue(url.contains(contextPath), "The fully qualified URL does not include context path.");
		// Calculate the relative URL from the fully qualified URL, minus the last
		// occurrence of the scheme and base context.
		url = url.substring(url.lastIndexOf("://") + 3);
		url = url.substring(url.indexOf(contextPath) + contextPath.length());
		if (url.length() > 1 && url.charAt(0) == '/') {
			url = url.substring(1);
		}
		return url;
	}

	/**
	 * If <tt>true</tt>, causes any redirection URLs to be calculated minus the protocol
	 * and context path (defaults to <tt>false</tt>).
	 */
	public void setContextRelative(boolean useRelativeContext) {
		this.contextRelative = useRelativeContext;
	}

	/**
	 * Returns <tt>true</tt>, if the redirection URL should be calculated minus the
	 * protocol and context path (defaults to <tt>false</tt>).
	 */
	protected boolean isContextRelative() {
		return this.contextRelative;
	}

	/**
	 * Sets the HTTP status code to use. The default is {@link HttpStatus#FOUND}.
	 * <p>
	 * Note that according to RFC 7231, with {@link HttpStatus#FOUND}, a user agent MAY
	 * change the request method from POST to GET for the subsequent request. If this
	 * behavior is undesired, {@link HttpStatus#TEMPORARY_REDIRECT} can be used instead.
	 * @param statusCode the HTTP status code to use.
	 * @since 6.2
	 */
	public void setStatusCode(HttpStatus statusCode) {
		Assert.notNull(statusCode, "statusCode cannot be null");
		this.statusCode = statusCode;
	}

}
