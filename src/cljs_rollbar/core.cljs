(ns cljs-rollbar.core
  (:require [cljs-rollbar.exception :refer [frame->map error->trace]]
            [clojure.string :as str]
            [kvlt.core      :as kvlt]
            [promesa.core   :as promesa]))

(def rollbar-api-version "1")

(let [key-aliases {:host    [:server :host]
                   :version [:server :code_version]
                   :env     [:environment]}]
  (defn- expand-aliases [message]
    (reduce
     (fn [acc [k out-k]]
       (if-let [v (acc k)]
         (-> acc (assoc-in out-k v) (dissoc k))
         acc))
     message key-aliases)))

(defn- error-body [m error]
  (-> m
      (merge (ex-data error))
      (update-in [:body :trace] #(merge (error->trace error) %))))

(defn- message-body [m message]
  (let [message (if (string? message)
                  {:body message}
                  message)]
    (update m :body #(merge {:message message} %))))

(def error? (partial instance? js/Error))

(defn- any-kv [m ks & [default]]
  (when-let [k (or (some ks (keys m)) default)]
    [k (m k)]))

(defn- ->level [m]
  (any-kv m #{:debug :info :warning :critical} :error))

(defn- tidy-envelope [m]
  (dissoc m :debug :info :warning :critical :error :token))

(defn- merge-most [& maps]
  (apply merge-with
         (fn [x y]
           (cond (map?    y) (merge-most x y)
                 (vector? y) (into x y)
                 :else       y))
         maps))

(defn- ->request-data [m]
  (let [now (.. js/Date now)]
    (-> m (assoc :timestamp now) expand-aliases tidy-envelope)))

(defn ^:no-doc envelop [{:keys [token] :as m}]
  (let [[level value] (->level m)
        m             (if (error? value)
                        (error-body   m value)
                        (message-body m value))]
    {:access_token token
     :data         (->request-data (assoc m :level level))}))

(defn rollbar! [{:keys [endpoint] :as m}]
  (-> (kvlt/request!
       {:url    endpoint
        :method :post
        :form   (envelop (dissoc m :endpoint))
        :type   :json
        :as     :json})
      (promesa/then :body)))

(defn defaulting [f & maps]
  (let [maps (into [] maps)]
    (fn applying-defaults [req]
      (f (apply merge-most (conj maps req))))))

(defn reporter [payload]
  (defaulting rollbar! payload))
