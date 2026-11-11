# seatkeeper-gateway · 通知网关

独立进程，固定端口 **8082**；只读 `notification` 表，不写任何业务表。

## 构建

在仓库根目录：

```cmd
mvn -q -DskipTests package
```

只构建本模块：

```cmd
mvn -q -DskipTests -pl seatkeeper-gateway -am package
```

产物：`seatkeeper-gateway/target/seatkeeper-gateway.jar`

## 启动

```cmd
cd seatkeeper-gateway
start-gateway.bat
```

或：

```cmd
java -jar seatkeeper-gateway/target/seatkeeper-gateway.jar --server.port=8082
```

## 验证

```cmd
curl http://localhost:8082/actuator/health
:: {"status":"UP",...}

curl http://localhost:8082/api/v1/gateway/ping
:: {"service":"seatkeeper-gateway","status":"UP","port":8082,...}
```

## 约定

| 项 | 值 |
|---|---|
| 端口 | 8082（不与 A 机 8080/8081 冲突） |
| 基础包 | `cn.edu.stu.seatkeeper.gateway` |
| 健康检查 | `/actuator/health` |
| 存活探针 | `/api/v1/gateway/ping` |
| 数据访问 | 只读 `notification` 表 |

## 周计划

| 周 | 内容 |
|---|---|
| W1 | 模块骨架、独立进程、8082、健康检查 |
| W2 | 会话结构 + lastId 游标设计 |
| W3 | 部署到 D 机，C 机可访问 |
| W4 | 2 秒轮询 notification 表，投递给在线会话（A0） |
| W5 | afterId 补拉接口 |
| W6 | WebSocket（A1） |
| W7 | docker-compose / NSSM / CI |
| W8 | 停网关演练 + 部署手册 |
