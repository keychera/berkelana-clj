(ns rules.room
  (:require
   [assets.assets :as asset]
   [assets.tiled :as tiled]
   [clojure.spec.alpha :as s]
   [com.rpl.specter :as sp]
   [engine.macros :refer [s->]]
   [engine.world :as world]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.instances :as instances]
   [play-cljc.transforms :as t]
   [rules.camera :as camera]
   [rules.grid-move :as grid-move]
   [rules.input :as input]
   [rules.pos2d :as pos2d]))

(s/def ::active keyword?)
(s/def ::boundary map?)
(s/def ::use keyword?)

(declare load-room)

(world/system system
  {::world/init-fn
   (fn test-room [_ world]
     (-> world
         (o/insert ::world/global ::active :room/home) 
         (o/insert :room/yard {::boundary {:x 0 :y 0 :width 8 :height 8}
                               ::use      :id/worldmap})
         (o/insert :room/home {::boundary {:x 8 :y 0 :width 8 :height 8}
                               ::use      :id/worldmap})))

   ::world/rules
   (o/ruleset
    {::test-cycle-room
     [:what
      [::input/r ::input/pressed-key ::input/keydown]
      [::world/global ::active room-id {:then false}]
      [room-id ::boundary boundary {:then false}]
      :then
      (s-> session
           (o/insert ::world/global ::active
                     (case room-id
                       :room/home :room/yard
                       :room/yard :room/home))
           (o/insert :chara/ubim 
                     (case room-id
                       :room/home  #::grid-move{:teleport true :pos-x 2 :pos-y 4}
                       :room/yard  #::grid-move{:teleport true :pos-x 10 :pos-y 4}) ))]

     ::active-room
     [:what
      [::world/global ::active room-id]
      [room-id ::boundary boundary]
      [room-id ::use asset-id]
      [asset-id ::asset/loaded? true]
      [::world/global ::world/game game]
      [::asset/global ::asset/db* db*]
      :then
      (load-room game db* asset-id boundary)
      (s-> session
           (o/insert ::camera/camera ::pos2d/pos2d {:x (- (:x boundary)) :y (- (:y boundary))}))]})

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
                          (let [i (or (get-in acc [(:firstgid tile) :i]) 0)]
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