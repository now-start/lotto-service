package org.nowstart.lotto.application.port.outbound

interface LoadLottoUsersPort {

    fun loadUsers(): List<LottoUser>
}

data class LottoUser(
    val id: String,
    val password: String,
    val count: Int,
    val email: String,
    val init: Boolean,
) {
    /** 로그·예외 메시지에 비밀번호가 남지 않도록 가린다. */
    override fun toString(): String = "LottoUser(id=$id, password=***, count=$count, email=$email, init=$init)"
}
