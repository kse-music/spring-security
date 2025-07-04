/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.security.config.annotation.web

import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.AnyRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * A Kotlin DSL to configure [HttpSecurity] request authorization using idiomatic Kotlin code.
 *
 * @author Eleftheria Stein
 * @since 5.3
 */
class AuthorizeRequestsDsl : AbstractRequestMatcherDsl() {
    private val authorizationRules = mutableListOf<AuthorizationRule>()
    private val PATTERN_TYPE = PatternType.PATH;

    /**
     * Adds a request authorization rule.
     *
     * @param matches the [RequestMatcher] to match incoming requests against
     * @param access the SpEL expression to secure the matching request
     * (i.e. "hasAuthority('ROLE_USER') and hasAuthority('ROLE_SUPER')")
     */
    fun authorize(matches: RequestMatcher = AnyRequestMatcher.INSTANCE,
                  access: String) {
        authorizationRules.add(MatcherAuthorizationRule(matches, access))
    }

    /**
     * Adds a request authorization rule for an endpoint matching the provided
     * pattern.
     * If Spring MVC is on the classpath, it will use an MVC matcher.
     * If Spring MVC is not on the classpath, it will use an ant matcher.
     * The MVC will use the same rules that Spring MVC uses for matching.
     * For example, often times a mapping of the path "/path" will match on
     * "/path", "/path/", "/path.html", etc.
     * If the current request will not be processed by Spring MVC, a reasonable default
     * using the pattern as an ant pattern will be used.
     *
     * @param pattern the pattern to match incoming requests against.
     * @param access the SpEL expression to secure the matching request
     * (i.e. "hasAuthority('ROLE_USER') and hasAuthority('ROLE_SUPER')")
     */
    fun authorize(pattern: String, access: String) {
        authorizationRules.add(PatternAuthorizationRule(pattern = pattern,
                                                        patternType = PATTERN_TYPE,
                                                        rule = access))
    }

    /**
     * Adds a request authorization rule for an endpoint matching the provided
     * pattern.
     * If Spring MVC is on the classpath, it will use an MVC matcher.
     * If Spring MVC is not on the classpath, it will use an ant matcher.
     * The MVC will use the same rules that Spring MVC uses for matching.
     * For example, often times a mapping of the path "/path" will match on
     * "/path", "/path/", "/path.html", etc.
     * If the current request will not be processed by Spring MVC, a reasonable default
     * using the pattern as an ant pattern will be used.
     *
     * @param method the HTTP method to match the income requests against.
     * @param pattern the pattern to match incoming requests against.
     * @param access the SpEL expression to secure the matching request
     * (i.e. "hasAuthority('ROLE_USER') and hasAuthority('ROLE_SUPER')")
     */
    fun authorize(method: HttpMethod, pattern: String, access: String) {
        authorizationRules.add(PatternAuthorizationRule(pattern = pattern,
                                                        patternType = PATTERN_TYPE,
                                                        httpMethod = method,
                                                        rule = access))
    }

    /**
     * Adds a request authorization rule for an endpoint matching the provided
     * pattern.
     * If Spring MVC is on the classpath, it will use an MVC matcher.
     * If Spring MVC is not on the classpath, it will use an ant matcher.
     * The MVC will use the same rules that Spring MVC uses for matching.
     * For example, often times a mapping of the path "/path" will match on
     * "/path", "/path/", "/path.html", etc.
     * If the current request will not be processed by Spring MVC, a reasonable default
     * using the pattern as an ant pattern will be used.
     *
     * @param pattern the pattern to match incoming requests against.
     * @param servletPath the servlet path to match incoming requests against. This
     * only applies when using an MVC pattern matcher.
     * @param access the SpEL expression to secure the matching request
     * (i.e. "hasAuthority('ROLE_USER') and hasAuthority('ROLE_SUPER')")
     */
    fun authorize(pattern: String, servletPath: String, access: String) {
        authorizationRules.add(PatternAuthorizationRule(pattern = pattern,
                                                        patternType = PATTERN_TYPE,
                                                        servletPath = servletPath,
                                                        rule = access))
    }

    /**
     * Adds a request authorization rule for an endpoint matching the provided
     * pattern.
     * If Spring MVC is on the classpath, it will use an MVC matcher.
     * If Spring MVC is not on the classpath, it will use an ant matcher.
     * The MVC will use the same rules that Spring MVC uses for matching.
     * For example, often times a mapping of the path "/path" will match on
     * "/path", "/path/", "/path.html", etc.
     * If the current request will not be processed by Spring MVC, a reasonable default
     * using the pattern as an ant pattern will be used.
     *
     * @param method the HTTP method to match the income requests against.
     * @param pattern the pattern to match incoming requests against.
     * @param servletPath the servlet path to match incoming requests against. This
     * only applies when using an MVC pattern matcher.
     * @param access the SpEL expression to secure the matching request
     * (i.e. "hasAuthority('ROLE_USER') and hasAuthority('ROLE_SUPER')")
     */
    fun authorize(method: HttpMethod, pattern: String, servletPath: String, access: String) {
        authorizationRules.add(PatternAuthorizationRule(pattern = pattern,
                                                        patternType = PATTERN_TYPE,
                                                        servletPath = servletPath,
                                                        httpMethod = method,
                                                        rule = access))
    }

    /**
     * Specify that URLs require a particular authority.
     *
     * @param authority the authority to require (i.e. ROLE_USER, ROLE_ADMIN, etc).
     * @return the SpEL expression "hasAuthority" with the given authority as a
     * parameter
     */
    fun hasAuthority(authority: String) = "hasAuthority('$authority')"

    /**
     * Specify that URLs require any number of authorities.
     *
     * @param authorities the authorities to require (i.e. ROLE_USER, ROLE_ADMIN, etc).
     * @return the SpEL expression "hasAnyAuthority" with the given authorities as a
     * parameter
     */
    fun hasAnyAuthority(vararg authorities: String): String {
        val anyAuthorities = authorities.joinToString("','")
        return "hasAnyAuthority('$anyAuthorities')"
    }

    /**
     * Specify that URLs require a particular role.
     *
     * @param role the role to require (i.e. USER, ADMIN, etc).
     * @return the SpEL expression "hasRole" with the given role as a
     * parameter
     */
    fun hasRole(role: String) = "hasRole('$role')"

    /**
     * Specify that URLs require any number of roles.
     *
     * @param roles the roles to require (i.e. USER, ADMIN, etc).
     * @return the SpEL expression "hasAnyRole" with the given roles as a
     * parameter
     */
    fun hasAnyRole(vararg roles: String): String {
        val anyRoles = roles.joinToString("','")
        return "hasAnyRole('$anyRoles')"
    }

    /**
     * Specify that URLs are allowed by anyone.
     */
    val permitAll = "permitAll"

    /**
     * Specify that URLs are allowed by anonymous users.
     */
    val anonymous = "anonymous"

    /**
     * Specify that URLs are allowed by users that have been remembered.
     */
    val rememberMe = "rememberMe"

    /**
     * Specify that URLs are not allowed by anyone.
     */
    val denyAll = "denyAll"

    /**
     * Specify that URLs are allowed by any authenticated user.
     */
    val authenticated = "authenticated"

    /**
     * Specify that URLs are allowed by users who have authenticated and were not
     * "remembered".
     */
    val fullyAuthenticated = "fullyAuthenticated"

    internal fun get(): (ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry) -> Unit {
        return { requests ->
            authorizationRules.forEach { rule ->
                when (rule) {
                    is MatcherAuthorizationRule -> requests.requestMatchers(rule.matcher).access(rule.rule)
                    is PatternAuthorizationRule -> {
                        var builder = requests.applicationContext.getBeanProvider(
                            PathPatternRequestMatcher.Builder::class.java)
                                .getIfUnique(PathPatternRequestMatcher::withDefaults);
                        if (rule.servletPath != null) {
                            builder = builder.basePath(rule.servletPath)
                        }
                        requests.requestMatchers(builder.matcher(rule.httpMethod, rule.pattern)).access(rule.rule)
                    }
                }
            }
        }
    }
}

