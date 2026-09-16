package com.sunmoon.bo.domain.soldout;

import com.sunmoon.bo.domain.menu.MenuRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 생성자가 둘인 서비스는 Spring에게 어느 쪽인지 말해줘야 합니다
 * ({@code @Autowired}). 이 계열에서 그걸 빠뜨려 배포에서 죽은 일이
 * <b>두 번</b> 있었고(Order의 BusinessDayService, APP-SERVER의 MenuService),
 * 두 번 다 나머지 테스트는 전부 통과했습니다 — 직접 {@code new} 하니까요.
 *
 * <p>{@code SoldOutService}도 테스트가 날짜를 고정할 수 있게 생성자를
 * 둘 두었습니다. 그래서 같은 검사를 여기에도 둡니다. 데이터베이스도 웹
 * 계층도 없이 "Spring이 이걸 만들 수 있나"만 묻습니다.
 */
class SoldOutServiceWiringTest {

    @Test
    void springCanConstructTheService() {
        new ApplicationContextRunner()
                .withBean(SoldOutRepository.class, () -> mock(SoldOutRepository.class))
                .withBean(MenuRepository.class, () -> mock(MenuRepository.class))
                .withBean(MenuEventPublisher.class, () -> mock(MenuEventPublisher.class))
                .withBean(SoldOutService.class)
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(SoldOutService.class));
    }
}
