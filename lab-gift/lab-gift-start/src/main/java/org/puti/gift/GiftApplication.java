package org.puti.gift;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author alin
 */
@MapperScan("org.puti.gift.infra.mapper")
@SpringBootApplication(scanBasePackages = "org.puti.gift")
public class GiftApplication {

    public static void main(String[] args) {
        SpringApplication.run(GiftApplication.class, args);
    }
}
