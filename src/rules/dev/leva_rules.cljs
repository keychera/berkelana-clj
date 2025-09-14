(ns rules.dev.leva-rules
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]))

(def rules
  (o/ruleset
   {::leva-slider
    [:what
     [::dev ::dev-slider value]]}))

(s/def ::dev-slider number?)
