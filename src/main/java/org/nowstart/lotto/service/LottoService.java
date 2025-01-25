package org.nowstart.lotto.service;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.LottoResultDto;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LottoService {

    @Value("${lotto.id}")
    private String lottoId;
    @Value("${lotto.password}")
    private String lottoPassword;
    @Value("${lotto.count}")
    private String lottoCount;
    private final Page page;

    /**
     * 로또 로그인
     *
     * @return LottoUserDto
     */
    public LottoUserDto loginLotto() {
        page.navigate("https://dhlottery.co.kr/user.do?method=login");

        if (page.getByPlaceholder("아이디").count() > 0) {
            page.getByPlaceholder("아이디").fill(lottoId);
            page.getByPlaceholder("비밀번호").fill(lottoPassword);
            page.getByRole(AriaRole.GROUP, new Page.GetByRoleOptions().setName("LOGIN")).getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("로그인")).click();
        }

        // 로그인 후 모바일 화면으로 전환되는 문제 있음
        page.navigate("https://dhlottery.co.kr/common.do?method=main");
        Locator information = page.locator("ul.information");

        return LottoUserDto.builder()
                .name(information.locator("li:nth-child(1) > span > strong").innerText())
                .deposit(information.locator("li.money > a:nth-child(2) > strong").innerText())
                .build();
    }

    /**
     * 로또 당첨 확인
     *
     * @return List<LottoResultDto>
     */
    public List<LottoResultDto> checkLotto() {
        page.navigate("https://dhlottery.co.kr/userSsl.do?method=myPage");

        Locator table = page.locator("#article > div:nth-child(2) > div > div:nth-child(2) > table > tbody > tr");

        return table.all().stream().map(row -> LottoResultDto.builder()
                .date(row.locator("td:nth-child(1)").innerText())
                .round(row.locator("td:nth-child(2)").innerText())
                .name(row.locator("td:nth-child(3)").innerText())
                .number(row.locator("td:nth-child(4)").innerText().replace(" ", ""))
                .count(row.locator("td:nth-child(5)").innerText())
                .result(row.locator("td:nth-child(6)").innerText())
                .price(row.locator("td:nth-child(7)").innerText())
                .build()).toList();
    }

    /**
     * 로또 상세 확인
     *
     * @param lottoResultDto LottoResultDto
     * @return ByteArrayResource
     */
    public ByteArrayResource detailLotto(LottoResultDto lottoResultDto) {
        page.navigate("https://dhlottery.co.kr/myPage.do?method=lotto645Detail&orderNo=0000000000000000&barcode=" + lottoResultDto.getNumber() + "&issueNo=1");
        return new ByteArrayResource(page.screenshot());
    }

    /**
     * 로또 구매
     */
    public void buyLotto() {
        page.navigate("https://ol.dhlottery.co.kr/olotto/game/game645.do");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("자동번호발급 구매 수량 전체를 자동번호로 발급 받을 수 있습니다.")).click();
        page.getByRole(AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName("적용수량")).selectOption(lottoCount);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("확인")).click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("구매하기")).click();
        page.locator("#popupLayerConfirm").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("확인")).click();
    }
}
