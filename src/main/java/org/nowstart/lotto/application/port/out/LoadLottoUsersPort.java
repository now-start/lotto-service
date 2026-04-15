package org.nowstart.lotto.application.port.out;

import java.util.List;
import org.nowstart.lotto.domain.model.LottoUser;

public interface LoadLottoUsersPort {

    List<LottoUser> loadUsers();
}
