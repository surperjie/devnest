package com.devnest.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 基座自检(P0 task 0.6):证明 {@link AbstractContainerTest} 提供的容器确实可用。
 *
 * <p><b>为什么基座自己要有测试</b>:没有自检的测试基础设施,会在几个月后变成
 * "配置写错了但没人发现,所有仓储测试都被跳过却显示绿色"的静默漏洞
 * (路线图风险 R1「自指漏洞」)。这里用真实连接证明容器不是摆设。
 *
 * <p>本机无 Docker 时整个类被显式跳过,原因写入报告 —— 不算静默降级。
 */
@EnabledIfDockerAvailable
@DisplayName("集成测试基座自检(需要 Docker)")
class ContainerSupportTest extends AbstractContainerTest {

    @Test
    @DisplayName("MySQL 容器可用:能连上并执行 SQL")
    void mysql_容器可用且能执行查询() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                mysqlJdbcUrl(), mysqlUsername(), mysqlPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT VERSION()")) {

            assertThat(resultSet.next())
                    .as("SELECT VERSION() 应当返回一行")
                    .isTrue();
            assertThat(resultSet.getString(1))
                    .as("镜像固定为 mysql:8.0.36,主版本号必须是 8")
                    .startsWith("8.");
        }
    }

    @Test
    @DisplayName("MySQL 容器可用:默认字符集为 utf8mb4")
    void mysql_默认字符集为_utf8mb4() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                mysqlJdbcUrl(), mysqlUsername(), mysqlPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT @@character_set_server")) {

            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1))
                    .as("基座刻意指定了 --character-set-server=utf8mb4,要与生产一致")
                    .isEqualTo("utf8mb4");
        }
    }

    @Test
    @DisplayName("Redis 容器可用:按 RESP 协议 PING 能收到 PONG")
    void redis_容器可用且能完成_PING() throws IOException {
        // 直接用 RESP 协议而不是引入 Jedis:证明的是"容器里跑的是真的 Redis",
        // 而不依赖任何客户端库的封装。也避免为一条冒烟测试引入新的编译期依赖
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(redisHost(), redisPort()), 5_000);
            socket.setSoTimeout(5_000);

            OutputStream out = socket.getOutputStream();
            out.write("PING\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            String statusLine = reader.readLine();

            assertThat(statusLine)
                    .as("Redis 对 PING 应回复简单字符串 +PONG")
                    .isEqualTo("+PONG");
        }
    }
}
