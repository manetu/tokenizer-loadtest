# tokenizer-loadtest

The Manetu tokenizer-loadtest is a command-line tool to measure tokenizer performance

## Installing

### Prerequisites

- JDK (tested with JDK22)
- make

## Building

```
$ make
```

## Usage

```shell
Usage: manetu-tokenizer-loadtest [global-options] action [options]

Actions:
 - tokenize: Generates random values to tokenize based on an input set of vaults
 - translate: Translates values based on an input of previously tokenized data

 use 'action -h' for action specific help

Global Options:
  -h, --help
  -v, --version                 Print the version and exit
  -u, --url URL                 The connection URL
      --[no-]progress    true   Enable/disable progress output (default: enabled)
  -l, --log-level LEVEL  :info  Select the logging verbosity level from: [trace, debug, info, error]
  -c, --concurrency NUM  16     The number of parallel jobs to run
  -d, --driver DRIVER    :grpc  Select the driver from: [grpc]

```

### Tokenize

```shell
Usage: manetu-tokenizer-loadtest [global-options] tokenize [options] <vault-list>

Generates tokens from synthetic data based on input vaults.  <vault-list> is a path to a file
containing a list of vault MRNs, one per line.

Options:
  -h, --help
      --value-min MIN         4    The minimum size of values to generate
      --value-max MAX         32   The maximum size of values to generate
  -t, --tokens-per-job COUNT  1    The number of tokens per job to generate
  -j, --jobs JOBS             100  The number of jobs to generate

Global Options:
  -h, --help
  -v, --version                 Print the version and exit
  -u, --url URL                 The connection URL
      --[no-]progress    true   Enable/disable progress output (default: enabled)
  -l, --log-level LEVEL  :info  Select the logging verbosity level from: [trace, debug, info, error]
  -c, --concurrency NUM  16     The number of parallel jobs to run
  -d, --driver DRIVER    :grpc  Select the driver from: [grpc]
```

## Running on Kubernetes

The following instructions allow you to deploy this tool into a Kubernetes instance.  You would typically use the Kubernetes option to run the tool in close proximity to a Manetu instance running in the same cluster, though this is not strictly required.

Manetu hosts a Docker-based version of this tool on Dockerhub:

[https://hub.docker.com/repository/docker/manetuops/tokenizer-loadtest/general](https://hub.docker.com/repository/docker/manetuops/tokenizer-loadtest/general)

We will use this to deploy into Kubernetes.

### Setup

#### Credentials

You must inject a Personal Access Token to your Manetu instance as a Kubernetes [Secret](https://kubernetes.io/docs/concepts/configuration/secret/) into your cluster for the tool to use, like so:

```shell
kubectl create secret generic manetu-tokenizer-loadtest --from-literal=MANETU_TOKEN=<your token>
```

#### Vault data

You must also deploy the list of vault MRNs you wish to use as a Kubernetes [ConfigMap](https://kubernetes.io/docs/concepts/configuration/configmap/).  The ConfigMap should have a single binding named 'mrns.list' which we will use to inject the file into our deployment in the next step.

##### Example

``` shell
kubectl create configmap manetu-tokenizer-loadtest --from-file=mrns.list=examples/vaults.list
```

### Launching the test

Next, we can define a Kubernetes [Job](https://kubernetes.io/docs/concepts/workloads/controllers/job/) that leverages our secret/configmap like so:

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: manetu-tokenizer-loadtest
spec:
  template:
    spec:
      restartPolicy: Never
      containers:
      - name: tokenizer-loadtest
        image: manetuops/tokenizer-loadtest:latest
        imagePullPolicy: Always
        args:
          - "-u"
          - "https://ingress.manetu-platform"
          - "--insecure"
          - "-c"
          - "600"
          - "tokenize"
          - "-j"
          - "100000"
          - "/etc/manetu/loadtest/mrns.list"
        envFrom:
          - secretRef:
              name: manetu-tokenizer-loadtest
        volumeMounts:
          - name: data
            mountPath: "/etc/manetu/loadtest"
            readOnly: true
      volumes:
        - name: data
          configMap:
            name: manetu-tokenizer-loadtest
```

For convenience, this file is available in this repository as [kubernetes/job.yaml](./kubernetes/job.yaml).  You may apply this like so:

```shell
kubectl apply -f kubernetes/job.yaml
```

### Obtaining results

Once the job is completed, you may use 'kubectl logs' to obtain the test results.  First, obtain the name of the pod, like so:

```shell
$ kubectl get pod
NAME                              READY   STATUS    RESTARTS   AGE
manetu-tokenizer-loadtest-p9tg6   1/1     Running   0          5s
```

Then, query for the job logs, like so:

```shell
$ kubectl logs -f manetu-tokenizer-loadtest-p9tg6
2024-10-24T22:13:22.561Z INFO processing 100000 records
100000/100000   100% [==================================================]  ETA: 00:0058
|-----------+----------+------+-------+--------+-------+--------+--------+---------+----------------+---------|
| Successes | Failures |  Min |  Mean | Stddev |  P50  |   P90  |   P99  |   Max   | Total Duration |   Rate  |
|-----------+----------+------+-------+--------+-------+--------+--------+---------+----------------+---------|
| 100000.0  | 0.0      | 6.05 | 74.98 | 79.11  | 63.98 | 108.82 | 214.01 | 1266.95 | 20796.25       | 4808.56 |
|-----------+----------+------+-------+--------+-------+--------+--------+---------+----------------+---------|
```

> Tip: If the job is experiencing errors, you may set LOG_LEVEL to 'trace' to diagnose the problem.
