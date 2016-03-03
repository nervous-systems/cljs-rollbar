(ns cljs-rollbar.exception
  (:require [clojure.string :as str]))

(defn- strip-parens [s]
  (cond-> s (str/starts-with? s "(") (subs 1 (dec (count s)))))

(defn- frame-keys [s]
  (let [s (strip-parens s)]
    (zipmap [:filename :lineno :colno] (str/split s #":"))))

(defn- demunge* [s]
  (let [segments (-> s demunge (str/split #"/"))]
    (if (< 1 (count segments))
      (str (str/join "." (butlast segments)) "/" (last segments))
      s)))

(let [start-index (count "at ")]
  (defn frame->map [f]
    (let [cols                 (-> f (subs start-index) (str/split #" " 2))
          [file+lineno method] (cond-> cols (= 2 (count cols)) reverse)]
      (cond-> (frame-keys file+lineno)
        method (assoc :method (demunge* method))))))

(defn error->trace [e]
  (let [[error & frames] (str/split-lines (.. e -stack))
        [cls msg]        (str/split error #": " 2)
        exception        (cond-> {:class cls} msg (assoc :message msg))]
    {:exception exception
     :frames    (for [frame frames]
                  (frame->map (str/trim frame)))}))
