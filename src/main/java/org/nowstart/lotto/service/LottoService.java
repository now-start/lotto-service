package org.nowstart.lotto.service;

import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import org.nowstart.lotto.data.dto.BatchExecutionResult;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.nowstart.lotto.data.type.TaskMode;
import org.nowstart.lotto.data.type.TriggerType;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LottoService {

    private final LottoLoginService lottoLoginService;
    private final LottoTaskOrchestrator lottoTaskOrchestrator;

    public LottoUserDto loginLotto(Page page, LottoProperties.User user) {
        return lottoLoginService.login(page, user);
    }

    public BatchExecutionResult execute(TaskMode mode, TriggerType trigger) {
        return lottoTaskOrchestrator.execute(mode, trigger);
    }
}
