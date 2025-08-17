(ns rules.dev.dev-only
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]))

;; specs
(s/def ::message string?)
(s/def ::value any?)

(def rules
  (o/ruleset
   {::warning [:what [warning-id ::message message]]

    ::dev-value [:what [anything ::value value]]}))

(defn warn [world message]
  (o/insert world ::warning {::message message}))

(defn send-dev-value
  ([world dev-value]
   (send-dev-value world true dev-value))
  ([world pred? dev-value]
   (if pred?
     (o/insert world ::dev-value {::value dev-value})
     world)))
