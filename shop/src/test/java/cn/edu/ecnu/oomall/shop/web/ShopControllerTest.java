package cn.edu.ecnu.oomall.shop.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import cn.edu.ecnu.oomall.core.api.ApiResponse;

class ShopControllerTest {

    @Test
    void shopStatusOptionsContainEveryStatusAndDisplayName() {
        ShopController controller = new ShopController(null);

        ApiResponse<List<ShopController.ShopStatusOption>> response = controller.shopStatusOptions();

        assertThat(response.code()).isEqualTo("OK");
        List<ShopController.ShopStatusOption> options = response.data();
        assertThat(options).hasSize(4);
        assertThat(options.get(0).code()).isEqualTo("NEW");
        assertThat(options.get(0).name()).isEqualTo("待审核");
        assertThat(options.get(1).code()).isEqualTo("OFFLINE");
        assertThat(options.get(1).name()).isEqualTo("已下线");
        assertThat(options.get(2).code()).isEqualTo("ONLINE");
        assertThat(options.get(2).name()).isEqualTo("营业中");
        assertThat(options.get(3).code()).isEqualTo("REJECTED");
        assertThat(options.get(3).name()).isEqualTo("已拒绝");
    }
}
