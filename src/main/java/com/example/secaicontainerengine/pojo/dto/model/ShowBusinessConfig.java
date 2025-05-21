package com.example.secaicontainerengine.pojo.dto.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ShowBusinessConfig implements Serializable {
    private String dimension;
    private List<String> metrics;
    private List<String> methods;
    private static final long serialVersionUID = 1L;
}