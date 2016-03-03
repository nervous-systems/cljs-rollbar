(ns cljs-rollbar.node
  (:require [cljs.nodejs :as nodejs]
            [cljs-rollbar.core :refer [rollbar-api-version]]))

(def cljs-rollbar-node-version "0.1.0")

(def ^:private endpoint
  (str "https://api.rollbar.com/api/" rollbar-api-version "/item/"))

(let [os (nodejs/require "os")]
  (def default-payload
    {:endpoint    endpoint
     :platform    (.platform os)
     :language    "clojurescript"
     :framework   "node-js"
     :environment "unspecified"
     :server      {:code_version "0.1.0"
                   :host (.hostname os)
                   :argv (.. js/process -argv concat)
                   :pid  (.. js/process -pid)}
     :notifier    {:name    "rollbar-cljs-node"
                   :version cljs-rollbar-node-version}}))
