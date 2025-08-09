(ns rules.dev.leva-rules
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]))

(def rules
  (o/ruleset
   {::leva-rotation
    [:what
     [::leva-spritesheet ::rotation rotation]]

    ::leva-scale
    [:what
     [::leva-spritesheet ::scale scale]]}))

(s/def ::rotation number?)
(s/def ::scale number?)
