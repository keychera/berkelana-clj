(ns rules.asset.spritesheet
  (:require
   #?(:clj [engine.macros :refer [vars->map]]
      :cljs [engine.macros :refer-macros [vars->map]])
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as entities-2d]
   [rules.asset.asset :as asset]))

(s/def ::frame-width int?)
(s/def ::frame-height int?)

(def rules
  (o/ruleset
   {::sprite-metadata
    [:what
     [asset-id ::asset/loaded? true]
     [asset-id ::frame-width frame-width]
     [asset-id ::frame-height frame-height]
     :then
     (swap! asset/db* update asset-id #(merge % (vars->map frame-width frame-height)))]}))

(defmethod asset/process-asset ::asset/spritesheet
  [game world* {:keys [asset-id asset-type asset-path]} {:keys [data width height]}]
  (let [image-entity (entities-2d/->image-entity game data width height)
        image-entity (c/compile game image-entity)
        loaded-image (assoc image-entity :width width :height height :asset-type asset-type)]
    (println "loaded spritesheet asset from" asset-path)
    (swap! asset/db* assoc asset-id loaded-image)
    (swap! world* #(-> % (o/insert asset-id ::asset/loaded? true)))))

