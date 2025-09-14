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
   [rules.input :as input]
   [rules.room :as room]
   [rules.sprites :as sprites]
   [rules.ubim :as ubim]))

;; world system require spec.alpha
(s/def ::this any?)

(defn initial-esse [world game]
  (-> world
      (esse :chara/ubim
            grid-move/default #::grid-move{:pos-x 2 :pos-y 4}
            #::sprites{:sprite-from-asset :id/berkelana :frame-index 0}
            #::ubim{:anim-tick 0 :anim-elapsed-ms 0})
      (esse :prop/bucket
            grid-move/default #::grid-move{:pos-x 4 :pos-y 4 :pushable? true}
            #::sprites{:sprite-from-asset :id/worldmap :frame-index (+ (* 48 9) 5)}
            #::ubim{:anim-tick 0 :anim-elapsed-ms 0})
      (esse :prop/bucket2
            grid-move/default #::grid-move{:pos-x 3 :pos-y 5 :pushable? true}
            #::sprites{:sprite-from-asset :id/worldmap :frame-index (+ (* 48 9) 5)}
            #::ubim{:anim-tick 0 :anim-elapsed-ms 0})
      (esse :prop/kani
            grid-move/default #::grid-move{:pos-x 5 :pos-y 5 :unwalkable? true}
            #::sprites{:sprite-from-asset :id/worldmap :frame-index (+ (* 48 18) 28)}
            #::ubim{:anim-tick 0 :anim-elapsed-ms 0})
      (o/insert ::world/global ::room/active :room/yard)
      (o/insert :room/yard {::room/boundary {:x 0 :y 0 :width 8 :height 8}
                            ::room/use      :id/worldmap})
      (o/insert :room/home {::room/boundary {:x 8 :y 0 :width 8 :height 8}
                            ::room/use      :id/worldmap})
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
      [::input/r ::input/pressed-key ::input/keydown]
      [::world/global ::world/game game]
      :then
      (println "resetting world!")
      (swap! (::world/init-cnt* game) inc)
      (s-> (initial-esse session game))]})})
