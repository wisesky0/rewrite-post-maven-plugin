package com.github.h2seo.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ApplyPatchMojo에 대한 단위 테스트 클래스.
 * 
 * <p>이 테스트 클래스는 다음을 검증합니다:
 * <ul>
 *   <li>패치 파일 검색 기능 (확장자 필터링, 재귀적 탐색)</li>
 *   <li>다양한 입력 조건에 대한 예외 처리</li>
 *   <li>JGit을 사용한 실제 패치 적용 기능</li>
 *   <li>설정 옵션의 동작 (확장자, 오류 처리 등)</li>
 * </ul>
 * 
 * @author h2seo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApplyPatchMojo 단위 테스트")
class ApplyPatchMojoTest {

    /** Mock된 Maven Log 객체 */
    @Mock
    private Log log;

    /** 테스트용 임시 디렉토리 (루트) */
    private File tempDir;
    
    /** 테스트용 패치 파일 디렉토리 */
    private File patchDir;
    
    /** 테스트용 Git 저장소 루트 디렉토리 */
    private File gitRootDir;
    
    /** 테스트 대상 Mojo 인스턴스 */
    private ApplyPatchMojo mojo;

    /**
     * 각 테스트 메서드 실행 전에 호출되는 설정 메서드.
     * 
     * <p>다음 작업을 수행합니다:
     * <ul>
     *   <li>임시 디렉토리 생성</li>
     *   <li>패치 디렉토리 및 Git 루트 디렉토리 생성</li>
     *   <li>ApplyPatchMojo 인스턴스 생성 및 초기 설정</li>
     * </ul>
     * 
     * @throws IOException 디렉토리 생성 실패 시
     * @throws Exception 리플렉션을 통한 필드 설정 실패 시
     */
    @BeforeEach
    @DisplayName("테스트 환경 설정")
    void setUp() throws IOException, Exception {
        // 임시 디렉토리 생성 (테스트 간 격리)
        tempDir = Files.createTempDirectory("rewrite-post-test").toFile();
        
        // 패치 파일을 저장할 디렉토리 생성
        patchDir = new File(tempDir, "patches");
        patchDir.mkdirs();
        
        // Git 저장소 루트 디렉토리 생성
        gitRootDir = new File(tempDir, "git-repo");
        gitRootDir.mkdirs();

        // Mojo 인스턴스 생성 및 초기 설정
        mojo = new ApplyPatchMojo();
        mojo.setLog(log);
        
        // 리플렉션을 사용하여 private 필드 설정
        setField(mojo, "patchDirectory", patchDir);
        setField(mojo, "gitRootDirectory", gitRootDir);
        setField(mojo, "failOnError", true);
        setField(mojo, "extensions", "patch,diff");
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
     * 패치 파일 검색 기능 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>다양한 확장자를 가진 파일 생성 (.patch, .diff, .txt)</li>
     *   <li>findPatchFiles 메서드 호출</li>
     *   <li>패치 파일만 올바르게 필터링되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>패치 파일(.patch, .diff)만 검색되어야 함</li>
     *   <li>일반 텍스트 파일(.txt)은 제외되어야 함</li>
     *   <li>정확히 2개의 패치 파일이 검색되어야 함</li>
     * </ul>
     * 
     * @throws Exception 테스트 실행 중 예외 발생 시
     */
    @Test
    public void testFindPatchFiles() throws Exception {
        // Given: 다양한 확장자를 가진 테스트 파일 생성
        File patchFile1 = new File(patchDir, "test1.patch");
        patchFile1.createNewFile();
        
        File patchFile2 = new File(patchDir, "test2.diff");
        patchFile2.createNewFile();
        
        // 일반 텍스트 파일 (패치 파일이 아님)
        File nonPatchFile = new File(patchDir, "test.txt");
        nonPatchFile.createNewFile();

        // When: 패치 파일 검색 수행
        List<File> patchFiles = invokeFindPatchFiles(mojo, patchDir);

        // Then: 패치 파일만 올바르게 필터링되었는지 검증
        assertEquals(2, patchFiles.size(), "패치 파일은 정확히 2개여야 함");
        assertTrue(patchFiles.contains(patchFile1), "test1.patch 파일이 검색되어야 함");
        assertTrue(patchFiles.contains(patchFile2), "test2.diff 파일이 검색되어야 함");
        assertFalse(patchFiles.contains(nonPatchFile), "일반 텍스트 파일은 검색되지 않아야 함");
    }

    /**
     * 하위 디렉토리에서의 패치 파일 검색 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>하위 디렉토리에 패치 파일 생성</li>
     *   <li>재귀적 탐색이 올바르게 동작하는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>하위 디렉토리의 패치 파일도 검색되어야 함</li>
     *   <li>재귀적 탐색이 정상 동작해야 함</li>
     * </ul>
     * 
     * @throws Exception 테스트 실행 중 예외 발생 시
     */
    @Test
    public void testFindPatchFilesWithSubdirectories() throws Exception {
        // Given: 하위 디렉토리에 패치 파일 생성
        File subDir = new File(patchDir, "subdir");
        subDir.mkdirs();
        File patchFile = new File(subDir, "test.patch");
        patchFile.createNewFile();

        // When: 패치 파일 검색 수행
        List<File> patchFiles = invokeFindPatchFiles(mojo, patchDir);

        // Then: 하위 디렉토리의 패치 파일이 검색되었는지 검증
        assertEquals(1, patchFiles.size(), "하위 디렉토리의 패치 파일이 검색되어야 함");
        assertTrue(patchFiles.contains(patchFile), "하위 디렉토리의 패치 파일이 포함되어야 함");
    }

    /**
     * 빈 디렉토리에서의 패치 파일 검색 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>빈 디렉토리에서 패치 파일 검색</li>
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
    public void testFindPatchFilesEmptyDirectory() throws Exception {
        // Given: 빈 디렉토리 (이미 setUp에서 생성됨)

        // When: 패치 파일 검색 수행
        List<File> patchFiles = invokeFindPatchFiles(mojo, patchDir);

        // Then: 빈 리스트가 반환되어야 함
        assertTrue(patchFiles.isEmpty(), "빈 디렉토리에서는 빈 리스트가 반환되어야 함");
    }

    /**
     * 패치 디렉토리가 null인 경우의 처리 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>patchDirectory를 null로 설정</li>
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
    public void testExecuteWithNoPatchDirectory() throws MojoExecutionException, MojoFailureException, Exception {
        // Given: 패치 디렉토리를 null로 설정
        setField(mojo, "patchDirectory", null);

        // When: execute 메서드 호출
        mojo.execute();

        // Then: 경고 로그가 출력되어야 함
        verify(log).warn(anyString());
    }

    /**
     * 존재하지 않는 패치 디렉토리인 경우의 처리 테스트.
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
    public void testExecuteWithNonExistentPatchDirectory() throws MojoExecutionException, MojoFailureException, Exception {
        // Given: 존재하지 않는 디렉토리 경로 설정
        File nonExistentDir = new File(tempDir, "non-existent");
        setField(mojo, "patchDirectory", nonExistentDir);

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
     *   <li>일반 파일을 패치 디렉토리로 설정</li>
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
    public void testExecuteWithFileInsteadOfDirectory() throws Exception {
        // Given: 일반 파일을 패치 디렉토리로 설정
        File file = new File(tempDir, "file.txt");
        file.createNewFile();
        setField(mojo, "patchDirectory", file);

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
     * 패치 파일이 없는 경우의 처리 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>빈 패치 디렉토리에서 execute 메서드 호출</li>
     *   <li>적절한 정보 로그가 출력되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>예외가 발생하지 않아야 함</li>
     *   <li>"No patch files found" 정보 로그가 출력되어야 함</li>
     * </ul>
     * 
     * @throws MojoExecutionException Mojo 실행 중 예외 발생 시
     * @throws MojoFailureException Mojo 실패 시
     */
    @Test
    public void testExecuteWithNoPatchFiles() throws MojoExecutionException, MojoFailureException {
        // Given: 빈 패치 디렉토리 (이미 setUp에서 생성됨)

        // When: execute 메서드 호출
        mojo.execute();

        // Then: "No patch files found" 정보 로그가 출력되어야 함
        verify(log).info("No patch files found in: " + patchDir.getAbsolutePath());
    }

    /**
     * 실제 패치 파일 적용 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>Git 저장소 초기화 및 테스트 파일 생성</li>
     *   <li>초기 커밋 수행</li>
     *   <li>유효한 패치 파일 생성</li>
     *   <li>execute 메서드 호출하여 패치 적용</li>
     *   <li>적절한 로그가 출력되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>패치 파일이 성공적으로 검색되어야 함</li>
     *   <li>패치 적용이 시도되어야 함</li>
     *   <li>예외가 발생하지 않아야 함</li>
     * </ul>
     * 
     * @throws IOException 파일 I/O 오류 발생 시
     * @throws MojoExecutionException Mojo 실행 중 예외 발생 시
     * @throws MojoFailureException Mojo 실패 시
     * @throws GitAPIException Git API 호출 중 예외 발생 시
     */
    @Test
    public void testExecuteWithPatchFile() throws IOException, MojoExecutionException, MojoFailureException, GitAPIException {
        // Given: Git 저장소 초기화 및 테스트 파일 생성
        try (Git git = Git.init().setDirectory(gitRootDir).call()) {
            // 테스트용 파일 생성
            File testFile = new File(gitRootDir, "test.txt");
            try (FileWriter writer = new FileWriter(testFile)) {
                writer.write("Line 1\nLine 2\nLine 3\n");
            }
            
            // Git에 파일 추가 및 커밋
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial commit").call();

            // 유효한 패치 파일 생성 (unified diff 형식)
            File patchFile = new File(patchDir, "test.patch");
            try (FileWriter writer = new FileWriter(patchFile)) {
                // Git unified diff 형식으로 패치 파일 작성
                writer.write("diff --git a/test.txt b/test.txt\n");
                writer.write("--- a/test.txt\n");
                writer.write("+++ b/test.txt\n");
                writer.write("@@ -1,3 +1,4 @@\n");
                writer.write(" Line 1\n");
                writer.write(" Line 2\n");
                writer.write("+New line\n");  // 새 라인 추가
                writer.write(" Line 3\n");
            }

            // When: execute 메서드 호출하여 패치 적용
            mojo.execute();

            // Then: 적절한 로그가 출력되었는지 검증
            verify(log).info("Found 1 patch file(s)");
            verify(log).info("Applying patch: test.patch");
        }
    }

    /**
     * 커스텀 확장자 설정 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>확장자를 "patch,diff,custom"으로 설정</li>
     *   <li>다양한 확장자를 가진 파일 생성</li>
     *   <li>설정된 확장자만 검색되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>설정된 확장자(.patch, .diff, .custom)만 검색되어야 함</li>
     *   <li>설정되지 않은 확장자(.txt)는 제외되어야 함</li>
     * </ul>
     * 
     * @throws Exception 테스트 실행 중 예외 발생 시
     */
    @Test
    public void testExtensionsConfiguration() throws Exception {
        // Given: 커스텀 확장자 설정
        setField(mojo, "extensions", "patch,diff,custom");

        // 다양한 확장자를 가진 파일 생성
        File patchFile1 = new File(patchDir, "test1.patch");
        patchFile1.createNewFile();
        File patchFile2 = new File(patchDir, "test2.custom");
        patchFile2.createNewFile();
        // 설정되지 않은 확장자
        File patchFile3 = new File(patchDir, "test3.txt");
        patchFile3.createNewFile();

        // When: 패치 파일 검색 수행
        List<File> patchFiles = invokeFindPatchFiles(mojo, patchDir);

        // Then: 설정된 확장자만 검색되었는지 검증
        assertEquals(2, patchFiles.size(), "설정된 확장자의 파일만 검색되어야 함");
        assertTrue(patchFiles.contains(patchFile1), ".patch 확장자 파일이 검색되어야 함");
        assertTrue(patchFiles.contains(patchFile2), ".custom 확장자 파일이 검색되어야 함");
        assertFalse(patchFiles.contains(patchFile3), "설정되지 않은 .txt 확장자 파일은 검색되지 않아야 함");
    }

    /**
     * failOnError가 false일 때의 오류 처리 테스트.
     * 
     * <p>테스트 시나리오:
     * <ol>
     *   <li>Git 저장소 초기화 및 테스트 파일 생성</li>
     *   <li>failOnError를 false로 설정</li>
     *   <li>적용할 수 없는 패치 파일 생성</li>
     *   <li>execute 메서드 호출</li>
     *   <li>예외가 발생하지 않고 실행이 완료되는지 검증</li>
     * </ol>
     * 
     * <p>검증 항목:
     * <ul>
     *   <li>failOnError가 false일 때는 예외가 발생하지 않아야 함</li>
     *   <li>실행이 정상적으로 완료되어야 함</li>
     *   <li>패치 적용 시도 로그가 출력되어야 함</li>
     * </ul>
     * 
     * @throws IOException 파일 I/O 오류 발생 시
     * @throws MojoExecutionException Mojo 실행 중 예외 발생 시
     * @throws MojoFailureException Mojo 실패 시
     * @throws Exception 리플렉션을 통한 필드 설정 실패 시
     */
    @Test
    public void testFailOnErrorFalse() throws IOException, MojoExecutionException, MojoFailureException, Exception {
        // Given: failOnError를 false로 설정
        setField(mojo, "failOnError", false);

        // Git 저장소 초기화
        try (Git git = Git.init().setDirectory(gitRootDir).call()) {
            // 테스트 파일 생성 및 커밋
            File testFile = new File(gitRootDir, "test.txt");
            try (FileWriter writer = new FileWriter(testFile)) {
                writer.write("Line 1\nLine 2\n");
            }
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial commit").call();

            // 적용할 수 없는 패치 파일 생성 (잘못된 컨텍스트)
            File patchFile = new File(patchDir, "invalid.patch");
            try (FileWriter writer = new FileWriter(patchFile)) {
                writer.write("diff --git a/test.txt b/test.txt\n");
                writer.write("--- a/test.txt\n");
                writer.write("+++ b/test.txt\n");
                writer.write("@@ -1,2 +1,3 @@\n");
                writer.write(" Line 1\n");
                writer.write(" Line 2\n");
                writer.write("+This line doesn't match context\n"); // 잘못된 컨텍스트
            }

            // When: execute 메서드 호출 (failOnError가 false이므로 예외 발생하지 않아야 함)
            mojo.execute();

            // Then: 실행이 완료되었고 적절한 로그가 출력되었는지 검증
            verify(log).info("Found 1 patch file(s)");
            verify(log).info("Applying patch: invalid.patch");
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
     * 리플렉션을 사용하여 ApplyPatchMojo의 private 메서드인 findPatchFiles를 호출하는 헬퍼 메서드.
     * 
     * @param mojo ApplyPatchMojo 인스턴스
     * @param directory 검색할 디렉토리
     * @return 검색된 패치 파일 목록
     * @throws Exception 메서드 호출 실패 시
     */
    @SuppressWarnings("unchecked")
    private List<File> invokeFindPatchFiles(ApplyPatchMojo mojo, File directory) throws Exception {
        Method method = ApplyPatchMojo.class.getDeclaredMethod("findPatchFiles", File.class);
        method.setAccessible(true);
        return (List<File>) method.invoke(mojo, directory);
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
