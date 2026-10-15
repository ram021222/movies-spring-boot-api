package com.example.movies;

import java.math.BigDecimal;

public class Movie {
    private Long id;
    private String title;
    private String director;
    private String genre;
    private BigDecimal rating;
    private Integer durationMinutes;
    private Boolean nowShowing;

    public Movie() {
    }

    public Movie(Long id, String title, String director, String genre,
                 BigDecimal rating, Integer durationMinutes, Boolean nowShowing) {
        this.id = id;
        this.title = title;
        this.director = director;
        this.genre = genre;
        this.rating = rating;
        this.durationMinutes = durationMinutes;
        this.nowShowing = nowShowing;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public Boolean getNowShowing() { return nowShowing; }
    public void setNowShowing(Boolean nowShowing) { this.nowShowing = nowShowing; }
}