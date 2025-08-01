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

package org.springframework.security.config.annotation.method.configuration;

import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationTextPublisher;
import jakarta.annotation.security.DenyAll;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.JdkRegexpMethodPointcut;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Role;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationConfigurationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoPage;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.annotation.BusinessService;
import org.springframework.security.access.annotation.BusinessServiceImpl;
import org.springframework.security.access.annotation.ExpressionProtectedBusinessServiceImpl;
import org.springframework.security.access.annotation.Jsr250BusinessServiceImpl;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.authorization.method.AuthorizationAdvisor;
import org.springframework.security.authorization.method.AuthorizationAdvisorProxyFactory;
import org.springframework.security.authorization.method.AuthorizationAdvisorProxyFactory.TargetVisitor;
import org.springframework.security.authorization.method.AuthorizationInterceptorsOrder;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.AuthorizeReturnObject;
import org.springframework.security.authorization.method.MethodAuthorizationDeniedHandler;
import org.springframework.security.authorization.method.MethodInvocationResult;
import org.springframework.security.config.annotation.SecurityContextChangedListenerConfig;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.observation.SecurityObservationSettings;
import org.springframework.security.config.test.SpringTestContext;
import org.springframework.security.config.test.SpringTestContextExtension;
import org.springframework.security.config.test.SpringTestParentApplicationContextExecutionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithSecurityContextTestExecutionListener;
import org.springframework.security.web.util.ThrowableAnalyzer;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link PrePostMethodSecurityConfiguration}.
 *
 * @author Evgeniy Cheban
 * @author Josh Cummings
 */
@ExtendWith({ SpringExtension.class, SpringTestContextExtension.class })
@ContextConfiguration(classes = SecurityContextChangedListenerConfig.class)
@TestExecutionListeners(listeners = { WithSecurityContextTestExecutionListener.class,
		SpringTestParentApplicationContextExecutionListener.class })
public class PrePostMethodSecurityConfigurationTests {

	public final SpringTestContext spring = new SpringTestContext(this);

	@Autowired(required = false)
	MethodSecurityService methodSecurityService;

	@Autowired(required = false)
	BusinessService businessService;

	@Autowired(required = false)
	MockMvc mvc;

	@WithMockUser
	@Test
	public void customMethodSecurityPreAuthorizeAdminWhenRoleUserThenAccessDeniedException() {
		this.spring.register(CustomMethodSecurityServiceConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::preAuthorizeAdmin)
			.withMessage("Access Denied");
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void customMethodSecurityPreAuthorizeAdminWhenRoleAdminThenPasses() {
		this.spring.register(CustomMethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeAdmin();
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void preAuthorizeWhenRoleAdminThenAccessDeniedException() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::preAuthorize)
			.withMessage("Access Denied");
	}

	@WithAnonymousUser
	@Test
	public void preAuthorizePermitAllWhenRoleAnonymousThenPasses() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		String result = this.methodSecurityService.preAuthorizePermitAll();
		assertThat(result).isNull();
	}

	@WithAnonymousUser
	@Test
	public void preAuthorizeNotAnonymousWhenRoleAnonymousThenAccessDeniedException() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(this.methodSecurityService::preAuthorizeNotAnonymous)
			.withMessage("Access Denied");
	}

	@WithMockUser
	@Test
	public void preAuthorizeNotAnonymousWhenRoleUserThenPasses() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeNotAnonymous();
	}

	@WithMockUser
	@Test
	public void securedWhenRoleUserThenAccessDeniedException() {
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::secured)
			.withMessage("Access Denied");
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void securedWhenRoleAdminThenPasses() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		String result = this.methodSecurityService.secured();
		assertThat(result).isNull();
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void securedUserWhenRoleAdminThenAccessDeniedException() {
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::securedUser)
			.withMessage("Access Denied");
		SecurityContextHolderStrategy strategy = this.spring.getContext().getBean(SecurityContextHolderStrategy.class);
		verify(strategy, atLeastOnce()).getContext();
	}

	@WithMockUser
	@Test
	public void securedUserWhenRoleUserThenPasses() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		String result = this.methodSecurityService.securedUser();
		assertThat(result).isNull();
	}

	@WithMockUser
	@Test
	public void preAuthorizeAdminWhenRoleUserThenAccessDeniedException() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::preAuthorizeAdmin)
			.withMessage("Access Denied");
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void preAuthorizeAdminWhenRoleAdminThenPasses() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeAdmin();
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void preAuthorizeAdminWhenSecurityContextHolderStrategyThenUses() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeAdmin();
		SecurityContextHolderStrategy strategy = this.spring.getContext().getBean(SecurityContextHolderStrategy.class);
		verify(strategy, atLeastOnce()).getContext();
	}

	@WithMockUser(authorities = "PREFIX_ADMIN")
	@Test
	public void preAuthorizeAdminWhenRoleAdminAndCustomPrefixThenPasses() {
		this.spring.register(CustomGrantedAuthorityDefaultsConfig.class, MethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeAdmin();
	}

	@WithMockUser
	@Test
	public void postHasPermissionWhenParameterIsNotGrantThenAccessDeniedException() {
		this.spring.register(CustomPermissionEvaluatorConfig.class, MethodSecurityServiceConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.postHasPermission("deny"))
			.withMessage("Access Denied");
	}

	@WithMockUser
	@Test
	public void postHasPermissionWhenParameterIsGrantThenPasses() {
		this.spring.register(CustomPermissionEvaluatorConfig.class, MethodSecurityServiceConfig.class).autowire();
		String result = this.methodSecurityService.postHasPermission("grant");
		assertThat(result).isNull();
	}

	@WithMockUser
	@Test
	public void postAnnotationWhenParameterIsNotGrantThenAccessDeniedException() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.postAnnotation("deny"))
			.withMessage("Access Denied");
	}

	@WithMockUser
	@Test
	public void postAnnotationWhenParameterIsGrantThenPasses() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		String result = this.methodSecurityService.postAnnotation("grant");
		assertThat(result).isNull();
	}

	@WithMockUser("bob")
	@Test
	public void methodReturningAListWhenPrePostFiltersConfiguredThenFiltersList() {
		this.spring.register(BusinessServiceConfig.class).autowire();
		List<String> names = new ArrayList<>();
		names.add("bob");
		names.add("joe");
		names.add("sam");
		List<?> result = this.businessService.methodReturningAList(names);
		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isEqualTo("bob");
	}

	@WithMockUser("bob")
	@Test
	public void methodReturningAnArrayWhenPostFilterConfiguredThenFiltersArray() {
		this.spring.register(BusinessServiceConfig.class).autowire();
		List<String> names = new ArrayList<>();
		names.add("bob");
		names.add("joe");
		names.add("sam");
		Object[] result = this.businessService.methodReturningAnArray(names.toArray());
		assertThat(result).hasSize(1);
		assertThat(result[0]).isEqualTo("bob");
	}

	@WithMockUser("bob")
	@Test
	public void securedUserWhenCustomBeforeAdviceConfiguredAndNameBobThenPasses() {
		this.spring.register(CustomAuthorizationManagerBeforeAdviceConfig.class, MethodSecurityServiceConfig.class)
			.autowire();
		String result = this.methodSecurityService.securedUser();
		assertThat(result).isNull();
	}

	@WithMockUser("joe")
	@Test
	public void securedUserWhenCustomBeforeAdviceConfiguredAndNameNotBobThenAccessDeniedException() {
		this.spring.register(CustomAuthorizationManagerBeforeAdviceConfig.class, MethodSecurityServiceConfig.class)
			.autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::securedUser)
			.withMessage("Access Denied");
	}

	@WithMockUser("bob")
	@Test
	public void securedUserWhenCustomAfterAdviceConfiguredAndNameBobThenGranted() {
		this.spring.register(CustomAuthorizationManagerAfterAdviceConfig.class, MethodSecurityServiceConfig.class)
			.autowire();
		String result = this.methodSecurityService.securedUser();
		assertThat(result).isEqualTo("granted");
	}

	@WithMockUser("joe")
	@Test
	public void securedUserWhenCustomAfterAdviceConfiguredAndNameNotBobThenAccessDeniedException() {
		this.spring.register(CustomAuthorizationManagerAfterAdviceConfig.class, MethodSecurityServiceConfig.class)
			.autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::securedUser)
			.withMessage("Access Denied for User 'joe'");
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void jsr250WhenRoleAdminThenAccessDeniedException() {
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::jsr250)
			.withMessage("Access Denied");
	}

	@WithAnonymousUser
	@Test
	public void jsr250PermitAllWhenRoleAnonymousThenPasses() {
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		String result = this.methodSecurityService.jsr250PermitAll();
		assertThat(result).isNull();
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void rolesAllowedUserWhenRoleAdminThenAccessDeniedException() {
		this.spring.register(BusinessServiceConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.businessService::rolesAllowedUser)
			.withMessage("Access Denied");
		SecurityContextHolderStrategy strategy = this.spring.getContext().getBean(SecurityContextHolderStrategy.class);
		verify(strategy, atLeastOnce()).getContext();
	}

	@WithMockUser
	@Test
	public void rolesAllowedUserWhenRoleUserThenPasses() {
		this.spring.register(BusinessServiceConfig.class).autowire();
		this.businessService.rolesAllowedUser();
	}

	@WithMockUser(roles = { "ADMIN", "USER" })
	@Test
	public void manyAnnotationsWhenMeetsConditionsThenReturnsFilteredList() throws Exception {
		List<String> names = Arrays.asList("harold", "jonathan", "pete", "bo");
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		List<String> filtered = this.methodSecurityService.manyAnnotations(new ArrayList<>(names));
		assertThat(filtered).hasSize(2);
		assertThat(filtered).containsExactly("harold", "jonathan");
	}

	// gh-4003
	// gh-4103
	@WithMockUser
	@Test
	public void manyAnnotationsWhenUserThenFails() {
		List<String> names = Arrays.asList("harold", "jonathan", "pete", "bo");
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.manyAnnotations(new ArrayList<>(names)));
	}

	@WithMockUser
	@Test
	public void manyAnnotationsWhenShortListThenFails() {
		List<String> names = Arrays.asList("harold", "jonathan", "pete");
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.manyAnnotations(new ArrayList<>(names)));
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void manyAnnotationsWhenAdminThenFails() {
		List<String> names = Arrays.asList("harold", "jonathan", "pete", "bo");
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.manyAnnotations(new ArrayList<>(names)));
	}

	// gh-3183
	@Test
	public void repeatedAnnotationsWhenPresentThenFails() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		assertThatExceptionOfType(AnnotationConfigurationException.class)
			.isThrownBy(() -> this.methodSecurityService.repeatedAnnotations());
	}

	// gh-3183
	@Test
	public void repeatedJsr250AnnotationsWhenPresentThenFails() {
		this.spring.register(Jsr250Config.class).autowire();
		assertThatExceptionOfType(AnnotationConfigurationException.class)
			.isThrownBy(() -> this.businessService.repeatedAnnotations());
	}

	// gh-3183
	@Test
	public void repeatedSecuredAnnotationsWhenPresentThenFails() {
		this.spring.register(SecuredConfig.class).autowire();
		assertThatExceptionOfType(AnnotationConfigurationException.class)
			.isThrownBy(() -> this.businessService.repeatedAnnotations());
	}

	@WithMockUser
	@Test
	public void preAuthorizeWhenAuthorizationEventPublisherThenUses() {
		this.spring.register(MethodSecurityServiceConfig.class, AuthorizationEventPublisherConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.preAuthorize());
		AuthorizationEventPublisher publisher = this.spring.getContext().getBean(AuthorizationEventPublisher.class);
		verify(publisher).publishAuthorizationEvent(any(Supplier.class), any(MethodInvocation.class),
				any(AuthorizationDecision.class));
	}

	@WithMockUser
	@Test
	public void postAuthorizeWhenAuthorizationEventPublisherThenUses() {
		this.spring.register(MethodSecurityServiceConfig.class, AuthorizationEventPublisherConfig.class).autowire();
		this.methodSecurityService.postAnnotation("grant");
		AuthorizationEventPublisher publisher = this.spring.getContext().getBean(AuthorizationEventPublisher.class);
		verify(publisher).publishAuthorizationEvent(any(Supplier.class), any(MethodInvocationResult.class),
				any(AuthorizationDecision.class));
	}

	// gh-10305
	@WithMockUser
	@Test
	public void beanInSpelWhenEvaluatedThenLooksUpBean() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeBean(true);
	}

	@Test
	public void configureWhenAspectJThenRegistersAspects() {
		this.spring.register(AspectJMethodSecurityServiceConfig.class).autowire();
		assertThat(this.spring.getContext().containsBean("preFilterAspect$0")).isTrue();
		assertThat(this.spring.getContext().containsBean("postFilterAspect$0")).isTrue();
		assertThat(this.spring.getContext().containsBean("preAuthorizeAspect$0")).isTrue();
		assertThat(this.spring.getContext().containsBean("postAuthorizeAspect$0")).isTrue();
		assertThat(this.spring.getContext().containsBean("securedAspect$0")).isTrue();
		assertThat(this.spring.getContext().containsBean("annotationSecurityAspect$0")).isFalse();
	}

	@Test
	public void configureWhenBeanOverridingDisallowedThenWorks() {
		this.spring.register(MethodSecurityServiceConfig.class, BusinessServiceConfig.class)
			.postProcessor(disallowBeanOverriding())
			.autowire();
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void methodSecurityAdminWhenRoleHierarchyBeanAvailableThenUses() {
		this.spring.register(RoleHierarchyConfig.class, MethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeUser();
		this.methodSecurityService.securedUser();
		this.methodSecurityService.jsr250RolesAllowedUser();
	}

	@WithMockUser
	@Test
	public void methodSecurityUserWhenRoleHierarchyBeanAvailableThenUses() {
		this.spring.register(RoleHierarchyConfig.class, MethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeUser();
		this.methodSecurityService.securedUser();
		this.methodSecurityService.jsr250RolesAllowedUser();
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void methodSecurityAdminWhenAuthorizationEventPublisherBeanAvailableThenUses() {
		this.spring
			.register(RoleHierarchyConfig.class, MethodSecurityServiceConfig.class,
					AuthorizationEventPublisherConfig.class)
			.autowire();
		this.methodSecurityService.preAuthorizeUser();
		this.methodSecurityService.securedUser();
		this.methodSecurityService.jsr250RolesAllowedUser();
	}

	@WithMockUser
	@Test
	public void methodSecurityUserWhenAuthorizationEventPublisherBeanAvailableThenUses() {
		this.spring
			.register(RoleHierarchyConfig.class, MethodSecurityServiceConfig.class,
					AuthorizationEventPublisherConfig.class)
			.autowire();
		this.methodSecurityService.preAuthorizeUser();
		this.methodSecurityService.securedUser();
		this.methodSecurityService.jsr250RolesAllowedUser();
	}

	@Test
	public void allAnnotationsWhenAdviceBeforeOffsetPreFilterThenReturnsFilteredList() {
		this.spring.register(ReturnBeforeOffsetPreFilterConfig.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		List<String> filtered = this.methodSecurityService.allAnnotations(new ArrayList<>(list));
		assertThat(filtered).hasSize(5);
		assertThat(filtered).containsExactly("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
	}

	@Test
	public void allAnnotationsWhenAdviceBeforeOffsetPreAuthorizeThenReturnsFilteredList() {
		this.spring.register(ReturnBeforeOffsetPreAuthorizeConfig.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		List<String> filtered = this.methodSecurityService.allAnnotations(new ArrayList<>(list));
		assertThat(filtered).hasSize(4);
		assertThat(filtered).containsExactly("DropOnPreAuthorize", "DropOnPostAuthorize", "DropOnPostFilter",
				"DoNotDrop");
	}

	@Test
	public void allAnnotationsWhenAdviceBeforeOffsetSecuredThenReturnsFilteredList() {
		this.spring.register(ReturnBeforeOffsetSecuredConfig.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		List<String> filtered = this.methodSecurityService.allAnnotations(new ArrayList<>(list));
		assertThat(filtered).hasSize(3);
		assertThat(filtered).containsExactly("DropOnPostAuthorize", "DropOnPostFilter", "DoNotDrop");
	}

	@Test
	@WithMockUser
	public void allAnnotationsWhenAdviceBeforeOffsetJsr250WithInsufficientRolesThenFails() {
		this.spring.register(ReturnBeforeOffsetJsr250Config.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.allAnnotations(new ArrayList<>(list)));
	}

	@Test
	@WithMockUser(roles = "SECURED")
	public void allAnnotationsWhenAdviceBeforeOffsetJsr250ThenReturnsFilteredList() {
		this.spring.register(ReturnBeforeOffsetJsr250Config.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		List<String> filtered = this.methodSecurityService.allAnnotations(new ArrayList<>(list));
		assertThat(filtered).hasSize(3);
		assertThat(filtered).containsExactly("DropOnPostAuthorize", "DropOnPostFilter", "DoNotDrop");
	}

	@Test
	@WithMockUser(roles = { "SECURED" })
	public void allAnnotationsWhenAdviceBeforeOffsetPostAuthorizeWithInsufficientRolesThenFails() {
		this.spring.register(ReturnBeforeOffsetPostAuthorizeConfig.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.allAnnotations(new ArrayList<>(list)));
	}

	@Test
	@WithMockUser(roles = { "SECURED", "JSR250" })
	public void allAnnotationsWhenAdviceBeforeOffsetPostAuthorizeThenReturnsFilteredList() {
		this.spring.register(ReturnBeforeOffsetPostAuthorizeConfig.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		List<String> filtered = this.methodSecurityService.allAnnotations(new ArrayList<>(list));
		assertThat(filtered).hasSize(3);
		assertThat(filtered).containsExactly("DropOnPostAuthorize", "DropOnPostFilter", "DoNotDrop");
	}

	@Test
	@WithMockUser(roles = { "SECURED", "JSR250" })
	public void allAnnotationsWhenAdviceBeforeOffsetPostFilterThenReturnsFilteredList() {
		this.spring.register(ReturnBeforeOffsetPostFilterConfig.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		List<String> filtered = this.methodSecurityService.allAnnotations(new ArrayList<>(list));
		assertThat(filtered).hasSize(2);
		assertThat(filtered).containsExactly("DropOnPostFilter", "DoNotDrop");
	}

	@Test
	@WithMockUser(roles = { "SECURED", "JSR250" })
	public void allAnnotationsWhenAdviceAfterAllOffsetThenReturnsFilteredList() {
		this.spring.register(ReturnAfterAllOffsetConfig.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		List<String> filtered = this.methodSecurityService.allAnnotations(new ArrayList<>(list));
		assertThat(filtered).hasSize(1);
		assertThat(filtered).containsExactly("DoNotDrop");
	}

	@ParameterizedTest
	@ValueSource(classes = { MetaAnnotationPlaceholderConfig.class })
	@WithMockUser
	public void methodeWhenParameterizedPreAuthorizeMetaAnnotationThenPasses(Class<?> config) {
		this.spring.register(config).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		assertThat(service.hasRole("USER")).isTrue();
	}

	@ParameterizedTest
	@ValueSource(classes = { MetaAnnotationPlaceholderConfig.class })
	@WithMockUser
	public void methodRoleWhenPreAuthorizeMetaAnnotationHardcodedParameterThenPasses(Class<?> config) {
		this.spring.register(config).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		assertThat(service.hasUserRole()).isTrue();
	}

	@ParameterizedTest
	@ValueSource(classes = { MetaAnnotationPlaceholderConfig.class })
	public void methodWhenParameterizedAnnotationThenFails(Class<?> config) {
		this.spring.register(config).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(service::placeholdersOnlyResolvedByMetaAnnotations);
	}

	@ParameterizedTest
	@ValueSource(classes = { MetaAnnotationPlaceholderConfig.class })
	@WithMockUser(authorities = "SCOPE_message:read")
	public void methodWhenMultiplePlaceholdersHasAuthorityThenPasses(Class<?> config) {
		this.spring.register(config).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		assertThat(service.readMessage()).isEqualTo("message");
	}

	@ParameterizedTest
	@ValueSource(classes = { MetaAnnotationPlaceholderConfig.class })
	@WithMockUser(roles = "ADMIN")
	public void methodWhenMultiplePlaceholdersHasRoleThenPasses(Class<?> config) {
		this.spring.register(config).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		assertThat(service.readMessage()).isEqualTo("message");
	}

	@ParameterizedTest
	@ValueSource(classes = { MetaAnnotationPlaceholderConfig.class })
	@WithMockUser
	public void methodWhenPostAuthorizeMetaAnnotationThenAuthorizes(Class<?> config) {
		this.spring.register(config).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		service.startsWithDave("daveMatthews");
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> service.startsWithDave("jenniferHarper"));
	}

	@ParameterizedTest
	@ValueSource(classes = { MetaAnnotationPlaceholderConfig.class })
	@WithMockUser
	public void methodWhenPreFilterMetaAnnotationThenFilters(Class<?> config) {
		this.spring.register(config).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		assertThat(service.parametersContainDave(new ArrayList<>(List.of("dave", "carla", "vanessa", "paul"))))
			.containsExactly("dave");
	}

	@ParameterizedTest
	@ValueSource(classes = { MetaAnnotationPlaceholderConfig.class })
	@WithMockUser
	public void methodWhenPostFilterMetaAnnotationThenFilters(Class<?> config) {
		this.spring.register(config).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		assertThat(service.resultsContainDave(new ArrayList<>(List.of("dave", "carla", "vanessa", "paul"))))
			.containsExactly("dave");
	}

	@Test
	@WithMockUser(authorities = "airplane:read")
	public void findByIdWhenAuthorizedResultThenAuthorizes() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		Flight flight = flights.findById("1");
		assertThatNoException().isThrownBy(flight::getAltitude);
		assertThatNoException().isThrownBy(flight::getSeats);
	}

	@Test
	@WithMockUser(authorities = "seating:read")
	public void findByIdWhenUnauthorizedResultThenDenies() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		Flight flight = flights.findById("1");
		assertThatNoException().isThrownBy(flight::getSeats);
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(flight::getAltitude);
	}

	@Test
	@WithMockUser(authorities = "airplane:read")
	public void findGeoResultByIdWhenAuthorizedResultThenAuthorizes() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		GeoResult<Flight> geoResultFlight = flights.findGeoResultFlightById("1");
		Flight flight = geoResultFlight.getContent();
		assertThatNoException().isThrownBy(flight::getAltitude);
		assertThatNoException().isThrownBy(flight::getSeats);
	}

	@Test
	@WithMockUser(authorities = "seating:read")
	public void findGeoResultByIdWhenUnauthorizedResultThenDenies() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		GeoResult<Flight> geoResultFlight = flights.findGeoResultFlightById("1");
		Flight flight = geoResultFlight.getContent();
		assertThatNoException().isThrownBy(flight::getSeats);
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(flight::getAltitude);
	}

	@Test
	@WithMockUser(authorities = "airplane:read")
	public void findByIdWhenAuthorizedResponseEntityThenAuthorizes() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		Flight flight = flights.webFindById("1").getBody();
		assertThatNoException().isThrownBy(flight::getAltitude);
		assertThatNoException().isThrownBy(flight::getSeats);
		assertThat(flights.webFindById("5").getBody()).isNull();
	}

	@Test
	@WithMockUser(authorities = "seating:read")
	public void findByIdWhenUnauthorizedResponseEntityThenDenies() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		Flight flight = flights.webFindById("1").getBody();
		assertThatNoException().isThrownBy(flight::getSeats);
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(flight::getAltitude);
	}

	@Test
	@WithMockUser(authorities = "airplane:read")
	public void findByIdWhenAuthorizedModelAndViewThenAuthorizes() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		Flight flight = (Flight) flights.webViewFindById("1").getModel().get("flight");
		assertThatNoException().isThrownBy(flight::getAltitude);
		assertThatNoException().isThrownBy(flight::getSeats);
		assertThat(flights.webViewFindById("5").getModel().get("flight")).isNull();
	}

	@Test
	@WithMockUser(authorities = "seating:read")
	public void findByIdWhenUnauthorizedModelAndViewThenDenies() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		Flight flight = (Flight) flights.webViewFindById("1").getModel().get("flight");
		assertThatNoException().isThrownBy(flight::getSeats);
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(flight::getAltitude);
		assertThat(flights.webViewFindById("5").getModel().get("flight")).isNull();
	}

	@Test
	@WithMockUser(authorities = "seating:read")
	public void findAllWhenUnauthorizedResultThenDenies() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		flights.findAll().forEachRemaining((flight) -> {
			assertThatNoException().isThrownBy(flight::getSeats);
			assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(flight::getAltitude);
		});
	}

	@Test
	public void removeWhenAuthorizedResultThenRemoves() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		flights.remove("1");
	}

	@Test
	@WithMockUser(authorities = "airplane:read")
	public void findAllWhenPostFilterThenFilters() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		flights.findAll()
			.forEachRemaining((flight) -> assertThat(flight.getPassengers()).extracting(Passenger::getName)
				.doesNotContain("Kevin Mitnick"));
	}

	@Test
	@WithMockUser(authorities = "airplane:read")
	public void findPageWhenPostFilterThenFilters() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		flights.findPage()
			.forEach((flight) -> assertThat(flight.getPassengers()).extracting(Passenger::getName)
				.doesNotContain("Kevin Mitnick"));
	}

	@Test
	@WithMockUser(authorities = "airplane:read")
	public void findSliceWhenPostFilterThenFilters() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		flights.findSlice()
			.forEach((flight) -> assertThat(flight.getPassengers()).extracting(Passenger::getName)
				.doesNotContain("Kevin Mitnick"));
	}

	@Test
	@WithMockUser(authorities = "airplane:read")
	public void findGeoPageWhenPostFilterThenFilters() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		flights.findGeoPage()
			.forEach((flight) -> assertThat(flight.getContent().getPassengers()).extracting(Passenger::getName)
				.doesNotContain("Kevin Mitnick"));
	}

	@Test
	@WithMockUser(authorities = "airplane:read")
	public void findGeoResultsWhenPostFilterThenFilters() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		flights.findGeoResults()
			.forEach((flight) -> assertThat(flight.getContent().getPassengers()).extracting(Passenger::getName)
				.doesNotContain("Kevin Mitnick"));
	}

	@Test
	@WithMockUser(authorities = "airplane:read")
	public void findAllWhenPreFilterThenFilters() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		flights.findAll().forEachRemaining((flight) -> {
			flight.board(new ArrayList<>(List.of("John")));
			assertThat(flight.getPassengers()).extracting(Passenger::getName).doesNotContain("John");
			flight.board(new ArrayList<>(List.of("John Doe")));
			assertThat(flight.getPassengers()).extracting(Passenger::getName).contains("John Doe");
		});
	}

	@Test
	@WithMockUser(authorities = "seating:read")
	public void findAllWhenNestedPreAuthorizeThenAuthorizes() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		flights.findAll().forEachRemaining((flight) -> {
			List<Passenger> passengers = flight.getPassengers();
			passengers.forEach((passenger) -> assertThatExceptionOfType(AccessDeniedException.class)
				.isThrownBy(passenger::getName));
		});
	}

	@Test
	@WithMockUser
	void getCardNumberWhenPostAuthorizeAndNotAdminThenReturnMasked() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class,
					MethodSecurityService.CardNumberMaskingPostProcessor.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String cardNumber = service.postAuthorizeGetCardNumberIfAdmin("4444-3333-2222-1111");
		assertThat(cardNumber).isEqualTo("****-****-****-1111");
	}

	@Test
	@WithMockUser
	void getCardNumberWhenPreAuthorizeAndNotAdminThenReturnMasked() {
		this.spring.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.StarMaskingHandler.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String cardNumber = service.preAuthorizeGetCardNumberIfAdmin("4444-3333-2222-1111");
		assertThat(cardNumber).isEqualTo("***");
	}

	@Test
	@WithMockUser
	void getCardNumberWhenPreAuthorizeAndNotAdminAndChildHandlerThenResolveCorrectHandlerAndReturnMasked() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.StarMaskingHandler.class,
					MethodSecurityService.StartMaskingHandlerChild.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String cardNumber = service.preAuthorizeWithHandlerChildGetCardNumberIfAdmin("4444-3333-2222-1111");
		assertThat(cardNumber).isEqualTo("***-child");
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void preAuthorizeWhenHandlerAndAccessDeniedNotThrownFromPreAuthorizeThenHandled() {
		this.spring.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.StarMaskingHandler.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		assertThat(service.preAuthorizeThrowAccessDeniedManually()).isEqualTo("***");
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void postAuthorizeWhenHandlerAndAccessDeniedNotThrownFromPostAuthorizeThenHandled() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.PostMaskingPostProcessor.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		assertThat(service.postAuthorizeThrowAccessDeniedManually()).isEqualTo("***");
	}

	@Test
	@WithMockUser
	void preAuthorizeWhenDeniedAndHandlerWithCustomAnnotationThenHandlerCanUseMaskFromOtherAnnotation() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationHandler.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.preAuthorizeDeniedMethodWithMaskAnnotation();
		assertThat(result).isEqualTo("methodmask");
	}

	@Test
	@WithMockUser
	void preAuthorizeWhenDeniedAndHandlerWithCustomAnnotationInClassThenHandlerCanUseMaskFromOtherAnnotation() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationHandler.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.preAuthorizeDeniedMethodWithNoMaskAnnotation();
		assertThat(result).isEqualTo("classmask");
	}

	@Test
	@WithMockUser
	void postAuthorizeWhenDeniedAndHandlerWithCustomAnnotationThenHandlerCanUseMaskFromOtherAnnotation() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationPostProcessor.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.postAuthorizeDeniedMethodWithMaskAnnotation();
		assertThat(result).isEqualTo("methodmask");
	}

	@Test
	@WithMockUser
	void postAuthorizeWhenDeniedAndHandlerWithCustomAnnotationInClassThenHandlerCanUseMaskFromOtherAnnotation() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationPostProcessor.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.postAuthorizeDeniedMethodWithNoMaskAnnotation();
		assertThat(result).isEqualTo("classmask");
	}

	@Test
	@WithMockUser
	void postAuthorizeWhenDeniedAndHandlerWithCustomAnnotationUsingBeanThenHandlerCanUseMaskFromOtherAnnotation() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationPostProcessor.class,
					MyMasker.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.postAuthorizeWithMaskAnnotationUsingBean();
		assertThat(result).isEqualTo("ok-masked");
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void postAuthorizeWhenAllowedAndHandlerWithCustomAnnotationUsingBeanThenInvokeMethodNormally() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationPostProcessor.class,
					MyMasker.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.postAuthorizeWithMaskAnnotationUsingBean();
		assertThat(result).isEqualTo("ok");
	}

	@Test
	@WithMockUser
	void preAuthorizeWhenDeniedAndHandlerWithCustomAnnotationUsingBeanThenHandlerCanUseMaskFromOtherAnnotation() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationHandler.class,
					MyMasker.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.preAuthorizeWithMaskAnnotationUsingBean();
		assertThat(result).isEqualTo("mask");
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void preAuthorizeWhenAllowedAndHandlerWithCustomAnnotationUsingBeanThenInvokeMethodNormally() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationHandler.class,
					MyMasker.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.preAuthorizeWithMaskAnnotationUsingBean();
		assertThat(result).isEqualTo("ok");
	}

	@Test
	@WithMockUser
	void getUserWhenAuthorizedAndUserEmailIsProtectedAndNotAuthorizedThenReturnEmailMasked() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class,
					UserRecordWithEmailProtected.EmailMaskingPostProcessor.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		UserRecordWithEmailProtected user = service.getUserRecordWithEmailProtected();
		assertThat(user.email()).isEqualTo("use******@example.com");
		assertThat(user.name()).isEqualTo("username");
	}

	@Test
	@WithMockUser
	void getUserWhenNotAuthorizedAndHandlerFallbackValueThenReturnFallbackValue() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.UserFallbackDeniedHandler.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		UserRecordWithEmailProtected user = service.getUserWithFallbackWhenUnauthorized();
		assertThat(user.email()).isEqualTo("Protected");
		assertThat(user.name()).isEqualTo("Protected");
	}

	@Test
	@WithMockUser
	void getUserWhenNotAuthorizedThenHandlerUsesCustomAuthorizationDecision() {
		this.spring.register(MethodSecurityServiceConfig.class, CustomResultConfig.class).autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		MethodAuthorizationDeniedHandler handler = this.spring.getContext()
			.getBean(MethodAuthorizationDeniedHandler.class);
		assertThat(service.checkCustomResult(false)).isNull();
		verify(handler).handleDeniedInvocation(any(), any(Authz.AuthzResult.class));
		verify(handler, never()).handleDeniedInvocationResult(any(), any(Authz.AuthzResult.class));
		clearInvocations(handler);
		assertThat(service.checkCustomResult(true)).isNull();
		verify(handler).handleDeniedInvocationResult(any(), any(Authz.AuthzResult.class));
		verify(handler, never()).handleDeniedInvocation(any(), any(Authz.AuthzResult.class));
	}

	// gh-15352
	@Test
	void annotationsInChildClassesDoNotAffectSuperclasses() {
		this.spring.register(AbstractClassConfig.class).autowire();
		this.spring.getContext().getBean(ClassInheritingAbstractClassWithNoAnnotations.class).method();
	}

	// gh-15592
	@Test
	void autowireWhenDefaultsThenCreatesExactlyOneAdvisorPerAnnotation() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		AuthorizationAdvisorProxyFactory proxyFactory = this.spring.getContext()
			.getBean(AuthorizationAdvisorProxyFactory.class);
		assertThat(proxyFactory).hasSize(5);
		assertThat(this.spring.getContext().getBeanNamesForType(AuthorizationAdvisor.class)).hasSize(5)
			.containsExactlyInAnyOrder("preFilterAuthorizationMethodInterceptor",
					"preAuthorizeAuthorizationMethodInterceptor", "postAuthorizeAuthorizationMethodInterceptor",
					"postFilterAuthorizationMethodInterceptor", "authorizeReturnObjectMethodInterceptor");
	}

	// gh-15592
	@Test
	void autowireWhenAspectJAutoProxyAndFactoryBeanThenExactlyOneAdvisorPerAnnotation() {
		this.spring.register(AspectJAwareAutoProxyAndFactoryBeansConfig.class).autowire();
		AuthorizationAdvisorProxyFactory proxyFactory = this.spring.getContext()
			.getBean(AuthorizationAdvisorProxyFactory.class);
		assertThat(proxyFactory).hasSize(5);
		assertThat(this.spring.getContext().getBeanNamesForType(AuthorizationAdvisor.class)).hasSize(5)
			.containsExactlyInAnyOrder("preFilterAuthorizationMethodInterceptor",
					"preAuthorizeAuthorizationMethodInterceptor", "postAuthorizeAuthorizationMethodInterceptor",
					"postFilterAuthorizationMethodInterceptor", "authorizeReturnObjectMethodInterceptor");
	}

	// gh-15651
	@Test
	@WithMockUser(roles = "ADMIN")
	public void adviseWhenPrePostEnabledThenEachInterceptorRunsExactlyOnce() {
		this.spring.register(MethodSecurityServiceConfig.class, CustomMethodSecurityExpressionHandlerConfig.class)
			.autowire();
		MethodSecurityExpressionHandler expressionHandler = this.spring.getContext()
			.getBean(MethodSecurityExpressionHandler.class);
		this.methodSecurityService.manyAnnotations(new ArrayList<>(Arrays.asList("harold", "jonathan", "tim", "bo")));
		verify(expressionHandler, times(4)).createEvaluationContext(any(Supplier.class), any());
	}

	// gh-15721
	@Test
	@WithMockUser(roles = "uid")
	public void methodWhenMetaAnnotationPropertiesHasClassProperties() {
		this.spring.register(MetaAnnotationPlaceholderConfig.class).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		assertThat(service.getIdPath("uid")).isEqualTo("uid");
	}

	@Test
	@WithMockUser
	public void prePostMethodWhenObservationRegistryThenObserved() {
		this.spring.register(MethodSecurityServiceEnabledConfig.class, ObservationRegistryConfig.class).autowire();
		this.methodSecurityService.preAuthorizePermitAll();
		ObservationHandler<?> handler = this.spring.getContext().getBean(ObservationHandler.class);
		verify(handler).onStart(any());
		verify(handler).onStop(any());
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::preAuthorize);
		verify(handler).onError(any());
	}

	@Test
	@WithMockUser
	public void securedMethodWhenObservationRegistryThenObserved() {
		this.spring.register(MethodSecurityServiceEnabledConfig.class, ObservationRegistryConfig.class).autowire();
		this.methodSecurityService.securedUser();
		ObservationHandler<?> handler = this.spring.getContext().getBean(ObservationHandler.class);
		verify(handler).onStart(any());
		verify(handler).onStop(any());
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::secured);
		verify(handler).onError(any());
	}

	@Test
	@WithMockUser
	public void jsr250MethodWhenObservationRegistryThenObserved() {
		this.spring.register(MethodSecurityServiceEnabledConfig.class, ObservationRegistryConfig.class).autowire();
		this.methodSecurityService.jsr250RolesAllowedUser();
		ObservationHandler<?> handler = this.spring.getContext().getBean(ObservationHandler.class);
		verify(handler).onStart(any());
		verify(handler).onStop(any());
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(this.methodSecurityService::jsr250RolesAllowed);
		verify(handler).onError(any());
	}

	@Test
	@WithMockUser
	public void prePostMethodWhenExcludeAuthorizationObservationsThenUnobserved() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, ObservationRegistryConfig.class,
					SelectableObservationsConfig.class)
			.autowire();
		this.methodSecurityService.preAuthorizePermitAll();
		ObservationHandler<?> handler = this.spring.getContext().getBean(ObservationHandler.class);
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::preAuthorize);
		verifyNoInteractions(handler);
	}

	@Test
	@WithMockUser
	public void securedMethodWhenExcludeAuthorizationObservationsThenUnobserved() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, ObservationRegistryConfig.class,
					SelectableObservationsConfig.class)
			.autowire();
		this.methodSecurityService.securedUser();
		ObservationHandler<?> handler = this.spring.getContext().getBean(ObservationHandler.class);
		verifyNoInteractions(handler);
	}

	@Test
	@WithMockUser
	public void jsr250MethodWhenExcludeAuthorizationObservationsThenUnobserved() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, ObservationRegistryConfig.class,
					SelectableObservationsConfig.class)
			.autowire();
		this.methodSecurityService.jsr250RolesAllowedUser();
		ObservationHandler<?> handler = this.spring.getContext().getBean(ObservationHandler.class);
		verifyNoInteractions(handler);
	}

	@Test
	@WithMockUser
	public void preAuthorizeWhenDenyAllThenPublishesParameterizedAuthorizationDeniedEvent() {
		this.spring
			.register(MethodSecurityServiceConfig.class, EventPublisherConfig.class, AuthorizationDeniedListener.class)
			.autowire();
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.preAuthorize());
		assertThat(this.spring.getContext().getBean(AuthorizationDeniedListener.class).invocations).isEqualTo(1);
	}

	// gh-16819
	@Test
	void autowireWhenDefaultsThenAdvisorAnnotationsAreSorted() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		AuthorizationAdvisorProxyFactory proxyFactory = this.spring.getContext()
			.getBean(AuthorizationAdvisorProxyFactory.class);
		AnnotationAwareOrderComparator comparator = AnnotationAwareOrderComparator.INSTANCE;
		AuthorizationAdvisor previous = null;
		for (AuthorizationAdvisor advisor : proxyFactory) {
			boolean ordered = previous == null || comparator.compare(previous, advisor) < 0;
			assertThat(ordered).isTrue();
			previous = advisor;
		}
	}

	@Test
	void getWhenPostAuthorizeAuthenticationNameMatchesThenRespondsWithOk() throws Exception {
		this.spring.register(WebMvcMethodSecurityConfig.class, BasicController.class).autowire();
		// @formatter:off
		MockHttpServletRequestBuilder requestWithUser = get("/authorized-person")
				.param("name", "rob")
				.with(user("rob"));
		// @formatter:on
		this.mvc.perform(requestWithUser).andExpect(status().isOk());
	}

	@Test
	void getWhenPostAuthorizeAuthenticationNameNotMatchThenRespondsWithForbidden() throws Exception {
		this.spring.register(WebMvcMethodSecurityConfig.class, BasicController.class).autowire();
		// @formatter:off
		MockHttpServletRequestBuilder requestWithUser = get("/authorized-person")
				.param("name", "john")
				.with(user("rob"));
		// @formatter:on
		this.mvc.perform(requestWithUser).andExpect(status().isForbidden());
	}

	@Test
	void getWhenPostAuthorizeWithinServiceAuthenticationNameMatchesThenRespondsWithOk() throws Exception {
		this.spring.register(WebMvcMethodSecurityConfig.class, BasicController.class, BasicService.class).autowire();
		// @formatter:off
		MockHttpServletRequestBuilder requestWithUser = get("/greetings/authorized-person")
				.param("name", "rob")
				.with(user("rob"));
		// @formatter:on
		MvcResult mvcResult = this.mvc.perform(requestWithUser).andExpect(status().isOk()).andReturn();
		assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo("Hello: rob");
	}

	@Test
	void getWhenPostAuthorizeWithinServiceAuthenticationNameNotMatchThenCustomHandlerRespondsWithForbidden()
			throws Exception {
		this.spring
			.register(WebMvcMethodSecurityConfig.class, BasicController.class, BasicService.class,
					BasicControllerAdvice.class)
			.autowire();
		// @formatter:off
		MockHttpServletRequestBuilder requestWithUser = get("/greetings/authorized-person")
				.param("name", "john")
				.with(user("rob"));
		// @formatter:on
		MvcResult mvcResult = this.mvc.perform(requestWithUser).andExpect(status().isForbidden()).andReturn();
		assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo("""
				{"message":"Access Denied"}\
				""");
	}

	@Test
	void getWhenPostAuthorizeAuthenticationNameNotMatchThenCustomHandlerRespondsWithForbidden() throws Exception {
		this.spring
			.register(WebMvcMethodSecurityConfig.class, BasicController.class, BasicService.class,
					BasicControllerAdvice.class)
			.autowire();
		// @formatter:off
		MockHttpServletRequestBuilder requestWithUser = get("/authorized-person")
				.param("name", "john")
				.with(user("rob"));
		// @formatter:on
		MvcResult mvcResult = this.mvc.perform(requestWithUser).andExpect(status().isForbidden()).andReturn();
		assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo("""
				{"message":"Could not write JSON: Access Denied"}\
				""");
	}

	@Test
	void getWhenCustomAdvisorAuthenticationNameMatchesThenRespondsWithOk() throws Exception {
		this.spring.register(WebMvcMethodSecurityCustomAdvisorConfig.class, BasicController.class).autowire();
		// @formatter:off
		MockHttpServletRequestBuilder requestWithUser = get("/authorized-person")
				.param("name", "rob")
				.with(user("rob"));
		// @formatter:on
		this.mvc.perform(requestWithUser).andExpect(status().isOk());
	}

	@Test
	void getWhenCustomAdvisorAuthenticationNameNotMatchThenRespondsWithForbidden() throws Exception {
		this.spring.register(WebMvcMethodSecurityCustomAdvisorConfig.class, BasicController.class).autowire();
		// @formatter:off
		MockHttpServletRequestBuilder requestWithUser = get("/authorized-person")
				.param("name", "john")
				.with(user("rob"));
		// @formatter:on
		this.mvc.perform(requestWithUser).andExpect(status().isForbidden());
	}

	private static Consumer<ConfigurableWebApplicationContext> disallowBeanOverriding() {
		return (context) -> ((AnnotationConfigWebApplicationContext) context).setAllowBeanDefinitionOverriding(false);
	}

	private static Advisor returnAdvisor(int order) {
		JdkRegexpMethodPointcut pointcut = new JdkRegexpMethodPointcut();
		pointcut.setPattern(".*MethodSecurityServiceImpl.*");
		MethodInterceptor interceptor = (mi) -> mi.getArguments()[0];
		DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
		advisor.setOrder(order);
		return advisor;
	}

	@Configuration
	static class AuthzConfig {

		@Bean
		Authz authz() {
			return new Authz();
		}

	}

	@Configuration
	@EnableCustomMethodSecurity
	static class CustomMethodSecurityServiceConfig {

		@Bean
		MethodSecurityService methodSecurityService() {
			return new MethodSecurityServiceImpl();
		}

	}

	@Configuration
	@EnableMethodSecurity
	static class MethodSecurityServiceConfig {

		@Bean
		MethodSecurityService methodSecurityService() {
			return new MethodSecurityServiceImpl();
		}

		@Bean
		Authz authz() {
			return new Authz();
		}

	}

	@Configuration
	@EnableMethodSecurity(jsr250Enabled = true)
	static class BusinessServiceConfig {

		@Bean
		BusinessService businessService() {
			return new ExpressionProtectedBusinessServiceImpl();
		}

	}

	@Configuration
	@EnableMethodSecurity(prePostEnabled = false, securedEnabled = true)
	static class SecuredConfig {

		@Bean
		BusinessService businessService() {
			return new BusinessServiceImpl<>();
		}

	}

	@Configuration
	@EnableMethodSecurity(prePostEnabled = false, jsr250Enabled = true)
	static class Jsr250Config {

		@Bean
		BusinessService businessService() {
			return new Jsr250BusinessServiceImpl();
		}

	}

	@Configuration
	@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
	static class MethodSecurityServiceEnabledConfig {

		@Bean
		MethodSecurityService methodSecurityService() {
			return new MethodSecurityServiceImpl();
		}

	}

	@Configuration
	@EnableMethodSecurity
	static class CustomMethodSecurityExpressionHandlerConfig {

		private final MethodSecurityExpressionHandler expressionHandler = spy(
				new DefaultMethodSecurityExpressionHandler());

		@Bean
		MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
			return this.expressionHandler;
		}

	}

	@EnableMethodSecurity
	static class CustomPermissionEvaluatorConfig {

		@Bean
		MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
			DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
			expressionHandler.setPermissionEvaluator(new PermissionEvaluator() {
				@Override
				public boolean hasPermission(Authentication authentication, Object targetDomainObject,
						Object permission) {
					return "grant".equals(targetDomainObject);
				}

				@Override
				public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType,
						Object permission) {
					throw new UnsupportedOperationException();
				}
			});
			return expressionHandler;
		}

	}

	@Configuration
	@EnableMethodSecurity
	static class CustomGrantedAuthorityDefaultsConfig {

		@Bean
		GrantedAuthorityDefaults grantedAuthorityDefaults() {
			return new GrantedAuthorityDefaults("PREFIX_");
		}

	}

	@Configuration
	@EnableMethodSecurity
	static class CustomAuthorizationManagerBeforeAdviceConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor customBeforeAdvice(SecurityContextHolderStrategy strategy) {
			JdkRegexpMethodPointcut pointcut = new JdkRegexpMethodPointcut();
			pointcut.setPattern(".*MethodSecurityServiceImpl.*securedUser");
			AuthorizationManager<MethodInvocation> authorizationManager = (a,
					o) -> new AuthorizationDecision("bob".equals(a.get().getName()));
			AuthorizationManagerBeforeMethodInterceptor before = new AuthorizationManagerBeforeMethodInterceptor(
					pointcut, authorizationManager);
			before.setSecurityContextHolderStrategy(strategy);
			return before;
		}

	}

	@Configuration
	@EnableMethodSecurity
	static class CustomAuthorizationManagerAfterAdviceConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor customAfterAdvice(SecurityContextHolderStrategy strategy) {
			JdkRegexpMethodPointcut pointcut = new JdkRegexpMethodPointcut();
			pointcut.setPattern(".*MethodSecurityServiceImpl.*securedUser");
			MethodInterceptor interceptor = (mi) -> {
				Authentication auth = strategy.getContext().getAuthentication();
				if ("bob".equals(auth.getName())) {
					return "granted";
				}
				throw new AccessDeniedException("Access Denied for User '" + auth.getName() + "'");
			};
			DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
			advisor.setOrder(AuthorizationInterceptorsOrder.POST_FILTER.getOrder() + 1);
			return advisor;
		}

	}

	@Configuration
	static class AuthorizationEventPublisherConfig {

		private final AuthorizationEventPublisher publisher = mock(AuthorizationEventPublisher.class);

		@Bean
		AuthorizationEventPublisher authorizationEventPublisher() {
			return this.publisher;
		}

	}

	@EnableMethodSecurity(mode = AdviceMode.ASPECTJ, securedEnabled = true)
	static class AspectJMethodSecurityServiceConfig {

		@Bean
		MethodSecurityService methodSecurityService() {
			return new MethodSecurityServiceImpl();
		}

		@Bean
		Authz authz() {
			return new Authz();
		}

	}

	@Configuration
	@EnableMethodSecurity(jsr250Enabled = true, securedEnabled = true)
	static class RoleHierarchyConfig {

		@Bean
		static RoleHierarchy roleHierarchy() {
			return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_USER");
		}

	}

	@Import(OffsetConfig.class)
	static class ReturnBeforeOffsetPreFilterConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor returnBeforePreFilter() {
			return returnAdvisor(AuthorizationInterceptorsOrder.PRE_FILTER.getOrder() + OffsetConfig.OFFSET - 1);
		}

	}

	@Configuration
	@Import(OffsetConfig.class)
	static class ReturnBeforeOffsetPreAuthorizeConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor returnBeforePreAuthorize() {
			return returnAdvisor(AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder() + OffsetConfig.OFFSET - 1);
		}

	}

	@Configuration
	@Import(OffsetConfig.class)
	static class ReturnBeforeOffsetSecuredConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor returnBeforeSecured() {
			return returnAdvisor(AuthorizationInterceptorsOrder.SECURED.getOrder() + OffsetConfig.OFFSET - 1);
		}

	}

	@Configuration
	@Import(OffsetConfig.class)
	static class ReturnBeforeOffsetJsr250Config {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor returnBeforeJsr250() {
			return returnAdvisor(AuthorizationInterceptorsOrder.JSR250.getOrder() + OffsetConfig.OFFSET - 1);
		}

	}

	@Configuration
	@Import(OffsetConfig.class)
	static class ReturnBeforeOffsetPostAuthorizeConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor returnBeforePreAuthorize() {
			return returnAdvisor(AuthorizationInterceptorsOrder.POST_AUTHORIZE.getOrder() + OffsetConfig.OFFSET - 1);
		}

	}

	@Configuration
	@Import(OffsetConfig.class)
	static class ReturnBeforeOffsetPostFilterConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor returnBeforePostFilter() {
			return returnAdvisor(AuthorizationInterceptorsOrder.POST_FILTER.getOrder() + OffsetConfig.OFFSET - 1);
		}

	}

	@Configuration
	@Import(OffsetConfig.class)
	static class ReturnAfterAllOffsetConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor returnAfterAll() {
			return returnAdvisor(AuthorizationInterceptorsOrder.POST_FILTER.getOrder() + OffsetConfig.OFFSET + 1);
		}

	}

	@Configuration
	@EnableMethodSecurity(offset = OffsetConfig.OFFSET, jsr250Enabled = true, securedEnabled = true)
	static class OffsetConfig {

		static final int OFFSET = 2;

		@Bean
		MethodSecurityService methodSecurityService() {
			return new MethodSecurityServiceImpl();
		}

		@Bean
		Authz authz() {
			return new Authz();
		}

	}

	@Configuration
	@EnableMethodSecurity
	static class MetaAnnotationPlaceholderConfig {

		@Bean
		AnnotationTemplateExpressionDefaults methodSecurityDefaults() {
			return new AnnotationTemplateExpressionDefaults();
		}

		@Bean
		MetaAnnotationService metaAnnotationService() {
			return new MetaAnnotationService();
		}

	}

	static class MetaAnnotationService {

		@RequireRole(role = "#role")
		boolean hasRole(String role) {
			return true;
		}

		@RequireRole(role = "'USER'")
		boolean hasUserRole() {
			return true;
		}

		@PreAuthorize("hasRole({role})")
		void placeholdersOnlyResolvedByMetaAnnotations() {
		}

		@HasClaim(claim = "message:read", roles = { "'ADMIN'" })
		String readMessage() {
			return "message";
		}

		@ResultStartsWith("dave")
		String startsWithDave(String value) {
			return value;
		}

		@ParameterContains("dave")
		List<String> parametersContainDave(List<String> list) {
			return list;
		}

		@ResultContains("dave")
		List<String> resultsContainDave(List<String> list) {
			return list;
		}

		@RestrictedAccess(entityClass = EntityClass.class)
		String getIdPath(String id) {
			return id;
		}

	}

	@Retention(RetentionPolicy.RUNTIME)
	@PreAuthorize("hasRole({idPath})")
	@interface RestrictedAccess {

		String idPath() default "#id";

		Class<?> entityClass();

		String[] recipes() default {};

	}

	static class EntityClass {

	}

	@Retention(RetentionPolicy.RUNTIME)
	@PreAuthorize("hasRole({role})")
	@interface RequireRole {

		String role();

	}

	@Retention(RetentionPolicy.RUNTIME)
	@PreAuthorize("hasAuthority('SCOPE_{claim}') || hasAnyRole({roles})")
	@interface HasClaim {

		String claim();

		String[] roles() default {};

	}

	@Retention(RetentionPolicy.RUNTIME)
	@PostAuthorize("returnObject.startsWith('{value}')")
	@interface ResultStartsWith {

		String value();

	}

	@Retention(RetentionPolicy.RUNTIME)
	@PreFilter("filterObject.contains('{value}')")
	@interface ParameterContains {

		String value();

	}

	@Retention(RetentionPolicy.RUNTIME)
	@PostFilter("filterObject.contains('{value}')")
	@interface ResultContains {

		String value();

	}

	@EnableMethodSecurity
	@Configuration
	static class AuthorizeResultConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		static TargetVisitor skipValueTypes() {
			return TargetVisitor.defaultsSkipValueTypes();
		}

		@Bean
		FlightRepository flights() {
			FlightRepository flights = new FlightRepository();
			Flight one = new Flight("1", 35000d, 35);
			one.board(new ArrayList<>(List.of("Marie Curie", "Kevin Mitnick", "Ada Lovelace")));
			flights.save(one);
			Flight two = new Flight("2", 32000d, 72);
			two.board(new ArrayList<>(List.of("Albert Einstein")));
			flights.save(two);
			return flights;
		}

		@Bean
		RoleHierarchy roleHierarchy() {
			return RoleHierarchyImpl.withRolePrefix("").role("airplane:read").implies("seating:read").build();
		}

	}

	@AuthorizeReturnObject
	static class FlightRepository {

		private final Map<String, Flight> flights = new ConcurrentHashMap<>();

		Iterator<Flight> findAll() {
			return this.flights.values().iterator();
		}

		Page<Flight> findPage() {
			return new PageImpl<>(new ArrayList<>(this.flights.values()));
		}

		Slice<Flight> findSlice() {
			return new SliceImpl<>(new ArrayList<>(this.flights.values()));
		}

		GeoPage<Flight> findGeoPage() {
			List<GeoResult<Flight>> results = new ArrayList<>();
			for (Flight flight : this.flights.values()) {
				results.add(new GeoResult<>(flight, new Distance(flight.altitude)));
			}
			return new GeoPage<>(new GeoResults<>(results));
		}

		GeoResults<Flight> findGeoResults() {
			List<GeoResult<Flight>> results = new ArrayList<>();
			for (Flight flight : this.flights.values()) {
				results.add(new GeoResult<>(flight, new Distance(flight.altitude)));
			}
			return new GeoResults<>(results);
		}

		Flight findById(String id) {
			return this.flights.get(id);
		}

		GeoResult<Flight> findGeoResultFlightById(String id) {
			Flight flight = this.flights.get(id);
			return new GeoResult<>(flight, new Distance(flight.altitude));
		}

		Flight save(Flight flight) {
			this.flights.put(flight.getId(), flight);
			return flight;
		}

		void remove(String id) {
			this.flights.remove(id);
		}

		ResponseEntity<Flight> webFindById(String id) {
			Flight flight = this.flights.get(id);
			if (flight == null) {
				return ResponseEntity.notFound().build();
			}
			return ResponseEntity.ok(flight);
		}

		ModelAndView webViewFindById(String id) {
			Flight flight = this.flights.get(id);
			if (flight == null) {
				return new ModelAndView("error", HttpStatusCode.valueOf(404));
			}
			return new ModelAndView("flights", Map.of("flight", flight));
		}

	}

	@AuthorizeReturnObject
	static class Flight {

		private final String id;

		private final Double altitude;

		private final Integer seats;

		private final List<Passenger> passengers = new ArrayList<>();

		Flight(String id, Double altitude, Integer seats) {
			this.id = id;
			this.altitude = altitude;
			this.seats = seats;
		}

		String getId() {
			return this.id;
		}

		@PreAuthorize("hasAuthority('airplane:read')")
		Double getAltitude() {
			return this.altitude;
		}

		@PreAuthorize("hasAuthority('seating:read')")
		Integer getSeats() {
			return this.seats;
		}

		@PostAuthorize("hasAuthority('seating:read')")
		@PostFilter("filterObject.name != 'Kevin Mitnick'")
		List<Passenger> getPassengers() {
			return this.passengers;
		}

		@PreAuthorize("hasAuthority('seating:read')")
		@PreFilter("filterObject.contains(' ')")
		void board(List<String> passengers) {
			for (String passenger : passengers) {
				this.passengers.add(new Passenger(passenger));
			}
		}

	}

	public static class Passenger {

		String name;

		public Passenger(String name) {
			this.name = name;
		}

		@PreAuthorize("hasAuthority('airplane:read')")
		public String getName() {
			return this.name;
		}

	}

	@EnableMethodSecurity
	static class CustomResultConfig {

		MethodAuthorizationDeniedHandler handler = mock(MethodAuthorizationDeniedHandler.class);

		@Bean
		MethodAuthorizationDeniedHandler methodAuthorizationDeniedHandler() {
			return this.handler;
		}

	}

	abstract static class AbstractClassWithNoAnnotations {

		String method() {
			return "ok";
		}

	}

	@PreAuthorize("denyAll()")
	@Secured("DENIED")
	@DenyAll
	static class ClassInheritingAbstractClassWithNoAnnotations extends AbstractClassWithNoAnnotations {

	}

	@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
	static class AbstractClassConfig {

		@Bean
		ClassInheritingAbstractClassWithNoAnnotations inheriting() {
			return new ClassInheritingAbstractClassWithNoAnnotations();
		}

	}

	@Configuration
	@EnableMethodSecurity
	static class AspectJAwareAutoProxyAndFactoryBeansConfig {

		@Bean
		static BeanDefinitionRegistryPostProcessor beanDefinitionRegistryPostProcessor() {
			return AopConfigUtils::registerAspectJAnnotationAutoProxyCreatorIfNecessary;
		}

		@Component
		static class MyFactoryBean implements FactoryBean<Object> {

			@Override
			public Object getObject() throws Exception {
				return new Object();
			}

			@Override
			public Class<?> getObjectType() {
				return Object.class;
			}

		}

	}

	@Configuration
	static class ObservationRegistryConfig {

		private final ObservationRegistry registry = ObservationRegistry.create();

		private final ObservationHandler<Observation.Context> handler = spy(new ObservationTextPublisher());

		@Bean
		ObservationRegistry observationRegistry() {
			return this.registry;
		}

		@Bean
		ObservationHandler<Observation.Context> observationHandler() {
			return this.handler;
		}

		@Bean
		ObservationRegistryPostProcessor observationRegistryPostProcessor(
				ObjectProvider<ObservationHandler<Observation.Context>> handler) {
			return new ObservationRegistryPostProcessor(handler);
		}

	}

	static class ObservationRegistryPostProcessor implements BeanPostProcessor {

		private final ObjectProvider<ObservationHandler<Observation.Context>> handler;

		ObservationRegistryPostProcessor(ObjectProvider<ObservationHandler<Observation.Context>> handler) {
			this.handler = handler;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
			if (bean instanceof ObservationRegistry registry) {
				registry.observationConfig().observationHandler(this.handler.getObject());
			}
			return bean;
		}

	}

	@Configuration
	static class SelectableObservationsConfig {

		@Bean
		SecurityObservationSettings observabilityDefaults() {
			return SecurityObservationSettings.withDefaults().shouldObserveAuthorizations(false).build();
		}

	}

	@Configuration
	static class EventPublisherConfig {

		@Bean
		static AuthorizationEventPublisher eventPublisher(ApplicationEventPublisher publisher) {
			return new SpringAuthorizationEventPublisher(publisher);
		}

	}

	@Component
	static class AuthorizationDeniedListener {

		int invocations;

		@EventListener
		void onRequestDenied(AuthorizationDeniedEvent<? extends MethodInvocation> denied) {
			this.invocations++;
		}

	}

	@EnableWebMvc
	@EnableWebSecurity
	@EnableMethodSecurity
	static class WebMvcMethodSecurityConfig {

	}

	@EnableWebMvc
	@EnableWebSecurity
	@EnableMethodSecurity
	static class WebMvcMethodSecurityCustomAdvisorConfig {

		@Bean
		AuthorizationAdvisor customAdvisor(SecurityContextHolderStrategy strategy) {
			JdkRegexpMethodPointcut pointcut = new JdkRegexpMethodPointcut();
			pointcut.setPattern(".*AuthorizedPerson.*getName");
			return new AuthorizationAdvisor() {
				@Override
				public Object invoke(MethodInvocation mi) throws Throwable {
					Authentication auth = strategy.getContext().getAuthentication();
					Object result = mi.proceed();
					if (auth.getName().equals(result)) {
						return result;
					}
					throw new AccessDeniedException("Access Denied for User '" + auth.getName() + "'");
				}

				@Override
				public Pointcut getPointcut() {
					return pointcut;
				}

				@Override
				public Advice getAdvice() {
					return this;
				}

				@Override
				public int getOrder() {
					return AuthorizationInterceptorsOrder.POST_FILTER.getOrder() + 1;
				}
			};
		}

	}

	@RestController
	static class BasicController {

		@Autowired(required = false)
		BasicService service;

		@GetMapping("/greetings/authorized-person")
		String getAuthorizedPersonGreeting(@RequestParam String name) {
			AuthorizedPerson authorizedPerson = this.service.getAuthorizedPerson(name);
			return "Hello: " + authorizedPerson.getName();
		}

		@AuthorizeReturnObject
		@GetMapping(value = "/authorized-person", produces = MediaType.APPLICATION_JSON_VALUE)
		AuthorizedPerson getAuthorizedPerson(@RequestParam String name) {
			return new AuthorizedPerson(name);
		}

	}

	@ControllerAdvice
	static class BasicControllerAdvice {

		@ExceptionHandler(AccessDeniedException.class)
		ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
			Map<String, String> responseBody = Map.of("message", ex.getMessage());
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(responseBody);
		}

		@ExceptionHandler(HttpMessageNotWritableException.class)
		ResponseEntity<Map<String, String>> handleHttpMessageNotWritable(HttpMessageNotWritableException ex) {
			ThrowableAnalyzer throwableAnalyzer = new ThrowableAnalyzer();
			Throwable[] causeChain = throwableAnalyzer.determineCauseChain(ex);
			Throwable t = throwableAnalyzer.getFirstThrowableOfType(AccessDeniedException.class, causeChain);
			if (t != null) {
				Map<String, String> responseBody = Map.of("message", ex.getMessage());
				return ResponseEntity.status(HttpStatus.FORBIDDEN).body(responseBody);
			}
			throw ex;
		}

	}

	@Service
	static class BasicService {

		@AuthorizeReturnObject
		AuthorizedPerson getAuthorizedPerson(String name) {
			return new AuthorizedPerson(name);
		}

	}

	public static class AuthorizedPerson {

		final String name;

		AuthorizedPerson(String name) {
			this.name = name;
		}

		@PostAuthorize("returnObject == authentication.name")
		public String getName() {
			return this.name;
		}

	}

}
