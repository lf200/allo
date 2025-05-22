package com.example.secaicontainerengine.pojo.dto.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ShowBusinessConfig implements Serializable {

    private List<Evaluation> evaluateMethods;
    private static final long serialVersionUID = 1L;

}