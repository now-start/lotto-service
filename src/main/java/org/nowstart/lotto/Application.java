package org.nowstart.lotto;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.MessageDto;
import org.nowstart.lotto.data.dto.PageDto;
import org.nowstart.lotto.data.dto.UserDto;
import org.nowstart.lotto.data.entity.UserEntity;
import org.nowstart.lotto.data.type.ConstantsType;
import org.nowstart.lotto.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableScheduling
@EnableDiscoveryClient
@SpringBootApplication
@ConfigurationPropertiesScan
@RequiredArgsConstructor
public class Application implements CommandLineRunner {

    private final UserRepository userRepository;
    private final Browser browser;
    private final JavaMailSender javaMailSender;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) {
        for (UserEntity user : userRepository.findAll()) {
            if (user.getInit() == null || !user.getInit()) {
                log.info("[Init][{}] - Skip", user.getId());
                continue;
            }

            log.info("[Init][{}] - Start", user.getId());

            try (PageDto pageDto = createManagedPage()) {
                UserDto userDto = login(pageDto.page(), user);
                sendEmail(MessageDto.builder()
                        .subject(String.format("⏳[%s] Init Test⏳", user.getId()))
                        .text(userDto.toString())
                        .to(user.getEmail())
                        .build());
                log.info("[Init][{}] - Success, deposit: {}", user.getId(), userDto.getDeposit());
            } catch (Exception e) {
                log.error("[Init][{}] - Failed", user.getId(), e);
            }

            log.info("[Init][{}] - Complete", user.getId());
        }
    }

    private PageDto createManagedPage() {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(ConstantsType.USER_AGENT_CHROME.getValue())
                .setIsMobile(false));

        context.addInitScript(ConstantsType.SCRIPT_CHROME_PLATFORM.getValue());
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));

        return new PageDto(context.newPage(), page -> {
            BrowserContext ctx = page.context();
            try {
                if (!page.isClosed()) {
                    page.close();
                }
                ctx.close();
            } catch (Exception e) {
                log.warn("리소스 종료 실패", e);
            }
        });
    }

    private UserDto login(Page page, UserEntity user) {
        page.navigate(ConstantsType.URL_LOGIN.getValue());

        Locator idInput = page.getByPlaceholder(ConstantsType.ID_INPUT.getValue());
        if (idInput.isVisible()) {
            idInput.fill(user.getId());
            page.getByPlaceholder(ConstantsType.PASSWORD_INPUT.getValue()).fill(user.getPassword());
            page.click(ConstantsType.LOGIN_LINK.getValue());
        }

        Locator changeLaterLink = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
                .setName(ConstantsType.CHANGE_LATER.getValue()));
        if (changeLaterLink.isVisible()) {
            changeLaterLink.click();
        }
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.navigate(ConstantsType.URL_MY_PAGE.getValue());

        return UserDto.builder()
                .name(page.locator(ConstantsType.USER_NAME.getValue()).innerText())
                .deposit(page.locator(ConstantsType.USER_DEPOSIT.getValue()).innerText())
                .build();
    }

    private void sendEmail(MessageDto message) throws Exception {
        MimeMessageHelper helper = new MimeMessageHelper(javaMailSender.createMimeMessage(), true, "UTF-8");
        helper.setFrom("no-reply@nowstart.org");
        helper.setTo(message.getTo());
        helper.setSubject(message.getSubject());
        helper.setText(message.getText());
        javaMailSender.send(helper.getMimeMessage());
    }
}
