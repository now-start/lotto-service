package org.nowstart.lotto.service;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.LottoResultDto;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static java.util.stream.Collectors.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LottoService {

    @Value("${lotto.id}")
    private String lottoId;
    @Value("${lotto.password}")
    private String lottoPassword;
    private final Page page;

    /**
     * 로또 로그인
     * @return LottoUserDto
     */
    public LottoUserDto loginLotto() {
        page.navigate("https://dhlottery.co.kr/user.do?method=login");

        page.fill("[placeholder=\"아이디\"]", lottoId);
        page.fill("[placeholder=\"비밀번호\"]", lottoPassword);
        page.click("form[name=\"jform\"] >> text=로그인");

        Locator information = page.locator("ul.information");

        return LottoUserDto.builder()
            .name(information.locator("li:nth-child(1) > span > strong").innerText())
            .deposit(information.locator("li.money > a:nth-child(2) > strong").innerText())
            .build();
    }

    /**
     * 로또 당첨 확인
     * @return List<LottoResultDto>
     */
    public List<LottoResultDto> checkLotto() {
        page.navigate("https://dhlottery.co.kr/userSsl.do?method=myPage");

        Locator table = page.locator("#article > div:nth-child(2) > div > div:nth-child(2) > table > tbody > tr");

        return table.all().stream().map(row -> LottoResultDto.builder()
            .date(row.locator("td:nth-child(1)").innerText())
            .round(row.locator("td:nth-child(2)").innerText())
            .name(row.locator("td:nth-child(3)").innerText())
            .number(row.locator("td:nth-child(4)").innerText().replace(" ", ","))
            .count(row.locator("td:nth-child(5)").innerText())
            .result(row.locator("td:nth-child(6)").innerText())
            .price(row.locator("td:nth-child(7)").innerText())
            .build()).toList();
    }

    /**
     * 로또 구매
     */
    public void buyLotto() {
        //#article > div:nth-child(2) > div > div:nth-child(2)document.querySelector("#article > div:nth-child(2) > div > div:nth-child(2) > div")
        //#article > div:nth-child(2) > div > div:nth-child(2) > table > tbody
        //#article > div:nth-child(2) > div > div:nth-child(2) > table > tbody > tr:nth-child(1)
    }
}
