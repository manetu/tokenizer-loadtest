;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.tokenizer-loadtest.utils
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [buddy.core.codecs :refer [str->bytes bytes->str bytes->b64 b64->bytes]]))

(defn prep-usage [msg] (->> msg flatten (string/join \newline)))

(defn exit [status msg & args]
  (do
    (apply println msg args)
    status))

(defn version [] (str "manetu-tokenizer-loadtest version: v" (System/getProperty "tokenizer-loadtest.version")))

(defn async-xform [xform in]
  (let [out (async/chan 32 xform)]
    (async/pipe in out)
    out))

(defn async-pipe [out in]
  (async/pipe in out))

(defn async-map [f input-ch]
  (async-xform (map f) input-ch))

(defn b64-> [x]
  (-> x str->bytes b64->bytes))

(defn ->b64 [x]
  (-> x bytes->b64 bytes->str))

(defn http-post
  [url args]
  (log/debug "http request:" url args)
  (p/create
    (fn [resolve reject]
      (http/post url args
                 (fn [{:keys [error status] :as r}]
                   (log/trace "r:" r)
                   (cond
                     (some? error)
                     (reject error)

                     (> status 299)
                     (reject (ex-info "bad status" r))

                     :default
                     (try
                       (resolve (update r :body json/parse-string))
                       (catch Exception ex
                         (log/error ex)
                         (reject ex)))))))))