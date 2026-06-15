package com.demo;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Controller
public class DocumentAIController {

    private LocalDocumentAIAgent aiAgent;

    // 知识库根目录
    private static final String KNOWLEDGE_BASE_DIR = "knowledge_base";
    private static final String DEFAULT_DOC_PATH = KNOWLEDGE_BASE_DIR + File.separator + "公安工作文本.txt";

    /**
     * 初始化知识库目录
     */
    @PostConstruct
    public void init() {
        try {
            // 创建知识库根目录
            File kbDir = new File(KNOWLEDGE_BASE_DIR);
            if (!kbDir.exists()) {
                kbDir.mkdirs();
                System.out.println("📁 创建知识库目录: " + KNOWLEDGE_BASE_DIR);
            }

            // 检查是否有默认文档，如果没有则创建示例文档
            File defaultDoc = new File(DEFAULT_DOC_PATH);
            if (!defaultDoc.exists()) {
                createSampleDocument();
            }

            // 加载知识库
            loadKnowledgeBase();

        } catch (Exception e) {
            System.err.println("❌ 初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 创建示例文档（首次运行时）
     */
    private void createSampleDocument() {
        try {
            File kbDir = new File(KNOWLEDGE_BASE_DIR);
            if (!kbDir.exists()) {
                kbDir.mkdirs();
            }

            String sampleContent = "公安工作示例文档\n\n" +
                    "社区民警应当每月开展不少于两次入户走访活动，全面掌握辖区实有人口信息。\n\n" +
                    "派出所接到群众报警后，城区范围内必须15分钟内到达现场。\n\n" +
                    "治安案件的办理程序包括受案、调查、告知、决定、送达和执行六个环节。\n\n" +
                    "矛盾纠纷调解应当遵循自愿、合法、公正的原则。\n\n" +
                    "反电信网络诈骗工作坚持\"快接警、快止付、快研判、快抓捕\"的四快原则。";

            Files.write(Paths.get(DEFAULT_DOC_PATH), sampleContent.getBytes("UTF-8"));
            System.out.println("📄 创建示例文档: " + DEFAULT_DOC_PATH);
        } catch (Exception e) {
            System.err.println("创建示例文档失败: " + e.getMessage());
        }
    }

    /**
     * 加载整个知识库
     */
    private void loadKnowledgeBase() throws IOException {
        File kbDir = new File(KNOWLEDGE_BASE_DIR);
        if (!kbDir.exists() || kbDir.listFiles() == null || kbDir.listFiles().length == 0) {
            System.out.println("⚠️ 知识库为空，请上传文档");
            return;
        }

        // 收集所有txt文件
        List<Path> txtFiles = new ArrayList<>();
        Files.walk(Paths.get(KNOWLEDGE_BASE_DIR))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".txt"))
                .forEach(txtFiles::add);

        if (txtFiles.isEmpty()) {
            System.out.println("⚠️ 知识库中没有txt文件");
            return;
        }

        // 合并所有文档内容
        StringBuilder combinedContent = new StringBuilder();
        for (Path filePath : txtFiles) {
            try {
                String content = new String(Files.readAllBytes(filePath), "UTF-8");
                combinedContent.append("【").append(filePath.getFileName().toString()).append("】\n");
                combinedContent.append(content).append("\n\n");
                System.out.println("   ✅ 加载: " + filePath.getFileName());
            } catch (Exception e) {
                System.err.println("   ⚠️ 加载失败: " + filePath.getFileName());
            }
        }

        // 创建临时文件用于AI加载
        String tempFile = KNOWLEDGE_BASE_DIR + File.separator + "_combined_temp.txt";
        Files.write(Paths.get(tempFile), combinedContent.toString().getBytes("UTF-8"));
        aiAgent = new LocalDocumentAIAgent(tempFile);

        System.out.println("✅ 知识库加载成功！共 " + txtFiles.size() + " 个文档");
    }

    /**
     * 重新加载知识库
     */
    private void reloadKnowledgeBase() {
        try {
            loadKnowledgeBase();
        } catch (Exception e) {
            System.err.println("重新加载知识库失败: " + e.getMessage());
        }
    }

    /**
     * 首页
     */
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("docLoaded", aiAgent != null);
        if (aiAgent != null) {
            model.addAttribute("docInfo", "已加载知识库，共 " + aiAgent.getSentenceCount() + " 个句子");
        } else {
            model.addAttribute("docInfo", "知识库为空，请上传文档");
        }
        return "index";
    }

    /**
     * 上传单个文档到知识库
     */
    @PostMapping("/api/upload")
    @ResponseBody
    public Map<String, Object> uploadDocument(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        if (file.isEmpty()) {
            response.put("success", false);
            response.put("message", "请选择文件");
            return response;
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || (!originalName.endsWith(".txt") && !originalName.endsWith(".TXT"))) {
            response.put("success", false);
            response.put("message", "请上传txt格式的文件");
            return response;
        }

        try {
            // 保存到 knowledge_base 目录
            String fileName = originalName;
            Path targetPath = Paths.get(KNOWLEDGE_BASE_DIR, fileName);

            // 如果文件已存在，添加时间戳
            if (Files.exists(targetPath)) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String nameWithoutExt = originalName.substring(0, originalName.lastIndexOf('.'));
                String ext = originalName.substring(originalName.lastIndexOf('.'));
                fileName = nameWithoutExt + "_" + timestamp + ext;
                targetPath = Paths.get(KNOWLEDGE_BASE_DIR, fileName);
            }

            Files.write(targetPath, file.getBytes());

            // 重新加载知识库
            reloadKnowledgeBase();

            response.put("success", true);
            response.put("message", "文档上传成功！已保存到知识库: " + fileName);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "上传失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 上传zip压缩包（自动解压到 knowledge_base）
     */
    @PostMapping("/api/upload-folder")
    @ResponseBody
    public Map<String, Object> uploadFolder(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        if (file.isEmpty()) {
            response.put("success", false);
            response.put("message", "请选择文件");
            return response;
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.endsWith(".zip")) {
            response.put("success", false);
            response.put("message", "请上传zip格式的压缩包");
            return response;
        }

        try {
            // 创建临时解压目录
            String tempDirName = "temp_extract_" + System.currentTimeMillis();
            Path tempDir = Paths.get(tempDirName);
            Files.createDirectories(tempDir);

            int extractedCount = 0;
            List<String> extractedFiles = new ArrayList<>();

            // 解压zip文件
            try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    Path entryPath = tempDir.resolve(entryName);

                    if (!entry.isDirectory()) {
                        // 只处理txt文件
                        if (entryName.toLowerCase().endsWith(".txt")) {
                            // 确保父目录存在
                            Files.createDirectories(entryPath.getParent());
                            // 复制文件内容
                            try (OutputStream os = Files.newOutputStream(entryPath)) {
                                byte[] buffer = new byte[8192];
                                int len;
                                while ((len = zis.read(buffer)) > 0) {
                                    os.write(buffer, 0, len);
                                }
                            }
                            extractedCount++;
                            extractedFiles.add(entryName);
                        }
                    }
                    zis.closeEntry();
                }
            }

            if (extractedCount == 0) {
                // 清理临时目录
                deleteDirectory(tempDir);
                response.put("success", false);
                response.put("message", "压缩包中没有找到txt文件");
                return response;
            }

            // 将解压的文件移动到 knowledge_base 目录
            int movedCount = 0;
            File tempDirFile = tempDir.toFile();
            List<File> txtFiles = findTxtFiles(tempDirFile);

            for (File sourceFile : txtFiles) {
                try {
                    String fileName = sourceFile.getName();
                    Path targetPath = Paths.get(KNOWLEDGE_BASE_DIR, fileName);

                    // 如果文件已存在，添加时间戳
                    if (Files.exists(targetPath)) {
                        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                        String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
                        String ext = fileName.substring(fileName.lastIndexOf('.'));
                        fileName = nameWithoutExt + "_" + timestamp + ext;
                        targetPath = Paths.get(KNOWLEDGE_BASE_DIR, fileName);
                    }

                    Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                    movedCount++;
                } catch (Exception e) {
                    System.err.println("移动文件失败: " + e.getMessage());
                }
            }

            // 清理临时目录
            deleteDirectory(tempDir);

            // 重新加载知识库
            reloadKnowledgeBase();

            response.put("success", true);
            response.put("message", String.format("文件夹上传成功！共解压 %d 个txt文件，已添加到知识库", movedCount));
            response.put("fileCount", movedCount);
            response.put("files", extractedFiles);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "上传失败: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    /**
     * 递归查找所有txt文件
     */
    private List<File> findTxtFiles(File directory) {
        List<File> txtFiles = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    txtFiles.addAll(findTxtFiles(file));
                } else if (file.getName().toLowerCase().endsWith(".txt")) {
                    txtFiles.add(file);
                }
            }
        }
        return txtFiles;
    }

    /**
     * 获取知识库中文档列表
     */
    @GetMapping("/api/file-list")
    @ResponseBody
    public Map<String, Object> getFileList() {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> files = new ArrayList<>();
            Files.walk(Paths.get(KNOWLEDGE_BASE_DIR))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".txt"))
                    .forEach(path -> {
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("name", path.getFileName().toString());
                        fileInfo.put("path", path.toString());
                        try {
                            fileInfo.put("size", Files.size(path));
                            fileInfo.put("modified", Files.getLastModifiedTime(path).toString());
                        } catch (Exception e) {
                            fileInfo.put("size", 0);
                        }
                        files.add(fileInfo);
                    });

            response.put("success", true);
            response.put("files", files);
            response.put("count", files.size());
            response.put("directory", KNOWLEDGE_BASE_DIR);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * 删除知识库中的文件
     */
    @DeleteMapping("/api/delete-file")
    @ResponseBody
    public Map<String, Object> deleteFile(@RequestParam String fileName) {
        Map<String, Object> response = new HashMap<>();

        try {
            Path filePath = Paths.get(KNOWLEDGE_BASE_DIR, fileName);
            if (!Files.exists(filePath)) {
                response.put("success", false);
                response.put("message", "文件不存在");
                return response;
            }

            Files.delete(filePath);

            // 重新加载知识库
            reloadKnowledgeBase();

            response.put("success", true);
            response.put("message", "文件删除成功: " + fileName);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 获取知识库统计信息
     */
    @GetMapping("/api/kb-info")
    @ResponseBody
    public Map<String, Object> getKBInfo() {
        Map<String, Object> response = new HashMap<>();

        try {
            File kbDir = new File(KNOWLEDGE_BASE_DIR);
            List<Map<String, Object>> files = new ArrayList<>();
            long totalSize = 0;
            int fileCount = 0;

            if (kbDir.exists()) {
                for (File file : kbDir.listFiles()) {
                    if (file.isFile() && file.getName().endsWith(".txt")) {
                        Map<String, Object> info = new HashMap<>();
                        info.put("name", file.getName());
                        info.put("size", file.length());
                        files.add(info);
                        totalSize += file.length();
                        fileCount++;
                    }
                }
            }

            response.put("success", true);
            response.put("directory", KNOWLEDGE_BASE_DIR);
            response.put("fileCount", fileCount);
            response.put("totalSize", totalSize);
            response.put("files", files);

            if (aiAgent != null) {
                response.put("sentenceCount", aiAgent.getSentenceCount());
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * 删除目录（递归）
     */
    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            System.err.println("删除失败: " + p);
                        }
                    });
        }
    }

    // ========== 以下是原有的API方法 ==========

    @GetMapping("/api/summary")
    @ResponseBody
    public Map<String, Object> getSummary() {
        Map<String, Object> response = new HashMap<>();
        if (aiAgent == null) {
            response.put("success", false);
            response.put("message", "知识库为空，请先上传文档");
            return response;
        }
        try {
            response.put("success", true);
            response.put("message", aiAgent.generateSummary());
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "生成总结失败: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/api/ask")
    @ResponseBody
    public Map<String, Object> askQuestion(@RequestParam String question) {
        Map<String, Object> response = new HashMap<>();
        if (aiAgent == null) {
            response.put("success", false);
            response.put("message", "知识库为空，请先上传文档");
            return response;
        }
        try {
            response.put("success", true);
            response.put("message", aiAgent.askQuestion(question));
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "问答失败: " + e.getMessage());
        }
        return response;
    }

    @GetMapping("/api/info")
    @ResponseBody
    public Map<String, Object> getDocumentInfo() {
        Map<String, Object> response = new HashMap<>();
        if (aiAgent == null) {
            response.put("success", false);
            response.put("message", "知识库为空");
            return response;
        }
        response.put("success", true);
        response.put("data", aiAgent.getStatistics());
        return response;
    }
}
////实现单个文件的训练
//// DocumentAIController.java
//package com.demo;
//
//import jakarta.annotation.PostConstruct;
//import org.springframework.stereotype.Controller;
//import org.springframework.ui.Model;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.*;
//import java.nio.file.*;
//
///**
// * Web控制器 - 处理HTTP请求
// */
//@Controller
//public class DocumentAIController {
//
//    private LocalDocumentAIAgent aiAgent;
//    private static final String DEFAULT_DOC_PATH = "test.txt";
//
//    /**
//     * 启动时自动加载默认文档
//     */
//    @PostConstruct
//    public void init() {
//        try {
//            File defaultDoc = new File(DEFAULT_DOC_PATH);
//            if (defaultDoc.exists()) {
//                aiAgent = new LocalDocumentAIAgent(DEFAULT_DOC_PATH);
//                System.out.println("✅ 已加载默认文档: " + DEFAULT_DOC_PATH);
//            } else {
//                System.out.println("⚠️ 未找到默认文档，请通过Web界面上传");
//            }
//        } catch (Exception e) {
//            System.err.println("❌ 初始化失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 首页 - 聊天界面
//     */
//    @GetMapping("/")
//    public String index(Model model) {
//        model.addAttribute("docLoaded", aiAgent != null);
//        if (aiAgent != null) {
//            model.addAttribute("docInfo", "已加载文档，共 " + aiAgent.getSentenceCount() + " 个句子");
//        } else {
//            model.addAttribute("docInfo", "未加载文档，请先上传");
//        }
//        return "index";
//    }
//
//    /**
//     * 获取文档总结（API接口）
//     */
//    @GetMapping("/api/summary")
//    @ResponseBody
//    public ApiResponse getSummary() {
//        if (aiAgent == null) {
//            return ApiResponse.error("请先上传文档");
//        }
//
//        try {
//            String summary = aiAgent.generateSummary();
//            return ApiResponse.success(summary);
//        } catch (Exception e) {
//            return ApiResponse.error("生成总结失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 问答接口（API）
//     */
//    @PostMapping("/api/ask")
//    @ResponseBody
//    public ApiResponse askQuestion(@RequestParam String question) {
//        if (aiAgent == null) {
//            return ApiResponse.error("请先上传文档");
//        }
//
//        if (question == null || question.trim().isEmpty()) {
//            return ApiResponse.error("问题不能为空");
//        }
//
//        try {
//            String answer = aiAgent.askQuestion(question);
//            return ApiResponse.success(answer);
//        } catch (Exception e) {
//            return ApiResponse.error("问答失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 上传文档接口
//     */
//    @PostMapping("/api/upload")
//    @ResponseBody
//    public ApiResponse uploadDocument(@RequestParam("file") MultipartFile file) {
//        if (file.isEmpty()) {
//            return ApiResponse.error("请选择文件");
//        }
//
//        try {
//            // 保存上传的文件
//            String uploadPath = "uploaded_" + System.currentTimeMillis() + ".txt";
//            Path path = Paths.get(uploadPath);
//            Files.write(path, file.getBytes());
//
//            // 重新加载AI智能体
//            aiAgent = new LocalDocumentAIAgent(uploadPath);
//
//            return ApiResponse.success("文档上传成功！共 " + aiAgent.getSentenceCount() + " 个句子");
//        } catch (Exception e) {
//            return ApiResponse.error("上传失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 获取文档统计信息
//     */
//    @GetMapping("/api/info")
//    @ResponseBody
//    public ApiResponse getDocumentInfo() {
//        if (aiAgent == null) {
//            return ApiResponse.error("未加载文档");
//        }
//
//        return ApiResponse.success(aiAgent.getStatistics());
//    }
//
//    /**
//     * API响应包装类
//     */
//    static class ApiResponse {
//        private boolean success;
//        private String message;
//        private Object data;
//
//        public ApiResponse(boolean success, String message, Object data) {
//            this.success = success;
//            this.message = message;
//            this.data = data;
//        }
//
//        public static ApiResponse success(String message) {
//            return new ApiResponse(true, message, null);
//        }
//
//        public static ApiResponse success(Object data) {
//            return new ApiResponse(true, "success", data);
//        }
//
//        public static ApiResponse error(String message) {
//            return new ApiResponse(false, message, null);
//        }
//
//        // Getters and Setters
//        public boolean isSuccess() { return success; }
//        public void setSuccess(boolean success) { this.success = success; }
//        public String getMessage() { return message; }
//        public void setMessage(String message) { this.message = message; }
//        public Object getData() { return data; }
//        public void setData(Object data) { this.data = data; }
//    }
//}
