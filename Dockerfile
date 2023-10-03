# This Dockerfile requires BuildKit to be enabled, by setting the environment variable
# DOCKER_BUILDKIT=1
# see https://docs.docker.com/build/buildkit/#getting-started
# We use the ubuntu release name only as otherwise renovate fails to update the tag & both digests
# see https://github.com/camunda/zeebe/pull/14071#discussion_r1311176361
ARG BASE_IMAGE="ubuntu:jammy"
ARG BASE_DIGEST="sha256:74e3aaf45db3327200bcba464bc6f6863118cf3b52cf48a1d3e5038a003ec42e"
ARG JDK_IMAGE="eclipse-temurin:17-jdk-jammy"
ARG JDK_DIGEST="sha256:892d2ba323635bfb5ccdc60bf355612959c3e11fb131ed461d0f6f147ff42c59"

# set to "build" to build zeebe from scratch instead of using a distball
ARG DIST="distball"

### Base image ##
# All package installation, updates, etc., anything with APT should be done here in a single step
# hadolint ignore=DL3006
FROM ${BASE_IMAGE}@${BASE_DIGEST} as base

WORKDIR /

# Upgrade all outdated packages and install missing ones (e.g. locales, tini)
# This breaks reproducibility of builds, but is acceptable to gain access to security patches faster
# than the base image releases
# FYI, installing packages via APT also updates the dpkg files, which are few MBs, but removing or
# caching them could break stuff (like not knowing the package is present) or container scanners
# hadolint ignore=DL3008
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    --mount=type=cache,target=/var/log/apt,sharing=locked \
    apt-get -qq update && \
    apt-get install -yqq --no-install-recommends tini ca-certificates && \
    apt-get upgrade -yqq --no-install-recommends


### Build custom JRE using the base JDK image
# hadolint ignore=DL3006
FROM ${JDK_IMAGE}@${JDK_DIGEST} as jre-build

# Build a custom JRE which will strip down and compress modules to end up with a smaller Java \
# distribution than the official JRE. This will also include useful debugging tools like
# jcmd, jmod, jps, etc., which take little to no space. Anecdotally, compressing modules add around
# 10ms to the start up time, which is negligible considering our application takes ~10s to start up.
# See https://adoptium.net/blog/2021/10/jlink-to-produce-own-runtime/
# hadolint ignore=DL3018
RUN jlink \
     --add-modules ALL-MODULE-PATH \
     --strip-debug \
     --no-man-pages \
     --no-header-files \
     --compress=2 \
     --output /jre && \
   rm -rf /jre/lib/src.zip

### Java base image
FROM base AS java
WORKDIR /

# Inherit from previous build stage
ARG JAVA_HOME=/opt/java/openjdk

# Default to UTF-8 file encoding
ENV LANG='C.UTF-8' LC_ALL='C.UTF-8'

# Setup JAVA_HOME and binaries in the path
ENV JAVA_HOME ${JAVA_HOME}
ENV PATH $JAVA_HOME/bin:$PATH

# Copy JRE from previous build stage
COPY --from=jre-build /jre ${JAVA_HOME}

# https://github.com/docker-library/openjdk/issues/212#issuecomment-420979840
# https://openjdk.java.net/jeps/341
# TL;DR generate some class data sharing for faster load time
RUN java -Xshare:dump;

### Build zeebe from scratch ###
# hadolint ignore=DL3006
FROM java as build
WORKDIR /zeebe
ENV MAVEN_OPTS -XX:MaxRAMPercentage=80
COPY --link . ./
RUN --mount=type=cache,target=/root/.m2,rw \
    ./mvnw -B -am -pl dist package -T1C -D skipChecks -D skipTests && \
    mv dist/target/camunda-zeebe .

### Extract zeebe from distball ###
# hadolint ignore=DL3006
FROM base as distball
WORKDIR /zeebe
ARG DISTBALL="dist/target/camunda-zeebe-*.tar.gz"
COPY --link ${DISTBALL} zeebe.tar.gz

# Remove zbctl from the distribution to reduce CVE related maintenance effort w.r.t to containers
RUN mkdir camunda-zeebe && \
    tar xfvz zeebe.tar.gz --strip 1 -C camunda-zeebe && \
    find . -type f -name 'zbctl*' -delete

### Image containing the zeebe distribution ###
# hadolint ignore=DL3006
FROM ${DIST} as dist

### Application Image ###
# TARGETARCH is provided by buildkit
# https://docs.docker.com/engine/reference/builder/#automatic-platform-args-in-the-global-scope
# hadolint ignore=DL3006
FROM java as app
# leave unset to use the default value at the top of the file
ARG BASE_DIGEST
ARG BASE_IMAGE
ARG VERSION=""
ARG DATE=""
ARG REVISION=""

# OCI labels: https://github.com/opencontainers/image-spec/blob/main/annotations.md
LABEL org.opencontainers.image.base.digest="${BASE_DIGEST}"
LABEL org.opencontainers.image.base.name="docker.io/library/${BASE_IMAGE}"
LABEL org.opencontainers.image.created="${DATE}"
LABEL org.opencontainers.image.authors="zeebe@camunda.com"
LABEL org.opencontainers.image.url="https://zeebe.io"
LABEL org.opencontainers.image.documentation="https://docs.camunda.io/docs/self-managed/zeebe-deployment/"
LABEL org.opencontainers.image.source="https://github.com/camunda/zeebe"
LABEL org.opencontainers.image.version="${VERSION}"
# According to https://github.com/opencontainers/image-spec/blob/main/annotations.md#pre-defined-annotation-keys
# and given we set the base.name and base.digest, we reference the manifest of the base image here
LABEL org.opencontainers.image.ref.name="${BASE_IMAGE}"
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.vendor="Camunda Services GmbH"
LABEL org.opencontainers.image.licenses="(Apache-2.0 AND LicenseRef-Zeebe-Community-1.1)"
LABEL org.opencontainers.image.title="Zeebe"
LABEL org.opencontainers.image.description="Workflow engine for microservice orchestration"

# OpenShift labels: https://docs.openshift.com/container-platform/4.10/openshift_images/create-images.html#defining-image-metadata
LABEL io.openshift.tags="bpmn,orchestration,workflow"
LABEL io.k8s.description="Workflow engine for microservice orchestration"
LABEL io.openshift.non-scalable="false"
LABEL io.openshift.min-memory="512Mi"
LABEL io.openshift.min-cpu="1"

ENV ZB_HOME=/usr/local/zeebe \
    ZEEBE_BROKER_GATEWAY_NETWORK_HOST=0.0.0.0 \
    ZEEBE_STANDALONE_GATEWAY=false \
    ZEEBE_RESTORE=false
ENV PATH "${ZB_HOME}/bin:${PATH}"
# Disable RocksDB runtime check for musl, which launches `ldd` as a shell process
# We know there's no need to check for musl on this image
ENV ROCKSDB_MUSL_LIBC=false

WORKDIR ${ZB_HOME}
EXPOSE 26500 26501 26502
VOLUME /tmp
VOLUME ${ZB_HOME}/data
VOLUME ${ZB_HOME}/logs

RUN groupadd -g 1000 zeebe && \
    adduser -u 1000 zeebe --system --ingroup zeebe && \
    chmod g=u /etc/passwd && \
    # These directories are to be mounted by users, eagerly creating them and setting ownership
    # helps to avoid potential permission issues due to default volume ownership.
    mkdir ${ZB_HOME}/data && \
    mkdir ${ZB_HOME}/logs && \
    chown -R 1000:0 ${ZB_HOME} && \
    chmod -R 0775 ${ZB_HOME}

COPY --link --chown=1000:0 docker/utils/startup.sh /usr/local/bin/startup.sh
COPY --from=dist --chown=1000:0 /zeebe/camunda-zeebe ${ZB_HOME}

ENTRYPOINT ["tini", "--", "/usr/local/bin/startup.sh"]
