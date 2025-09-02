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
      [asset-id ::asset/type asset-type]
      [asset-id ::asset/loaded? true]]})})

(defn render [game world camera game-width game-height]
  (let [db @(:db* (first (o/query-all world ::asset/db*)))
        asset->esses (->> (o/query-all world ::sprite-esse) (group-by :asset-id))]
    (doseq [[asset-id esses] asset->esses]
      (let [instanced'
            (reduce
             (fn assoc-esse [instanced [idx esse]]
               (let [{:keys [x y asset-id frame-index]} esse
                     {::spritesheet/keys [raw frame-height frame-width]} (get db asset-id)
                     frames-per-row (/ (:width instanced) frame-width)
                     frame-x (mod frame-index frames-per-row)
                     frame-y (quot frame-index frames-per-row)
                     crop-x (* frame-x frame-width)
                     crop-y (* frame-y frame-height)
                     sprite (-> raw
                                (t/crop crop-x crop-y frame-width frame-height)
                                (t/invert camera)
                                (t/translate x y)
                                (t/scale frame-width frame-height))]
                 (instances/assoc instanced idx sprite)))
             (::spritesheet/instanced (get db asset-id))
             (map-indexed vector esses))]
        (c/render game (-> instanced' (t/project game-width game-height)))))))
