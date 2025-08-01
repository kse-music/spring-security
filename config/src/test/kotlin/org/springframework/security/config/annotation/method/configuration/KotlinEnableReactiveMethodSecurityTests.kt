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

package org.springframework.security.config.annotation.method.configuration

import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@ContextConfiguration
class KotlinEnableReactiveMethodSecurityTests {

    private lateinit var delegate: KotlinReactiveMessageService

    @Autowired
    var messageService: KotlinReactiveMessageService? = null

    @AfterEach
    fun cleanup() {
        clearAllMocks()
    }

    @Autowired
    fun setConfig(config: Config) {
        this.delegate = config.delegate
    }

    @Test
    fun `suspendingNoAuth always success`() {
        runBlocking {
            assertThat(messageService!!.suspendingNoAuth()).isEqualTo("success")
        }
    }

    @Test
    @WithMockUser(authorities = ["ROLE_ADMIN"])
    fun `suspendingPreAuthorizeHasRole when user has role then success`() {
        runBlocking {
            assertThat(messageService!!.suspendingPreAuthorizeHasRole()).isEqualTo("admin")
        }
    }

    @Test
    fun `suspendingPreAuthorizeHasRole when user does not have role then denied`() {
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.suspendingPreAuthorizeHasRole()
            }
        }
    }

    @Test
    @WithMockUser
    fun `suspendingPreAuthorizeBean when authorized then success`() {
        runBlocking {
            assertThat(messageService!!.suspendingPreAuthorizeBean(true)).isEqualTo("check")
        }
    }

    @Test
    fun `suspendingPreAuthorizeBean when not authorized then denied`() {
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.suspendingPreAuthorizeBean(false)
            }
        }
    }

    @Test
    @WithMockUser("user")
    fun `suspendingPostAuthorize when authorized then success`() {
        runBlocking {
            assertThat(messageService!!.suspendingPostAuthorizeContainsName()).isEqualTo("user")
        }
    }

    @Test
    @WithMockUser("other-user")
    fun `suspendingPostAuthorize when not authorized then denied`() {
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.suspendingPostAuthorizeContainsName()
            }
        }
    }

    @Test
    fun `suspendingPreAuthorizeDelegate when user does not have role then delegate not called`() {
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.suspendingPreAuthorizeDelegate()
            }
        }
        verify { delegate wasNot Called }
    }

    @Test
    @WithMockUser(authorities = ["ROLE_ADMIN"])
    fun `suspendingPreAuthorizeDelegate when user has role then delegate called`() {
        coEvery { delegate.suspendingPreAuthorizeHasRole() } returns "ok"
        runBlocking {
            messageService!!.suspendingPreAuthorizeDelegate()
        }
        coVerify(exactly = 1) { delegate.suspendingPreAuthorizeHasRole() }
    }

    @Test
    @WithMockUser
    fun `suspendingPrePostAuthorizeHasRoleContainsName when not pre authorized then delegate not called`() {
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.suspendingPrePostAuthorizeHasRoleContainsName()
            }
        }
        verify { delegate wasNot Called }
    }

    @Test
    @WithMockUser(authorities = ["ROLE_ADMIN"])
    fun `suspendingPrePostAuthorizeHasRoleContainsName when not post authorized then exception`() {
        coEvery { delegate.suspendingPrePostAuthorizeHasRoleContainsName() } returns "wrong"
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.suspendingPrePostAuthorizeHasRoleContainsName()
            }
        }
        coVerify(exactly = 1) { delegate.suspendingPrePostAuthorizeHasRoleContainsName() }
    }

    @Test
    @WithMockUser(authorities = ["ROLE_ADMIN"])
    fun `suspendingPrePostAuthorizeHasRoleContainsName when authorized then success`() {
        coEvery { delegate.suspendingPrePostAuthorizeHasRoleContainsName() } returns "user"
        runBlocking {
            assertThat(messageService!!.suspendingPrePostAuthorizeHasRoleContainsName()).contains("user")
        }
        coVerify(exactly = 1) { delegate.suspendingPrePostAuthorizeHasRoleContainsName() }
    }

    @Test
    @WithMockUser(authorities = ["ROLE_ADMIN"])
    fun `suspendingFlowPreAuthorize when user has role then success`() {
        runBlocking {
            assertThat(messageService!!.suspendingFlowPreAuthorize().toList()).containsExactly(1, 2, 3)
        }
    }

    @Test
    fun `suspendingFlowPreAuthorize when user does not have role then denied`() {
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.suspendingFlowPreAuthorize().collect()
            }
        }
    }

    @Test
    fun `suspendingFlowPostAuthorize when authorized then success`() {
        runBlocking {
            assertThat(messageService!!.suspendingFlowPostAuthorize(true).toList()).containsExactly(1, 2, 3)
        }
    }

    @Test
    fun `suspendingFlowPostAuthorize when not authorized then denied`() {
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.suspendingFlowPostAuthorize(false).collect()
            }
        }
    }

    @Test
    fun `suspendingFlowPreAuthorizeDelegate when not authorized then delegate not called`() {
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.suspendingFlowPreAuthorizeDelegate().collect()
            }
        }
        verify { delegate wasNot Called }
    }

    @Test
    fun `suspendingFlowPrePostAuthorizeBean when not pre authorized then delegate not called`() {
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.suspendingFlowPrePostAuthorizeBean(true).collect()
            }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `suspendingFlowPrePostAuthorizeBean when not post authorized then denied`() {
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.suspendingFlowPrePostAuthorizeBean(false).collect()
            }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `suspendingFlowPrePostAuthorizeBean when authorized then success`() {
        runBlocking {
            assertThat(messageService!!.suspendingFlowPrePostAuthorizeBean(true).toList()).containsExactly(1, 2, 3)
        }
    }

    @Test
    @WithMockUser(authorities = ["ROLE_ADMIN"])
    fun `suspendingFlowPreAuthorizeDelegate when user has role then delegate called`() {
        coEvery { delegate.flowPreAuthorize() } returns flow { }
        runBlocking {
            messageService!!.suspendingFlowPreAuthorizeDelegate().collect()
        }
        coVerify(exactly = 1) { delegate.flowPreAuthorize() }
    }

    @Test
    @WithMockUser(authorities = ["ROLE_ADMIN"])
    fun `flowPreAuthorize when user has role then success`() {
        runBlocking {
            assertThat(messageService!!.flowPreAuthorize().toList()).containsExactly(1, 2, 3)
        }
    }

    @Test
    fun `flowPreAuthorize when user does not have role then denied`() {
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.flowPreAuthorize().collect()
            }
        }
    }

    @Test
    fun `flowPostAuthorize when authorized then success`() {
        runBlocking {
            assertThat(messageService!!.flowPostAuthorize(true).toList()).containsExactly(1, 2, 3)
        }
    }

    @Test
    fun `flowPostAuthorize when not authorized then denied`() {
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.flowPostAuthorize(false).collect()
            }
        }
    }

    @Test
    fun `flowPreAuthorizeDelegate when user does not have role then delegate not called`() {
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.flowPreAuthorizeDelegate().collect()
            }
        }
        verify { delegate wasNot Called }
    }

    @Test
    @WithMockUser(authorities = ["ROLE_ADMIN"])
    fun `flowPreAuthorizeDelegate when user has role then delegate called`() {
        coEvery { delegate.flowPreAuthorize() } returns flow { }
        runBlocking {
            messageService!!.flowPreAuthorizeDelegate().collect()
        }
        coVerify(exactly = 1) { delegate.flowPreAuthorize() }
    }

    @Test
    fun `flowPrePostAuthorize when not pre authorized then denied`() {
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.flowPrePostAuthorize(true).collect()
            }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `flowPrePostAuthorize when not post authorized then denied`() {
        assertThatExceptionOfType(AccessDeniedException::class.java).isThrownBy {
            runBlocking {
                messageService!!.flowPrePostAuthorize(false).collect()
            }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `flowPrePostAuthorize when authorized then success`() {
        runBlocking {
            assertThat(messageService!!.flowPrePostAuthorize(true).toList()).containsExactly(1, 2, 3)
        }
    }

    @Configuration
    @EnableReactiveMethodSecurity
    open class Config {
        var delegate = mockk<KotlinReactiveMessageService>()

        @Bean
        open fun messageService(): KotlinReactiveMessageServiceImpl {
            return KotlinReactiveMessageServiceImpl(this.delegate)
        }

        @Bean
        open fun authz(): Authz {
            return Authz()
        }

        open class Authz {
            fun check(r: Boolean): Boolean {
                return r
            }
        }
    }
}
