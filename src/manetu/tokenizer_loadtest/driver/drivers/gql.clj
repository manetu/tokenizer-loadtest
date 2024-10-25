;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.tokenizer-loadtest.driver.drivers.gql
  (:require [promesa.core :as p]
            [taoensso.timbre :as log]
            [cheshire.core :as json]
            [graphql-query.core :refer [graphql-query]]
            [manetu.tokenizer-loadtest.driver.api :as api]
            [manetu.tokenizer-loadtest.utils :refer [http-post] :as utils]))

(defn gql-post
  [{:keys [url insecure token] :as ctx} body]
  (log/trace "GQL: post:" body)
  (-> (http-post url
                 {:insecure? insecure
                  :basic-auth ["" token]
                  :headers {"content-type" "application/json"
                            "accepts" "application/json"}
                  :body (json/generate-string body)})
      (p/then (fn [{{:strs [data errors]} :body :as r}]
                (log/trace "result:" r "data:" data "errors:" errors)
                (if (some? errors)
                  (p/rejected (ex-info "graphql error" r))
                  data)))))

(defn -tokenize
  [ctx mrn values]
  (log/trace "GQL: tokenize:" mrn values)
  (-> (gql-post ctx
                {:query (graphql-query
                          {:operation {:operation/type :mutation
                                       :operation/name "tokenize"}
                           :queries [[:tokenize {:vault_mrn mrn :values (mapv (fn [value] {:value (utils/->b64 value)}) values)}
                                      [:value]]]})})
      (p/then (fn [data]
                (let [values (map :value (:tokenize data))]
                  {:mrn mrn :values values})))))

(defn -translate [ctx mrn tokens]
  (log/trace "GQL: translate:" mrn tokens)
  (p/rejected (ex-info "not-implemented" {})))

(defrecord GraphQLDriver [ctx]
  api/LoadDriver
  (tokenize [_ mrn values]
    (-tokenize ctx mrn values))
  (translate [_ mrn tokens]
    (-translate ctx mrn tokens)))

(defn create
  [ctx]
  (GraphQLDriver. (update ctx :url #(str % "/graphql"))))
