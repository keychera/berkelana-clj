(ns assets.spritesheet
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as e2d]
   [assets.assets :as asset]
   [engine.utils :as utils]
   [engine.world :as world]
   [play-cljc.instances :as instances]))

(s/def ::frame-width int?)
(s/def ::frame-height int?)

(defmethod asset/process-asset ::asset/spritesheet
  [game asset-id {::asset/keys [img-to-load]}]
  (utils/get-image
   img-to-load
   (fn [{:keys [data width height]}]
     (let [raw       (e2d/->image-entity game data width height)
           instanced (c/compile game (instances/->instanced-entity raw))
           instanced (assoc instanced :width width :height height)]
       (println "loaded spritesheet asset from" img-to-load)
       (swap! (::asset/db* game) assoc-in [asset-id ::raw] raw)
       (swap! (::asset/db* game) assoc-in [asset-id ::instanced] instanced)
       (swap! (::world/atom* game) #(-> % (o/insert asset-id ::asset/loaded? true)))))))
