(ns assets.spritesheet
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as e2d]
   [assets.assets :as asset]
   [engine.utils :as utils]))

(s/def ::frame-width int?)
(s/def ::frame-height int?)

(defmethod asset/process-asset ::asset/spritesheet
  [game world* asset-id {::asset/keys [img-to-load]}]
  (utils/get-image
   img-to-load
   (fn [{:keys [data width height]}]
     (let [raw-image-entity (e2d/->image-entity game data width height)
           image-entity     (c/compile game raw-image-entity)
           image-entity     (assoc image-entity :width width :height height)]
       (println "loaded spritesheet asset from" img-to-load)
       (swap! asset/db* assoc-in [asset-id ::raw-image] raw-image-entity)
       (swap! asset/db* assoc-in [asset-id ::image] image-entity)
       (swap! world* #(-> % (o/insert asset-id ::asset/loaded? true)))))))
