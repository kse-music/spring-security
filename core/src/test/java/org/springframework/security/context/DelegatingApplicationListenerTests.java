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

package org.springframework.security.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class DelegatingApplicationListenerTests {

	@Mock
	SmartApplicationListener delegate;

	ApplicationEvent event;

	DelegatingApplicationListener listener;

	@BeforeEach
	public void setup() {
		this.event = new ApplicationEvent(this) {
		};
		this.listener = new DelegatingApplicationListener();
		this.listener.addListener(this.delegate);
	}

	@Test
	public void processEventNull() {
		this.listener.onApplicationEvent(null);
		verify(this.delegate, never()).onApplicationEvent(any(ApplicationEvent.class));
	}

	@Test
	public void processEventSuccess() {
		given(this.delegate.supportsEventType(this.event.getClass())).willReturn(true);
		given(this.delegate.supportsSourceType(this.event.getSource().getClass())).willReturn(true);
		this.listener.onApplicationEvent(this.event);
		verify(this.delegate).onApplicationEvent(this.event);
	}

	@Test
	public void processEventEventTypeNotSupported() {
		this.listener.onApplicationEvent(this.event);
		verify(this.delegate, never()).onApplicationEvent(any(ApplicationEvent.class));
	}

	@Test
	public void processEventSourceTypeNotSupported() {
		given(this.delegate.supportsEventType(this.event.getClass())).willReturn(true);
		this.listener.onApplicationEvent(this.event);
		verify(this.delegate, never()).onApplicationEvent(any(ApplicationEvent.class));
	}

	@Test
	public void addNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.listener.addListener(null));
	}

}
