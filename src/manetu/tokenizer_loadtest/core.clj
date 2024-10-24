;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.tokenizer-loadtest.core
  (:require [manetu.tokenizer-loadtest.time :as t]
            [medley.core :as m]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [clojure.core.async :refer [>!! <! go go-loop] :as async]
            [progrock.core :as pr]
            [kixi.stats.core :as kixi]
            [doric.core :refer [table]]
            [manetu.tokenizer-loadtest.stats :as stats]))

(defn round2
  "Round a double to the given precision (number of significant digits)"
  [precision ^double d]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* d factor)) factor)))

(defn execute-command
  [ctx f record]
  (log/trace "record:" record)
  (let [start (t/now)]
    @(-> (f ctx record)
         (p/then
          (fn [result]
            {:success true :result result}))
         (p/catch
          (fn [e]
            (log/trace (str "ERROR:" (ex-message e) (ex-data e)))
            {:success false :exception e}))
         (p/then
          (fn [result]
            (let [end (t/now)
                  d (t/duration end start)]
              (log/trace "processed in" d "msecs")
              (assoc result
                     :duration d)))))))

(defn execute-commands
  [{:keys [concurrency] :as ctx} f output-ch input-ch]
  (p/create
   (fn [resolve reject]
     (go
       (log/trace "launching" concurrency "requests")
       (<! (async/pipeline-blocking concurrency
                                    output-ch
                                    (map (partial execute-command ctx f))
                                    input-ch))
       (resolve true)))))

(defn show-progress
  [{:keys [progress concurrency] :as ctx} n mux]
  (when progress
    (let [ch (async/chan (* 4 concurrency))]
      (async/tap mux ch)
      (p/create
       (fn [resolve reject]
         (go-loop [bar (pr/progress-bar n)]
           (if (= (:progress bar) (:total bar))
             (do (pr/print (pr/done bar))
                 (resolve true))
             (do (<! ch)
                 (pr/print bar)
                 (recur (pr/tick bar))))))))))

(defn transduce-promise
  [{:keys [concurrency] :as ctx} n mux xform f]
  (p/create
   (fn [resolve reject]
     (go
       (let [ch (async/chan (* 4 concurrency))]
         (async/tap mux ch)
         (let [result (<! (async/transduce xform f (f) ch))]
           (resolve result)))))))

(defn compute-summary-stats
  [options n mux]
  (-> (transduce-promise options n mux (map :duration) stats/summary)
      (p/then (fn [{:keys [dist] :as summary}]
                (-> summary
                    (dissoc :dist)
                    (merge dist)
                    (as-> $ (m/map-vals #(round2 2 (or % 0)) $)))))))

(defn successful?
  [{:keys [success]}]
  (true? success))

(defn failed?
  [{:keys [success]}]
  (false? success))

(defn count-msgs
  [ctx n mux pred]
  (transduce-promise ctx n mux (filter pred) kixi/count))

(defn compute-stats
  [ctx n mux]
  (-> (p/all [(compute-summary-stats ctx n mux)
              (count-msgs ctx n mux successful?)
              (count-msgs ctx n mux failed?)])
      (p/then (fn [[summary s f]] (assoc summary :successes s :failures f)))))

(defn render
  [ctx {:keys [failures] :as stats}]
  (let [stats (m/map-vals (partial round2 2) stats)]
    (println (table [:successes :failures :min :mean :stddev :p50 :p90 :p99 :max :total-duration :rate] [stats]))
    (if (pos? failures)
      -1
      0)))

(defn process
  [{:keys [concurrency] :as ctx} {:keys [nr-records exec] :as options} input-ch]
  (let [output-ch (async/chan (* 4 concurrency))
        mux (async/mult output-ch)]
    (log/info "processing" nr-records "records")
    (-> (p/all [(t/now)
                (execute-commands ctx exec output-ch input-ch)
                (show-progress ctx nr-records mux)
                (compute-stats ctx nr-records mux)])
        (p/then
         (fn [[start _ _ {:keys [successes] :as stats}]]
           (let [end (t/now)
                 d (t/duration end start)]
             (assoc stats :total-duration d :rate (* (/ successes d) 1000)))))
        (p/then (partial render ctx)))))
