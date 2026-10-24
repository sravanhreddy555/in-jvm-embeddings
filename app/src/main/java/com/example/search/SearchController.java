package com.example.search;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SearchController {

    private final SemanticSearchService searchService;

    public SearchController(SemanticSearchService searchService) {
        this.searchService = searchService;
    }

    /** {@code GET /search?q=kafka engineer&k=5} returns {@code [{id, title, score}]}, best match first. */
    @GetMapping("/search")
    public List<SemanticSearchService.Hit> search(@RequestParam String q, @RequestParam(defaultValue = "5") int k) {
        return searchService.search(q, k);
    }
}
