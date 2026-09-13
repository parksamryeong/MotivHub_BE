package com.motivhub.be.realtime.service;

public enum TaskEditableField {
    DESCRIPTION("description"),
    NOTE("note");

    private final String pathSegment;

    TaskEditableField(String pathSegment) {
        this.pathSegment = pathSegment;
    }

    public String pathSegment() {
        return pathSegment;
    }

    public static TaskEditableField fromPathSegment(String segment) {
        for (TaskEditableField field : values()) {
            if (field.pathSegment.equals(segment)) {
                return field;
            }
        }
        throw new IllegalArgumentException("알 수 없는 편집 필드입니다: " + segment);
    }
}
