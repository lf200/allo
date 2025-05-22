package com.example.secaicontainerengine.pojo.dto.model;


import lombok.Data;

import java.util.List;

@Data
public class Evaluation {

    private String dimension;
    private List<String> metrics;
    private List<String> methods;

}
