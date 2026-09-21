# 1. 注册顾客
curl -i -X POST 'http://127.0.0.1:8081/api/customers' \
  -H 'Content-Type: application/json' \
  -d '{
    "mobile": "13910000001",
    "password": "Password123",
    "name": "张三"
  }'

# 2. 登录，复制响应 data.id，替换下面命令中的 YOUR_SESSION_ID
curl -i -X POST 'http://127.0.0.1:8081/api/auth/sessions' \
  -H 'Content-Type: application/json' \
  -d '{
    "mobile": "13910000001",
    "password": "Password123"
  }'

# 3. 查询地址列表
curl -i -X GET 'http://127.0.0.1:8081/api/addresses?page=0&pageSize=10' \
  -H 'X-Session-Id: YOUR_SESSION_ID'

# 4. 新增地址，复制响应 data.id，替换后续命令中的 YOUR_ADDRESS_ID
curl -i -X POST 'http://127.0.0.1:8081/api/addresses' \
  -H 'Content-Type: application/json' \
  -H 'X-Session-Id: YOUR_SESSION_ID' \
  -d '{
    "consignee": "张三",
    "mobile": "13910000001",
    "address": "上海市浦东新区世纪大道1号",
    "beDefault": true
  }'

# 5. 修改地址
curl -i -X PUT 'http://127.0.0.1:8081/api/addresses/YOUR_ADDRESS_ID' \
  -H 'Content-Type: application/json' \
  -H 'X-Session-Id: YOUR_SESSION_ID' \
  -d '{
    "consignee": "李四",
    "mobile": "13910000002",
    "address": "上海市浦东新区世纪大道2号",
    "beDefault": true
  }'

# 6. 内部查询地址，复制注册响应 data.id，替换 YOUR_CUSTOMER_ID
curl -i -X GET 'http://127.0.0.1:8081/api/internal/customers/YOUR_CUSTOMER_ID/addresses/YOUR_ADDRESS_ID' \
  -H 'X-Internal-Call: true'

# 7. 删除地址
curl -i -X DELETE 'http://127.0.0.1:8081/api/addresses/YOUR_ADDRESS_ID' \
  -H 'X-Session-Id: YOUR_SESSION_ID'

# 8. 退出登录
curl -i -X DELETE 'http://127.0.0.1:8081/api/auth/sessions/current' \
  -H 'X-Session-Id: YOUR_SESSION_ID'
