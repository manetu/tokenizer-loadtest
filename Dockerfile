FROM ubuntu:24.10 as base

WORKDIR /src

RUN \
  set -eux \
  && apt-get update \
  && apt-get install -y --no-install-suggests \
        openjdk-22-jdk \
        make \
        wget

FROM base as builder

COPY project.clj .
COPY lein .
RUN ./lein deps

COPY . .
RUN make bin

FROM base as runtime

COPY --from=builder /src/target/uberjar/app.jar /usr/local/

ENTRYPOINT ["java", "-jar", "/usr/local/app.jar"]
