;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.tokenizer-loadtest.main
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [taoensso.timbre :as log]
            [environ.core :refer [env]]
            [manetu.tokenizer-loadtest.driver.core :as driver.core]
            [manetu.tokenizer-loadtest.actions.tokenize.cli :as tokenize]
            [manetu.tokenizer-loadtest.utils :refer [prep-usage exit version]])
  (:gen-class))

(defn set-logging
  [level]
  (log/set-config!
   {:level level
    :ns-whitelist  ["manetu.*"]
    :appenders
    {:custom
     {:enabled? true
      :async false
      :fn (fn [{:keys [timestamp_ msg_ level] :as data}]
            (println (force timestamp_) (string/upper-case (name level)) (force msg_)))}}}))

(def log-levels #{:trace :debug :info :error})
(defn print-loglevels []
  (str "[" (string/join ", " (map name log-levels)) "]"))
(def loglevel-description
  (str "Select the logging verbosity level from: " (print-loglevels)))

(def drivers (into #{} (keys driver.core/driver-map)))
(defn print-drivers []
  (str "[" (string/join ", " (map name drivers)) "]"))
(def driver-description
  (str "Select the driver from: " (print-drivers)))

(def options-spec
  [["-h" "--help"]
   ["-v" "--version" "Print the version and exit"]
   ["-u" "--url URL" "The connection URL"]
   [nil "--insecure" "Disable TLS host checking"]
   [nil "--[no-]progress" "Enable/disable progress output (default: enabled)"
    :default true]
   ["-l" "--log-level LEVEL" loglevel-description
    :default :info
    :parse-fn keyword
    :validate [log-levels (str "Must be one of " (print-loglevels))]]
   ["-c" "--concurrency NUM" "The number of parallel jobs to run"
    :default 16
    :parse-fn #(Integer/parseInt %)
    :validate [pos? "Must be a positive integer"]]
   ["-d" "--driver DRIVER" driver-description
    :default :gql
    :parse-fn keyword
    :validate [drivers (str "Must be one of " (print-drivers))]]])

(defn usage [options-summary]
  (prep-usage [(version)
               ""
               "Usage: manetu-tokenizer-loadtest [global-options] action [options]"
               ""
               "Actions:"
               " - tokenize: Generates random values to tokenize based on an input set of vaults"
               " - translate: Translates values based on an input of previously tokenized data"
               ""
               " use 'action -h' for action specific help"
               ""
               "Global Options:"
               options-summary]))

(defn -app
  [& args]
  (let [{{:keys [help url driver log-level] :as global-options} :options
         global-summary :summary
         :keys [arguments errors]}
        (parse-opts args options-spec :in-order true)]
    (cond

      help
      (exit 0 (usage global-summary))

      (not= errors nil)
      (exit -1 "Error: " (string/join errors))

      (:version global-options)
      (exit 0 (version))

      (not (some? url))
      (exit -1 "Must set --url")

      (not (some? (env :manetu-token)))
      (exit -1 "Must set MANETU_TOKEN")

      :else
      (let [options (driver.core/init (assoc global-options :token (env :manetu-token)))]
        (set-logging log-level)
        (try
          (case (first arguments)
            "tokenize" (tokenize/exec global-summary options arguments)
            (exit -1 (usage global-summary)))
          (catch Exception ex
            (exit -1 (str "ERROR: " (ex-message ex)))))))))

(defn -main
  [& args]
  (let [code (apply -app args)]
    (shutdown-agents)
    (System/exit code)))
