package org.nowstart.lotto

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.support.GenericApplicationContext

class RemoteConfigTest {

    @Test
    fun `비로컬 프로필은 lotto 이름으로 원격 SMTP 타임아웃을 가져온다`() {
        val requests = ConcurrentLinkedQueue<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requests.add(path)
            val profile = path.substringAfterLast('/')
            val response = """
                {"name":"lotto","profiles":["$profile"],"propertySources":[{"name":"remote-mail","source":{
                    "spring.mail.properties.mail.smtp.connectiontimeout":"1234",
                    "spring.mail.properties.mail.smtp.timeout":"2345"
                }}]}
            """.trimIndent().toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            GenericApplicationContext().use { context ->
                TestPropertyValues.of(
                    "spring.profiles.active=config-test",
                    "SPRING_CONFIG_IMPORT=configserver:http://127.0.0.1:${server.address.port}",
                ).applyTo(context)
                ConfigDataApplicationContextInitializer().initialize(context)

                assertThat(requests).contains("/lotto/config-test")
                val mail = Binder.get(context.environment)
                    .bind("spring.mail.properties", Bindable.mapOf(String::class.java, String::class.java)).get()
                assertThat(mail).containsEntry("mail.smtp.connectiontimeout", "1234")
                    .containsEntry("mail.smtp.timeout", "2345")
                    .doesNotContainKey("mail.smtp.writetimeout")
            }
        } finally {
            server.stop(0)
        }
    }
}
