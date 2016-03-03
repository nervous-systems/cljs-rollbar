(ns cljs-rollbar.lambda
  (:require [cljs-rollbar.exception :refer [frame->map error->trace]]
            [cljs-rollbar.core :refer [merge-most]]
            [clojure.string :as str]
            [kvlt.core      :as kvlt]
            [promesa.core   :as promesa]))

(def ^:private rollbar-defaults (atom nil))

(defn context->alias
  "Return nil or the string alias of the Lambda fn currently being invoked."
  [ctx]
  (when-let [arn (:function-arn ctx)]
    (let [[_ [_ _ alias]] (split-with #(not= % "function") (str/split arn #":"))]
      (and alias (re-find #"^[a-zA-Z]" alias) alias))))

(defn context->defaults [event ctx]
  (let [environment (or (context->alias ctx) "$LATEST")]
   {:request     {:function (:function-name ctx)
                  :url      (:function-arn  ctx)
                  :params   event}
    :environment environment
    :server      {:code_version environment}
    :person      {:id      (-> (:identity ctx) :cognito-id (or "Anonymous"))}}))

(defn reporting-errors [rollbar!]
  (fn handle-error [error event ctx]
    (let [defaults (context->defaults event ctx)]
      (promesa/then (rollbar! (assoc defaults :error error))
       (fn [_]
         (throw error))))))

(defn current-defaults!
  ([] @rollbar-defaults)
  ([event ctx]
   (reset! rollbar-defaults (context->defaults event ctx))))
