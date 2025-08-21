(ns engine.engine
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [assets.assets :as assets]
   [assets.texts :as texts]
   [assets.tiled :as tiled]
   [engine.context :as context]
   [engine.refresh :refer [*refresh?]]
   [engine.utils :as utils]
   [engine.world :as world]
   [game.chapter1 :as chapter1]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as e]
   [rules.camera :as camera]
   [rules.dev.dev-only :as dev-only]
   [rules.dialogues :as dialogues]
   [rules.shader :as shader]
   [rules.time :as time]
   [rules.ubim :as ubim]
   [rules.window :as window]))

(defn compile-all [game world* first-init?]
  (shader/load-shader game world*)
  (assets/load-asset game world*)
  (when first-init? (texts/init game)))

(defn init [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
  (let [[game-width game-height] (utils/get-size game)
        first-init? (nil? @world/world*)]
    (reset! context/game* game)
    (swap! world/world*
           (fn [world]
             (-> (world/init-world world)
                 (window/set-window game-width game-height)
                 (chapter1/init first-init?)
                 (o/fire-rules))))
    (compile-all game world/world* first-init?)))

(def screen-entity
  {:viewport {:x 0 :y 0 :width 0 :height 0}
   :clear {:color [(/ 0 255) (/ 0 255) (/ 0 255) 1.0] :depth 1}})

(def camera (e/->camera true))
;; SCROT but the last one is the first

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
            {game-width :width game-height :height} (first (o/query-all world ::window/window))
            {cam-fn :cam-fn}  (first (o/query-all world ::camera/camera-matrix))
            camera  (cam-fn camera)]
        (when (and (pos? game-width) (pos? game-height))
          (c/render game (-> screen-entity
                             (update :viewport assoc :width game-width :height game-height)))
          (tiled/render-tiled-map game camera game-width game-height)
          (ubim/render game world camera game-width game-height)
          (dialogues/render game world camera game-width game-height)
          (texts/render game world camera game-width game-height)
          (shader/render-shader-esses game world game-width game-height)))
      (catch #?(:clj Exception :cljs js/Error) err
        (swap! world/world* #(-> % (dev-only/warn (str "tick error " err))))
        #?(:clj  (println err)
           :cljs (js/console.error err)))))
  game)
