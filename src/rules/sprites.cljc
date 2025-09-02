(ns rules.sprites
  (:require
   [assets.assets :as asset]
   [assets.spritesheet :as spritesheet]
   [engine.world :as world]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.instances :as instances]
   [play-cljc.transforms :as t]
   [rules.pos2d :as pos2d]
   [clojure.spec.alpha :as s]))

(s/def ::sprite-from-asset any?)
(s/def ::frame-index int?)

(world/system system
  {::world/rules
   (o/ruleset
    {::sprite-esse
     [:what
      [esse-id ::pos2d/x x]
      [esse-id ::pos2d/y y]
      [esse-id ::frame-index frame-index]
      [esse-id ::sprite-from-asset asset-id]
      [asset-id ::asset/loaded? true]]})})

(defn render [game world camera game-width game-height]
  (doseq [sprite-esse (o/query-all world ::sprite-esse)]
    (let [db* (:db* (first (o/query-all world ::asset/db*)))
          {:keys [x y asset-id frame-index]} sprite-esse
          {::spritesheet/keys [raw instanced frame-height frame-width]} (get @db* asset-id)
          frames-per-row (/ (:width instanced) frame-width)
          frame-x (mod frame-index frames-per-row)
          frame-y (quot frame-index frames-per-row)
          crop-x (* frame-x frame-width)
          crop-y (* frame-y frame-height)
          sprite (-> raw
                     (t/crop crop-x crop-y frame-width frame-height)
                     (t/invert camera)
                     (t/translate x y)
                     (t/scale frame-width frame-height))
          instanced (-> (instances/assoc instanced 0 sprite)
                        (t/project game-width game-height))
          ;; t/project is done here because of webgl only bug that is caused by instanced having no :uniforms
          ;; which make (gl game uniform1i location unit) is not called on render
          ;; and somehow lwjgl still works fine while webgl uses texts' texture instead
          ]
      (c/render game instanced))))