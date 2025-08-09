(ns rules.asset.spritesheet
  (:require
   #?(:clj [engine.macros :refer [insert! vars->map]]
      :cljs [engine.macros :refer-macros [insert! vars->map]])
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as entities-2d]
   [rules.asset.image :as image]))

(s/def ::frame-width int?)
(s/def ::frame-height int?)

(def rules
  (o/ruleset
   {::sprite-metadata
    [:what
     [asset-id ::image/asset spritesheet {:then false}]
     [asset-id ::frame-width frame-width]
     [asset-id ::frame-height frame-height]
     :then
     (insert! asset-id ::image/metadata (merge spritesheet (vars->map frame-width frame-height)))]}))

(defmethod image/process-asset ::image/spritesheet
  [game world* {:keys [asset-id asset-type asset-path]} {:keys [data width height]}]
  (let [image-entity (entities-2d/->image-entity game data width height)
        image-entity (c/compile game image-entity)
        loaded-image (assoc image-entity :width width :height height :asset-type asset-type)]
    (println "loaded spritesheet asset from" asset-path)
    (swap! world* #(-> % (o/insert asset-id ::image/asset loaded-image)))))

