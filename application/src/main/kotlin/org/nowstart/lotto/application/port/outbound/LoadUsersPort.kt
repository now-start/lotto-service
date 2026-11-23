package org.nowstart.lotto.application.port.outbound

interface LoadUsersPort {

    fun loadUsers(): List<UserAccount>
}

data class UserAccount(
    val id: String,
    val password: String,
    val count: Int,
    val email: String,
    val init: Boolean,
) {
    /** 로그·예외 메시지에 비밀번호가 남지 않도록 가린다. */
    override fun toString(): String = "UserAccount(id=$id, password=***, count=$count, email=$email, init=$init)"
}
