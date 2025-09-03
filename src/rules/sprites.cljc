(ns rules.sprites
  (:require
   [assets.assets :as asset]
   [assets.spritesheet :as spritesheet]
   [clojure.spec.alpha :as s]
   [engine.world :as world]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.instances :as instances]
   [play-cljc.transforms :as t]
   [rules.instanceable :as instanceable]
   [rules.pos2d :as pos2d]))

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

(add-tap #(def hmm %))
(comment
  hmm)

;; nulls are nasty here, it makes the browser js callstack blowup (Idk why)
(defn render [game world camera game-width game-height]
  (let [db @(:db* (first (o/query-all world ::asset/db*)))
        asset-id->esses (->> (o/query-all world ::sprite-esse) (group-by :asset-id))]
    (doseq [[asset-id esses] asset-id->esses]
      (let [asset-type (-> (get db asset-id) ::asset/type)
            instanced  (::instanceable/instanced
                        (case asset-type
                          ::asset/spritesheet (get db asset-id)
                          ::asset/tiledmap    (get-in db [asset-id :atlas])))
            raw        (::instanceable/raw
                        (case asset-type
                          ::asset/spritesheet (get db asset-id)
                          ::asset/tiledmap    (get-in db [asset-id :atlas])))
            frame-height (case asset-type
                           ::asset/spritesheet (::spritesheet/frame-height (get db asset-id))
                           ::asset/tiledmap    16)
            frame-width  (case asset-type
                           ::asset/spritesheet (::spritesheet/frame-width (get db asset-id))
                           ::asset/tiledmap    16)
            offset-fn    (case asset-type
                           ::asset/spritesheet #(- % 8)
                           ::asset/tiledmap    identity)
            ;; hardcoded for now until we can separate spritesheet and tiledmap
            instanced'
            (reduce
             (fn assoc-esse [instanced [idx esse]]
               (let [{:keys [x y frame-index]} esse
                     frames-per-row (/ (:width instanced) frame-width) ;; assuming instanced have width
                     frame-x (mod frame-index frames-per-row)
                     frame-y (quot frame-index frames-per-row)
                     crop-x (* frame-x frame-width)
                     crop-y (* frame-y frame-height)
                     sprite (-> raw
                                (t/crop crop-x crop-y frame-width frame-height)
                                (t/invert camera)
                                (t/translate (offset-fn x) (offset-fn y))
                                (t/scale frame-width frame-height))]
                 (instances/assoc instanced idx sprite)))
             instanced
             (map-indexed vector esses))]
        (c/render game (-> instanced' (t/project game-width game-height)))))))
