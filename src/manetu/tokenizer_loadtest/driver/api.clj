;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.tokenizer-loadtest.driver.api)

(defprotocol LoadDriver
  (tokenize [this mrn values])
  (translate [this mrn tokens]))
