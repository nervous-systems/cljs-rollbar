(ns rollbar-cljs-node.core
  (:require [rollbar-cljs-node.util :as util]
            [clojure.string :as str]
            [kvlt.core :as kvlt]))

(def VERSION 1)
(def ENDPOINT (str "https://api.rollbar.com/api/" VERSION "/item/"))

(util/when-node
  (def os (js/require "os"))

  (defn guess-os []
    (.platform os))

  (defn guess-hostname []
    (.hostname os)))

(util/when-not-node
  (defn guess-os []
    "browser"))

(defn default-payload* []
  {:os (guess-os)
   :language "clojure"
   :framework "clojurescript"
   :level "debug"
   :server {:code_version "0.1.0"
            :host (guess-hostname)}
   :notifier {:name "rollbar-cljs-node"}})


(defn split-file+lineno [s]
  (let [s (cond-> s (= (first s) \() (subs 1 (dec (count s))))]
    (take 2(str/split s #":"))))

(let [start-index (count "at ")]
  (defn frame->map [f]

    (let [cols (str/split (subs f start-index) #" " 2)
          [file+lineno method] (cond-> cols (= 2 (count cols)) reverse)
          [file lineno] (split-file+lineno file+lineno)]
      (cond->
          {:filename file
           :lineno   (js/parseInt lineno)}
        method (assoc :method method)))))

(defn error->trace [e]
  (let [[error & frames] (str/split (.. e -stack) #"\n")
        [cls msg] (str/split error #": " 2)
        exception (cond-> {:class cls} msg (assoc :message msg))]
    {:exception exception
     :frames (for [frame frames]
               (frame->map (str/trim frame)))}))

(defn post! [token payload]
  (kvlt/request!
   {:url ENDPOINT
    :method :post
    :body (.stringify
           js/JSON
           (clj->js {:access_token token
                     :data
                     (merge (default-payload*)
                            {:environment "production"
                             :timestamp (.getTime (js/Date.))
                             :body {:trace_chain [(error->trace (js/Error. "lESS OK"))]}} payload)}))
    :type :json}))
