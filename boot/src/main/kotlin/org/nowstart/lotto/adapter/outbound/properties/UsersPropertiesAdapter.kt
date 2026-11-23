package org.nowstart.lotto.adapter.outbound.properties

import org.nowstart.lotto.application.port.outbound.LoadUsersPort
import org.nowstart.lotto.application.port.outbound.UserAccount
import org.nowstart.lotto.config.AutomationProperties
import org.springframework.stereotype.Component

/** 설정(lotto.users)에서 사용자 목록을 읽는다. 값 검증은 [AutomationProperties]의 Bean Validation이 맡는다. */
@Component
class UsersPropertiesAdapter(private val automationProperties: AutomationProperties) : LoadUsersPort {

    override fun loadUsers(): List<UserAccount> = automationProperties.users.map {
        UserAccount(
            id = it.id.orEmpty(),
            password = it.password.orEmpty(),
            count = it.count,
            email = it.email.orEmpty(),
            init = it.init,
        )
    }
}
