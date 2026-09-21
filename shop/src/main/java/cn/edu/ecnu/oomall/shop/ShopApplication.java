package cn.edu.ecnu.oomall.shop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** shop 微服务启动入口。 */
@SpringBootApplication(scanBasePackages = "cn.edu.ecnu.oomall")
@MapperScan("cn.edu.ecnu.oomall.shop.repository")
public class ShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
