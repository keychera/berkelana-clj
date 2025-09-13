(ns rules.room
  (:require
   [assets.assets :as asset]
   [assets.tiled :as tiled]
   [clojure.spec.alpha :as s]
   [engine.world :as world]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.instances :as instances]
   [play-cljc.transforms :as t]
   [com.rpl.specter :as sp]))

(s/def ::boundary map?)
(s/def ::use keyword?)

(declare load-room)

(world/system system
  {::world/init-fn
   (fn test-room [_ world]
     (-> world
         (o/insert :room/home {::boundary {:x 8 :y 0 :width 8 :height 8}
                               ::use      :id/worldmap})))

   ::world/rules
   (o/ruleset
    {::active-room
     [:what
      [room-id ::boundary boundary]
      [room-id ::use asset-id]
      [asset-id ::asset/loaded? true]
      [::world/global ::world/game game]
      [::asset/global ::asset/db* db*]
      :then
      (load-room game db* asset-id boundary)]})

   ::world/render-fn
   (fn [game _world camera game-width game-height]
     (let [{:keys [::instanced]} (get @(::asset/db* game) :id/worldmap)
           tile-size (-> instanced first :instanced-map :tile-size)]
       (doseq [entity (->> instanced (sort-by :firstgid) (map :instanced-map) (map :entity))]
         (c/render game (-> entity
                            (t/project game-width game-height)
                            (t/invert camera)
                            (t/scale tile-size tile-size))))))})

(defn in-boundary? [x y room]
  (and (>= x (:x room)) (< x (+ (:x room) (:width room)))
       (>= y (:y room)) (< y (+ (:y room) (:height room)))))

(defn load-room [game db* asset-id boundary]
  (let [{::tiled/keys [tiled-map firstgid->instanced-map]} (get @(::asset/db* game) asset-id)
        instanced-room (reduce
                        (fn [acc tile]
                          (let [i (or (get-in acc [(:firstgid tile) :i]) 0)
                                ]
                            (if (in-boundary? (:tile-x tile) (:tile-y tile) boundary)
                              (-> acc
                                  (update-in [(:firstgid tile) :entity] #(instances/assoc % i (:tile-img tile)))
                                  (update-in [(:firstgid tile) :i] inc))
                              acc)))
                        firstgid->instanced-map
                        (:tiles tiled-map))]
    (swap! db* #(-> % (update asset-id merge {::instanced (->> instanced-room
                                                               (map (fn [[k v]] {:firstgid k :instanced-map v})))})))))


(sp/transform [sp/MAP-VALS :entity] inc {1 {:entity 2} 2 {:entity 4}})