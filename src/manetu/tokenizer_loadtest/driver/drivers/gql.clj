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
  [ctx {:keys [mrn type] :as params} values]
  (log/trace "GQL: tokenize:" params values)
  (-> (gql-post ctx
                {:query (graphql-query
                         {:operation {:operation/type :mutation
                                      :operation/name "tokenize"}
                          :queries [[:tokenize (-> {:vault_mrn mrn
                                                    :values (mapv (fn [value] {:value (utils/->b64 value)}) values)}
                                                   (cond-> (not= type :EPHEMERAL) (assoc :type type)))
                                     [:value]]]})})
      (p/then (fn [{:strs [tokenize] :as data}]
                (map #(get % "value") tokenize)))))

(defn -translate
  [ctx {:keys [mrn context-embedded?]} tokens]
  (log/trace "GQL: translate:" mrn tokens)
  (-> (gql-post ctx
                {:query (graphql-query
                         {:queries [[:translate_tokens (-> {:tokens (mapv (fn [value] {:value value}) tokens)}
                                                           (cond-> (not context-embedded?) (assoc :vault_mrn mrn)))
                                     [:value]]]})})
      (p/then (fn [{:strs [translate_tokens] :as data}]
                (map #(get % "value") translate_tokens)))))

(defrecord GraphQLDriver [ctx]
  api/LoadDriver
  (tokenize [_ params values]
    (-tokenize ctx params values))
  (translate [_ params tokens]
    (-translate ctx params tokens)))

(defn create
  [ctx]
  (GraphQLDriver. (update ctx :url #(str % "/graphql"))))
