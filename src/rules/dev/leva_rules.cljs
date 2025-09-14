(ns rules.dev.leva-rules
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]
   [engine.world :as world]))

(world/system sysyem
  {::world/init-fn
   (fn [world _game]
     (o/insert world ::dev ::dev-slider 0))

   ::world/rules
   (o/ruleset
    {::dev-slider
     [:what
      [::dev ::dev-slider value]]})})

(defn get-slider-value [world]
  (some-> (first (o/query-all world ::dev-slider)) :value))

(s/def ::dev-slider number?)
