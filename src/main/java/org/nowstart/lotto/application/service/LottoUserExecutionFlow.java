package org.nowstart.lotto.application.service;

@FunctionalInterface
interface LottoUserExecutionFlow {

    LottoUserExecutionContext execute(LottoUserAutomationExecutor automationExecutor);
}
