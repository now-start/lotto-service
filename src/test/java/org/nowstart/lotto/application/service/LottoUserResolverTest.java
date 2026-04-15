package org.nowstart.lotto.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort;
import org.nowstart.lotto.domain.exception.InvalidManualUserSelectionException;
import org.nowstart.lotto.domain.model.LottoUser;

@ExtendWith(MockitoExtension.class)
class LottoUserResolverTest {

    @Mock
    private LoadLottoUsersPort loadLottoUsersPort;

    private LottoUserResolver lottoUserResolver;

    @BeforeEach
    void setUp() {
        lottoUserResolver = new LottoUserResolver(loadLottoUsersPort);
    }

    @Test
    void shouldReturnAllUsersWhenRequestMissing() {
        List<LottoUser> users = List.of(createUser("user1"), createUser("user2"));
        when(loadLottoUsersPort.loadUsers()).thenReturn(users);

        List<LottoUser> resolvedUsers = lottoUserResolver.resolve(null);

        assertThat(resolvedUsers).containsExactlyElementsOf(users);
    }

    @Test
    void shouldReturnSelectedUsersInRequestedOrder() {
        List<LottoUser> users = List.of(createUser("user1"), createUser("user2"), createUser("user3"));
        when(loadLottoUsersPort.loadUsers()).thenReturn(users);

        List<LottoUser> resolvedUsers = lottoUserResolver.resolve(List.of("user3", "user1"));

        assertThat(resolvedUsers).extracting(LottoUser::id).containsExactly("user3", "user1");
    }

    @Test
    void shouldThrowWhenInvalidUserIdIncluded() {
        List<LottoUser> users = List.of(createUser("user1"), createUser("user2"), createUser("user3"));
        when(loadLottoUsersPort.loadUsers()).thenReturn(users);

        assertThatThrownBy(() -> lottoUserResolver.resolve(List.of("user1", "missing")))
                .isInstanceOf(InvalidManualUserSelectionException.class)
                .satisfies(exception -> {
                    InvalidManualUserSelectionException invalid = (InvalidManualUserSelectionException) exception;
                    assertThat(invalid.getInvalidUserIds()).containsExactly("missing");
                    assertThat(invalid.getAvailableUserIds()).containsExactly("user1", "user2", "user3");
                });
    }

    private LottoUser createUser(String id) {
        return new LottoUser(id, "password", 1, id + "@nowstart.org", false);
    }
}
