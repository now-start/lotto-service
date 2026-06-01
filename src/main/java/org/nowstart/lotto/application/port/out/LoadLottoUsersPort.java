package org.nowstart.lotto.application.port.out;

import java.util.List;

public interface LoadLottoUsersPort {

    List<LottoUser> loadUsers();

    record LottoUser(
            String id,
            String password,
            int count,
            String email,
            boolean init
    ) {
    }
}
