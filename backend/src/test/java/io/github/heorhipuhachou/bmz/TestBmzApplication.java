package io.github.heorhipuhachou.bmz;

import org.springframework.boot.SpringApplication;

public class TestBmzApplication {

	public static void main(String[] args) {
		SpringApplication.from(BmzApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
