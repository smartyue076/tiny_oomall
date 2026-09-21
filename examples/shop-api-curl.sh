# 1. 注册商家
curl -i -X POST 'http://127.0.0.1:8082/api/merchants' \
  -H 'Content-Type: application/json' \
  -d '{
    "mobile": "13910000001",
    "password": "Password123",
    "name": "商家—张三"
  }'

# 2. 登录，复制响应 data.id，替换下面命令中的 YOUR_SESSION_ID
curl -i -X POST 'http://127.0.0.1:8082/api/auth/sessions' \
  -H 'Content-Type: application/json' \
  -d '{
    "mobile": "13910000001",
    "password": "Password123"
  }'

# 3. 注册平台管理员（教学环境使用；生产系统通常不开放此接口）
curl -i -X POST 'http://127.0.0.1:8082/api/platform-admins' \
  -H 'Content-Type: application/json' \
  -d '{
    "mobile": "13810000001",
    "password": "Password123",
    "name": "平台管理员"
  }'

# 4. 平台管理员登录
curl -i -X POST 'http://127.0.0.1:8082/api/auth/platform-admin-sessions' \
  -H 'Content-Type: application/json' \
  -d '{
    "mobile": "13810000001",
    "password": "Password123"
  }'

# 5. 商户退出登录
curl -i -X DELETE 'http://127.0.0.1:8082/api/auth/sessions/current' \
  -H 'X-Session-Id: YOUR_SESSION_ID'

# 6. 平台管理员退出登录
curl -i -X DELETE 'http://127.0.0.1:8082/api/auth/platform-admin-sessions/current' \
  -H 'X-Session-Id: YOUR_PLATFORM_ADMIN_SESSION_ID'

# 7. 提交店铺上线申请
curl -i -X POST 'http://127.0.0.1:8082/api/shop-applications' \
  -H 'Content-Type: application/json' \
  -H 'X-Session-Id: YOUR_SESSION_ID' \
  -d '{
    "name": "店铺—张三",
    "contact": "张三",
    "mobile": "13910000001"
  }'

# 8. 平台管理员分页查看 NEW 状态的店铺申请
curl -i -G 'http://127.0.0.1:8082/api/shop-review-queue' \
  -H 'X-Session-Id: YOUR_PLATFORM_ADMIN_SESSION_ID' \
  --data-urlencode 'status=NEW' \
  --data-urlencode 'page=0' \
  --data-urlencode 'pageSize=10'

# 9. 平台管理员按状态和店铺名称精确查询
curl -i -G 'http://127.0.0.1:8082/api/shop-review-queue' \
  -H 'X-Session-Id: YOUR_PLATFORM_ADMIN_SESSION_ID' \
  --data-urlencode 'status=NEW' \
  --data-urlencode 'name=店铺—张三' \
  --data-urlencode 'page=0' \
  --data-urlencode 'pageSize=10'

# 10. 获取店铺状态选项（无需登录）
curl -i 'http://127.0.0.1:8082/api/shop-status-options'

# 11. 分页查看已上线店铺（无需登录）
curl -i -G 'http://127.0.0.1:8082/api/shops' \
  --data-urlencode 'page=0' \
  --data-urlencode 'pageSize=10'

# 12. 商户修改自己店铺的资料；字段均可选，只传需要修改的字段
curl -i -X PUT 'http://127.0.0.1:8082/api/merchant/shop/detail' \
  -H 'Content-Type: application/json' \
  -H 'X-Session-Id: YOUR_MERCHANT_SESSION_ID' \
  -d '{
    "name": "店铺—张三",
    "contact": "张三",
    "mobile": "13910000001"
  }'

# 13. 商户查看自己的店铺管理详情，不需要传 shopId。
curl -i 'http://127.0.0.1:8082/api/merchant/shop/detail' \
  -H 'X-Session-Id: YOUR_MERCHANT_SESSION_ID'

# 14. 平台管理员查看指定店铺的管理详情
# 将 1 替换为实际的 shopId。
curl -i 'http://127.0.0.1:8082/api/shops/1/management-detail' \
  -H 'X-Session-Id: YOUR_PLATFORM_ADMIN_SESSION_ID'

# 15. 平台管理员通过店铺申请。通过后状态变为 OFFLINE，商户可再上线。
curl -i -X PUT 'http://127.0.0.1:8082/api/shop-applications/1/review' \
  -H 'Content-Type: application/json' \
  -H 'X-Session-Id: YOUR_PLATFORM_ADMIN_SESSION_ID' \
  -d '{
    "conclusion": "APPROVED"
  }'

# 16. 平台管理员拒绝店铺申请。
curl -i -X PUT 'http://127.0.0.1:8082/api/shop-applications/1/review' \
  -H 'Content-Type: application/json' \
  -H 'X-Session-Id: YOUR_PLATFORM_ADMIN_SESSION_ID' \
  -d '{
    "conclusion": "REJECTED",
    "reason": "申请资料不完整"
  }'

# 17. 商户上线自己的店铺。仅 OFFLINE 状态可以上线。
curl -i -X PUT 'http://127.0.0.1:8082/api/merchant/shop/online' \
  -H 'X-Session-Id: YOUR_MERCHANT_SESSION_ID'

# 18. 商户下线自己的店铺。仅 ONLINE 状态可以下线。
curl -i -X PUT 'http://127.0.0.1:8082/api/merchant/shop/offline' \
  -H 'X-Session-Id: YOUR_MERCHANT_SESSION_ID'

# 19. 平台管理员下线指定店铺。将 1 替换为实际的 shopId。
curl -i -X PUT 'http://127.0.0.1:8082/api/shops/1/offline' \
  -H 'X-Session-Id: YOUR_PLATFORM_ADMIN_SESSION_ID'
