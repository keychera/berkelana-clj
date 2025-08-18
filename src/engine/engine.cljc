(ns engine.engine
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [assets.asset :as asset]
   [assets.spritesheet :as spritesheet]
   [assets.tiled :as tiled]
   [engine.refresh :refer [*refresh?]]
   [engine.utils :as utils]
   [engine.world :as world]
   [game.chapter1 :as chapter1]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.transforms :as t]
   [rules.dev.dev-only :as dev-only]
   [rules.shader :as shader]
   [rules.time :as time]))

(defn compile-all [game world*]
  (shader/load-shader game world*)
  (asset/load-asset game world*))

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
                 (chapter1/init (nil? world))
                 (o/fire-rules))))
    (compile-all game world/world*)))

(defn render-sprites-esses [game world game-width game-height]
  (let [sprite-esses (o/query-all world ::world/sprite-esse)]
    (doseq [sprite-esse sprite-esses]
      (let [{:keys [x y asset-id frame-index]} sprite-esse
            {::spritesheet/keys [image frame-height frame-width]} (get @asset/db* asset-id)
            frames-per-row (/ (:width image) frame-width)
            frame-x (mod frame-index frames-per-row)
            frame-y (quot frame-index frames-per-row)
            crop-x (* frame-x frame-width)
            crop-y (* frame-y frame-height)
            scale 4]
        (c/render game
                  (-> image
                      (t/project game-width game-height)
                      (t/translate x y)
                      (t/scale (* frame-width scale) (* frame-height scale))
                      (t/crop crop-x crop-y frame-width frame-height)))))))

(def screen-entity
  {:viewport {:x 0 :y 0 :width 0 :height 0}
   :clear {:color [(/ 0 255) (/ 0 255) (/ 0 255) 1.0] :depth 1}})

(defn tick [game]
  (if @*refresh?
    (try (println "calling (init game)")
         (swap! *refresh? not)
         (init game)
         (catch #?(:clj Exception :cljs js/Error) err
           (swap! world/world* #(-> % (dev-only/warn (str "init error " err))))
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
          (tiled/render-tiled-map game game-width game-height)
          (render-sprites-esses game world game-width game-height)
          (shader/render-shader-esses game world game-width game-height)))
      (catch #?(:clj Exception :cljs js/Error) err
        (swap! world/world* #(-> % (dev-only/warn (str "tick error " err))))
        #?(:clj  (println err)
           :cljs (js/console.error err)))))
  game)
