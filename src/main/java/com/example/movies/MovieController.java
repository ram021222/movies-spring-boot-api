package com.example.movies;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/api/movies", "/api/records"})
public class MovieController {
    // This in-memory map preserves creation order and is empty after a restart.
    private final Map<Long, Movie> movies = new LinkedHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final Set<Long> genericRecords = new HashSet<>();

    private static final Set<String> GENRES = Set.of(
            "action", "comedy", "drama", "thriller", "scifi"
    );
    private static final Map<String, String> FIELD_ALIASES = Map.of(
            "title", "text1",
            "director", "text2",
            "genre", "enum",
            "rating", "amount",
            "durationMinutes", "count",
            "nowShowing", "flag"
    );
    private static final Set<String> LIST_PARAMETERS = Set.of(
            "page", "limit", "genre", "nowShowing", "nowshowing", "minRating",
            "maxRating", "sort", "order", "search", "enum", "flag",
            "minAmount", "maxAmount", "minamount", "maxamount"
    );

    @PostMapping
    public synchronized ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Movie movie = readFullMovie(body);
        ensureUnique(movie, null);
        movie.setId(nextId.getAndIncrement());
        movies.put(movie.getId(), movie);
        if (isGeneric(body)) {
            genericRecords.add(movie.getId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(movie));
    }

    @GetMapping("/{id}")
    public synchronized Map<String, Object> getOne(@PathVariable String id) {
        return toResponse(findMovie(id));
    }

    @GetMapping
    public synchronized Map<String, Object> list(@RequestParam Map<String, String> query) {
        if (!LIST_PARAMETERS.containsAll(query.keySet())) {
            throw badRequest("query parameter is not supported");
        }

        int page = positiveInteger(query, "page", 1);
        int limit = positiveInteger(query, "limit", 10);
        if (limit > 50) {
            throw badRequest("limit must be at most 50");
        }

        String requestedGenre = queryValue(query, "genre", "enum");
        String genre = requestedGenre == null ? null : genre(requestedGenre);
        Boolean nowShowing = parseBooleanQuery(query);
        BigDecimal minRating = parseDecimalQuery(query, "minRating", "minAmount", "minamount");
        BigDecimal maxRating = parseDecimalQuery(query, "maxRating", "maxAmount", "maxamount");
        String search = query.getOrDefault("search", "").trim().toLowerCase(Locale.ROOT);

        String sort = query.get("sort");
        String order = query.getOrDefault("order", "asc");
        if (!order.equalsIgnoreCase("asc") && !order.equalsIgnoreCase("desc")) {
            throw badRequest("order must be asc or desc");
        }
        if (sort != null && !Set.of("title", "rating", "durationMinutes", "text1", "amount", "count").contains(sort)) {
            throw badRequest("sort must be text1, amount, or count");
        }

        List<Movie> matching = new ArrayList<>(movies.values()).stream()
                .filter(movie -> genre == null || movie.getGenre().equals(genre))
                .filter(movie -> nowShowing == null || movie.getNowShowing().equals(nowShowing))
                .filter(movie -> minRating == null || movie.getRating().compareTo(minRating) >= 0)
                .filter(movie -> maxRating == null || movie.getRating().compareTo(maxRating) <= 0)
                .filter(movie -> search.isEmpty()
                        || movie.getTitle().toLowerCase(Locale.ROOT).contains(search)
                        || movie.getDirector().toLowerCase(Locale.ROOT).contains(search))
                .collect(Collectors.toList());

        // A reversed range is valid; it simply has no matching movies.
        if (minRating != null && maxRating != null && minRating.compareTo(maxRating) > 0) {
            matching.clear();
        }

        if (sort != null) {
            Comparator<Movie> comparator = comparatorFor(sort);
            if (order.equalsIgnoreCase("desc")) {
                comparator = comparator.reversed();
            }
            matching.sort(comparator);
        }

        int total = matching.size();
        int totalPages = total == 0 ? 0 : (int) ((total + (long) limit - 1) / limit);
        long startLong = (long) (page - 1) * limit;
        List<Movie> data;
        if (startLong >= total) {
            data = List.of();
        } else {
            int start = (int) startLong;
            int end = (int) Math.min(startLong + limit, total);
            data = new ArrayList<>(matching.subList(start, end));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data.stream().map(this::toResponse).toList());
        response.put("page", page);
        response.put("limit", limit);
        response.put("total", total);
        response.put("totalPages", totalPages);
        return response;
    }

    @PutMapping("/{id}")
    public synchronized Map<String, Object> replace(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Movie existing = findMovie(id);
        Movie replacement = readFullMovie(body);
        ensureUnique(replacement, existing.getId());
        replacement.setId(existing.getId());
        movies.put(existing.getId(), replacement);
        genericRecords.remove(existing.getId());
        if (isGeneric(body)) {
            genericRecords.add(existing.getId());
        }
        return toResponse(replacement);
    }

    @PatchMapping("/{id}")
    public synchronized Map<String, Object> patch(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Movie existing = findMovie(id);
        if (body == null) {
            return toResponse(existing);
        }

        String title = hasField(body, "title")
                ? text(fieldValue(body, "title"), "text1", 3, 60) : existing.getTitle();
        String director = hasField(body, "director")
                ? text(fieldValue(body, "director"), "text2", 2, 40) : existing.getDirector();
        String genre = hasField(body, "genre")
                ? genre(fieldValue(body, "genre")) : existing.getGenre();
        BigDecimal rating = hasField(body, "rating")
                ? rating(fieldValue(body, "rating")) : existing.getRating();
        Integer duration = hasField(body, "durationMinutes")
                ? duration(fieldValue(body, "durationMinutes")) : existing.getDurationMinutes();
        Boolean showing = hasField(body, "nowShowing")
                ? booleanField(fieldValue(body, "nowShowing"), "flag") : existing.getNowShowing();

        Movie updated = new Movie(existing.getId(), title, director, genre, rating, duration, showing);
        ensureUnique(updated, existing.getId());
        movies.put(existing.getId(), updated);
        if (isGeneric(body)) {
            genericRecords.add(existing.getId());
        }
        return toResponse(updated);
    }

    @DeleteMapping("/{id}")
    public synchronized ResponseEntity<Void> delete(@PathVariable String id) {
        Movie movie = findMovie(id);
        movies.remove(movie.getId());
        genericRecords.remove(movie.getId());
        return ResponseEntity.noContent().build();
    }

    private Movie readFullMovie(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw badRequest("request body must contain all movie fields");
        }
        // Only known fields are read. A body-supplied id and other extra fields are ignored.
        return new Movie(null,
                text(fieldValue(body, "title"), "text1", 3, 60),
                text(fieldValue(body, "director"), "text2", 2, 40),
                genre(fieldValue(body, "genre")),
                rating(fieldValue(body, "rating")),
                duration(fieldValue(body, "durationMinutes")),
                booleanField(fieldValue(body, "nowShowing"), "flag"));
    }

    private boolean hasField(Map<String, Object> body, String field) {
        return body.containsKey(field) || body.containsKey(FIELD_ALIASES.get(field));
    }

    private Object fieldValue(Map<String, Object> body, String field) {
        String alias = FIELD_ALIASES.get(field);
        if (body.containsKey(field) && body.containsKey(alias)) {
            throw badRequest("provide only one of " + field + " or " + alias);
        }
        return body.containsKey(field) ? body.get(field) : body.get(alias);
    }

    private boolean isGeneric(Map<String, Object> body) {
        return body.keySet().stream().anyMatch(FIELD_ALIASES::containsValue);
    }

    private Map<String, Object> toResponse(Movie movie) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", movie.getId());
        if (genericRecords.contains(movie.getId())) {
            response.put("text1", movie.getTitle());
            response.put("text2", movie.getDirector());
            response.put("enum", movie.getGenre());
            response.put("amount", movie.getRating());
            response.put("count", movie.getDurationMinutes());
            response.put("flag", movie.getNowShowing());
        } else {
            response.put("title", movie.getTitle());
            response.put("director", movie.getDirector());
            response.put("genre", movie.getGenre());
            response.put("rating", movie.getRating());
            response.put("durationMinutes", movie.getDurationMinutes());
            response.put("nowShowing", movie.getNowShowing());
        }
        return response;
    }

    private void ensureUnique(Movie candidate, Long ignoredId) {
        boolean duplicate = movies.values().stream()
                .anyMatch(existing -> !existing.getId().equals(ignoredId)
                        && existing.getTitle().equalsIgnoreCase(candidate.getTitle()));
        if (duplicate) {
            throw badRequest("text1 must be unique");
        }
    }

    private String text(Object value, String field, int minLength, int maxLength) {
        if (!(value instanceof String)) {
            throw badRequest(field + " must be a string");
        }
        String result = ((String) value).trim();
        if (result.length() < minLength || result.length() > maxLength) {
            throw badRequest(field + " must be between " + minLength + " and " + maxLength + " characters");
        }
        return result;
    }

    private String genre(Object value) {
        String result = text(value, "genre", 1, 40);
        if (!GENRES.contains(result)) {
            throw badRequest("genre must be one of action, comedy, drama, thriller, scifi");
        }
        return result;
    }

    private BigDecimal rating(Object value) {
        BigDecimal result = decimal(value, "rating");
        if (result.compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest("rating must be greater than 0");
        }
        if (result.compareTo(BigDecimal.TEN) > 0) {
            throw badRequest("rating must be at most 10");
        }
        return result;
    }

    private Integer duration(Object value) {
        BigDecimal result = decimal(value, "durationMinutes");
        if (result.stripTrailingZeros().scale() > 0) {
            throw badRequest("durationMinutes must be a whole number");
        }
        if (result.compareTo(BigDecimal.ONE) < 0
                || result.compareTo(BigDecimal.valueOf(600)) > 0) {
            throw badRequest("durationMinutes must be between 1 and 600");
        }
        try {
            return result.intValueExact();
        } catch (ArithmeticException ex) {
            throw badRequest("durationMinutes must be a whole number");
        }
    }

    private Boolean booleanField(Object value, String field) {
        if (!(value instanceof Boolean)) {
            throw badRequest(field + " must be true or false");
        }
        return (Boolean) value;
    }

    private BigDecimal decimal(Object value, String field) {
        if (!(value instanceof Number)) {
            throw badRequest(field + " must be a number");
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            throw badRequest(field + " must be a number");
        }
    }

    private int positiveInteger(Map<String, String> query, String name, int defaultValue) {
        String raw = query.get(name);
        if (raw == null) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                throw badRequest(name + " must be a positive whole number");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw badRequest(name + " must be a positive whole number");
        }
    }

    private BigDecimal parseDecimalQuery(Map<String, String> query, String... names) {
        String raw = queryValue(query, names);
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            throw badRequest(names[0] + " must be a number");
        }
    }

    private Boolean parseBooleanQuery(Map<String, String> query) {
        String raw = queryValue(query, "nowShowing", "nowshowing", "flag");
        if (raw == null) {
            return null;
        }
        if (raw.equals("true")) {
            return true;
        }
        if (raw.equals("false")) {
            return false;
        }
        throw badRequest("nowShowing must be true or false");
    }

    private Comparator<Movie> comparatorFor(String sort) {
        return switch (sort) {
            case "title", "text1" -> Comparator.comparing(Movie::getTitle, String.CASE_INSENSITIVE_ORDER);
            case "rating", "amount" -> Comparator.comparing(Movie::getRating);
            case "durationMinutes", "count" -> Comparator.comparing(Movie::getDurationMinutes);
            default -> throw badRequest("sort must be text1, amount, or count");
        };
    }

    private String queryValue(Map<String, String> query, String... names) {
        String result = null;
        for (String name : names) {
            if (query.containsKey(name)) {
                if (result != null) {
                    throw badRequest("provide only one equivalent query parameter");
                }
                result = query.get(name);
            }
        }
        return result;
    }

    private Movie findMovie(String rawId) {
        long id;
        try {
            id = Long.parseLong(rawId);
        } catch (NumberFormatException ex) {
            throw notFound();
        }
        Movie movie = movies.get(id);
        if (movie == null) {
            throw notFound();
        }
        return movie;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found");
    }
}