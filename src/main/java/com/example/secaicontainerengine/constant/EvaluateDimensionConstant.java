package com.example.secaicontainerengine.constant;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EvaluateDimensionConstant {

    // method -> resultColumn 映射map
    public static final Map<String, String> METHOD_TO_TYPE_MAP;

    static {
        Map<String, String> map = new HashMap<>();

        //鲁棒性维度关联的指标
        map.put("fgsm", "robustness");
        map.put("pgd", "robustness");

        //基础维度关联的指标
        map.put("accuracy", "basic");
        map.put("precision", "basic");
        map.put("recall", "basic");
        map.put("f1score", "basic");

        //可解释性维度关联的指标
        map.put("shap", "interpretability");

        METHOD_TO_TYPE_MAP = Collections.unmodifiableMap(map);
    }

    public static String getType(String method) {
        return METHOD_TO_TYPE_MAP.get(method);
    }
}
