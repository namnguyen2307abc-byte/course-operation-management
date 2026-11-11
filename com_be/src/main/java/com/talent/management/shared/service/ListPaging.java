package com.talent.management.shared.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

public final class ListPaging {
    private ListPaging() { }

    public static PageRequest request(int page, int size, Sort sort) {
        if (page < 0 || page > 100000 || !Set.of(5, 10, 20, 50).contains(size)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trang không hợp lệ; số dòng phải là 5, 10, 20 hoặc 50");
        }
        return PageRequest.of(page, size, sort);
    }

    public static String keyword(String value) {
        String term = value == null ? "" : value.trim();
        if (term.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Từ khóa tối đa 100 ký tự");
        }
        return term.toLowerCase(java.util.Locale.ROOT).replace("!", "!!").replace("%", "!%")
                .replace("_", "!_").replace("[", "![");
    }
}
