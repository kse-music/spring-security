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

package org.springframework.security.web.authentication.logout;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.firewall.DefaultHttpFirewall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.web.servlet.TestMockHttpServletRequests.get;
import static org.springframework.security.web.servlet.TestMockHttpServletRequests.post;

/**
 * @author Luke Taylor
 */
public class LogoutHandlerTests {

	LogoutFilter filter;

	@BeforeEach
	public void setUp() {
		this.filter = new LogoutFilter("/success", new SecurityContextLogoutHandler());
	}

	@Test
	public void testRequiresLogoutUrlWorksWithPathParams() {
		MockHttpServletRequest request = post().requestUri("/context", "/logout;someparam=blah", null)
			.queryString("otherparam=blah")
			.build();
		MockHttpServletResponse response = new MockHttpServletResponse();
		DefaultHttpFirewall fw = new DefaultHttpFirewall();
		assertThat(this.filter.requiresLogout(fw.getFirewalledRequest(request), response)).isTrue();
	}

	@Test
	public void testRequiresLogoutUrlWorksWithQueryParams() {
		MockHttpServletRequest request = get().requestUri("/context", "/logout", null)
			.queryString("otherparam=blah")
			.build();
		MockHttpServletResponse response = new MockHttpServletResponse();
		assertThat(this.filter.requiresLogout(request, response)).isTrue();
	}

}
