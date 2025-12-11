#!/bin/bash

################################################################################
# groupId, package 경로, 문서 내용 변경 스크립트
#
# 이 스크립트는 다음을 수행합니다:
# 1. pom.xml의 groupId 변경
# 2. Java 소스 파일의 package 경로 변경
# 3. 디렉토리 구조 변경 (package 경로에 맞게)
# 4. README.md 및 기타 문서의 groupId 참조 변경
# 5. 테스트 파일의 package 경로 변경
#
# 사용법:
#   ./change-groupid-package.sh <new-groupId> <new-package-path>
#
# 예시:
#   ./change-groupid-package.sh com.example.myplugin com.example.myplugin.rewrite
################################################################################

set -e  # 오류 발생 시 스크립트 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 현재 값
OLD_GROUP_ID="com.github.h2seo"
OLD_PACKAGE="com.github.h2seo.plugins"
OLD_PACKAGE_DIR="com/github/h2seo/rewrite"

# 입력 검증
if [ $# -ne 2 ]; then
    echo -e "${RED}오류: 인자가 부족합니다.${NC}"
    echo "사용법: $0 <new-groupId> <new-package-path>"
    echo ""
    echo "예시:"
    echo "  $0 com.example.myplugin com.example.myplugin.rewrite"
    exit 1
fi

NEW_GROUP_ID="$1"
NEW_PACKAGE="$2"
NEW_PACKAGE_DIR=$(echo "$NEW_PACKAGE" | tr '.' '/')

# 현재 디렉토리 확인
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}groupId 및 package 경로 변경 스크립트${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}현재 설정:${NC}"
echo "  GroupId:  $OLD_GROUP_ID"
echo "  Package:  $OLD_PACKAGE"
echo ""
echo -e "${YELLOW}새로운 설정:${NC}"
echo "  GroupId:  $NEW_GROUP_ID"
echo "  Package:  $NEW_PACKAGE"
echo "  Package Dir: $NEW_PACKAGE_DIR"
echo ""
echo -e "${YELLOW}변경될 파일들:${NC}"
echo "  - pom.xml"
echo "  - README.md"
echo "  - src/main/java/${OLD_PACKAGE_DIR}/*.java"
echo "  - src/test/java/${OLD_PACKAGE_DIR}/*.java"
echo "  - 디렉토리 구조: ${OLD_PACKAGE_DIR} -> ${NEW_PACKAGE_DIR}"
echo ""

# 확인 요청
read -p "계속하시겠습니까? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}취소되었습니다.${NC}"
    exit 0
fi

# 백업 디렉토리 생성
BACKUP_DIR="backup_$(date +%Y%m%d_%H%M%S)"
echo -e "${BLUE}백업 생성 중: ${BACKUP_DIR}${NC}"
mkdir -p "$BACKUP_DIR"
cp -r src pom.xml README.md "$BACKUP_DIR/" 2>/dev/null || true
echo -e "${GREEN}백업 완료${NC}"
echo ""

# 1. pom.xml의 groupId 변경
echo -e "${BLUE}[1/5] pom.xml의 groupId 변경 중...${NC}"
if [ -f "pom.xml" ]; then
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        sed -i '' "s|<groupId>${OLD_GROUP_ID}</groupId>|<groupId>${NEW_GROUP_ID}</groupId>|g" pom.xml
    else
        # Linux
        sed -i "s|<groupId>${OLD_GROUP_ID}</groupId>|<groupId>${NEW_GROUP_ID}</groupId>|g" pom.xml
    fi
    echo -e "${GREEN}  ✓ pom.xml 변경 완료${NC}"
else
    echo -e "${RED}  ✗ pom.xml을 찾을 수 없습니다${NC}"
fi

# 2. README.md의 groupId 변경
echo -e "${BLUE}[2/5] README.md의 groupId 변경 중...${NC}"
if [ -f "README.md" ]; then
    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "s|<groupId>${OLD_GROUP_ID}</groupId>|<groupId>${NEW_GROUP_ID}</groupId>|g" README.md
    else
        sed -i "s|<groupId>${OLD_GROUP_ID}</groupId>|<groupId>${NEW_GROUP_ID}</groupId>|g" README.md
    fi
    echo -e "${GREEN}  ✓ README.md 변경 완료${NC}"
else
    echo -e "${YELLOW}  ⚠ README.md을 찾을 수 없습니다${NC}"
fi

# 3. Java 소스 파일의 package 선언 변경
echo -e "${BLUE}[3/5] Java 소스 파일의 package 선언 변경 중...${NC}"
OLD_PACKAGE_ESCAPED=$(echo "$OLD_PACKAGE" | sed 's/\./\\./g')
NEW_PACKAGE_ESCAPED=$(echo "$NEW_PACKAGE" | sed 's/\./\\./g')

find src -name "*.java" -type f | while read -r file; do
    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "s|^package ${OLD_PACKAGE_ESCAPED};|package ${NEW_PACKAGE};|g" "$file"
    else
        sed -i "s|^package ${OLD_PACKAGE_ESCAPED};|package ${NEW_PACKAGE};|g" "$file"
    fi
    echo -e "${GREEN}  ✓ $file 변경 완료${NC}"
done

# 4. 디렉토리 구조 변경
echo -e "${BLUE}[4/5] 디렉토리 구조 변경 중...${NC}"
OLD_MAIN_DIR="src/main/java/${OLD_PACKAGE_DIR}"
OLD_TEST_DIR="src/test/java/${OLD_PACKAGE_DIR}"
NEW_MAIN_DIR="src/main/java/${NEW_PACKAGE_DIR}"
NEW_TEST_DIR="src/test/java/${NEW_PACKAGE_DIR}"

# 메인 소스 디렉토리 변경
if [ -d "$OLD_MAIN_DIR" ]; then
    mkdir -p "$(dirname "$NEW_MAIN_DIR")"
    mv "$OLD_MAIN_DIR" "$NEW_MAIN_DIR"
    echo -e "${GREEN}  ✓ 메인 소스 디렉토리 이동: ${OLD_MAIN_DIR} -> ${NEW_MAIN_DIR}${NC}"
    
    # 빈 상위 디렉토리 정리
    old_parent=$(dirname "$OLD_MAIN_DIR")
    while [ "$old_parent" != "src/main/java" ] && [ -d "$old_parent" ]; do
        if [ -z "$(ls -A "$old_parent" 2>/dev/null)" ]; then
            rmdir "$old_parent" 2>/dev/null || true
            old_parent=$(dirname "$old_parent")
        else
            break
        fi
    done
else
    echo -e "${YELLOW}  ⚠ 메인 소스 디렉토리를 찾을 수 없습니다: ${OLD_MAIN_DIR}${NC}"
fi

# 테스트 소스 디렉토리 변경
if [ -d "$OLD_TEST_DIR" ]; then
    mkdir -p "$(dirname "$NEW_TEST_DIR")"
    mv "$OLD_TEST_DIR" "$NEW_TEST_DIR"
    echo -e "${GREEN}  ✓ 테스트 소스 디렉토리 이동: ${OLD_TEST_DIR} -> ${NEW_TEST_DIR}${NC}"
    
    # 빈 상위 디렉토리 정리
    old_parent=$(dirname "$OLD_TEST_DIR")
    while [ "$old_parent" != "src/test/java" ] && [ -d "$old_parent" ]; do
        if [ -z "$(ls -A "$old_parent" 2>/dev/null)" ]; then
            rmdir "$old_parent" 2>/dev/null || true
            old_parent=$(dirname "$old_parent")
        else
            break
        fi
    done
else
    echo -e "${YELLOW}  ⚠ 테스트 소스 디렉토리를 찾을 수 없습니다: ${OLD_TEST_DIR}${NC}"
fi

# 5. 기타 파일에서 package 참조 변경 (주석, 문서 등)
echo -e "${BLUE}[5/5] 기타 파일에서 package 참조 변경 중...${NC}"
find . -type f \( -name "*.java" -o -name "*.md" -o -name "*.xml" -o -name "*.txt" \) ! -path "./target/*" ! -path "./.git/*" ! -path "./${BACKUP_DIR}/*" | while read -r file; do
    if grep -q "$OLD_PACKAGE" "$file" 2>/dev/null; then
        if [[ "$OSTYPE" == "darwin"* ]]; then
            sed -i '' "s|${OLD_PACKAGE}|${NEW_PACKAGE}|g" "$file"
        else
            sed -i "s|${OLD_PACKAGE}|${NEW_PACKAGE}|g" "$file"
        fi
        echo -e "${GREEN}  ✓ $file 변경 완료${NC}"
    fi
done

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}변경 완료!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${YELLOW}변경 사항 요약:${NC}"
echo "  GroupId:  ${OLD_GROUP_ID} -> ${NEW_GROUP_ID}"
echo "  Package:  ${OLD_PACKAGE} -> ${NEW_PACKAGE}"
echo ""
echo -e "${YELLOW}백업 위치:${NC}"
echo "  ${BACKUP_DIR}"
echo ""
echo -e "${BLUE}다음 단계:${NC}"
echo "  1. 변경 사항 확인: git diff"
echo "  2. 빌드 테스트: mvn clean compile"
echo "  3. 테스트 실행: mvn test"
echo "  4. 문제가 없으면 백업 삭제: rm -rf ${BACKUP_DIR}"
echo ""

