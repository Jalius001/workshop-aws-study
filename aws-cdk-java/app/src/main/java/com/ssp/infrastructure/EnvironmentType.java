package com.ssp.infrastructure;

import lombok.Getter;

public enum EnvironmentType {

    DEV("dev"), 
    PROD("prod");

    @Getter
    private String id;

    EnvironmentType(final String id) {
        this.id = id;
    }

    public static EnvironmentType of(final String id) {
        for (EnvironmentType type : EnvironmentType.values()) {
            if (type.id.equals(id)) return type;
        }
        return null;
    }
}
