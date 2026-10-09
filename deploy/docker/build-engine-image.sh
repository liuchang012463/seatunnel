#!/usr/bin/env bash
#
# 构建带 JDBC 驱动隔离能力的 SeaTunnel 3.0.0 引擎镜像。
#
# 产物镜像基于 apache/seatunnel:3.0.0，只覆盖两个 jar（connector-jdbc、seatunnel-starter），
# 因此基础镜像里的第三方驱动、JVM、目录结构与已部署栈完全一致。
#
# 用法：deploy/docker/build-engine-image.sh [镜像标签]
#   默认标签：seatunnel:3.0.0-driver-isolation
#   可用 BASE_IMAGE 指定基础镜像，默认 apache/seatunnel:3.0.0
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
IMAGE_TAG="${1:-seatunnel:3.0.0-driver-isolation}"
BASE_IMAGE="${BASE_IMAGE:-apache/seatunnel:3.0.0}"
VERSION="3.0.0"
BUILD_DIR="${REPO_ROOT}/target/engine-image"

cd "${REPO_ROOT}"

echo "==> 构建引擎产物 (revision=${VERSION})"
./mvnw -B -pl seatunnel-connectors-v2/connector-jdbc,seatunnel-core/seatunnel-starter -am \
    -DskipTests -Dskip.ui=true -Drevision="${VERSION}" \
    -Dspotless.check.skip=true -Dcheckstyle.skip=true -Drat.skip=true package

echo "==> 准备镜像构建上下文"
rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}"
cp "${REPO_ROOT}/seatunnel-core/seatunnel-starter/target/seatunnel-starter.jar" "${BUILD_DIR}/"

CONNECTOR_JAR="$(ls "${REPO_ROOT}"/seatunnel-connectors-v2/connector-jdbc/target/connector-jdbc-"${VERSION}"*.jar \
    | grep -v '/original-' | head -1)"
cp "${CONNECTOR_JAR}" "${BUILD_DIR}/connector-jdbc-${VERSION}.jar"

echo "==> 构建镜像 ${IMAGE_TAG} (基础镜像 ${BASE_IMAGE})"
docker build \
    --build-arg BASE_IMAGE="${BASE_IMAGE}" \
    -t "${IMAGE_TAG}" \
    -f "${REPO_ROOT}/deploy/docker/Dockerfile.engine" \
    "${BUILD_DIR}"

echo "==> 完成：${IMAGE_TAG}"
