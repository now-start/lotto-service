package org.nowstart.lotto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Page;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nowstart.lotto.data.dto.BatchExecutionResult;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.dto.PageDto;
import org.nowstart.lotto.data.exception.InvalidManualUserSelectionException;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.nowstart.lotto.data.type.ExecutionStatus;
import org.nowstart.lotto.data.type.TaskMode;
import org.nowstart.lotto.data.type.TriggerType;

@ExtendWith(MockitoExtension.class)
class LottoServiceTest {

    @Mock
    private PageService pageService;

    @Mock
    private LottoLoginService lottoLoginService;

    @Mock
    private LottoPurchaseService lottoPurchaseService;

    @Mock
    private LottoResultService lottoResultService;

    @Mock
    private LottoNotificationService lottoNotificationService;

    @Mock
    private Page page;

    private LottoProperties lottoProperties;
    private LottoService lottoService;

    @BeforeEach
    void setUp() {
        lottoProperties = new LottoProperties();
        lottoProperties.setUsers(new ArrayList<>());
        lottoProperties.getUsers().add(createUser("user1"));
        lottoProperties.getUsers().add(createUser("user2"));
        lottoProperties.getUsers().add(createUser("user3"));

        lottoService = new LottoService(
                lottoProperties,
                pageService,
                lottoLoginService,
                lottoPurchaseService,
                lottoResultService,
                lottoNotificationService
        );
    }

    @Test
    void shouldExecuteAllUsersWhenUserIdsMissing() {
        stubManagedPage();
        when(lottoLoginService.login(eq(page), any(LottoProperties.User.class)))
                .thenReturn(LottoUserDto.builder().name("ok").deposit("1000").build());
        when(lottoResultService.check(page)).thenReturn(List.of());

        BatchExecutionResult result = lottoService.execute(TaskMode.CHECK_ONLY, TriggerType.MANUAL, null);

        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.totalUsers()).isEqualTo(3);
        assertThat(result.successUsers()).isEqualTo(3);
        assertThat(result.failedUsers()).isZero();

        verify(lottoNotificationService).sendSuccess(eq(lottoProperties.getUsers().get(0)), eq(TaskMode.CHECK_ONLY), any(LottoUserDto.class),
                anyList());
        verify(lottoNotificationService).sendSuccess(eq(lottoProperties.getUsers().get(1)), eq(TaskMode.CHECK_ONLY), any(LottoUserDto.class),
                anyList());
        verify(lottoNotificationService).sendSuccess(eq(lottoProperties.getUsers().get(2)), eq(TaskMode.CHECK_ONLY), any(LottoUserDto.class),
                anyList());
    }

    @Test
    void shouldExecuteSelectedUsersOnly() {
        List<String> selected = List.of("user2", "user1");
        stubManagedPage();
        when(lottoLoginService.login(eq(page), any(LottoProperties.User.class)))
                .thenReturn(LottoUserDto.builder().name("ok").deposit("1000").build());
        when(lottoResultService.check(page)).thenReturn(List.of());

        BatchExecutionResult result = lottoService.execute(TaskMode.BUY_AND_CHECK, TriggerType.MANUAL, selected);

        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.totalUsers()).isEqualTo(2);
        assertThat(result.successUsers()).isEqualTo(2);
        assertThat(result.failedUsers()).isZero();

        verify(lottoPurchaseService).buy(page, lottoProperties.getUsers().get(1));
        verify(lottoPurchaseService).buy(page, lottoProperties.getUsers().get(0));
        verify(lottoPurchaseService, never()).buy(page, lottoProperties.getUsers().get(2));
    }

    @Test
    void shouldThrowWhenInvalidUserIdIncluded() {
        assertThatThrownBy(() -> lottoService.execute(TaskMode.CHECK_ONLY, TriggerType.MANUAL, List.of("user1", "missing")))
                .isInstanceOf(InvalidManualUserSelectionException.class)
                .satisfies(exception -> {
                    InvalidManualUserSelectionException invalid = (InvalidManualUserSelectionException) exception;
                    assertThat(invalid.getInvalidUserIds()).containsExactly("missing");
                    assertThat(invalid.getAvailableUserIds()).containsExactly("user1", "user2", "user3");
                });
    }

    @Test
    void shouldReturnPartialFailureWhenSomeUsersFail() {
        stubManagedPage();
        when(lottoLoginService.login(eq(page), any(LottoProperties.User.class))).thenAnswer(invocation -> {
            LottoProperties.User user = invocation.getArgument(1);
            if ("user2".equals(user.getId())) {
                throw new IllegalStateException("login failed");
            }
            return LottoUserDto.builder().name("ok").deposit("5000").build();
        });
        when(lottoResultService.check(page)).thenReturn(List.of());

        BatchExecutionResult result = lottoService.execute(TaskMode.CHECK_ONLY, TriggerType.SCHEDULE, null);

        assertThat(result.status()).isEqualTo(ExecutionStatus.PARTIAL_FAILURE);
        assertThat(result.totalUsers()).isEqualTo(3);
        assertThat(result.successUsers()).isEqualTo(2);
        assertThat(result.failedUsers()).isEqualTo(1);

        verify(lottoNotificationService).sendFailure(eq(lottoProperties.getUsers().get(1)), eq(TaskMode.CHECK_ONLY), any(Exception.class));
    }

    private LottoProperties.User createUser(String id) {
        LottoProperties.User user = new LottoProperties.User();
        user.setId(id);
        user.setPassword("password");
        user.setEmail(id + "@nowstart.org");
        user.setCount(1);
        user.setInit(false);
        return user;
    }

    private void stubManagedPage() {
        when(pageService.createManagedPage()).thenAnswer(_ -> new PageDto(page, pageService));
    }
}
