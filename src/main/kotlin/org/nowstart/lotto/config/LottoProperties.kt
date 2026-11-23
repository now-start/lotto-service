package org.nowstart.lotto.config

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/** `lotto.*` 설정. 키 이름은 Config Server 값과 맞춰 그대로 유지한다. */
@Validated
@ConfigurationProperties(prefix = "lotto")
class LottoProperties {

    @field:Valid
    @field:NotEmpty(message = "최소 1명 이상의 사용자 정보가 필요합니다")
    var users: MutableList<User> = mutableListOf()

    @field:Min(value = 1, message = "동시 실행 세션 수는 최소 1이어야 합니다")
    @field:Max(value = 50, message = "동시 실행 세션 수는 최대 50까지 가능합니다")
    var maxConcurrentSessions: Int = 3

    /** true면 최종 결제 클릭 직전에 멈춘다(local 프로파일 기본 true). */
    var dryRun: Boolean = false

    @field:Valid
    var cron: Cron = Cron()

    class User {
        @field:NotBlank(message = "로또 사이트 아이디는 필수입니다")
        var id: String? = null

        @field:NotBlank(message = "로또 사이트 비밀번호는 필수입니다")
        var password: String? = null

        @field:Min(value = 1, message = "구매 수량은 최소 1장이어야 합니다")
        @field:Max(value = 5, message = "구매 수량은 최대 5장까지 가능합니다")
        var count: Int = 5

        @field:Email(message = "올바른 이메일 형식이어야 합니다")
        @field:NotBlank(message = "알림 수신 이메일은 필수입니다")
        var email: String? = null

        var init: Boolean = false
    }

    class Cron {
        @field:NotBlank(message = "로또 확인 크론 표현식은 필수입니다")
        var check: String = "0 0 22 * * 6"

        @field:NotBlank(message = "로또 구매 크론 표현식은 필수입니다")
        var buy: String = "0 0 9 * * 0"
    }
}
