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

package org.springframework.security.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jspecify.annotations.Nullable;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * An {@link ExecutorService} which wraps each {@link Runnable} in a
 * {@link DelegatingSecurityContextRunnable} and each {@link Callable} in a
 * {@link DelegatingSecurityContextCallable}.
 *
 * @author Rob Winch
 * @since 3.2
 */
public class DelegatingSecurityContextExecutorService extends DelegatingSecurityContextExecutor
		implements ExecutorService {

	/**
	 * Creates a new {@link DelegatingSecurityContextExecutorService} that uses the
	 * specified {@link SecurityContext}.
	 * @param delegateExecutorService the {@link ExecutorService} to delegate to. Cannot
	 * be null.
	 * @param securityContext the {@link SecurityContext} to use for each
	 * {@link DelegatingSecurityContextRunnable} and each
	 * {@link DelegatingSecurityContextCallable}.
	 */
	public DelegatingSecurityContextExecutorService(ExecutorService delegateExecutorService,
			@Nullable SecurityContext securityContext) {
		super(delegateExecutorService, securityContext);
	}

	/**
	 * Creates a new {@link DelegatingSecurityContextExecutorService} that uses the
	 * current {@link SecurityContext} from the {@link SecurityContextHolder}.
	 * @param delegate the {@link ExecutorService} to delegate to. Cannot be null.
	 */
	public DelegatingSecurityContextExecutorService(ExecutorService delegate) {
		this(delegate, null);
	}

	@Override
	public final void shutdown() {
		getDelegate().shutdown();
	}

	@Override
	public final List<Runnable> shutdownNow() {
		return getDelegate().shutdownNow();
	}

	@Override
	public final boolean isShutdown() {
		return getDelegate().isShutdown();
	}

	@Override
	public final boolean isTerminated() {
		return getDelegate().isTerminated();
	}

	@Override
	public final boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return getDelegate().awaitTermination(timeout, unit);
	}

	@Override
	public final <T> Future<T> submit(Callable<T> task) {
		return getDelegate().submit(wrap(task));
	}

	@Override
	public final <T> Future<T> submit(Runnable task, T result) {
		return getDelegate().submit(wrap(task), result);
	}

	@Override
	public final Future<?> submit(Runnable task) {
		return getDelegate().submit(wrap(task));
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public final List invokeAll(Collection tasks) throws InterruptedException {
		tasks = createTasks(tasks);
		return getDelegate().invokeAll(tasks);
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public final List invokeAll(Collection tasks, long timeout, TimeUnit unit) throws InterruptedException {
		tasks = createTasks(tasks);
		return getDelegate().invokeAll(tasks, timeout, unit);
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public final Object invokeAny(Collection tasks) throws InterruptedException, ExecutionException {
		tasks = createTasks(tasks);
		return getDelegate().invokeAny(tasks);
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public final Object invokeAny(Collection tasks, long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		tasks = createTasks(tasks);
		return getDelegate().invokeAny(tasks, timeout, unit);
	}

	private <T> @Nullable Collection<Callable<T>> createTasks(Collection<Callable<T>> tasks) {
		if (tasks == null) {
			return null;
		}
		List<Callable<T>> results = new ArrayList<>(tasks.size());
		for (Callable<T> task : tasks) {
			results.add(wrap(task));
		}
		return results;
	}

	private ExecutorService getDelegate() {
		return (ExecutorService) getDelegateExecutor();
	}

}
