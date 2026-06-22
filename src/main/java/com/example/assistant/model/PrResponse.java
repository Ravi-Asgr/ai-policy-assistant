package com.example.assistant.model;

import java.util.ArrayList;
import java.util.List;

public class PrResponse {

    /*
    message is "" is PrRecord found else set to LLL type response
     */
    private String message;
    private List<SearchResultRow> searchResultRow;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<SearchResultRow> getSearchResultRow() {
        return searchResultRow;
    }

    public void setSearchResultRow(List<SearchResultRow> searchResultRow) {
        this.searchResultRow = new ArrayList<>(searchResultRow);
    }
}
