package org.nowstart.lotto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nowstart.lotto.data.dto.BatchExecutionResult;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.dto.PageDto;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.nowstart.lotto.data.type.ExecutionStatus;
import org.nowstart.lotto.data.type.TaskMode;
import org.nowstart.lotto.data.type.TriggerType;

@ExtendWith(MockitoExtension.class)
class LottoTaskOrchestratorTest {

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
    private RetryExecutor retryExecutor;

    @Mock
    private Page page;

    private LottoProperties lottoProperties;
    private LottoTaskOrchestrator lottoTaskOrchestrator;

    @BeforeEach
    void setUp() {
        lottoProperties = new LottoProperties();
        lottoProperties.setUsers(new ArrayList<>());
        lottoTaskOrchestrator = new LottoTaskOrchestrator(
                pageService,
                lottoProperties,
                lottoLoginService,
                lottoPurchaseService,
                lottoResultService,
                lottoNotificationService,
                retryExecutor
        );

        when(pageService.createManagedPage()).thenAnswer(invocation -> new PageDto(page, pageService));
        when(retryExecutor.execute(anyString(), any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });
    }

    @Test
    void shouldReturnSuccessWhenAllUsersProcessed() {
        LottoProperties.User user = createUser("user1");
        lottoProperties.getUsers().add(user);
        when(lottoLoginService.login(page, user)).thenReturn(LottoUserDto.builder().name("u1").deposit("1000").build());
        when(lottoResultService.check(page)).thenReturn(List.of());

        BatchExecutionResult result = lottoTaskOrchestrator.execute(TaskMode.CHECK_ONLY, TriggerType.MANUAL);

        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.totalUsers()).isEqualTo(1);
        assertThat(result.successUsers()).isEqualTo(1);
        assertThat(result.failedUsers()).isZero();

        verify(lottoNotificationService).sendSuccess(eq(user), eq(TaskMode.CHECK_ONLY), any(LottoUserDto.class), anyList());
        verify(lottoNotificationService, never()).sendFailure(any(), any(), any());
    }

    @Test
    void shouldReturnPartialFailureWhenSomeUsersFail() {
        LottoProperties.User successUser = createUser("user1");
        LottoProperties.User failedUser = createUser("user2");
        lottoProperties.getUsers().add(successUser);
        lottoProperties.getUsers().add(failedUser);

        when(lottoLoginService.login(eq(page), any(LottoProperties.User.class))).thenAnswer(invocation -> {
            LottoProperties.User user = invocation.getArgument(1);
            if ("user2".equals(user.getId())) {
                throw new IllegalStateException("login failed");
            }
            return LottoUserDto.builder().name("ok").deposit("5000").build();
        });
        when(lottoResultService.check(page)).thenReturn(List.of());

        BatchExecutionResult result = lottoTaskOrchestrator.execute(TaskMode.CHECK_ONLY, TriggerType.SCHEDULE);

        assertThat(result.status()).isEqualTo(ExecutionStatus.PARTIAL_FAILURE);
        assertThat(result.totalUsers()).isEqualTo(2);
        assertThat(result.successUsers()).isEqualTo(1);
        assertThat(result.failedUsers()).isEqualTo(1);

        verify(lottoNotificationService).sendFailure(eq(failedUser), eq(TaskMode.CHECK_ONLY), any(Exception.class));
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
}
