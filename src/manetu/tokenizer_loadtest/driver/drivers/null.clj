;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.tokenizer-loadtest.driver.drivers.null
  (:require [taoensso.timbre :as log]
            [promesa.core :as p]
            [manetu.tokenizer-loadtest.driver.api :as api]))

(defn -tokenize [mrn values]
  (log/trace "NULL: tokenize:" mrn values)
  (p/resolved {:mrn mrn :tokens values}))

(defn -translate [mrn tokens]
  (log/trace "NULL: translate:" mrn tokens)
  (p/resolved {:mrn mrn :values tokens}))

(defrecord NullDriver []
  api/LoadDriver
  (tokenize [_ mrn values]
    (-tokenize mrn values))
  (translate [_ mrn tokens]
    (-translate mrn tokens)))

(defn create
  [ctx]
  (NullDriver.))
