package io.github.heorhipuhachou.bmuz;

import org.springframework.boot.SpringApplication;

public class TestBmuzApplication {

	public static void main(String[] args) {
		SpringApplication.from(BmuzApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
