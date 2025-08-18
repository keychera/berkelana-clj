(ns game.chapter1
  (:require
   [assets.asset :as asset]
   [assets.spritesheet :as spritesheet]
   [assets.tiled :as tiled]
   [rules.esse :as esse :refer [asset esse]]
   [rules.grid-move :as grid-move]
   [rules.pos2d :as pos2d]))

(defn init [session init-only?]
  (-> session
      (cond-> init-only?
        (-> (asset :asset/char0
                   #::asset{:img-to-load "char0.png" :type ::asset/spritesheet}
                   #::spritesheet{:frame-width 32 :frame-height 32})
            (asset :asset/worldmap
                   #::asset{:type ::asset/tiledmap}
                   #::tiled{:parsed-tmx tiled/world-map-tmx})))
      (esse :ubim
            grid-move/default #::grid-move{:target-attr-x ::pos2d/x :target-attr-y ::pos2d/y :pos-x 2 :pos-y 4}
            #::esse{:sprite-from-asset :asset/char0
                    :x 0 :y 0 :frame-index 0 :anim-tick 0 :anim-elapsed-ms 0})))