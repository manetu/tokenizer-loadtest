;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.tokenizer-loadtest.actions.tokenize.cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [taoensso.timbre :as log]
            [manetu.tokenizer-loadtest.utils :refer [prep-usage exit version]]
            [manetu.tokenizer-loadtest.actions.tokenize.core :as core]))

(def token-types #{:ephemeral :persistent})
(defn print-tokentypes []
  (str "[" (string/join ", " (map name token-types)) "]"))
(def tokentype-description
  (str "Select the token type from: " (print-tokentypes)))

(def options-spec
  [["-h" "--help"]
   [nil "--value-min MIN" "The minimum size of values to generate"
    :default 4
    :parse-fn parse-long
    :validate [pos? "Must be a positive integer"]]
   [nil "--value-max MAX" "The maximum size of values to generate"
    :default 32
    :parse-fn parse-long
    :validate [pos? "Must be a positive integer"]]
   ["-t" "--tokens-per-job COUNT" "The number of tokens per job to generate"
    :default 1
    :parse-fn parse-long
    :validate [pos? "Must be a positive integer"]]
   ["-j" "--jobs JOBS" "The number of jobs to generate"
    :default 100
    :parse-fn parse-long
    :validate [pos? "Must be a positive integer"]]
   [nil "--token-type TYPE" tokentype-description
    :default "ephemeral"
    :parse-fn keyword
    :validate [token-types (str "Must be one of " (print-tokentypes))]]
   ["-o" "--output FILE" "The path to a file for saving generated tokens compatible as an input to 'translate'"]])

(defn usage
  [global-summary local-summary]
  (prep-usage [(version)
               ""
               (str "Usage: manetu-tokenizer-loadtest [global-options] tokenize [options] <vault-list>")
               ""
               "Generates tokens from synthetic data based on input vaults.  <vault-list> is a path to a file"
               "containing a list of vault MRNs, one per line."
               ""
               "Options:"
               local-summary
               ""
               "Global Options:"
               global-summary]))

(defn exec
  [global-summary global-options args]
  (let [{{:keys [help token-type] :as local-options} :options
         :keys [arguments errors summary]} (parse-opts args options-spec)]
    (cond

      help
      (exit 0 (usage global-summary summary))

      (not= errors nil)
      (exit -1 "Error: " (string/join errors))

      :else
      (let [ctx (-> (merge global-options local-options)
                    (assoc :token-type (-> token-type name string/upper-case keyword)))]
        (log/debug "tokenize:" ctx)
        (core/exec ctx (second arguments))))))
