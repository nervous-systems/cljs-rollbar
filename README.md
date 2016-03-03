# cljs-rollbar

A client for [Rollbar](https://rollbar.com) currently geared towards
Clojurescript running on Node.  It'd be trivial to extend it to browser
environments, or the JVM, though the initial target was [Clojurescript AWS
Lambda functions](https://github.com/nervous-systems/cljs-lambda).  Features
intrinsic support for [Timbre](https://github.com/ptaoussanis/timbre).

# Usage

The model ought to be fairly familiar - there's a function `rollbar!`, which
takes a map & transforms it before issuing it as a `POST` request to the Rollbar
API, returning a [promesa](https://github.com/funcool/promesa) promise.

There are environment-specific modules (currently only `cljs-rollbar.node`)
which provide default maps containing information about the runtime environment.

```clojure
(...
  (:require [cljs-rollbar.core :as rollbar]
            [cljs-rollbar.node]))

(def report!
  (-> rollbar/rollbar!
      (rollbar/defaulting cljs-rollbar.node/default-payload)
      (rollbar/defaulting {:token "SECRET"})))
```

The simplest possible request:

```clojure
(report! {:info "Hello"})
```

In a Node REPL on my laptop with the `report!` definition above, I get the
following sent to Rollbar:

```clojure
{:access_token "SECRET"
 :data
 {:server
  {:code_version "0.1.0"
   :host         "brinstar.local"
   :argv         ["/usr/local/Cellar/node/6.3.1/bin/node"]
   :pid          69652}

  :level       :info
  :language    "clojurescript"
  :notifier    {:name "rollbar-cljs-node" :version "0.1.0"}
  :environment "unspecified"
  :timestamp   1470412615834
  :body        {:message {:body "Hello"}}
  :framework   "node-js"
  :platform    "darwin"}}
```

If I don't like any of those values, I can specify them when I define my
reporter, or when I emit the message, e.g:

```clojure
(def report!
  (-> rollbar/rollbar!
      (rollbar/defaulting {:env "prod"})))

;; `:env` is a supported abbreviation, as are `:host` & `:version`:

(report! {:info "Hello" :env "prod" :version "0.0.0"})
```

Similarly, it's easy to emit messages at different levels:

```clojure
;; Report an error, attach a `person` to the error
(report! {:error (ex-info "Oops" {:x 1}) :person {:id "boss"}})
;; Log an error instance without using the `error` level
(promesa.core/then
  (report! {:debug (js/Error. "Oops")})
  println)
;; => {:err 0, :result {:id nil, :uuid "dbd8081e330b4abf8c6f86586d26d863"}}
````

![Screenshot](https://raw.githubusercontent.com/nervous-systems/cljs-rollbar/master/doc/exception.png)

Aside from the keys expected by Rollbar, ad-hoc data can be put into the map
passed to the reporting function.

Additionally, the map attached to an `ExceptionInfo` instance (i.e. as in the
`ex-info` example above) will be merged into the toplevel Rollbar map and
associated with the error item.

# Timbre

If you're using [Timbre](https://github.com/ptaoussanis/timbre), it's
straightforward to configure a cljs-rollbar appender.

```clojure
(timbre/merge-config!
  {:appenders {:rollbar (rollbar/timbre-appender report!)}})
;; (Where report! is the function we defined above - rollbar! + some defaults)
```

We can then proceed to log as normal:

```clojure
(timbre/info "Hello" {:x 1})
```

Any map arguments which are passed to a Timbre logging call are merged into the
map supplied to the appender's reporting function & will become attributes of
the resulting Rollbar item.  Additionally, `line` and `file` toplevel attributes
are set to the information received from Timbre, when not reporting an error
(errors have their own designated line/file attrs in the Rollbar API -- non errors
do not, AFAICT)

```clojure
(timbre/error (js/Error. "Oops") {:version "0.2.0-final"})
(timbre/info {:env "dev"} "Stuff seems pretty good"     )
```

# Lambda

If you're using [cljs-lambda](https://github.com/nervous-systems/cljs-lambda),
connecting its error handling mechanism to cljs-rollbar is straightforward:

```clojure
(...
  (:require [cljs-lambda.util :refer [async-lambda-fn]]
            [cljs-rollbar.lambda]))

(def ^:export blow-up
  (async-lambda-fn
    (fn [event ctx]
      (throw (ex-info "Sorry" {::x 2})))
    {:error-handler (cljs-rollbar.lambda/reporting-errors report!)}))
```

This'll cause errors thrown inside `blow-up` (or asynchronously realized errors)
to be sent via our Rollbar reporting function `report!`.  The Lambda function
won't return to the caller until the error is acknowledged by Rollbar.  In the
event of a successful Rollbar API response, the underlying error will be passed
through to the caller as if the error handler wasn't in between.
