package com.demo;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * 极简本地文档AI智能体 - 支持多文档版本
 */
public class LocalDocumentAIAgent {

    private String documentContent;      // 合并后的文档内容
    private List<String> sentences;       // 所有句子
    private Map<String, Integer> keywordFrequency;  // 关键词频率
    private List<String> sourceFiles;     // 记录来源文件

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "的", "了", "是", "在", "我", "有", "和", "就", "不", "也", "都", "说",
            "a", "an", "the", "and", "of", "to", "in", "for", "on", "with", "that",
            "this", "is", "are", "was", "were", "be", "by", "at", "as", "from"
    ));

    // ========== 单文件模式 ==========
    public LocalDocumentAIAgent(String filePath) throws IOException {
        this.sourceFiles = new ArrayList<>();
        loadSingleDocument(filePath);
        initializeSentences();
        extractKeywords();
    }

    // ========== 文件夹模式：加载整个文件夹的所有txt文件 ==========
    public LocalDocumentAIAgent(String folderPath, boolean isFolder) throws IOException {
        this.sourceFiles = new ArrayList<>();
        loadFolderDocuments(folderPath);
        initializeSentences();
        extractKeywords();
    }

    /**
     * 加载单个文档
     */
    private void loadSingleDocument(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        this.documentContent = new String(Files.readAllBytes(path), "UTF-8");
        this.documentContent = this.documentContent.trim();
        this.sourceFiles.add(filePath);
        System.out.println("📄 已加载文档: " + filePath);
    }

    /**
     * 加载文件夹中的所有txt文件（支持递归搜索子文件夹）
     */
    private void loadFolderDocuments(String folderPath) throws IOException {
        Path folder = Paths.get(folderPath);
        if (!Files.exists(folder) || !Files.isDirectory(folder)) {
            throw new IOException("文件夹不存在: " + folderPath);
        }

        StringBuilder combinedContent = new StringBuilder();
        List<Path> txtFiles = new ArrayList<>();

        // 递归遍历文件夹，收集所有txt文件
        Files.walk(folder)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".txt"))
                .forEach(txtFiles::add);

        if (txtFiles.isEmpty()) {
            throw new IOException("文件夹中没有找到txt文件: " + folderPath);
        }

        System.out.println("📁 发现 " + txtFiles.size() + " 个txt文件");

        for (Path filePath : txtFiles) {
            try {
                String content = new String(Files.readAllBytes(filePath), "UTF-8");
                combinedContent.append(content).append("\n\n");
                this.sourceFiles.add(filePath.toString());
                System.out.println("   ✅ 已加载: " + filePath.getFileName());
            } catch (Exception e) {
                System.err.println("   ⚠️ 加载失败: " + filePath.getFileName() + " - " + e.getMessage());
            }
        }

        this.documentContent = combinedContent.toString().trim();
        System.out.println("📊 共加载 " + this.sourceFiles.size() + " 个文件，总字符数: " + documentContent.length());
    }
    private void initializeSentences() {
        sentences = new ArrayList<>();
        // 支持中英文标点分割
        String[] rawSentences = documentContent.split("[。！？；!?;\\n]+");
        for (String sentence : rawSentences) {
            sentence = sentence.trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        System.out.println("📝 已分割 " + sentences.size() + " 个句子");
    }

    private void extractKeywords() {
        keywordFrequency = new HashMap<>();
        String[] words = documentContent.split("[^\\u4e00-\\u9fa5a-zA-Z0-9]+");
        for (String word : words) {
            word = word.toLowerCase();
            if (word.isEmpty() || word.length() < 2 || STOP_WORDS.contains(word)) {
                continue;
            }
            keywordFrequency.put(word, keywordFrequency.getOrDefault(word, 0) + 1);
        }
        System.out.println("🔑 已提取 " + keywordFrequency.size() + " 个关键词");
    }

    // 获取来源文件信息
    public String getSourceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("📚 知识库信息\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("文档数量: ").append(sourceFiles.size()).append(" 个\n");
        sb.append("总句子数: ").append(sentences.size()).append(" 句\n");
        sb.append("总字符数: ").append(documentContent.length()).append(" 字符\n");
        sb.append("关键词数: ").append(keywordFrequency.size()).append(" 个\n\n");
        sb.append("📄 文件列表:\n");
        for (String file : sourceFiles) {
            Path path = Paths.get(file);
            sb.append("   • ").append(path.getFileName()).append("\n");
        }
        return sb.toString();
    }

    public String generateSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("【文档智能总结】\n");
        summary.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        summary.append("📊 文档统计：\n");
        summary.append("   • 文档数量: ").append(sourceFiles.size()).append(" 个\n");
        summary.append("   • 总字数: ").append(documentContent.length()).append(" 字符\n");
        summary.append("   • 总句子数: ").append(sentences.size()).append(" 句\n");
        summary.append("   • 独立词汇数: ").append(keywordFrequency.size()).append(" 个\n\n");

        summary.append("🏷️ 核心关键词：\n");
        List<Map.Entry<String, Integer>> sortedKeywords = new ArrayList<>(keywordFrequency.entrySet());
        sortedKeywords.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        for (int i = 0; i < Math.min(10, sortedKeywords.size()); i++) {
            Map.Entry<String, Integer> entry = sortedKeywords.get(i);
            summary.append("   ").append(i + 1).append(". ")
                    .append(entry.getKey()).append(" (").append(entry.getValue()).append("次)\n");
        }

        return summary.toString();
    }

    public String askQuestion(String question) {
        if (question == null || question.trim().isEmpty()) {
            return "问题不能为空，请重新输入。";
        }

        Set<String> questionKeywords = extractKeywordsFromText(question);
        if (questionKeywords.isEmpty()) {
            return "未从问题中提取到有效关键词，请使用更具体的词汇提问。";
        }

        SentenceMatch bestMatch = findBestMatchingSentence(questionKeywords);
        if (bestMatch == null || bestMatch.score <= 0.2) {
            return "未找到与问题相关的答案，请尝试其他问法或检查文档内容。";
        }

        String matchPercent = String.format("%.1f", bestMatch.score * 100);

        StringBuilder answer = new StringBuilder();
        answer.append("找到相关答案\n");
        answer.append("匹配度：").append(matchPercent).append("%\n");
        answer.append("回答：\n");
        answer.append(bestMatch.sentence).append("\n");

        if (bestMatch.score > 0.3) {
            answer.append("相关话题：\n");
            List<String> related = findRelatedKeywords(bestMatch.sentence);
            for (String keyword : related) {
                answer.append("- ").append(keyword).append("\n");
            }
        }

        return answer.toString();
    }

    private Set<String> extractKeywordsFromText(String text) {
        Set<String> keywords = new HashSet<>();
        Pattern chinesePhrase = Pattern.compile("[\\u4e00-\\u9fa5]{2,6}");
        Matcher matcher = chinesePhrase.matcher(text);
        while (matcher.find()) {
            String phrase = matcher.group();
            if (!STOP_WORDS.contains(phrase) && phrase.length() >= 2) {
                keywords.add(phrase);
                if (phrase.length() >= 3) {
                    for (int i = 0; i < phrase.length() - 1; i++) {
                        String bigram = phrase.substring(i, i + 2);
                        if (!STOP_WORDS.contains(bigram)) {
                            keywords.add(bigram);
                        }
                    }
                }
            }
        }
        Pattern englishWord = Pattern.compile("[a-zA-Z]{2,}");
        Matcher engMatcher = englishWord.matcher(text);
        while (engMatcher.find()) {
            keywords.add(engMatcher.group().toLowerCase());
        }
        return keywords;
    }

    private SentenceMatch findBestMatchingSentence(Set<String> questionKeywords) {
        SentenceMatch bestMatch = null;
        for (String sentence : sentences) {
            double score = calculateImprovedMatchScore(questionKeywords, sentence);
            if (bestMatch == null || score > bestMatch.score) {
                bestMatch = new SentenceMatch(sentence, score);
            }
            if (score > 0.6) break;
        }
        return bestMatch;
    }

    private double calculateImprovedMatchScore(Set<String> questionKeywords, String sentence) {
        if (questionKeywords.isEmpty()) return 0;
        double totalScore = 0;
        String sentenceLower = sentence.toLowerCase();
        for (String keyword : questionKeywords) {
            if (sentenceLower.contains(keyword.toLowerCase())) {
                totalScore += 1.0 + (keyword.length() / 10.0);
            } else {
                for (String sentenceKeyword : extractKeywordsFromText(sentence)) {
                    if (sentenceKeyword.contains(keyword) || keyword.contains(sentenceKeyword)) {
                        totalScore += 0.5;
                        break;
                    }
                }
            }
        }
        return Math.min(1.0, totalScore / questionKeywords.size());
    }

    private List<String> findRelatedKeywords(String sentence) {
        Set<String> sentenceKeywords = extractKeywordsFromText(sentence);
        List<Map.Entry<String, Integer>> sortedKeywords = new ArrayList<>(keywordFrequency.entrySet());
        sortedKeywords.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        List<String> related = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sortedKeywords) {
            if (!sentenceKeywords.contains(entry.getKey()) && related.size() < 3) {
                related.add(entry.getKey());
            }
        }
        return related;
    }

    // ========== Web界面需要的方法 ==========

    public int getSentenceCount() {
        return this.sentences.size();
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("charCount", documentContent.length());
        stats.put("sentenceCount", sentences.size());
        stats.put("keywordCount", keywordFrequency.size());
        stats.put("fileCount", sourceFiles.size());
        stats.put("fileNames", getFileNames());
        stats.put("topKeywords", getTopKeywords(10));
        return stats;
    }

    private List<String> getFileNames() {
        List<String> names = new ArrayList<>();
        for (String path : sourceFiles) {
            names.add(Paths.get(path).getFileName().toString());
        }
        return names;
    }

    private List<Map<String, Object>> getTopKeywords(int n) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(keywordFrequency.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        for (int i = 0; i < Math.min(n, sorted.size()); i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("keyword", sorted.get(i).getKey());
            item.put("frequency", sorted.get(i).getValue());
            result.add(item);
        }
        return result;
    }

    private static class SentenceMatch {
        String sentence;
        double score;
        SentenceMatch(String sentence, double score) {
            this.sentence = sentence;
            this.score = score;
        }
    }
}
//实现单个文件的训练
//package com.demo;
//
//import java.io.File;
//import java.io.FileNotFoundException;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.*;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
///**
// * 极简本地文档AI智能体Demo
// *
// * 功能：
// * 1. 读取本地test.txt文件
// * 2. 提供文档整体内容总结（基于关键词提取和统计）
// * 3. 提供关键词匹配式问答（基于TF-IDF和文本相似度）
// *
// * 特点：
// * - 不调用任何外网接口，不需要密钥，完全离线运行
// * - 使用纯Java原生类，无任何第三方依赖
// * - 控制台交互，结构极简
// * - 预留对接私有化本地大模型的接口注释
// *
// * @author AI Assistant
// * @version 1.0
// */
//public class LocalDocumentAIAgent {
//
//    // 文档内容
//    private String documentContent;
//
//    // 文档句子列表（用于问答匹配）
//    private List<String> sentences;
//
//    // 高频关键词（用于总结）
//    private Map<String, Integer> keywordFrequency;
//
//    // 停用词列表（过滤无意义的词）
//    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
//            "的", "了", "是", "在", "我", "有", "和", "就", "不", "也", "都", "说",
//            "a", "an", "the", "and", "of", "to", "in", "for", "on", "with", "that",
//            "this", "is", "are", "was", "were", "be", "by", "at", "as", "from"
//    ));
//
//    /**
//     * 构造函数 - 加载并初始化文档
//     *
//     * @param filePath 文档路径
//     * @throws IOException 文件读取异常
//     */
//    public LocalDocumentAIAgent(String filePath) throws IOException {
//        loadDocument(filePath);
//        initializeSentences();
//        extractKeywords();
//    }
//
//    /**
//     * 加载文档内容
//     *
//     * @param filePath 文件路径
//     */
//    private void loadDocument(String filePath) throws IOException {
//        System.out.println("📖 正在加载文档: " + filePath);
//
//        // 使用Java原生NIO读取文件内容
//        Path path = Paths.get(filePath);
//        this.documentContent = new String(Files.readAllBytes(path), "UTF-8");
//
//        // 简单清理文本
//        this.documentContent = this.documentContent.trim();
//
//        if (this.documentContent.isEmpty()) {
//            throw new IOException("文档内容为空");
//        }
//
//        System.out.println("✅ 文档加载成功，共 " + documentContent.length() + " 个字符");
//    }
//
//    /**
//     * 初始化句子列表（用于问答匹配）
//     */
//    private void initializeSentences() {
//        sentences = new ArrayList<>();
//
//        // 按句号、感叹号、问号、分号分割句子
//        String[] rawSentences = documentContent.split("[。！？；!?;]");
//
//        for (String sentence : rawSentences) {
//            sentence = sentence.trim();
//            if (!sentence.isEmpty()) {
//                sentences.add(sentence);
//            }
//        }
//
//        System.out.println("📝 已分割 " + sentences.size() + " 个句子");
//    }
//
//    /**
//     * 提取关键词及频率（简单分词模拟）
//     */
//    private void extractKeywords() {
//        keywordFrequency = new HashMap<>();
//
//        // 简单的正则分词（按非中文字符分割）
//        String[] words = documentContent.split("[^\\u4e00-\\u9fa5a-zA-Z0-9]+");
//
//        for (String word : words) {
//            word = word.toLowerCase();
//            // 过滤空词、停用词、单字符
//            if (word.isEmpty() || word.length() < 2 || STOP_WORDS.contains(word)) {
//                continue;
//            }
//
//            keywordFrequency.put(word, keywordFrequency.getOrDefault(word, 0) + 1);
//        }
//
//        System.out.println("🔑 已提取 " + keywordFrequency.size() + " 个关键词");
//    }
//
//    /**
//     * ========== 核心功能1：文档整体内容总结 ==========
//     *
//     * @return 文档总结文本
//     */
//    public String generateSummary() {
//        System.out.println("\n🤖 [智能体处理中] 正在生成文档总结...");
//
//        StringBuilder summary = new StringBuilder();
//        summary.append("【文档智能总结】\n");
//        summary.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
//
//        // 1. 基本信息
//        summary.append("📊 文档统计：\n");
//        summary.append("   • 总字数: ").append(documentContent.length()).append(" 字符\n");
//        summary.append("   • 总句子数: ").append(sentences.size()).append(" 句\n");
//        summary.append("   • 独立词汇数: ").append(keywordFrequency.size()).append(" 个\n\n");
//
//        // 2. 高频关键词（Top 10）
//        summary.append("🏷️ 核心关键词（按重要性排序）：\n");
//        List<Map.Entry<String, Integer>> sortedKeywords = new ArrayList<>(keywordFrequency.entrySet());
//        sortedKeywords.sort((a, b) -> b.getValue().compareTo(a.getValue()));
//
//        int rank = 1;
//        for (int i = 0; i < Math.min(10, sortedKeywords.size()); i++) {
//            Map.Entry<String, Integer> entry = sortedKeywords.get(i);
//            summary.append("   ").append(rank++).append(". ")
//                    .append(entry.getKey()).append(" (").append(entry.getValue()).append("次)\n");
//        }
//        summary.append("\n");
//
//        // 3. 内容摘要（提取包含高频关键词的前3个句子）
//        summary.append("📄 内容摘要：\n");
//        Set<String> topKeywords = new HashSet<>();
//        for (int i = 0; i < Math.min(5, sortedKeywords.size()); i++) {
//            topKeywords.add(sortedKeywords.get(i).getKey());
//        }
//
//        int sentenceCount = 0;
//        for (String sentence : sentences) {
//            for (String keyword : topKeywords) {
//                if (sentence.contains(keyword) || sentence.toLowerCase().contains(keyword)) {
//                    summary.append("   • ").append(sentence).append("\n");
//                    sentenceCount++;
//                    break;
//                }
//            }
//            if (sentenceCount >= 3) break;
//        }
//
//        summary.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
//        return summary.toString();
//    }
//
//    /**
//     * ========== 核心功能2：关键词匹配式问答 ==========
//     *
//     * @param question 用户问题
//     * @return 匹配到的答案
//     */
//    public String askQuestion(String question) {
//        if (question == null || question.trim().isEmpty()) {
//            return "❓ 问题不能为空，请重新输入。";
//        }
//
//        Set<String> questionKeywords = extractKeywordsFromText(question);
//        if (questionKeywords.isEmpty()) {
//            return "⚠️ 未从问题中提取到有效关键词，请使用更具体的词汇提问。\n\n💡 提示：可以尝试使用文档中的关键词提问。";
//        }
//
//        SentenceMatch bestMatch = findBestMatchingSentence(questionKeywords);
//        if (bestMatch == null || bestMatch.score <= 0.2) {
//            return "未找到与问题相关的答案。\n\n 建议：\n• 尝试使用文档中的原词提问\n• 换个角度描述问题\n• 上传更相关的文档";
//        }
//
//        // 格式化返回内容
//        StringBuilder answer = new StringBuilder();
//        answer.append("找到相关答案\n\n");
//        answer.append("匹配度: ").append(String.format("%.1f", bestMatch.score * 100)).append("%\n\n");
//        answer.append("回答:\n").append(bestMatch.sentence).append("\n\n");
//
//        // 添加相关建议
//        if (bestMatch.score > 0.3) {
//            answer.append("相关话题:\n");
//            List<String> related = findRelatedKeywords(bestMatch.sentence);
//            for (String keyword : related) {
//                answer.append("  • ").append(keyword).append("\n");
//            }
//        }
//
//        return answer.toString();
//    }
//
//    /**
//     * 从文本中提取关键词集合
//     */
//    /**
//     * 从文本中提取关键词集合（优化中文分词）
//     */
//    private Set<String> extractKeywordsFromText(String text) {
//        Set<String> keywords = new HashSet<>();
//
//        // 方法1：提取2-6个字符的中文短语（更符合中文习惯）
//        Pattern chinesePhrase = Pattern.compile("[\\u4e00-\\u9fa5]{2,6}");
//        Matcher matcher = chinesePhrase.matcher(text);
//        while (matcher.find()) {
//            String phrase = matcher.group();
//            if (!STOP_WORDS.contains(phrase) && phrase.length() >= 2) {
//                keywords.add(phrase);
//                // 如果是3字以上的词，也尝试拆分成2字词（提高匹配率）
//                if (phrase.length() >= 3) {
//                    for (int i = 0; i < phrase.length() - 1; i++) {
//                        String bigram = phrase.substring(i, i + 2);
//                        if (!STOP_WORDS.contains(bigram)) {
//                            keywords.add(bigram);
//                        }
//                    }
//                }
//            }
//        }
//
//        // 方法2：提取英文单词
//        Pattern englishWord = Pattern.compile("[a-zA-Z]{2,}");
//        Matcher engMatcher = englishWord.matcher(text);
//        while (engMatcher.find()) {
//            keywords.add(engMatcher.group().toLowerCase());
//        }
//
//        return keywords;
//    }
//
//    /**
//     * 寻找最佳匹配的句子（优化：支持部分匹配+模糊匹配）
//     */
//    private SentenceMatch findBestMatchingSentence(Set<String> questionKeywords) {
//        SentenceMatch bestMatch = null;
//
//        for (String sentence : sentences) {
//            double score = calculateImprovedMatchScore(questionKeywords, sentence);
//
//            if (bestMatch == null || score > bestMatch.score) {
//                bestMatch = new SentenceMatch(sentence, score);
//            }
//
//            // 如果完美匹配，直接返回
//            if (score > 0.6) {
//                break;
//            }
//        }
//
//        return bestMatch;
//    }
//
//    /**
//     * 改进的匹配分数计算（支持包含关系匹配）
//     */
//    private double calculateImprovedMatchScore(Set<String> questionKeywords, String sentence) {
//        if (questionKeywords.isEmpty()) return 0;
//
//        double totalScore = 0;
//        String sentenceLower = sentence.toLowerCase();
//
//        for (String keyword : questionKeywords) {
//            // 直接包含匹配（最重要）
//            if (sentenceLower.contains(keyword.toLowerCase())) {
//                // 关键词越长，权重越高
//                totalScore += 1.0 + (keyword.length() / 10.0);
//            }
//            // 部分匹配：如"治安"匹配"治安案件"
//            else {
//                for (String sentenceKeyword : extractKeywordsFromText(sentence)) {
//                    if (sentenceKeyword.contains(keyword) || keyword.contains(sentenceKeyword)) {
//                        totalScore += 0.5;
//                        break;
//                    }
//                }
//            }
//        }
//
//        // 归一化分数（0-1之间）
//        double normalizedScore = Math.min(1.0, totalScore / questionKeywords.size());
//
//        return normalizedScore;
//    }
//
//    /**
//     * 计算匹配分数
//     */
//    private double calculateMatchScore(Set<String> questionKeywords, Set<String> sentenceKeywords, String sentence) {
//        if (questionKeywords.isEmpty()) return 0;
//
//        // 交集数量
//        Set<String> intersection = new HashSet<>(questionKeywords);
//        intersection.retainAll(sentenceKeywords);
//
//        // 基础Jaccard系数
//        Set<String> union = new HashSet<>(questionKeywords);
//        union.addAll(sentenceKeywords);
//        double jaccard = union.isEmpty() ? 0 : (double) intersection.size() / union.size();
//
//        // 词频加权：如果匹配的关键词在文档中频繁出现，提升权重
//        double frequencyBoost = 0;
//        for (String matched : intersection) {
//            Integer freq = keywordFrequency.getOrDefault(matched, 1);
//            frequencyBoost += Math.log(freq + 1) / 10.0;
//        }
//
//        // 完整匹配奖励：如果句子包含完整的问题短语（不依赖分词）
//        double phraseBoost = 0;
//        String questionLower = String.join(" ", questionKeywords);
//        if (sentence.toLowerCase().contains(questionLower)) {
//            phraseBoost = 0.3;
//        }
//
//        return Math.min(1.0, jaccard + frequencyBoost + phraseBoost);
//    }
//
//    /**
//     * 寻找相关关键词（用于建议）
//     */
//    private List<String> findRelatedKeywords(String sentence) {
//        Set<String> sentenceKeywords = extractKeywordsFromText(sentence);
//        List<Map.Entry<String, Integer>> sortedKeywords = new ArrayList<>(keywordFrequency.entrySet());
//        sortedKeywords.sort((a, b) -> b.getValue().compareTo(a.getValue()));
//
//        List<String> related = new ArrayList<>();
//        for (Map.Entry<String, Integer> entry : sortedKeywords) {
//            if (!sentenceKeywords.contains(entry.getKey()) && related.size() < 3) {
//                related.add(entry.getKey());
//            }
//        }
//        return related;
//    }
//
//    // 在类中添加中文同义词映射
//    private static final Map<String, List<String>> SYNONYMS = new HashMap<>();
//
//    static {
//        SYNONYMS.put("多久", Arrays.asList("多少天", "多长时间", "期限"));
//        SYNONYMS.put("频率", Arrays.asList("次数", "多久一次", "周期"));
//        SYNONYMS.put("办理", Arrays.asList("处理", "办结", "完成"));
//    }
//
//    // 在匹配时扩展关键词
//    private Set<String> expandKeywords(Set<String> keywords) {
//        Set<String> expanded = new HashSet<>(keywords);
//        for (String kw : keywords) {
//            if (SYNONYMS.containsKey(kw)) {
//                expanded.addAll(SYNONYMS.get(kw));
//            }
//        }
//        return expanded;
//    }
//
//    /**
//     * ========== 预留接口：对接私有化本地大模型 ==========
//     * <p>
//     * 后续如需对接真正的本地大模型（如 llama.cpp、Ollama 本地版、通义千问本地版等）
//     * 可在此方法中实现HTTP调用或JNI调用，替换当前的规则匹配逻辑。
//     * <p>
//     * 示例对接方式：
//     * 1. 使用 ProcessBuilder 调用本地模型的可执行文件
//     * 2. 使用 Socket 连接本地模型服务（如 Ollama http://localhost:11434）
//     * 3. 使用 JNI 调用C++实现的模型推理
//     *
//     * @param prompt 用户提示词
//     * @param useLLM true:使用大模型，false:使用内置规则引擎
//     * @return 模型生成的回答
//     */
//    public String queryWithLocalLLM(String prompt, boolean useLLM) {
//        if (!useLLM) {
//            // 当前使用内置规则引擎
//            return askQuestion(prompt);
//        }
//
//        // TODO: 对接本地大模型实现
//        // 示例伪代码：
//        // String modelPath = "/path/to/your/local/model";
//        // ProcessBuilder pb = new ProcessBuilder(modelPath, "--prompt", prompt);
//        // Process process = pb.start();
//        // String result = readProcessOutput(process);
//        // return result;
//
//        return "[提示] 当前未配置本地大模型，请修改配置后使用。当前使用内置问答引擎：\n" + askQuestion(prompt);
//    }
//
//    /**
//     * 内部类：句子匹配结果
//     */
//    private static class SentenceMatch {
//        String sentence;
//        double score;
//
//        SentenceMatch(String sentence, double score) {
//            this.sentence = sentence;
//            this.score = score;
//        }
//    }
//
//    /**
//     * 控制台交互主循环
//     */
//    public void startConsole() {
//        Scanner scanner = new Scanner(System.in);
//
//        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
//        System.out.println("║         🤖 本地文档AI智能体 - 控制台交互模式            ║");
//        System.out.println("╚══════════════════════════════════════════════════════════╝");
//        System.out.println("💡 使用说明：");
//        System.out.println("   • 输入 \"总结\" 或 \"summary\" - 获取文档总结");
//        System.out.println("   • 输入任意问题 - 进行关键词匹配问答");
//        System.out.println("   • 输入 \"exit\" 或 \"quit\" - 退出程序");
//        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
//
//        while (true) {
//            System.out.print("👤 你: ");
//            String input = scanner.nextLine().trim();
//
//            if (input.isEmpty()) {
//                continue;
//            }
//
//            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
//                System.out.println("👋 感谢使用，再见！");
//                break;
//            }
//
//            if (input.equalsIgnoreCase("总结") || input.equalsIgnoreCase("summary")) {
//                System.out.println(generateSummary());
//            } else {
//                System.out.println(askQuestion(input));
//            }
//        }
//
//        scanner.close();
//    }
//
//    /**
//     * 获取句子数量（用于Web界面显示）
//     */
//    public int getSentenceCount() {
//        return this.sentences.size();
//    }
//
//    /**
//     * 获取文档统计信息（用于Web界面）
//     */
//    public Map<String, Object> getStatistics() {
//        Map<String, Object> stats = new HashMap<>();
//        stats.put("charCount", documentContent.length());
//        stats.put("sentenceCount", sentences.size());
//        stats.put("keywordCount", keywordFrequency.size());
//        stats.put("topKeywords", getTopKeywords(10));
//        return stats;
//    }
//
//    /**
//     * 获取Top N关键词
//     */
//    private List<Map<String, Object>> getTopKeywords(int n) {
//        List<Map<String, Object>> result = new ArrayList<>();
//        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(keywordFrequency.entrySet());
//        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
//
//        for (int i = 0; i < Math.min(n, sorted.size()); i++) {
//            Map<String, Object> item = new HashMap<>();
//            item.put("keyword", sorted.get(i).getKey());
//            item.put("frequency", sorted.get(i).getValue());
//            result.add(item);
//        }
//        return result;
//    }
//    /**
//     * 主方法 - 程序入口
//     */
//    public static void main(String[] args) {
//        try {
//            // ========== 配置说明 ==========
//            // 1. 修改文档路径：将下面的 "test.txt" 改为您的文件路径
//            //    例如："./docs/myfile.txt" 或 "C:/documents/data.txt"
//            // 2. 确保文件编码为 UTF-8（Java默认支持）
//            // 3. 首次运行前，请在项目根目录创建 test.txt 文件并写入内容
//            // ===============================
//
//            String docPath = "test.txt";  // ← 在这里修改文档路径
//
//            // 检查文件是否存在
//            File testFile = new File(docPath);
//            if (!testFile.exists()) {
//                System.err.println("❌ 错误: 找不到文件 '" + docPath + "'");
//                System.err.println("\n请按以下步骤操作：");
//                System.err.println("1. 在项目根目录下创建 " + docPath + " 文件");
//                System.err.println("2. 向文件中写入一些文档内容（例如技术文档、文章等）");
//                System.err.println("3. 重新运行程序");
//                System.err.println("\n示例内容：");
//                System.err.println("   Java是一种面向对象的编程语言，由Sun Microsystems公司于1995年推出。");
//                System.err.println("   Java具有跨平台特性，可以在Windows、Linux、macOS等系统上运行。");
//                System.err.println("   Java广泛应用于企业级开发、Android应用开发、大数据处理等领域。");
//                return;
//            }
//
//            // 初始化AI智能体
//            LocalDocumentAIAgent agent = new LocalDocumentAIAgent(docPath);
//
//            // 启动控制台交互
//            agent.startConsole();
//
//        } catch (FileNotFoundException e) {
//            System.err.println("❌ 文件未找到: " + e.getMessage());
//            System.err.println("请确认文档路径是否正确，文件是否存在。");
//        } catch (IOException e) {
//            System.err.println("❌ 读取文档失败: " + e.getMessage());
//            System.err.println("请检查文件编码是否为UTF-8，以及是否有读取权限。");
//        } catch (Exception e) {
//            System.err.println("❌ 系统异常: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//}
