(ns game.chapter1
  (:require
   [assets.assets :as asset]
   [assets.spritesheet :as spritesheet]
   [assets.tiled :as tiled]
   [rules.esse :as esse :refer [asset esse]]
   [rules.grid-move :as grid-move]
   [rules.sprites :as sprites]
   [rules.ubim :as ubim]))

(defn init [session init-only?]
  (-> session
      (cond-> init-only?
        (-> (asset :id/berkelana
                   #::asset{:type ::asset/spritesheet :img-to-load "berkelana.png"}
                   #::spritesheet{:frame-width 32 :frame-height 32})
            (asset :id/worldmap
                   #::asset{:type ::asset/tiledmap}
                   #::tiled{:parsed-tmx tiled/world-map-tmx})
            (esse :chara/ubim
                  #::grid-move{:pos-x 2 :pos-y 4} 
                  #::sprites{:sprite-from-asset :id/berkelana :frame-index 0}
                  #::ubim{:anim-tick 0 :anim-elapsed-ms 0})))
      ;; need to understand why this separation preserve the player pos on reload
      (esse :chara/ubim grid-move/default)
      (esse :prop/bucket
            grid-move/default #::grid-move{:pos-x 3 :pos-y 5 :pushable? true}
            #::sprites{:sprite-from-asset :id/worldmap :frame-index (+ (* 48 9) 5)}
            #::ubim{:anim-tick 0 :anim-elapsed-ms 0})
      (esse :prop/kani 
            grid-move/default #::grid-move{:pos-x 5 :pos-y 5 :unwalkable? true}
            #::sprites{:sprite-from-asset :id/worldmap :frame-index (+ (* 48 18) 28)}
            #::ubim{:anim-tick 0 :anim-elapsed-ms 0})))