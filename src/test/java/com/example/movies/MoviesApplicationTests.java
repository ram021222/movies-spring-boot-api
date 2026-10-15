package com.example.movies;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;

@SpringBootTest
class MoviesApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void genericRecordCrudAndQueriesWork() {
		MovieController controller = new MovieController();
		String text1 = "Record-" + System.nanoTime();
		Map<String, Object> request = Map.of(
				"text1", text1,
				"text2", "Test director",
				"enum", "drama",
				"amount", new BigDecimal("7.5"),
				"count", 120,
				"flag", true);

		Map<String, Object> created = controller.create(request).getBody();
		Assertions.assertNotNull(created);
		Assertions.assertEquals(text1, created.get("text1"));
		Long id = (Long) created.get("id");

		Assertions.assertEquals(text1, controller.getOne(id.toString()).get("text1"));
		Map<String, Object> listed = controller.list(Map.of(
				"enum", "drama", "minAmount", "7", "sort", "amount"));
		Assertions.assertEquals(1, listed.get("total"));
		Assertions.assertEquals(id, ((Map<?, ?>) ((java.util.List<?>) listed.get("data")).get(0)).get("id"));
		assertBadRequest(() -> controller.create(request));

		Assertions.assertEquals(true, controller.patch(id.toString(), Map.of()).get("flag"));
		Assertions.assertEquals(false, controller.patch(id.toString(),
				Map.of("flag", false, "ignored", "value")).get("flag"));
		Assertions.assertEquals(HttpStatus.NO_CONTENT, controller.delete(id.toString()).getStatusCode());
		assertNotFound(() -> controller.getOne(id.toString()));
	}

	private void assertBadRequest(Runnable action) {
		ResponseStatusException exception = Assertions.assertThrows(ResponseStatusException.class, action::run);
		Assertions.assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
	}

	private void assertNotFound(Runnable action) {
		ResponseStatusException exception = Assertions.assertThrows(ResponseStatusException.class, action::run);
		Assertions.assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
	}
}
