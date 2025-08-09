(ns rules.dev.leva-rules
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer-macros [insert!]]
   [odoyle.rules :as o]
   [rules.transform2d :as t2d]))

(def rules
  (o/ruleset
   {::leva-rotation
    [:what
     [::leva-spritesheet ::rotation rotation]
     #_#_:then
     (insert! :ubim ::t2d/rotate rotation)]

    ::leva-scale
    [:what
     [::leva-spritesheet ::scale scale]
     #_#_:then
     (insert! :ubim ::t2d/scale scale)]}))

(s/def ::rotation number?)
(s/def ::scale number?)
