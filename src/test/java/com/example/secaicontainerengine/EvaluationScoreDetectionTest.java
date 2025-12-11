package com.example.secaicontainerengine;

import com.example.secaicontainerengine.service.modelEvaluation.EvaluationResultServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 目标检测任务评分函数单元测试
 * 测试 computeSafetyScore, computeFairnessScore, computeGeneralizationScore
 * 是否正确支持 detection 和 classification 两种任务类型
 */
@SpringBootTest
public class EvaluationScoreDetectionTest {

    private EvaluationResultServiceImpl evaluationResultService;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        evaluationResultService = new EvaluationResultServiceImpl();
        objectMapper = new ObjectMapper();

        // 使用反射注入 ObjectMapper
        try {
            java.lang.reflect.Field field = EvaluationResultServiceImpl.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(evaluationResultService, objectMapper);
        } catch (Exception e) {
            fail("无法注入 ObjectMapper: " + e.getMessage());
        }
    }

    // ==================== 安全性评分测试 ====================

    @Test
    public void testComputeSafetyScore_Detection_MembershipInferenceASR() throws Exception {
        // 测试目标检测任务的安全性评分
        String safetyResult = "{\"membership_inference_asr\": \"0.2\"}";

        double score = invokeComputeSafetyScore(safetyResult, "detection");

        // membership_inference_asr = 0.2, 得分应该是 1 - 0.2 = 0.8
        assertEquals(0.8, score, 0.01, "Detection任务安全性得分计算错误");
        System.out.println("✓ Detection - 安全性评分测试通过: membership_inference_asr=0.2, score=" + score);
    }

    @Test
    public void testComputeSafetyScore_Detection_LowASR() throws Exception {
        // 测试低攻击成功率（高安全性）
        String safetyResult = "{\"membership_inference_asr\": \"0.05\"}";

        double score = invokeComputeSafetyScore(safetyResult, "detection");

        // membership_inference_asr = 0.05, 得分应该是 1 - 0.05 = 0.95
        assertEquals(0.95, score, 0.01, "Detection任务低ASR安全性得分计算错误");
        System.out.println("✓ Detection - 低ASR测试通过: membership_inference_asr=0.05, score=" + score);
    }

    @Test
    public void testComputeSafetyScore_Classification_AUC() throws Exception {
        // 测试分类任务的安全性评分（使用传统指标）
        String safetyResult = "{\"auc\": \"0.85\", \"tpr_at_fpr\": \"0.05\", \"attack_average_precision\": \"0.90\"}";

        double score = invokeComputeSafetyScore(safetyResult, "classification");

        // 应该使用 normalizeMetric 计算，得分应大于0
        assertTrue(score > 0, "Classification任务安全性得分应大于0");
        System.out.println("✓ Classification - 安全性评分测试通过: score=" + score);
    }

    @Test
    public void testComputeSafetyScore_EmptyResult() throws Exception {
        // 测试空结果
        String safetyResult = "{}";

        double score = invokeComputeSafetyScore(safetyResult, "detection");

        assertEquals(0.0, score, 0.01, "空结果应返回0.0");
        System.out.println("✓ 空结果测试通过: score=" + score);
    }

    // ==================== 公平性评分测试 ====================

    @Test
    public void testComputeFairnessScore_Detection_PerformanceGap() throws Exception {
        // 测试目标检测任务的公平性评分
        String fairnessResult = "{\"performance_gap\": \"0.15\"}";

        double score = invokeComputeFairnessScore(fairnessResult, "detection");

        // performance_gap = 0.15, 得分应该是 1 - 0.15 = 0.85
        assertEquals(0.85, score, 0.01, "Detection任务公平性得分计算错误");
        System.out.println("✓ Detection - 公平性评分测试通过: performance_gap=0.15, score=" + score);
    }

    @Test
    public void testComputeFairnessScore_Detection_SmallGap() throws Exception {
        // 测试小性能差距（高公平性）
        String fairnessResult = "{\"performance_gap\": \"0.02\"}";

        double score = invokeComputeFairnessScore(fairnessResult, "detection");

        // performance_gap = 0.02, 得分应该是 1 - 0.02 = 0.98
        assertEquals(0.98, score, 0.01, "Detection任务小gap公平性得分计算错误");
        System.out.println("✓ Detection - 小gap测试通过: performance_gap=0.02, score=" + score);
    }

    @Test
    public void testComputeFairnessScore_Classification_SPD() throws Exception {
        // 测试分类任务的公平性评分（使用传统指标）
        String fairnessResult = "{\"spd\": \"0.1\", \"dir\": \"0.95\", \"eod\": \"0.05\", \"aod\": \"0.03\", \"consistency\": \"0.92\"}";

        double score = invokeComputeFairnessScore(fairnessResult, "classification");

        // 应该使用多个指标计算平均值，得分应大于0
        assertTrue(score > 0, "Classification任务公平性得分应大于0");
        System.out.println("✓ Classification - 公平性评分测试通过: score=" + score);
    }

    // ==================== 泛化性评分测试 ====================

    @Test
    public void testComputeGeneralizationScore_Detection_GapPairs() throws Exception {
        // 测试目标检测任务的泛化性评分
        String generalizationResult = "{\"gap_pairs\": \"0.12\"}";

        double score = invokeComputeGeneralizationScore(generalizationResult, "detection");

        // gap_pairs = 0.12, 得分应该是 1 - 0.12 = 0.88
        assertEquals(0.88, score, 0.01, "Detection任务泛化性得分计算错误");
        System.out.println("✓ Detection - 泛化性评分测试通过: gap_pairs=0.12, score=" + score);
    }

    @Test
    public void testComputeGeneralizationScore_Detection_SmallGap() throws Exception {
        // 测试小泛化差距（高泛化性）
        String generalizationResult = "{\"gap_pairs\": \"0.03\"}";

        double score = invokeComputeGeneralizationScore(generalizationResult, "detection");

        // gap_pairs = 0.03, 得分应该是 1 - 0.03 = 0.97
        assertEquals(0.97, score, 0.01, "Detection任务小gap泛化性得分计算错误");
        System.out.println("✓ Detection - 小gap测试通过: gap_pairs=0.03, score=" + score);
    }

    @Test
    public void testComputeGeneralizationScore_Classification_MSP() throws Exception {
        // 测试分类任务的泛化性评分（使用传统指标）
        String generalizationResult = "{\"msp\": \"0.75\", \"entropy\": \"1.2\", \"rademacher\": \"0.05\"}";

        double score = invokeComputeGeneralizationScore(generalizationResult, "classification");

        // 应该使用 normalizeGeneralizationMetric 计算，得分应大于0
        assertTrue(score > 0, "Classification任务泛化性得分应大于0");
        System.out.println("✓ Classification - 泛化性评分测试通过: score=" + score);
    }

    // ==================== 边界条件测试 ====================

    @Test
    public void testAllScores_Detection_PerfectScore() throws Exception {
        // 测试完美得分（所有指标最优）
        String safetyResult = "{\"membership_inference_asr\": \"0.0\"}";
        String fairnessResult = "{\"performance_gap\": \"0.0\"}";
        String generalizationResult = "{\"gap_pairs\": \"0.0\"}";

        double safetyScore = invokeComputeSafetyScore(safetyResult, "detection");
        double fairnessScore = invokeComputeFairnessScore(fairnessResult, "detection");
        double generalizationScore = invokeComputeGeneralizationScore(generalizationResult, "detection");

        assertEquals(1.0, safetyScore, 0.01, "完美安全性得分应为1.0");
        assertEquals(1.0, fairnessScore, 0.01, "完美公平性得分应为1.0");
        assertEquals(1.0, generalizationScore, 0.01, "完美泛化性得分应为1.0");

        System.out.println("✓ 完美得分测试通过: safety=" + safetyScore + ", fairness=" + fairnessScore + ", generalization=" + generalizationScore);
    }

    @Test
    public void testAllScores_Detection_WorstScore() throws Exception {
        // 测试最差得分（所有指标最差）
        String safetyResult = "{\"membership_inference_asr\": \"1.0\"}";
        String fairnessResult = "{\"performance_gap\": \"1.0\"}";
        String generalizationResult = "{\"gap_pairs\": \"1.0\"}";

        double safetyScore = invokeComputeSafetyScore(safetyResult, "detection");
        double fairnessScore = invokeComputeFairnessScore(fairnessResult, "detection");
        double generalizationScore = invokeComputeGeneralizationScore(generalizationResult, "detection");

        assertEquals(0.0, safetyScore, 0.01, "最差安全性得分应为0.0");
        assertEquals(0.0, fairnessScore, 0.01, "最差公平性得分应为0.0");
        assertEquals(0.0, generalizationScore, 0.01, "最差泛化性得分应为0.0");

        System.out.println("✓ 最差得分测试通过: safety=" + safetyScore + ", fairness=" + fairnessScore + ", generalization=" + generalizationScore);
    }

    // ==================== 辅助方法：通过反射调用私有方法 ====================

    private double invokeComputeSafetyScore(String result, String task) throws Exception {
        Method method = EvaluationResultServiceImpl.class.getDeclaredMethod("computeSafetyScore", String.class, String.class);
        method.setAccessible(true);
        return (double) method.invoke(evaluationResultService, result, task);
    }

    private double invokeComputeFairnessScore(String result, String task) throws Exception {
        Method method = EvaluationResultServiceImpl.class.getDeclaredMethod("computeFairnessScore", String.class, String.class);
        method.setAccessible(true);
        return (double) method.invoke(evaluationResultService, result, task);
    }

    private double invokeComputeGeneralizationScore(String result, String task) throws Exception {
        Method method = EvaluationResultServiceImpl.class.getDeclaredMethod("computeGeneralizationScore", String.class, String.class);
        method.setAccessible(true);
        return (double) method.invoke(evaluationResultService, result, task);
    }
}
