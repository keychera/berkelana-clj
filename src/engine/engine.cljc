(ns engine.engine
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [engine.refresh :refer [*refresh?]]
   [engine.world :as world]
   [rules.shader :as shader]
   [rules.time :as time]
   [engine.utils :as utils]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as entities-2d]
   [play-cljc.transforms :as t]))

(defn load-image-asset [game world*]
  (doseq [{:keys [asset-id image-path]} (o/query-all @world* ::world/load-image)]
    (swap! world* #(o/insert % asset-id ::world/image-loading? true))
    (println "loading image asset for" asset-id image-path)
    (utils/get-image
     image-path
     (fn [{:keys [data width height]}]
       (let [image-entity (entities-2d/->image-entity game data width height)
             image-entity (c/compile game image-entity)
             loaded-image (assoc image-entity :width width :height height)]
         (swap! world*
                #(-> %
                     (o/retract asset-id ::world/image-loading?)
                     (o/insert asset-id ::world/image-asset loaded-image)
                     (o/fire-rules))))))))

(defn compile-all [game world*]
  (shader/load-shader game world*)
  (load-image-asset game world*))

(defn init [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
  (let [[game-width game-height] (utils/get-size game)]
    (swap! world/world*
           (fn [world]
             (-> (world/init-world world)
                 (o/insert ::world/window
                           {::world/width game-width
                            ::world/height game-height})
                 (o/fire-rules))))
    (compile-all game world/world*)))

(defn render-sprites-esses [game world game-width game-height]
  (let [sprite-esses (o/query-all world ::world/sprite-esse)
        {:keys [crop?]} (first (o/query-all world ::world/leva-spritesheet))]
    (doseq [sprite-esse sprite-esses]
      (let [{:keys [x y current-sprite frame-index]} sprite-esse]
        (if crop?
          (let [spritesheet-width 384
                frame-width 32
                frame-height 32
                frames-per-row (/ spritesheet-width frame-width)
                frame-x (mod frame-index frames-per-row)
                frame-y (quot frame-index frames-per-row)
                crop-x (* frame-x frame-width)
                crop-y (* frame-y frame-height)
                scale 4]
            (c/render game
                      (-> current-sprite
                          (t/project game-width game-height)
                          (t/translate x y)
                          (t/crop crop-x crop-y frame-width frame-height)
                          (t/scale (* frame-width scale) (* frame-height scale)))))
          (c/render game
                    (-> current-sprite
                        (t/project game-width game-height)
                        (t/translate x y)
                        (t/scale (:width current-sprite)
                                 (:height current-sprite))
                        (t/crop 0 0 (:width current-sprite) (:height current-sprite)))))))))

(def screen-entity
  {:viewport {:x 0 :y 0 :width 0 :height 0}
   :clear {:color [(/ 0 255) (/ 0 255) (/ 0 255) 1.0] :depth 1}})

(defn tick [game]
  (if @*refresh?
    (try (println "calling (compile-all game)")
         (swap! *refresh? not)
         (init game)
         (catch #?(:clj Exception :cljs js/Error) err
           (println "compile-all error")
           #?(:clj  (println err)
              :cljs (js/console.error err))))
    (try
      (let [{:keys [delta-time total-time]} game
            world (swap! world/world*
                           #(-> %
                                (time/insert total-time delta-time)
                                o/fire-rules))
            {game-width :width game-height :height} (first (o/query-all world ::world/window))]
        (when (and (pos? game-width) (pos? game-height))
          (c/render game (-> screen-entity
                             (update :viewport assoc :width game-width :height game-height)))
          (render-sprites-esses game world game-width game-height)
          (shader/render-shader-esses game world game-width game-height)))
      (catch #?(:clj Exception :cljs js/Error) err
        (println "tick error")
        #?(:clj  (println err)
           :cljs (js/console.error err)))))
  game)
