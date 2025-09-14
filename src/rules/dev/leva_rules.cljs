(ns rules.dev.leva-rules
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]
   [engine.world :as world]))

(world/system sysyem
  {::world/init-fn
   (fn [_game world]
     (o/insert world ::dev ::dev-slider 0))

   ::world/rules
   (o/ruleset
    {::dev-slider
     [:what
      [::dev ::dev-slider value]]})})

(s/def ::dev-slider number?)
