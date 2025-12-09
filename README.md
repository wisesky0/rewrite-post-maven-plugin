# Rewrite Post Maven Plugin

Maven 플러그인으로 지정된 디렉토리에 있는 patch 파일들을 읽어 소스 코드에 패치를 적용합니다.

## 기능

- 지정된 디렉토리에서 patch 파일 자동 검색
- JGit을 사용한 `git apply` 방식으로 패치 적용
- Git 저장소가 없는 경우 임시 저장소 자동 생성
- 재귀적 디렉토리 탐색 지원
- 여러 patch 파일 확장자 지원 (.patch, .diff 등)

## 사용 방법

### 기본 사용

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.github.h2seo</groupId>
            <artifactId>rewrite-post-maven-plugin</artifactId>
            <version>1.0.0</version>
        </plugin>
    </plugins>
</build>
```

### 설정 옵션

```xml
<plugin>
    <groupId>com.github.h2seo</groupId>
    <artifactId>rewrite-post-maven-plugin</artifactId>
    <version>1.0.0</version>
    <configuration>
        <!-- 패치 파일이 있는 디렉토리 (기본값: ${project.basedir}/patches) -->
        <patchDirectory>${project.basedir}/patches</patchDirectory>
        
        <!-- 패치를 적용할 Git 루트 디렉토리 (기본값: ${project.basedir}) -->
        <gitRootDirectory>${project.basedir}</gitRootDirectory>
        
        <!-- 오류 발생 시 실패 처리 (기본값: true) -->
        <failOnError>true</failOnError>
        
        <!-- 처리할 패치 파일 확장자 (쉼표로 구분, 기본값: patch,diff) -->
        <extensions>patch,diff</extensions>
    </configuration>
</plugin>
```

### 명령줄에서 실행

```bash
mvn rewrite-post:patch
```

또는 설정 옵션과 함께:

```bash
# 커스텀 패치 디렉토리 지정
mvn rewrite-post:patch -Drewrite-post.patchDirectory=./custom-patches

# 다른 Git 저장소에 패치 적용
mvn rewrite-post:patch -Drewrite-post.gitRootDirectory=/path/to/other/repo

# 모든 옵션 지정
mvn rewrite-post:patch \
    -Drewrite-post.patchDirectory=./patches \
    -Drewrite-post.gitRootDirectory=${project.basedir} \
    -Drewrite-post.extensions=patch,diff,patchfile
```

## 프로젝트 구조 예시

### 기본 구조 (기본 설정 사용)

```
my-project/
├── pom.xml
├── patches/                          # 기본 패치 디렉토리
│   ├── fix-security-issue.patch
│   ├── add-logging.diff
│   └── hotfixes/
│       ├── critical-fix.patch
│       └── performance-improvement.diff
├── .git/                             # Git 저장소 (기본 gitRootDirectory)
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── example/
│                   └── App.java
└── README.md
```

### 커스텀 패치 디렉토리 사용

```
my-project/
├── pom.xml
├── custom-patches/                   # 커스텀 패치 디렉토리
│   ├── upstream/
│   │   └── upstream-fix.patch
│   └── local/
│       └── local-modification.diff
├── .git/
└── src/
    └── ...
```

### 서브모듈이나 다른 저장소에 패치 적용

```
parent-project/
├── pom.xml
├── patches/
│   └── submodule-fix.patch
├── submodule/                        # 별도의 Git 저장소
│   ├── .git/
│   └── src/
└── main-project/                     # 메인 Git 저장소
    ├── .git/
    └── src/
```

## 요구사항

- Java 1.8 이상
- Maven 3.6 이상
- JGit 라이브러리 (의존성에 포함됨)

## 빌드

```bash
mvn clean install
```

## 라이선스

이 프로젝트는 자유롭게 사용할 수 있습니다.

