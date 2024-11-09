;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.tokenizer-loadtest.actions.tokenize.core
  (:require [clojure.java.io :as io]
            [clojure.core.async :refer [<!!] :as async]
            [taoensso.timbre :as log]
            [promesa.core :as p]
            [crypto.random]
            [manetu.tokenizer-loadtest.core :as core]
            [manetu.tokenizer-loadtest.utils :refer [async-map file->lines]]
            [manetu.tokenizer-loadtest.driver.api :as driver.api]))

(defn generate-values
  [{:keys [tokens-per-job value-min] :as ctx} mrn]
  {:mrn mrn :values (repeatedly tokens-per-job #(crypto.random/bytes value-min))})

(defn job-exec
  [{:keys [driver token-type] :as ctx} {:keys [mrn values] :as record}]
  (-> (driver.api/tokenize driver {:mrn mrn :type token-type} values)
      (p/then (fn [tokens]
                (log/debug "tokens:" tokens)
                (assoc record :tokens tokens)))))

(defn save-output
  [{:keys [output concurrency token-type]} mux]
  (log/debug "saving tokens to:" output)
  (let [ch (async/chan (* 4 concurrency))]
    (async/tap mux ch)
    (p/create
     (fn [resolve reject]
       (async/thread
         (with-open [wrtr (io/writer output)]
           (loop []
             (when-let [{{:keys [mrn tokens]} :result :as m} (<!! ch)]
               (log/debug "saving:" m)
               (.write wrtr (str (pr-str {:mrn mrn :context-embedded? (not= token-type :EPHEMERAL) :tokens tokens}) "\n"))
               (recur)))
           (resolve true)))))))

(defn exec [{:keys [jobs output] :as ctx} vault-list]
  (log/debug "using vault-list:" vault-list)
  (let [vaults (file->lines vault-list)]
    (log/debug "ctx:" ctx)
    (log/trace "found vaults:" vaults)
    @(->> (async/to-chan! (take jobs (cycle vaults)))
          (async-map (partial generate-values ctx))
          (core/process ctx (-> {:nr-records jobs :exec job-exec}
                                (cond-> (some? output) (assoc :post-exec (partial save-output ctx))))))))
