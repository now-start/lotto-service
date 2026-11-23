package org.nowstart.lotto.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.port.outbound.AccountSnapshot
import org.nowstart.lotto.application.port.outbound.AutomationPort
import org.nowstart.lotto.application.port.outbound.Delivery
import org.nowstart.lotto.application.port.outbound.LoadUsersPort
import org.nowstart.lotto.application.port.outbound.SendNotificationPort
import org.nowstart.lotto.application.port.outbound.UserAccount

class InitializeInteractorTest {

    @Test
    fun `기동 초기화는 사용 중인 계정을 건너뛰고 다른 계정은 독립 세션에서 동시에 로그인한다`() {
        val first = UserAccount("first", "password", 1, "first@example.org", true)
        val second = first.copy(id = "second", email = "second@example.org")
        val busy = first.copy(id = "busy")
        val disabled = first.copy(id = "disabled", init = false)
        val users = mockk<LoadUsersPort>()
        every { users.loadUsers() } returns listOf(busy, disabled, first, second)
        val locks = AccountLocks()
        locks.tryAcquire(busy.id)

        val automation = mockk<AutomationPort>()
        val sessions = ConcurrentLinkedQueue<FakeSession>()
        every { automation.openSession() } answers { FakeSession().also(sessions::add) }
        val logins = CyclicBarrier(2)
        every { automation.login(any(), any()) } answers {
            logins.await(10, TimeUnit.SECONDS)
            AccountSnapshot(secondArg<UserAccount>().id, "5000")
        }
        val recipients = ConcurrentLinkedQueue<String>()
        val notifications = SendNotificationPort { message ->
            recipients.add(message.to)
            Delivery.SENT
        }

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            InitializeInteractor(users, automation, notifications, NotificationFactory(), locks, executor).initialize()
        }

        verify(exactly = 0) { automation.login(any(), busy) }
        verify(exactly = 0) { automation.login(any(), disabled) }
        assertThat(recipients).containsExactlyInAnyOrder(first.email, second.email)
        assertThat(sessions).hasSize(2).allMatch { it.closed && !it.failed }
        assertThat(locks.tryAcquire(busy.id)).isFalse()
        locks.release(busy.id)
        assertThat(locks.isIdle()).isTrue()
    }
}
