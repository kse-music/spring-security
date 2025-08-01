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

package org.springframework.security.web.header;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Contract for writing headers to a {@link HttpServletResponse}
 *
 * @author Marten Deinum
 * @author Rob Winch
 * @since 3.2
 * @see HeaderWriterFilter
 */
public interface HeaderWriter {

	/**
	 * Create a {@code Header} instance.
	 * @param request the request
	 * @param response the response
	 */
	void writeHeaders(HttpServletRequest request, HttpServletResponse response);

}
