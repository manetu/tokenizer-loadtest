;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.tokenizer-loadtest.driver.core
  (:require [manetu.tokenizer-loadtest.driver.drivers.gql :as gql]
            [manetu.tokenizer-loadtest.driver.drivers.rest :as rest]
            [manetu.tokenizer-loadtest.driver.drivers.null :as null]))

(def driver-map
  {:gql gql/create
   :rest rest/create
   :null null/create})

(defn init [{:keys [driver] :as options}]
  (if-let [c (get driver-map driver)]
    (assoc options :driver (c options))
    (throw (ex-info "unknown driver" {:type driver}))))
