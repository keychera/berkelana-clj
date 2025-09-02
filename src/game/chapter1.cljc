(ns game.chapter1
  (:require
   [assets.assets :as asset]
   [assets.spritesheet :as spritesheet]
   [assets.tiled :as tiled]
   [rules.esse :as esse :refer [asset esse]]
   [rules.grid-move :as grid-move]
   [rules.pos2d :as pos2d]
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
            (esse :ubim
                  grid-move/default #::grid-move{:target-attr-y ::pos2d/y :pos-x 2 :pos-y 4}
                  #::pos2d{:x 0 :y 0}
                  #::sprites{:sprite-from-asset :id/berkelana :frame-index 0}
                  #::ubim{:anim-tick 0 :anim-elapsed-ms 0})))
      ;; need to understand why this separation preserve the player pos on reload
      (esse :ubim #::grid-move{:target-attr-x ::pos2d/x})
      (esse :prop/bucket
            #::pos2d{:x (* 16 3) :y (* 16 5)}
            #::sprites{:sprite-from-asset :id/worldmap :frame-index (+ (* 48 9) 5)}
            #::ubim{:anim-tick 0 :anim-elapsed-ms 0})
      (esse :prop/bucket2
            #::pos2d{:x (* 16 5) :y (* 16 5)}
            #::sprites{:sprite-from-asset :id/worldmap :frame-index (+ (* 48 18) 28)}
            #::ubim{:anim-tick 0 :anim-elapsed-ms 0})))