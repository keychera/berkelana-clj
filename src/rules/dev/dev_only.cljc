(ns rules.dev.dev-only
  (:require
   [clojure.spec.alpha :as s]
   [engine.world :as world]
   [odoyle.rules :as o]))

;; specs
(s/def ::message string?)
(s/def ::value string?)

(def rules
  (o/ruleset
   {::warning [:what [warning-id ::message message]]

    ::dev-value [:what [anything ::value value]]}))

(defn warn [world message]
  (o/insert world ::warning {::message message}))

(defn inspect-session
  "inspect value to be shown in leva in cljs context. use this fn inside rules passing session"
  [session & dev-value]
  (o/insert session ::dev-value {::value (str dev-value)}))

(defn inspect-game!
  "inspect value to be shown in leva in cljs context. use this fn outside rules passing game"
  ([game & dev-value]
   (swap! (::world/atom* game) #(apply inspect-session % dev-value))))
