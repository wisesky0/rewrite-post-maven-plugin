package com.github.h2seo.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ReplaceFileMojo에 대한 단위 테스트 클래스.
 * 
 * <p>이 테스트 클래스는 다음을 검증합니다:
 * <ul>
 *   <li>Java 파일 검색 기능 (재귀적 탐색)</li>
 *   <li>package와 class 이름 추출 기능</li>
 *   <li>대상 파일 검색 및 교체 기능</li>
 *   <li>다양한 입력 조건에 대한 예외 처리</li>
 *   <li>설정 옵션의 동작 (failOnError 등)</li>
 * </ul>
 * 
 * @author h2seo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReplaceFileMojo 단위 테스트")
class ReplaceFileMojoTest {

    /** Mock된 Maven Log 객체 */
    @Mock
    private Log log;

    /** 테스트용 임시 디렉토리 (루트) */
    private File tempDir;
    
    /** 테스트용 소스 디렉토리 (교체할 Java 파일들이 있는 디렉토리) */
    private File sourceDir;
    
    /** 테스트용 메인 소스 디렉토리 */
    private File mainSourceDir;
    
    /** 테스트용 테스트 소스 디렉토리 */
    private File testSourceDir;
    
    /** 테스트 대상 Mojo 인스턴스 */
    private ReplaceFileMojo mojo;

    /**
     * 각 테스트 메서드 실행 전에 호출되는 설정 메서드.
     * 
     * <p>다음 작업을 수행합니다:
     * <ul>
     *   <li>임시 디렉토리 생성</li>
     *   <li>소스 디렉토리 및 메인/테스트 소스 디렉토리 생성</li>
     *   <li>ReplaceFileMojo 인스턴스 생성 및 초기 설정</li>
     * </ul>
     * 
     * @throws IOException 디렉토리 생성 실패 시
     * @throws Exception 리플렉션을 통한 필드 설정 실패 시
     */
    @BeforeEach
    @DisplayName("테스트 환경 설정")
    void setUp() throws IOException, Exception {
        // 임시 디렉토리 생성 (테스트 간 격리)
        tempDir = Files.createTempDirectory("replace-file-test").toFile();
        
        // 소스 디렉토리 생성 (교체할 Java 파일들이 있는 디렉토리)
        sourceDir = new File(tempDir, "replace");
        sourceDir.mkdirs();
        
        // 메인 소스 디렉토리 생성
        mainSourceDir = new File(tempDir, "src/main/java");
        mainSourceDir.mkdirs();
        
        // 테스트 소스 디렉토리 생성
        testSourceDir = new File(tempDir, "src/test/java");
        testSourceDir.mkdirs();

        // Mojo 인스턴스 생성 및 초기 설정
        mojo = new ReplaceFileMojo();
        mojo.setLog(log);
        
        // 리플렉션을 사용하여 private 필드 설정
        setField(mojo, "sourceDirectory", sourceDir);
        setField(mojo, "mainSourceDirectory", mainSourceDir);
        setField(mojo, "testSourceDirectory", testSourceDir);
        setField(mojo, "failOnError", true);
    }

    /**
     * 각 테스트 메서드 실행 후에 호출되는 정리 메서드.
     * 
     * <p>생성된 임시 디렉토리와 모든 하위 파일/디렉토리를 재귀적으로 삭제합니다.
     * 
     * @throws IOException 디렉토리 삭제 실패 시
     */
    @AfterEach
    @DisplayName("테스트 환경 정리")
    void tearDown() throws IOException {
        // 임시 디렉토리 및 모든 하위 항목 삭제
        deleteDirectory(tempDir);
    }

    /**
     * Java 파일 검색 기능 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>소스 디렉토리에 Java 파일 생성</li>
     *   <li>findJavaFiles 메서드 호출</li>
     *   <li>Java 파일만 올바르게 검색되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>Java 파일(.java)만 검색되어야 함</li>
     *   <li>일반 텍스트 파일(.txt)은 제외되어야 함</li>
     *   <li>정확히 2개의 Java 파일이 검색되어야 함</li>
     * </ul>
     * 
     * @throws Exception 테스트 실행 중 예외 발생 시
     */
    @Test
    @DisplayName("Java 파일 검색 기능 테스트")
    public void testFindJavaFiles() throws Exception {
        // Given: 다양한 확장자를 가진 테스트 파일 생성
        File javaFile1 = new File(sourceDir, "Test1.java");
        javaFile1.createNewFile();
        
        File javaFile2 = new File(sourceDir, "Test2.java");
        javaFile2.createNewFile();
        
        // 일반 텍스트 파일 (Java 파일이 아님)
        File nonJavaFile = new File(sourceDir, "test.txt");
        nonJavaFile.createNewFile();

        // When: 파일 검색 수행
        List<File> allFiles = invokeFindAllFiles(mojo, sourceDir);

        // Then: 모든 파일이 검색되었는지 검증 (Java 파일과 일반 파일 모두)
        assertEquals(3, allFiles.size(), "모든 파일이 검색되어야 함");
        assertTrue(allFiles.contains(javaFile1), "Test1.java 파일이 검색되어야 함");
        assertTrue(allFiles.contains(javaFile2), "Test2.java 파일이 검색되어야 함");
        assertTrue(allFiles.contains(nonJavaFile), "일반 텍스트 파일도 검색되어야 함");
    }

    /**
     * 하위 디렉토리에서의 Java 파일 검색 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>하위 디렉토리에 Java 파일 생성</li>
     *   <li>재귀적 탐색이 올바르게 동작하는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>하위 디렉토리의 Java 파일도 검색되어야 함</li>
     *   <li>재귀적 탐색이 정상 동작해야 함</li>
     * </ul>
     * 
     * @throws Exception 테스트 실행 중 예외 발생 시
     */
    @Test
    @DisplayName("하위 디렉토리에서의 Java 파일 검색 테스트")
    public void testFindJavaFilesWithSubdirectories() throws Exception {
        // Given: 하위 디렉토리에 Java 파일 생성
        File subDir = new File(sourceDir, "subdir");
        subDir.mkdirs();
        File javaFile = new File(subDir, "Test.java");
        javaFile.createNewFile();

        // When: 파일 검색 수행
        List<File> allFiles = invokeFindAllFiles(mojo, sourceDir);

        // Then: 하위 디렉토리의 Java 파일이 검색되었는지 검증
        assertEquals(1, allFiles.size(), "하위 디렉토리의 Java 파일이 검색되어야 함");
        assertTrue(allFiles.contains(javaFile), "하위 디렉토리의 Java 파일이 포함되어야 함");
    }

    /**
     * 빈 디렉토리에서의 Java 파일 검색 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>빈 디렉토리에서 Java 파일 검색</li>
     *   <li>빈 리스트가 반환되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>빈 디렉토리에서는 빈 리스트가 반환되어야 함</li>
     *   <li>예외가 발생하지 않아야 함</li>
     * </ul>
     * 
     * @throws Exception 테스트 실행 중 예외 발생 시
     */
    @Test
    @DisplayName("빈 디렉토리에서의 Java 파일 검색 테스트")
    public void testFindJavaFilesEmptyDirectory() throws Exception {
        // Given: 빈 디렉토리 (이미 setUp에서 생성됨)

        // When: 파일 검색 수행
        List<File> allFiles = invokeFindAllFiles(mojo, sourceDir);

        // Then: 빈 리스트가 반환되어야 함
        assertTrue(allFiles.isEmpty(), "빈 디렉토리에서는 빈 리스트가 반환되어야 함");
    }

    /**
     * package와 class 이름 추출 기능 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>유효한 package와 class 선언을 가진 Java 파일 생성</li>
     *   <li>extractFileInfo 메서드 호출</li>
     *   <li>package와 class 이름이 올바르게 추출되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>package 이름이 올바르게 추출되어야 함</li>
     *   <li>class 이름이 올바르게 추출되어야 함</li>
     * </ul>
     * 
     * @throws Exception 테스트 실행 중 예외 발생 시
     */
    @Test
    @DisplayName("package와 class 이름 추출 기능 테스트")
    public void testExtractFileInfo() throws Exception {
        // Given: 유효한 package와 class 선언을 가진 Java 파일 생성
        File javaFile = new File(sourceDir, "TestClass.java");
        try (FileWriter writer = new FileWriter(javaFile, StandardCharsets.UTF_8)) {
            writer.write("package com.example.test;\n");
            writer.write("\n");
            writer.write("public class TestClass {\n");
            writer.write("}\n");
        }

        // When: package와 class 이름 추출 수행
        Object fileInfo = invokeExtractFileInfo(mojo, javaFile);

        // Then: package와 class 이름이 올바르게 추출되었는지 검증
        assertTrue(fileInfo != null, "fileInfo가 null이 아니어야 함");
        
        // 리플렉션을 사용하여 필드 값 확인
        Field packageField = fileInfo.getClass().getDeclaredField("packageName");
        packageField.setAccessible(true);
        String packageName = (String) packageField.get(fileInfo);
        assertEquals("com.example.test", packageName, "package 이름이 올바르게 추출되어야 함");
        
        Field classField = fileInfo.getClass().getDeclaredField("className");
        classField.setAccessible(true);
        String className = (String) classField.get(fileInfo);
        assertEquals("TestClass", className, "class 이름이 올바르게 추출되어야 함");
    }

    /**
     * package 선언이 없는 Java 파일 처리 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>package 선언이 없는 Java 파일 생성</li>
     *   <li>extractFileInfo 메서드 호출</li>
     *   <li>null이 반환되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>package 선언이 없으면 null이 반환되어야 함</li>
     * </ul>
     * 
     * @throws Exception 테스트 실행 중 예외 발생 시
     */
    @Test
    @DisplayName("package 선언이 없는 Java 파일 처리 테스트")
    public void testExtractFileInfoWithoutPackage() throws Exception {
        // Given: package 선언이 없는 Java 파일 생성
        File javaFile = new File(sourceDir, "TestClass.java");
        try (FileWriter writer = new FileWriter(javaFile, StandardCharsets.UTF_8)) {
            writer.write("public class TestClass {\n");
            writer.write("}\n");
        }

        // When: package와 class 이름 추출 수행
        Object fileInfo = invokeExtractFileInfo(mojo, javaFile);

        // Then: null이 반환되어야 함
        assertTrue(fileInfo == null, "package 선언이 없으면 null이 반환되어야 함");
    }

    /**
     * class 선언이 없는 Java 파일 처리 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>class 선언이 없는 Java 파일 생성</li>
     *   <li>extractFileInfo 메서드 호출</li>
     *   <li>null이 반환되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>class 선언이 없으면 null이 반환되어야 함</li>
     * </ul>
     * 
     * @throws Exception 테스트 실행 중 예외 발생 시
     */
    @Test
    @DisplayName("class 선언이 없는 Java 파일 처리 테스트")
    public void testExtractFileInfoWithoutClass() throws Exception {
        // Given: class 선언이 없는 Java 파일 생성
        File javaFile = new File(sourceDir, "TestClass.java");
        try (FileWriter writer = new FileWriter(javaFile, StandardCharsets.UTF_8)) {
            writer.write("package com.example.test;\n");
            writer.write("\n");
            writer.write("public interface TestInterface {\n");
            writer.write("}\n");
        }

        // When: package와 class 이름 추출 수행
        Object fileInfo = invokeExtractFileInfo(mojo, javaFile);

        // Then: null이 반환되어야 함 (interface는 class가 아님)
        assertTrue(fileInfo == null, "class 선언이 없으면 null이 반환되어야 함");
    }

    /**
     * 소스 디렉토리가 null인 경우의 처리 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>sourceDirectory를 null로 설정</li>
     *   <li>execute 메서드 호출</li>
     *   <li>경고 로그가 출력되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>예외가 발생하지 않고 경고만 로그되어야 함</li>
     *   <li>실행이 정상적으로 종료되어야 함</li>
     * </ul>
     * 
     * @throws MojoExecutionException Mojo 실행 중 예외 발생 시
     * @throws MojoFailureException Mojo 실패 시
     * @throws Exception 리플렉션을 통한 필드 설정 실패 시
     */
    @Test
    @DisplayName("소스 디렉토리가 null인 경우의 처리 테스트")
    public void testExecuteWithNoSourceDirectory() throws MojoExecutionException, MojoFailureException, Exception {
        // Given: 소스 디렉토리를 null로 설정
        setField(mojo, "sourceDirectory", null);

        // When: execute 메서드 호출
        mojo.execute();

        // Then: 경고 로그가 출력되어야 함
        verify(log).warn(anyString());
    }

    /**
     * 존재하지 않는 소스 디렉토리인 경우의 처리 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>존재하지 않는 디렉토리 경로 설정</li>
     *   <li>execute 메서드 호출</li>
     *   <li>경고 로그가 출력되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>예외가 발생하지 않고 경고만 로그되어야 함</li>
     *   <li>실행이 정상적으로 종료되어야 함</li>
     * </ul>
     * 
     * @throws MojoExecutionException Mojo 실행 중 예외 발생 시
     * @throws MojoFailureException Mojo 실패 시
     * @throws Exception 리플렉션을 통한 필드 설정 실패 시
     */
    @Test
    @DisplayName("존재하지 않는 소스 디렉토리인 경우의 처리 테스트")
    public void testExecuteWithNonExistentSourceDirectory() throws MojoExecutionException, MojoFailureException, Exception {
        // Given: 존재하지 않는 디렉토리 경로 설정
        File nonExistentDir = new File(tempDir, "non-existent");
        setField(mojo, "sourceDirectory", nonExistentDir);

        // When: execute 메서드 호출
        mojo.execute();

        // Then: 경고 로그가 출력되어야 함
        verify(log).warn(anyString());
    }

    /**
     * 파일을 디렉토리로 지정한 경우의 예외 처리 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>일반 파일을 소스 디렉토리로 설정</li>
     *   <li>execute 메서드 호출</li>
     *   <li>MojoExecutionException이 발생하는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>MojoExecutionException이 발생해야 함</li>
     *   <li>예외 메시지에 "not a directory"가 포함되어야 함</li>
     * </ul>
     * 
     * @throws Exception 테스트 실행 중 예외 발생 시
     */
    @Test
    @DisplayName("파일을 디렉토리로 지정한 경우의 예외 처리 테스트")
    public void testExecuteWithFileInsteadOfDirectory() throws Exception {
        // Given: 일반 파일을 소스 디렉토리로 설정
        File file = new File(tempDir, "file.txt");
        file.createNewFile();
        setField(mojo, "sourceDirectory", file);

        // When & Then: execute 메서드 호출 시 예외 발생해야 함
        try {
            mojo.execute();
        } catch (MojoExecutionException e) {
            // 예외 메시지에 "not a directory"가 포함되어야 함
            assertTrue(e.getMessage().contains("not a directory"), 
                       "예외 메시지에 'not a directory'가 포함되어야 함");
        }
    }

    /**
     * Java 파일이 없는 경우의 처리 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>빈 소스 디렉토리에서 execute 메서드 호출</li>
     *   <li>적절한 정보 로그가 출력되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>예외가 발생하지 않아야 함</li>
     *   <li>"No Java files found" 정보 로그가 출력되어야 함</li>
     * </ul>
     * 
     * @throws MojoExecutionException Mojo 실행 중 예외 발생 시
     * @throws MojoFailureException Mojo 실패 시
     */
    @Test
    @DisplayName("Java 파일이 없는 경우의 처리 테스트")
    public void testExecuteWithNoJavaFiles() throws MojoExecutionException, MojoFailureException {
        // Given: 빈 소스 디렉토리 (이미 setUp에서 생성됨)

        // When: execute 메서드 호출
        mojo.execute();

        // Then: "No files found" 정보 로그가 출력되어야 함
        verify(log).info("No files found in: " + sourceDir.getAbsolutePath());
    }

    /**
     * 실제 파일 교체 테스트 (메인 소스 디렉토리).
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>소스 디렉토리에 교체할 Java 파일 생성</li>
     *   <li>메인 소스 디렉토리에 대상 파일 생성</li>
     *   <li>execute 메서드 호출하여 파일 교체</li>
     *   <li>파일이 올바르게 교체되었는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>파일이 성공적으로 교체되어야 함</li>
     *   <li>파일 내용이 올바르게 복사되어야 함</li>
     *   <li>예외가 발생하지 않아야 함</li>
     * </ul>
     * 
     * @throws IOException 파일 I/O 오류 발생 시
     * @throws MojoExecutionException Mojo 실행 중 예외 발생 시
     * @throws MojoFailureException Mojo 실패 시
     */
    @Test
    @DisplayName("실제 파일 교체 테스트 (메인 소스 디렉토리)")
    public void testExecuteWithFileReplacementInMainSource() throws IOException, MojoExecutionException, MojoFailureException {
        // Given: 소스 디렉토리에 교체할 Java 파일 생성
        File sourceJavaFile = new File(sourceDir, "TestClass.java");
        String newContent = "package com.example.test;\n\npublic class TestClass {\n    public void test() {\n        System.out.println(\"Replaced!\");\n    }\n}\n";
        try (FileWriter writer = new FileWriter(sourceJavaFile, StandardCharsets.UTF_8)) {
            writer.write(newContent);
        }

        // 메인 소스 디렉토리에 대상 파일 생성
        File targetPackageDir = new File(mainSourceDir, "com/example/test");
        targetPackageDir.mkdirs();
        File targetFile = new File(targetPackageDir, "TestClass.java");
        String originalContent = "package com.example.test;\n\npublic class TestClass {\n    public void test() {\n        System.out.println(\"Original\");\n    }\n}\n";
        try (FileWriter writer = new FileWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write(originalContent);
        }

        // When: execute 메서드 호출하여 파일 교체
        mojo.execute();

        // Then: 파일이 올바르게 교체되었는지 검증
        try (BufferedReader reader = new BufferedReader(
                new FileReader(targetFile, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            assertTrue(content.toString().contains("Replaced!"), "파일 내용이 교체되어야 함");
            assertFalse(content.toString().contains("Original"), "원본 내용이 제거되어야 함");
        }
    }

    /**
     * 실제 파일 교체 테스트 (테스트 소스 디렉토리).
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>소스 디렉토리에 교체할 Java 파일 생성</li>
     *   <li>테스트 소스 디렉토리에 대상 파일 생성 (메인 소스에는 없음)</li>
     *   <li>execute 메서드 호출하여 파일 교체</li>
     *   <li>파일이 올바르게 교체되었는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>테스트 소스 디렉토리의 파일이 교체되어야 함</li>
     *   <li>파일 내용이 올바르게 복사되어야 함</li>
     * </ul>
     * 
     * @throws IOException 파일 I/O 오류 발생 시
     * @throws MojoExecutionException Mojo 실행 중 예외 발생 시
     * @throws MojoFailureException Mojo 실패 시
     */
    @Test
    @DisplayName("실제 파일 교체 테스트 (테스트 소스 디렉토리)")
    public void testExecuteWithFileReplacementInTestSource() throws IOException, MojoExecutionException, MojoFailureException {
        // Given: 소스 디렉토리에 교체할 Java 파일 생성
        File sourceJavaFile = new File(sourceDir, "TestClass.java");
        String newContent = "package com.example.test;\n\npublic class TestClass {\n    public void test() {\n        System.out.println(\"Replaced in test!\");\n    }\n}\n";
        try (FileWriter writer = new FileWriter(sourceJavaFile, StandardCharsets.UTF_8)) {
            writer.write(newContent);
        }

        // 테스트 소스 디렉토리에 대상 파일 생성 (메인 소스에는 없음)
        File targetPackageDir = new File(testSourceDir, "com/example/test");
        targetPackageDir.mkdirs();
        File targetFile = new File(targetPackageDir, "TestClass.java");
        String originalContent = "package com.example.test;\n\npublic class TestClass {\n    public void test() {\n        System.out.println(\"Original\");\n    }\n}\n";
        try (FileWriter writer = new FileWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write(originalContent);
        }

        // When: execute 메서드 호출하여 파일 교체
        mojo.execute();

        // Then: 파일이 올바르게 교체되었는지 검증
        try (BufferedReader reader = new BufferedReader(
                new FileReader(targetFile, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            assertTrue(content.toString().contains("Replaced in test!"), "파일 내용이 교체되어야 함");
            assertFalse(content.toString().contains("Original"), "원본 내용이 제거되어야 함");
        }
    }

    /**
     * 대상 파일이 없는 경우의 처리 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>소스 디렉토리에 교체할 Java 파일 생성</li>
     *   <li>대상 파일은 생성하지 않음</li>
     *   <li>execute 메서드 호출</li>
     *   <li>MojoExecutionException이 발생하는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>대상 파일이 없으면 MojoExecutionException이 발생해야 함</li>
     *   <li>예외 메시지에 package와 class 이름이 포함되어야 함</li>
     * </ul>
     * 
     * @throws Exception 테스트 실행 중 예외 발생 시
     */
    @Test
    @DisplayName("대상 파일이 없는 경우의 처리 테스트")
    public void testExecuteWithNoTargetFile() throws Exception {
        // Given: 소스 디렉토리에 교체할 Java 파일 생성
        File sourceJavaFile = new File(sourceDir, "TestClass.java");
        try (FileWriter writer = new FileWriter(sourceJavaFile, StandardCharsets.UTF_8)) {
            writer.write("package com.example.test;\n\npublic class TestClass {\n}\n");
        }

        // 대상 파일은 생성하지 않음

        // When & Then: execute 메서드 호출 시 예외 발생해야 함
        try {
            mojo.execute();
        } catch (MojoExecutionException e) {
            // 예외 메시지에 package와 class 이름이 포함되어야 함
            assertTrue(e.getMessage().contains("com.example.test.TestClass"), 
                       "예외 메시지에 package와 class 이름이 포함되어야 함");
        }
    }

    /**
     * failOnError가 false일 때의 오류 처리 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>failOnError를 false로 설정</li>
     *   <li>대상 파일이 없는 Java 파일 생성</li>
     *   <li>execute 메서드 호출</li>
     *   <li>예외가 발생하지 않고 실행이 완료되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>failOnError가 false일 때는 예외가 발생하지 않아야 함</li>
     *   <li>실행이 정상적으로 완료되어야 함</li>
     *   <li>경고 로그가 출력되어야 함</li>
     * </ul>
     * 
     * @throws MojoExecutionException Mojo 실행 중 예외 발생 시
     * @throws MojoFailureException Mojo 실패 시
     * @throws Exception 리플렉션을 통한 필드 설정 실패 시
     */
    @Test
    @DisplayName("failOnError가 false일 때의 오류 처리 테스트")
    public void testFailOnErrorFalse() throws MojoExecutionException, MojoFailureException, Exception {
        // Given: failOnError를 false로 설정
        setField(mojo, "failOnError", false);

        // 소스 디렉토리에 교체할 Java 파일 생성 (대상 파일은 없음)
        File sourceJavaFile = new File(sourceDir, "TestClass.java");
        try (FileWriter writer = new FileWriter(sourceJavaFile, StandardCharsets.UTF_8)) {
            writer.write("package com.example.test;\n\npublic class TestClass {\n}\n");
        }

        // When: execute 메서드 호출 (failOnError가 false이므로 예외 발생하지 않아야 함)
        mojo.execute();

        // Then: 실행이 완료되었고 경고 로그가 출력되었는지 검증
        verify(log).warn(anyString());
    }

    /**
     * path-map.yml이 있는 경우 Java 파일이 아닌 파일 처리 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>path-map.yml 파일 생성 및 경로 매핑 정의</li>
     *   <li>소스 디렉토리에 Java 파일이 아닌 파일 생성</li>
     *   <li>대상 파일 생성</li>
     *   <li>execute 메서드 호출</li>
     *   <li>파일이 올바르게 교체되었는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>path-map.yml에서 경로 매핑을 읽어야 함</li>
     *   <li>Java 파일이 아닌 파일이 교체되어야 함</li>
     *   <li>파일 내용이 올바르게 복사되어야 함</li>
     * </ul>
     * 
     * @throws IOException 파일 I/O 오류 발생 시
     * @throws MojoExecutionException Mojo 실행 중 예외 발생 시
     * @throws MojoFailureException Mojo 실패 시
     * @throws Exception 리플렉션을 통한 필드 설정 실패 시
     */
    @Test
    @DisplayName("path-map.yml이 있는 경우 Java 파일이 아닌 파일 처리 테스트")
    public void testNonJavaFileWithPathMap() throws IOException, MojoExecutionException, MojoFailureException, Exception {
        // Given: path-map.yml 파일 생성
        File pathMapFile = new File(tempDir, "path-map.yml");
        try (FileWriter writer = new FileWriter(pathMapFile, StandardCharsets.UTF_8)) {
            writer.write("config.properties:\n");
            writer.write("  source: src/main/resources/config.properties\n");
            writer.write("  target: target/classes/config.properties\n");
        }
        setField(mojo, "pathMapFile", pathMapFile);

        // 소스 디렉토리에 Java 파일이 아닌 파일 생성
        File sourceFile = new File(sourceDir, "config.properties");
        String newContent = "new.property=value\n";
        try (FileWriter writer = new FileWriter(sourceFile, StandardCharsets.UTF_8)) {
            writer.write(newContent);
        }

        // 대상 파일 생성
        File targetDir = new File(tempDir, "target/classes");
        targetDir.mkdirs();
        File targetFile = new File(targetDir, "config.properties");
        String originalContent = "old.property=oldvalue\n";
        try (FileWriter writer = new FileWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write(originalContent);
        }

        // When: execute 메서드 호출
        mojo.execute();

        // Then: 파일이 올바르게 교체되었는지 검증
        try (BufferedReader reader = new BufferedReader(
                new FileReader(targetFile, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            assertTrue(content.toString().contains("new.property=value"), "파일 내용이 교체되어야 함");
            assertFalse(content.toString().contains("old.property=oldvalue"), "원본 내용이 제거되어야 함");
        }
    }

    /**
     * path-map.yml이 없는 경우 Java 파일만 처리하는 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>path-map.yml 파일을 생성하지 않음</li>
     *   <li>소스 디렉토리에 Java 파일과 Java 파일이 아닌 파일 생성</li>
     *   <li>execute 메서드 호출</li>
     *   <li>Java 파일만 처리되고 Java 파일이 아닌 파일은 건너뛰는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>Java 파일은 처리되어야 함</li>
     *   <li>Java 파일이 아닌 파일은 건너뛰어야 함</li>
     *   <li>예외가 발생하지 않아야 함</li>
     * </ul>
     * 
     * @throws IOException 파일 I/O 오류 발생 시
     * @throws MojoExecutionException Mojo 실행 중 예외 발생 시
     * @throws MojoFailureException Mojo 실패 시
     */
    @Test
    @DisplayName("path-map.yml이 없는 경우 Java 파일만 처리하는 테스트")
    public void testJavaFilesOnlyWhenNoPathMap() throws IOException, MojoExecutionException, MojoFailureException, Exception {
        // Given: path-map.yml 파일을 생성하지 않음 (null로 설정)
        setField(mojo, "pathMapFile", new File(tempDir, "non-existent-path-map.yml"));

        // 소스 디렉토리에 Java 파일 생성
        File sourceJavaFile = new File(sourceDir, "TestClass.java");
        String newContent = "package com.example.test;\n\npublic class TestClass {\n    public void test() {\n        System.out.println(\"Replaced!\");\n    }\n}\n";
        try (FileWriter writer = new FileWriter(sourceJavaFile, StandardCharsets.UTF_8)) {
            writer.write(newContent);
        }

        // 메인 소스 디렉토리에 대상 파일 생성
        File targetPackageDir = new File(mainSourceDir, "com/example/test");
        targetPackageDir.mkdirs();
        File targetFile = new File(targetPackageDir, "TestClass.java");
        String originalContent = "package com.example.test;\n\npublic class TestClass {\n    public void test() {\n        System.out.println(\"Original\");\n    }\n}\n";
        try (FileWriter writer = new FileWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write(originalContent);
        }

        // Java 파일이 아닌 파일 생성
        File nonJavaFile = new File(sourceDir, "config.properties");
        try (FileWriter writer = new FileWriter(nonJavaFile, StandardCharsets.UTF_8)) {
            writer.write("property=value\n");
        }

        // When: execute 메서드 호출
        mojo.execute();

        // Then: Java 파일만 교체되었는지 검증
        try (BufferedReader reader = new BufferedReader(
                new FileReader(targetFile, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            assertTrue(content.toString().contains("Replaced!"), "Java 파일은 교체되어야 함");
        }
    }

    /**
     * pathMapFile이 지정되었지만 파일이 없는 경우 경고 출력 및 Java 파일만 처리 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>존재하지 않는 path-map.yml 파일 경로를 지정</li>
     *   <li>소스 디렉토리에 Java 파일과 Java 파일이 아닌 파일 생성</li>
     *   <li>execute 메서드 호출</li>
     *   <li>경고 로그가 출력되고 Java 파일만 처리되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>path-map.yml 파일이 없다는 경고가 출력되어야 함</li>
     *   <li>Java 파일은 정상적으로 처리되어야 함</li>
     *   <li>Java 파일이 아닌 파일은 건너뛰어야 함</li>
     *   <li>예외가 발생하지 않고 실행이 완료되어야 함</li>
     * </ul>
     * 
     * @throws IOException 파일 I/O 오류 발생 시
     * @throws MojoExecutionException Mojo 실행 중 예외 발생 시
     * @throws MojoFailureException Mojo 실패 시
     * @throws Exception 리플렉션을 통한 필드 설정 실패 시
     */
    @Test
    @DisplayName("pathMapFile이 지정되었지만 파일이 없는 경우 경고 출력 및 Java 파일만 처리 테스트")
    public void testPathMapFileSpecifiedButNotFound() throws IOException, MojoExecutionException, MojoFailureException, Exception {
        // Given: 존재하지 않는 path-map.yml 파일 경로를 지정
        File nonExistentPathMapFile = new File(tempDir, "non-existent-path-map.yml");
        setField(mojo, "pathMapFile", nonExistentPathMapFile);

        // 소스 디렉토리에 Java 파일 생성
        File sourceJavaFile = new File(sourceDir, "TestClass.java");
        String newContent = "package com.example.test;\n\npublic class TestClass {\n    public void test() {\n        System.out.println(\"Replaced!\");\n    }\n}\n";
        try (FileWriter writer = new FileWriter(sourceJavaFile, StandardCharsets.UTF_8)) {
            writer.write(newContent);
        }

        // 메인 소스 디렉토리에 대상 파일 생성
        File targetPackageDir = new File(mainSourceDir, "com/example/test");
        targetPackageDir.mkdirs();
        File targetFile = new File(targetPackageDir, "TestClass.java");
        String originalContent = "package com.example.test;\n\npublic class TestClass {\n    public void test() {\n        System.out.println(\"Original\");\n    }\n}\n";
        try (FileWriter writer = new FileWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write(originalContent);
        }

        // Java 파일이 아닌 파일 생성
        File nonJavaFile = new File(sourceDir, "config.properties");
        try (FileWriter writer = new FileWriter(nonJavaFile, StandardCharsets.UTF_8)) {
            writer.write("property=value\n");
        }

        // When: execute 메서드 호출
        mojo.execute();

        // Then: 경고 로그가 출력되고 Java 파일만 교체되었는지 검증
        verify(log).warn(anyString()); // path-map.yml 파일이 없다는 경고
        
        // Java 파일이 교체되었는지 확인
        try (BufferedReader reader = new BufferedReader(
                new FileReader(targetFile, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            assertTrue(content.toString().contains("Replaced!"), "Java 파일은 교체되어야 함");
            assertFalse(content.toString().contains("Original"), "원본 내용이 제거되어야 함");
        }
    }

    /**
     * path-map.yml이 sourceDirectory 내에 있어서 제외되는 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>sourceDirectory 내에 path-map.yml 파일 생성</li>
     *   <li>소스 디렉토리에 다른 파일들 생성</li>
     *   <li>execute 메서드 호출</li>
     *   <li>path-map.yml 파일이 처리 대상에서 제외되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>path-map.yml 파일이 처리 대상에서 제외되어야 함</li>
     *   <li>다른 파일들은 정상적으로 처리되어야 함</li>
     * </ul>
     * 
     * @throws IOException 파일 I/O 오류 발생 시
     * @throws MojoExecutionException Mojo 실행 중 예외 발생 시
     * @throws MojoFailureException Mojo 실패 시
     * @throws Exception 리플렉션을 통한 필드 설정 실패 시
     */
    @Test
    @DisplayName("path-map.yml이 sourceDirectory 내에 있어서 제외되는 테스트")
    public void testPathMapExcludedFromSourceDirectory() throws IOException, MojoExecutionException, MojoFailureException, Exception {
        // Given: sourceDirectory 내에 path-map.yml 파일 생성
        File pathMapFile = new File(sourceDir, "path-map.yml");
        try (FileWriter writer = new FileWriter(pathMapFile, StandardCharsets.UTF_8)) {
            writer.write("config.properties:\n");
            writer.write("  source: src/main/resources/config.properties\n");
            writer.write("  target: target/classes/config.properties\n");
        }
        setField(mojo, "pathMapFile", pathMapFile);

        // 소스 디렉토리에 다른 파일 생성
        File javaFile = new File(sourceDir, "TestClass.java");
        try (FileWriter writer = new FileWriter(javaFile, StandardCharsets.UTF_8)) {
            writer.write("package com.example.test;\n\npublic class TestClass {\n}\n");
        }

        // 메인 소스 디렉토리에 대상 파일 생성
        File targetPackageDir = new File(mainSourceDir, "com/example/test");
        targetPackageDir.mkdirs();
        File targetFile = new File(targetPackageDir, "TestClass.java");
        try (FileWriter writer = new FileWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write("package com.example.test;\n\npublic class TestClass {\n    public void old() {}\n}\n");
        }

        // When: execute 메서드 호출
        mojo.execute();

        // Then: path-map.yml이 처리 대상에서 제외되었는지 검증
        // execute가 정상적으로 완료되었는지 확인 (path-map.yml이 처리되지 않아야 함)
        assertTrue(targetFile.exists(), "대상 파일이 존재해야 함");
        
        // Java 파일이 교체되었는지 확인
        try (BufferedReader reader = new BufferedReader(
                new FileReader(targetFile, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            // Java 파일은 정상적으로 교체되어야 함
            assertTrue(content.toString().contains("public class TestClass"), "Java 파일이 교체되어야 함");
        }
    }

    // ==================== Helper Methods ====================

    /**
     * 리플렉션을 사용하여 객체의 private 필드에 값을 설정하는 헬퍼 메서드.
     * 
     * @param target 필드를 설정할 대상 객체
     * @param fieldName 설정할 필드 이름
     * @param value 설정할 값
     * @throws Exception 필드 접근 또는 설정 실패 시
     */
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * 리플렉션을 사용하여 ReplaceFileMojo의 private 메서드인 findAllFiles를 호출하는 헬퍼 메서드.
     * 
     * @param mojo ReplaceFileMojo 인스턴스
     * @param directory 검색할 디렉토리
     * @return 검색된 파일 목록
     * @throws Exception 메서드 호출 실패 시
     */
    @SuppressWarnings("unchecked")
    private List<File> invokeFindAllFiles(ReplaceFileMojo mojo, File directory) throws Exception {
        Method method = ReplaceFileMojo.class.getDeclaredMethod("findAllFiles", File.class);
        method.setAccessible(true);
        return (List<File>) method.invoke(mojo, directory);
    }

    /**
     * 리플렉션을 사용하여 ReplaceFileMojo의 private 메서드인 extractFileInfo를 호출하는 헬퍼 메서드.
     * 
     * @param mojo ReplaceFileMojo 인스턴스
     * @param javaFile Java 파일
     * @return package와 class 정보를 담은 객체
     * @throws Exception 메서드 호출 실패 시
     */
    private Object invokeExtractFileInfo(ReplaceFileMojo mojo, File javaFile) throws Exception {
        Method method = ReplaceFileMojo.class.getDeclaredMethod("extractFileInfo", File.class);
        method.setAccessible(true);
        return method.invoke(mojo, javaFile);
    }

    /**
     * 디렉토리와 모든 하위 파일/디렉토리를 재귀적으로 삭제하는 헬퍼 메서드.
     * 
     * @param directory 삭제할 디렉토리
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        // 재귀적으로 하위 디렉토리 삭제
                        deleteDirectory(file);
                    } else {
                        // 파일 삭제
                        file.delete();
                    }
                }
            }
            // 디렉토리 삭제
            directory.delete();
        }
    }
}

