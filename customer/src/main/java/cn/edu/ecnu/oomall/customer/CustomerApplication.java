package cn.edu.ecnu.oomall.customer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** customer 微服务启动入口。 */
@SpringBootApplication(scanBasePackages = "cn.edu.ecnu.oomall")
@MapperScan("cn.edu.ecnu.oomall.customer.repository")
public class CustomerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomerApplication.class, args);
    }
}
