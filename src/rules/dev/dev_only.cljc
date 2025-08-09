(ns rules.dev.dev-only
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]))

(def rules
  (o/ruleset
   {::warning
    [:what
     [warning-id ::message message]]}))

;; specs
(s/def ::message string?)

(defn warn [session message]
  (o/insert session ::warning {::message message}))
