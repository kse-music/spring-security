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

package org.springframework.security.web.jackson2;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.springframework.security.web.savedrequest.DefaultSavedRequest;

/**
 * Jackson mixin class to serialize/deserialize {@link DefaultSavedRequest}. This mixin
 * use {@link org.springframework.security.web.savedrequest.DefaultSavedRequest.Builder}
 * to deserialized json.In order to use this mixin class you also need to register
 * {@link CookieMixin}.
 * <p>
 * <pre>
 *     ObjectMapper mapper = new ObjectMapper();
 *     mapper.registerModule(new WebServletJackson2Module());
 * </pre>
 *
 * @author Jitendra Singh
 * @since 4.2
 * @see WebServletJackson2Module
 * @see org.springframework.security.jackson2.SecurityJackson2Modules
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
@JsonDeserialize(builder = DefaultSavedRequest.Builder.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE)
abstract class DefaultSavedRequestMixin {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	String matchingRequestParameterName;

}
