(ns cljs-rollbar.timbre
  (:require [cljs-rollbar.exception :refer [frame->map error->trace]]
            [cljs-rollbar.core :refer [merge-most]]))

(def timbre-level->rollbar {:warn :warning :fatal :critical :trace :debug :report :debug})

(defn timbre->trace [m]
  {:exception {:class    "Error"
               :message  (force (m :msg_))}
   :frame     {:lineno   (m :?line)
               :filename (m :?file "unknown")}})

(defn appender [rollbar!]
  {:enabled?     true
   :async?       false
   :min-level    nil
   :rate-limit   nil
   :output-fn    :inherit
   :ns-blacklist ["kvlt.*"]
   :fn
   (fn [{level :level err :?err msg_ :msg_ args :vargs :as event}]
     (let [level (timbre-level->rollbar level level)
           maps  (filter map? args)
           m     (if err
                   {level err
                    :body {:trace (merge-most (timbre->trace event) (error->trace err))}}
                   {level (force msg_)
                    :line (event :?line)
                    :file (event :?file)})]
       (rollbar! (apply merge m maps))))})
