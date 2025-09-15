(ns rules.room
  (:require
   [assets.assets :as asset]
   [assets.tiled :as tiled]
   [clojure.core.match :as cm]
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
   [rules.pos2d :as pos2d]))

(s/def ::currently-at keyword?)
(s/def ::boundary map?)
(s/def ::use-tiledmap keyword?)

(declare load-room)

(world/system system
  {::world/rules
   (o/ruleset
    {::test-cycle-room
     [:what
      [::world/global ::currently-at room-id {:then false}]
      [room-id ::boundary boundary {:then false}]
      [esse-id ::grid-move/move-state ::grid-move/idle]
      [esse-id ::grid-move/pos-x pos-x {:then false}]
      [esse-id ::grid-move/pos-y pos-y {:then false}]
      :when (= esse-id :chara/ubim)
      :then
      (cm/match [room-id pos-x pos-y]
        [:room/home 10 6] (s-> session
                               (o/insert ::world/global ::currently-at :room/yard)
                               (o/insert esse-id #::grid-move{:facing :down :prev-x 2  :prev-y 3 :next-x 2 :next-y 4 :move-y 1
                                                              :move-state ::grid-move/check-world-boundaries}))
        [:room/yard 2  3] (s-> session
                               (o/insert ::world/global ::currently-at :room/home)
                               (o/insert esse-id #::grid-move{:facing :up :prev-x 10 :prev-y 6 :next-x 10 :next-y 5 :move-y -1
                                                              :move-state ::grid-move/check-world-boundaries}))
        :else :no-op)]

     ::active-room
     [:what
      [::world/global ::currently-at room-id]
      [room-id ::boundary boundary]
      [room-id ::use-tiledmap asset-id]
      [asset-id ::asset/loaded? true]
      [::world/global ::world/game game]
      [::asset/global ::asset/db* db*]
      :then
      (load-room game db* asset-id boundary)
      (s-> session 
           (o/insert ::camera/camera ::pos2d/pos2d {:x (- (:x boundary)) :y (- (:y boundary))}))]})

   ::world/render-fn
   (fn [_world game camera game-width game-height]
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