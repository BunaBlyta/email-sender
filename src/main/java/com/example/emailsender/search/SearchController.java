package com.example.emailsender.search;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inbox/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchResponse search(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "20") int maxResults) {
        return searchService.search(
                principal.getAttribute("email"),
                query,
                maxResults
        );
    }
}
