;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.tokenizer-loadtest.driver.drivers.rest
  (:require [cheshire.core :as json]
            [promesa.core :as p]
            [ring.util.codec :refer [url-encode]]
            [manetu.tokenizer-loadtest.driver.api :as api]
            [manetu.tokenizer-loadtest.utils :refer [->b64 b64-> http-post] :as utils]))

(defn tokenize! [{:keys [url insecure token] :or {insecure false} :as ctx} mode mrn values]
  (-> (http-post (str url "/api/v1/token/" (url-encode mrn) "/bulk")
                 {:insecure? insecure
                  :basic-auth ["" token]
                  :query-params {:mode mode}
                  :headers {"content-type" "application/json"
                            "accepts" "application/json"}
                  :body (json/generate-string (map (fn [value] {:value (->b64 value)}) values))})
      (p/then (fn [{:keys [body] :as r}]
                (map (comp b64-> :value) body)))))

(defrecord RestDriver [ctx]
  api/LoadDriver
  (tokenize [_ mrn values]
    (tokenize! ctx "create" mrn values))
  (translate [_ mrn tokens]
    (tokenize! ctx "translate" mrn tokens)))

(defn create
  [ctx]
  (RestDriver. ctx))
