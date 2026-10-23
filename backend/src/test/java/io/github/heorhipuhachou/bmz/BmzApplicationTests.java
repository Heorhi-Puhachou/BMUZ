package io.github.heorhipuhachou.bmz;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BmzApplicationTests {

	@Test
	void contextLoads() {
	}

}
