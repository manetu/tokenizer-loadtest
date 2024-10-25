;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.tokenizer-loadtest.actions.tokenize.core
  (:require  [clojure.java.io :as io]
             [clojure.core.async :as async]
             [taoensso.timbre :as log]
             [promesa.core :as p]
             [crypto.random]
             [manetu.tokenizer-loadtest.core :as core]
             [manetu.tokenizer-loadtest.utils :refer [async-map]]
             [manetu.tokenizer-loadtest.driver.api :as driver.api]))

(defn file->lines
  "opens the file at 'path' and reads in all lines to a collection"
  [path]
  (with-open [r (-> path
                    (io/input-stream)
                    (io/reader))]
    (doall (line-seq r))))

(defn generate-values
  [{:keys [tokens-per-job value-min] :as ctx} mrn]
  {:mrn mrn :values (repeatedly tokens-per-job #(crypto.random/bytes value-min))})

(defn job-exec
  [{:keys [driver] :as ctx} {:keys [mrn values] :as record}]
  (-> (driver.api/tokenize driver mrn values)
      (p/then (fn [tokens]
                (assoc record :tokens tokens)))))

(defn exec [{:keys [jobs] :as ctx} vault-list]
  (log/debug "using vault-list:" vault-list)
  (let [vaults (file->lines vault-list)]
    (log/debug "found vaults:" vaults)
    @(->> (async/to-chan! (take jobs (cycle vaults)))
          (async-map (partial generate-values ctx))
          (core/process ctx {:nr-records jobs :exec job-exec}))))
