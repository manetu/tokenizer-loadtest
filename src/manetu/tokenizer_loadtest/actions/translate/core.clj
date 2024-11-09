;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.tokenizer-loadtest.actions.translate.core
  (:require [clojure.core.async :refer [<!!] :as async]
            [taoensso.timbre :as log]
            [promesa.core :as p]
            [crypto.random]
            [manetu.tokenizer-loadtest.core :as core]
            [manetu.tokenizer-loadtest.utils :refer [file->lines]]
            [manetu.tokenizer-loadtest.driver.api :as driver.api]))

(defn job-exec
  [{:keys [driver] :as ctx} {:keys [mrn context-embedded? tokens] :as record}]
  (-> (driver.api/translate driver {:mrn mrn :context-embedded? context-embedded?} tokens)
      (p/then (fn [values]
                (log/debug "values:" values)
                (assoc record :values values)))))

(defn exec [{:keys [jobs] :as ctx} tokens-list]
  (log/debug "using tokens-list:" tokens-list)
  (let [tokens (->> (file->lines tokens-list)
                    (map read-string))]
    (log/debug "ctx:" ctx)
    (log/trace "found tokens:" tokens)
    @(->> (async/to-chan! (take jobs (cycle tokens)))
          (core/process ctx (-> {:nr-records jobs :exec job-exec})))))
