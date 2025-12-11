package com.github.h2seo.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.yaml.snakeyaml.Yaml;

/**
 * Maven 플러그인 Mojo 클래스: 지정된 디렉토리의 파일을 읽어 소스 파일을 찾아 교체합니다.
 * 
 * <p>이 Mojo는 다음을 수행합니다:
 * <ol>
     *   <li>지정된 디렉토리에서 모든 파일 검색 (재귀적 탐색)</li>
     *   <li>Java 파일인 경우: package와 class 이름을 추출하여 src/main/java와 src/test/java에서 일치하는 파일 검색</li>
     *   <li>Java 파일이 아닌 경우: path-map.yml 파일에서 파일명 기준으로 경로 매핑을 읽어 처리</li>
     *   <li>path-map.yml이 없는 경우: Java 파일만 처리</li>
     *   <li>찾은 파일을 새 파일 내용으로 교체</li>
 * </ol>
 * 
 * <p>path-map.yml 형식 예시:
 * <pre>
 * {@code
 * config.properties:
 *   source: src/main/resources/config.properties
 *   target: target/classes/config.properties
 * README.md:
 *   source: README.md
 *   target: docs/README.md
 * }
 * </pre>
 * 
 * <p>사용 예시:
 * <pre>
 * {@code
 * mvn rewrite-post:replace
 * }
 * </pre>
 * 
 * @author h2seo
 * @version 1.0.0
 * @since 1.0.0
 */
@Mojo(name = "replace")
public class ReplaceFileMojo extends AbstractMojo {

    /**
     * 디렉토리 경로: 교체할 파일들이 있는 디렉토리.
     * 
     * <p>이 디렉토리에서 파일들을 재귀적으로 검색하여 처리합니다.
     * Java 파일은 package와 class 이름으로, 그 외 파일은 path-map.yml의 경로 매핑으로 처리합니다.
     * 
     * <p>기본값은 프로젝트 루트 디렉토리의 <code>replace</code> 하위 디렉토리입니다.
     * 
     * <p>설정 방법:
     * <ul>
     *   <li>pom.xml: <code>&lt;sourceDirectory&gt;${project.basedir}/my-replace&lt;/sourceDirectory&gt;</code></li>
     *   <li>명령줄: <code>-Drewrite-post.sourceDirectory=./custom-replace</code></li>
     * </ul>
     */
    @Parameter(property = "rewrite-post.sourceDirectory", defaultValue = "${project.basedir}/replace")
    private File sourceDirectory;

    /**
     * 프로젝트의 메인 소스 디렉토리 경로.
     * 
     * <p>이 디렉토리에서 교체할 파일을 검색합니다.
     * 기본값은 <code>src/main/java</code>입니다.
     */
    @Parameter(property = "rewrite-post.mainSourceDirectory", defaultValue = "${project.basedir}/src/main/java")
    private File mainSourceDirectory;

    /**
     * 프로젝트의 테스트 소스 디렉토리 경로.
     * 
     * <p>이 디렉토리에서 교체할 파일을 검색합니다.
     * 기본값은 <code>src/test/java</code>입니다.
     */
    @Parameter(property = "rewrite-post.testSourceDirectory", defaultValue = "${project.basedir}/src/test/java")
    private File testSourceDirectory;

    /**
     * 오류 발생 시 빌드를 중단할지 여부를 결정하는 플래그.
     * 
     * <p>이 값이 <code>true</code>인 경우, 파일 교체에 실패하면
     * <code>MojoExecutionException</code>을 발생시켜 빌드를 중단합니다.
     * 
     * <p><code>false</code>인 경우, 파일 교체 실패 시 경고 로그만 출력하고
     * 빌드를 계속 진행합니다.
     * 
     * <p>기본값은 <code>true</code>입니다.
     */
    @Parameter(property = "rewrite-post.failOnError", defaultValue = "true")
    private boolean failOnError;

    /**
     * path-map.yml 파일 경로.
     * 
     * <p>Java 파일이 아닌 파일의 경로 매핑을 정의하는 YAML 파일입니다.
     * 파일명을 키로 하여 source와 target 경로를 지정합니다.
     * 
     * <p>기본값은 프로젝트 루트 디렉토리의 <code>path-map.yml</code>입니다.
     */
    @Parameter(property = "rewrite-post.pathMapFile", defaultValue = "${project.basedir}/path-map.yml")
    private File pathMapFile;

    /**
     * package 선언을 추출하기 위한 정규식 패턴.
     */
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([a-zA-Z_][a-zA-Z0-9_.]*)\\s*;");

    /**
     * public class 선언을 추출하기 위한 정규식 패턴.
     */
    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern.compile("^\\s*public\\s+(?:final\\s+)?(?:abstract\\s+)?class\\s+([a-zA-Z_][a-zA-Z0-9_]*)");

    /**
     * class 선언을 추출하기 위한 정규식 패턴 (public이 아닌 경우).
     */
    private static final Pattern CLASS_PATTERN = Pattern.compile("^\\s*(?:public\\s+)?(?:final\\s+)?(?:abstract\\s+)?class\\s+([a-zA-Z_][a-zA-Z0-9_]*)");

    /**
     * Mojo 실행 메서드: 파일을 읽어 소스 파일을 찾아 교체합니다.
     * 
     * <p>이 메서드는 다음 단계를 수행합니다:
     * <ol>
     *   <li>소스 디렉토리 유효성 검사</li>
     *   <li>path-map.yml 파일 로드 (존재하는 경우)</li>
     *   <li>소스 디렉토리에서 모든 파일 검색 (재귀적 탐색)</li>
     *   <li>Java 파일인 경우: package와 class 이름으로 처리</li>
     *   <li>Java 파일이 아닌 경우: path-map.yml에서 경로 매핑으로 처리</li>
     *   <li>일치하는 파일 교체</li>
     * </ol>
     * 
     * @throws MojoExecutionException 소스 디렉토리가 유효하지 않거나, 파일 교체 실패 시
     *                                (failOnError가 true인 경우)
     * @throws MojoFailureException Mojo 실행 실패 시
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // 소스 디렉토리 유효성 검사
        if (sourceDirectory == null || !sourceDirectory.exists()) {
            getLog().warn("Source directory does not exist: " + sourceDirectory);
            return;
        }

        if (!sourceDirectory.isDirectory()) {
            throw new MojoExecutionException("Source directory is not a directory: " + sourceDirectory);
        }

        getLog().info("Reading files from: " + sourceDirectory.getAbsolutePath());
        getLog().info("Main source directory: " + mainSourceDirectory.getAbsolutePath());
        getLog().info("Test source directory: " + testSourceDirectory.getAbsolutePath());

        // path-map.yml 파일 로드
        Map<String, PathMapping> pathMap = loadPathMap();
        boolean hasPathMap = pathMap != null && !pathMap.isEmpty();
        
        if (hasPathMap) {
            getLog().info("Loaded path-map.yml with " + pathMap.size() + " mapping(s)");
        } else if (pathMapFile != null && pathMapFile.exists() && pathMapFile.isFile()) {
            // pathMapFile이 존재하지만 비어있거나 파싱 오류가 발생한 경우
            getLog().info("path-map.yml is empty or invalid, processing Java files only");
        } else if (pathMapFile == null) {
            // pathMapFile이 지정되지 않은 경우 (기본값도 사용하지 않는 경우)
            getLog().info("path-map.yml not specified, processing Java files only");
        }
        // pathMapFile이 지정되었지만 파일이 없는 경우는 loadPathMap()에서 이미 경고 출력됨

        // 소스 디렉토리에서 모든 파일 검색 (path-map.yml 제외)
        List<File> allFiles = findAllFiles(sourceDirectory);
        
        // sourceDirectory 내에 있는 path-map.yml 파일 제외
        if (pathMapFile != null && pathMapFile.exists()) {
            try {
                String sourceDirPath = sourceDirectory.getCanonicalPath();
                String pathMapFilePath = pathMapFile.getCanonicalPath();
                
                // path-map.yml이 sourceDirectory 내에 있는지 확인
                if (pathMapFilePath.startsWith(sourceDirPath)) {
                    allFiles.removeIf(file -> {
                        try {
                            return file.getCanonicalPath().equals(pathMapFilePath);
                        } catch (IOException e) {
                            return false;
                        }
                    });
                    getLog().debug("Excluded path-map.yml from processing");
                }
            } catch (IOException e) {
                getLog().warn("Failed to check path-map.yml location: " + e.getMessage());
            }
        }
        
        if (allFiles.isEmpty()) {
            getLog().info("No files found in: " + sourceDirectory.getAbsolutePath());
            return;
        }

        getLog().info("Found " + allFiles.size() + " file(s)");

        int successCount = 0;
        int failureCount = 0;

        // 각 파일에 대해 처리
        for (File sourceFile : allFiles) {
            try {
                getLog().info("Processing file: " + sourceFile.getName());
                
                boolean replaced = false;
                
                // Java 파일인 경우
                if (sourceFile.getName().endsWith(".java")) {
                    // package와 class 이름 추출
                    JavaFileInfo fileInfo = extractFileInfo(sourceFile);
                    
                    if (fileInfo == null) {
                        getLog().warn("Could not extract package or class name from: " + sourceFile.getName());
                        failureCount++;
                        continue;
                    }

                    getLog().debug("Package: " + fileInfo.packageName + ", Class: " + fileInfo.className);

                    // 대상 파일 찾기 및 교체
                    replaced = replaceTargetFile(sourceFile, fileInfo);
                    
                    if (replaced) {
                        successCount++;
                        getLog().info("Successfully replaced file: " + fileInfo.className);
                    } else {
                        failureCount++;
                        String message = "Target file not found for: " + fileInfo.packageName + "." + fileInfo.className;
                        if (failOnError) {
                            throw new MojoExecutionException(message);
                        } else {
                            getLog().warn(message);
                        }
                    }
                } else {
                    // Java 파일이 아닌 경우
                    if (hasPathMap) {
                        // path-map.yml에서 경로 매핑 찾기
                        PathMapping mapping = pathMap.get(sourceFile.getName());
                        if (mapping != null) {
                            // 프로젝트 루트 디렉토리를 기준으로 경로 계산 (pathMapFile의 부모 디렉토리 사용)
                            File projectBaseDir = pathMapFile.getParentFile();
                            if (projectBaseDir == null) {
                                projectBaseDir = new File(".");
                            }
                            File targetFile = new File(projectBaseDir, mapping.target);
                            
                            if (targetFile.exists() && targetFile.isFile()) {
                                getLog().info("Replacing file using path-map: " + targetFile.getAbsolutePath());
                                copyFile(sourceFile, targetFile);
                                replaced = true;
                                successCount++;
                            } else {
                                failureCount++;
                                String message = "Target file not found: " + targetFile.getAbsolutePath();
                                if (failOnError) {
                                    throw new MojoExecutionException(message);
                                } else {
                                    getLog().warn(message);
                                }
                            }
                        } else {
                            getLog().debug("No path mapping found for: " + sourceFile.getName() + ", skipping");
                        }
                    } else {
                        // path-map.yml이 없으면 Java 파일이 아닌 파일은 건너뜀
                        getLog().debug("Skipping non-Java file (path-map.yml not found): " + sourceFile.getName());
                    }
                }
            } catch (Exception e) {
                failureCount++;
                String message = "Failed to process file: " + sourceFile.getName() + " - " + e.getMessage();
                if (failOnError) {
                    throw new MojoExecutionException(message, e);
                } else {
                    getLog().warn(message);
                }
            }
        }

        getLog().info("File replacement completed. Success: " + successCount + ", Failed: " + failureCount);
    }

    /**
     * 지정된 디렉토리에서 모든 파일을 재귀적으로 검색합니다.
     * 
     * @param directory 검색할 디렉토리
     * @return 검색된 파일 목록
     */
    private List<File> findAllFiles(File directory) {
        List<File> allFiles = new ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            paths.filter(Files::isRegularFile)
                 .map(Path::toFile)
                 .forEach(allFiles::add);
        } catch (IOException e) {
            getLog().warn("Error scanning for files: " + e.getMessage());
        }

        return allFiles;
    }

    /**
     * path-map.yml 파일을 로드하여 경로 매핑 정보를 반환합니다.
     * 
     * <p>pathMapFile이 지정되었지만 파일이 없는 경우 경고를 출력하고 null을 반환합니다.
     * 
     * @return 파일명을 키로 하는 경로 매핑 맵, 파일이 없거나 오류 발생 시 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, PathMapping> loadPathMap() {
        if (pathMapFile == null) {
            return null;
        }
        
        // pathMapFile이 지정되었지만 파일이 없는 경우 경고 출력
        if (!pathMapFile.exists() || !pathMapFile.isFile()) {
            getLog().warn("path-map.yml file not found: " + pathMapFile.getAbsolutePath() + 
                         ". Processing Java files only.");
            return null;
        }

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(new FileInputStream(pathMapFile));
            
            if (data == null || data.isEmpty()) {
                return null;
            }

            Map<String, PathMapping> pathMap = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String fileName = entry.getKey();
                Object value = entry.getValue();
                
                if (value instanceof Map) {
                    Map<String, Object> mappingData = (Map<String, Object>) value;
                    String source = (String) mappingData.get("source");
                    String target = (String) mappingData.get("target");
                    
                    if (source != null && target != null) {
                        pathMap.put(fileName, new PathMapping(source, target));
                    } else {
                        getLog().warn("Invalid path mapping for " + fileName + ": missing source or target");
                    }
                } else {
                    getLog().warn("Invalid path mapping format for " + fileName);
                }
            }
            
            return pathMap;
        } catch (Exception e) {
            getLog().warn("Failed to load path-map.yml: " + e.getMessage());
            return null;
        }
    }

    /**
     * Java 파일에서 package와 class 이름을 추출합니다.
     * 
     * @param javaFile Java 파일
     * @return package와 class 이름을 포함한 정보 객체, 추출 실패 시 null
     * @throws IOException 파일 읽기 오류 발생 시
     */
    private JavaFileInfo extractFileInfo(File javaFile) throws IOException {
        String packageName = null;
        String className = null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(javaFile), StandardCharsets.UTF_8))) {
            String line;
            boolean foundPackage = false;
            boolean foundClass = false;

            while ((line = reader.readLine()) != null) {
                // package 선언 추출
                if (!foundPackage) {
                    Matcher packageMatcher = PACKAGE_PATTERN.matcher(line);
                    if (packageMatcher.find()) {
                        packageName = packageMatcher.group(1);
                        foundPackage = true;
                    }
                }

                // class 선언 추출 (public class 우선, 없으면 일반 class)
                if (!foundClass) {
                    Matcher publicClassMatcher = PUBLIC_CLASS_PATTERN.matcher(line);
                    if (publicClassMatcher.find()) {
                        className = publicClassMatcher.group(1);
                        foundClass = true;
                    } else {
                        Matcher classMatcher = CLASS_PATTERN.matcher(line);
                        if (classMatcher.find()) {
                            className = classMatcher.group(1);
                            foundClass = true;
                        }
                    }
                }

                // package와 class를 모두 찾으면 종료
                if (foundPackage && foundClass) {
                    break;
                }
            }
        }

        // package나 class를 찾지 못한 경우
        if (packageName == null || className == null) {
            return null;
        }

        return new JavaFileInfo(packageName, className);
    }

    /**
     * package와 class 이름을 기반으로 대상 파일을 찾아 교체합니다.
     * 
     * <p>먼저 src/main/java에서 검색하고, 없으면 src/test/java에서 검색합니다.
     * 
     * @param sourceFile 교체할 소스 파일
     * @param fileInfo package와 class 정보
     * @return 교체 성공 여부
     * @throws IOException 파일 I/O 오류 발생 시
     */
    private boolean replaceTargetFile(File sourceFile, JavaFileInfo fileInfo) throws IOException {
        // package 경로를 디렉토리 경로로 변환
        String packagePath = fileInfo.packageName.replace('.', File.separatorChar);
        
        // 파일명 생성 (class 이름 + .java)
        String fileName = fileInfo.className + ".java";

        // src/main/java에서 검색
        File targetFile = new File(mainSourceDirectory, packagePath + File.separator + fileName);
        if (targetFile.exists() && targetFile.isFile()) {
            getLog().info("Replacing file in main source: " + targetFile.getAbsolutePath());
            copyFile(sourceFile, targetFile);
            return true;
        }

        // src/test/java에서 검색
        targetFile = new File(testSourceDirectory, packagePath + File.separator + fileName);
        if (targetFile.exists() && targetFile.isFile()) {
            getLog().info("Replacing file in test source: " + targetFile.getAbsolutePath());
            copyFile(sourceFile, targetFile);
            return true;
        }

        return false;
    }

    /**
     * 소스 파일을 대상 파일로 복사합니다.
     * 
     * @param sourceFile 소스 파일
     * @param targetFile 대상 파일
     * @throws IOException 파일 복사 오류 발생 시
     */
    private void copyFile(File sourceFile, File targetFile) throws IOException {
        // 대상 파일의 부모 디렉토리가 존재하는지 확인하고 없으면 생성
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // 파일 내용 복사
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(sourceFile), StandardCharsets.UTF_8));
             OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8)) {
            
            char[] buffer = new char[8192];
            int length;
            while ((length = reader.read(buffer)) > 0) {
                writer.write(buffer, 0, length);
            }
        }
    }

    /**
     * Java 파일의 package와 class 정보를 담는 내부 클래스.
     */
    private static class JavaFileInfo {
        final String packageName;
        final String className;

        JavaFileInfo(String packageName, String className) {
            this.packageName = packageName;
            this.className = className;
        }
    }

    /**
     * path-map.yml의 경로 매핑 정보를 담는 내부 클래스.
     */
    private static class PathMapping {
        final String source;
        final String target;

        PathMapping(String source, String target) {
            this.source = source;
            this.target = target;
        }
    }
}
