;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.tokenizer-loadtest.actions.translate.cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [taoensso.timbre :as log]
            [manetu.tokenizer-loadtest.utils :refer [prep-usage exit version]]
            [manetu.tokenizer-loadtest.actions.translate.core :as core]))

(def options-spec
  [["-h" "--help"]
   ["-j" "--jobs JOBS" "The number of jobs to generate"
    :default 100
    :parse-fn parse-long
    :validate [pos? "Must be a positive integer"]]])

(defn usage
  [global-summary local-summary]
  (prep-usage [(version)
               ""
               (str "Usage: manetu-tokenizer-loadtest [global-options] translate [options] <token-list>")
               ""
               "Translates tokens from a previous 'tokenize' run.  <token-list> is a path to a file"
               "containing token data captured with 'tokenize --output'."
               ""
               "Options:"
               local-summary
               ""
               "Global Options:"
               global-summary]))

(defn exec
  [global-summary global-options args]
  (let [{{:keys [help] :as local-options} :options
         :keys [arguments errors summary]} (parse-opts args options-spec)]
    (cond

      help
      (exit 0 (usage global-summary summary))

      (not= errors nil)
      (exit -1 "Error: " (string/join errors))

      :else
      (let [ctx (merge global-options local-options)]
        (log/debug "translate:" ctx)
        (core/exec ctx (second arguments))))))
