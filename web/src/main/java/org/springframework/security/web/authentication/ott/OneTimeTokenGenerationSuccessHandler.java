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

package org.springframework.security.web.authentication.ott;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.ott.OneTimeToken;

/**
 * Defines a strategy to handle generated one-time tokens.
 *
 * @author Marcus da Coregio
 * @since 6.4
 */
@FunctionalInterface
public interface OneTimeTokenGenerationSuccessHandler {

	/**
	 * Handles generated one-time tokens
	 */
	void handle(HttpServletRequest request, HttpServletResponse response, OneTimeToken oneTimeToken)
			throws IOException, ServletException;

}
