(ns game.chapter1
  (:require
   [assets.assets :as asset]
   [assets.spritesheet :as spritesheet]
   [assets.tiled :as tiled]
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [engine.world :as world]
   [odoyle.rules :as o]
   [rules.esse :as esse :refer [asset esse]]
   [rules.grid-move :as grid-move]
   [rules.interface.input :as input]
   [rules.room :as room]
   [rules.shader :as shader]
   [rules.sprites :as sprites]
   [rules.ubim :as ubim]))

;; world system require spec.alpha
(s/def ::this any?)

(defn initial-esse [world game]
  (-> world
      (o/insert ::world/global ::room/currently-at :room/yard)
      (o/insert :room/yard {::room/boundary {:x 0 :y 0 :width 8 :height 8}
                            ::room/use-tiledmap      :id/worldmap})
      (esse :chara/ubim
            grid-move/default #::grid-move{:pos-x 2 :pos-y 4}
            #::room{:currently-at :room/yard}
            #::sprites{:sprite-from-asset :id/berkelana :frame-index 0}
            #::ubim{:anim-tick 0 :anim-elapsed-ms 0})
      (esse :prop/bucket
            grid-move/default #::grid-move{:pos-x 5 :pos-y 5 :pushable? true}
            #::room{:currently-at :room/yard}
            #::sprites{:sprite-from-asset :id/worldmap :frame-index (+ (* 48 9) 5)}) 
      (esse :prop/shader
            grid-move/default #::grid-move{:pos-x 3 :pos-y 5 :pushable? true}
            #::room{:currently-at :room/yard}
            {::shader/shader-to-load shader/->hati})
      (o/insert :room/home {::room/boundary {:x 8 :y 0 :width 8 :height 8}
                            ::room/use-tiledmap      :id/worldmap})
      (esse :prop/kani
            grid-move/default #::grid-move{:pos-x 12 :pos-y 4 :unwalkable? true}
            #::room{:currently-at :room/home}
            #::sprites{:sprite-from-asset :id/worldmap :frame-index (+ (* 48 18) 28)})
      (cond->
       (not (world/first-init? game))
        (-> (esse :id/berkelana #::asset{:loaded? true})
            (esse :id/worldmap #::asset{:loaded? true})))))

(world/system system
  {::world/init-fn
   (fn [world game]
     (tap> world)
     (-> world
         (cond->
          (world/first-init? game)
           (-> (asset :id/berkelana
                      #::asset{:type ::asset/spritesheet :img-to-load "berkelana.png"}
                      #::spritesheet{:frame-width 32 :frame-height 32})
               (asset :id/worldmap
                      #::asset{:type ::asset/tiledmap}
                      #::tiled{:parsed-tmx tiled/world-map-tmx})))
         (initial-esse game)))

   ::world/rules
   (o/ruleset
    {::reset
     [:what
      [::world/global ::world/control :reset]
      [::world/global ::world/game game]
      :then
      (println "resetting world!")
      (swap! (::world/init-cnt* game) inc)
      (s-> (initial-esse session game))]})})
