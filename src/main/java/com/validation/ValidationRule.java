package com.validation;

@FunctionalInterface
public interface ValidationRule<T> {
    /** Tra ve null neu hop le, hoac thong bao loi neu khong hop le. */
    String validate(T value);
}