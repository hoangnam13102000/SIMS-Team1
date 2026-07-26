package com.utils;

import java.util.List;

public class PaginationHelper {
    
    public static class PaginationResult<T> {
        private List<T> data;
        private int currentPage;
        private int pageSize;
        private int totalRecords;
        private int totalPages;
        
        public PaginationResult() {
            // Default constructor
        }
        
        public PaginationResult(List<T> data, int currentPage, int pageSize, int totalRecords) {
            this.data = data;
            this.currentPage = currentPage;
            this.pageSize = pageSize;
            this.totalRecords = totalRecords;
            this.totalPages = (int) Math.ceil((double) totalRecords / pageSize);
        }
        
        // Getters and Setters
        public List<T> getData() {
            return data;
        }
        
        public void setData(List<T> data) {
            this.data = data;
        }
        
        public int getCurrentPage() {
            return currentPage;
        }
        
        public void setCurrentPage(int currentPage) {
            this.currentPage = currentPage;
        }
        
        public int getPageSize() {
            return pageSize;
        }
        
        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }
        
        public int getTotalRecords() {
            return totalRecords;
        }
        
        public void setTotalRecords(int totalRecords) {
            this.totalRecords = totalRecords;
        }
        
        public int getTotalPages() {
            return totalPages;
        }
        
        public void setTotalPages(int totalPages) {
            this.totalPages = totalPages;
        }
        
        public boolean hasNext() {
            return currentPage < totalPages;
        }
        
        public boolean hasPrevious() {
            return currentPage > 1;
        }
        
        public int getStartRecord() {
            return (currentPage - 1) * pageSize + 1;
        }
        
        public int getEndRecord() {
            return Math.min(currentPage * pageSize, totalRecords);
        }
        
        @Override
        public String toString() {
            return String.format("Page %d/%d (Records %d-%d/%d)", 
                currentPage, totalPages, getStartRecord(), getEndRecord(), totalRecords);
        }
    }
}