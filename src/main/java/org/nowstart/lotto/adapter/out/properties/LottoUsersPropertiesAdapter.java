package org.nowstart.lotto.adapter.out.properties;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort;
import org.nowstart.lotto.config.LottoProperties;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LottoUsersPropertiesAdapter implements LoadLottoUsersPort {

    private final LottoProperties lottoProperties;

    @Override
    public List<LottoUser> loadUsers() {
        return lottoProperties.getUsers().stream()
                .map(user -> new LottoUser(
                        user.getId(),
                        user.getPassword(),
                        user.getCount(),
                        user.getEmail(),
                        Boolean.TRUE.equals(user.getInit())
                ))
                .toList();
    }
}
