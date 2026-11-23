package org.nowstart.lotto.adapter.outbound.properties

import org.nowstart.lotto.application.port.outbound.LoadLottoUsersPort
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.config.LottoProperties
import org.springframework.stereotype.Component

/** 설정(lotto.users)에서 사용자 목록을 읽는다. 값 검증은 [LottoProperties]의 Bean Validation이 맡는다. */
@Component
class LottoUsersPropertiesAdapter(private val lottoProperties: LottoProperties) : LoadLottoUsersPort {

    override fun loadUsers(): List<LottoUser> = lottoProperties.users.map {
        LottoUser(
            id = it.id.orEmpty(),
            password = it.password.orEmpty(),
            count = it.count,
            email = it.email.orEmpty(),
            init = it.init,
        )
    }
}
