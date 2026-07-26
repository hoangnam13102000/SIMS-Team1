package com.validation;

import java.util.ArrayList;
import java.util.List;

public final class FormValidator {

    private final List<FieldCheck> fields = new ArrayList<>();

    public FieldCheck field(String value) {
        FieldCheck check = new FieldCheck(value);
        fields.add(check);
        return check;
    }

    public String validate() {
        for (FieldCheck check : fields) {
            for (ValidationRule<String> rule : check.rules) {
                String error = rule.validate(check.value);
                if (error != null) return error;
            }
        }
        return null;
    }

    public static final class FieldCheck {
        private final String value;
        private final List<ValidationRule<String>> rules = new ArrayList<>();

        private FieldCheck(String value) { this.value = value; }

        public FieldCheck rule(ValidationRule<String> rule) { rules.add(rule); return this; }
        public FieldCheck required(String message) { return rule(Rules.required(message)); }
        public FieldCheck minLength(int min, String message) { return rule(Rules.minLength(min, message)); }
        public FieldCheck maxLength(int max, String message) { return rule(Rules.maxLength(max, message)); }
        public FieldCheck matches(String regex, String message) { return rule(Rules.matches(regex, message)); }
        public FieldCheck email(String message) { return rule(Rules.email(message)); }
        public FieldCheck phoneVn(String message) { return rule(Rules.phoneVn(message)); }
        public FieldCheck digitsOnly(String message) { return rule(Rules.digitsOnly(message)); }
        public FieldCheck exactLength(int length, String message) { return rule(Rules.exactLength(length, message)); }
        public FieldCheck integer(String message) { return rule(Rules.integer(message)); }
        public FieldCheck longNumber(String message) { return rule(Rules.longNumber(message)); }
        public FieldCheck positiveLong(String message) { return rule(Rules.positiveLong(message)); }
    }
}