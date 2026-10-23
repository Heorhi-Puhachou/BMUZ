package io.github.heorhipuhachou.bmuz;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BmuzApplicationTests {

	@Test
	void contextLoads() {
	}

}
