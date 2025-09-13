(ns engine.engine
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   #?(:cljs [rules.dev.leva-rules :as leva-rules])
   [assets.assets :as assets]
   [assets.texts :as texts]
   [assets.tiled :as tiled]
   [com.rpl.specter :as sp]
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
   [rules.grid-move :as grid-move]
   [rules.input :as input]
   [rules.room :as room]
   [rules.shader :as shader]
   [rules.sprites :as sprites]
   [rules.time :as time]
   [rules.ubim :as ubim]
   [rules.window :as window]))

(defn compile-all [game first-init?]
  (shader/load-shader game)
  (assets/load-asset game)
  (when first-init? (texts/init game)))

(def all-rules-legacy-abstraction
  [window/rules
   time/rules
   input/rules
   tiled/rules
   shader/rules
   ubim/rules
   #?(:cljs leva-rules/rules)
   dev-only/rules])

(def all-systems
  ;; gonna refactor everything to this
  (concat [assets/system
           camera/system
           dialogues/system
           texts/system
           sprites/system
           grid-move/system
           room/system]
          (into [] (map (fn [r] {::world/rules r})) all-rules-legacy-abstraction)))

(defn ->game [context]
  (merge
   (c/->game context)
   {::render-fns*                   (atom nil)
    ::world/atom*                   (atom nil)
    ::world/prev-rules*             (atom nil)   ;; this is dev-only
    ::assets/db*                    (atom {})
    ::texts/font-instances*         (atom {})
    ::dialogues/dialogue-instances* (atom nil)}))

(defn init [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
  (let [[game-width game-height] (utils/get-size game)
        first-init? (nil? @(::world/atom* game))
        all-rules   (apply concat (sp/select [sp/ALL ::world/rules] all-systems))
        all-init    (sp/select [sp/ALL ::world/init-fn some?] all-systems)
        reload-fns  (sp/select [sp/ALL ::world/reload-fn some?] all-systems)
        render-fns  (sp/select [sp/ALL ::world/render-fn some?] all-systems)]
    (reset! (::render-fns* game) render-fns)
    (swap! (::world/atom* game)
           (fn [world]
             (-> (world/init-world game world all-rules reload-fns)
                 (as-> w (reduce (fn [w init-fn] (init-fn game w)) w all-init))
                 (window/set-window game-width game-height)
                 (chapter1/init first-init?)
                 (o/fire-rules))))
    (compile-all game first-init?)))

(def screen-entity
  {:viewport {:x 0 :y 0 :width 0 :height 0}
   :clear    {:color [(/ 0 255) (/ 0 255) (/ 0 255) 1.0] :depth 1}})

(def camera (e/->camera true))

(defn make-limited-logger [limit]
  (let [counter (atom 0)]
    (fn [{world* ::world/atom*} & args]
      ;; evaling the ::world/atom* will blow up js stack 
      (let [messages (apply str args)]
        (when (< @counter limit)
          (println messages)
          (some-> world* (swap!  #(-> % (dev-only/warn messages))))
          (swap! counter inc))
        (when (= @counter limit)
          (println "[SUPRESSED]" messages)
          (swap! counter inc))))))

(def log-once (make-limited-logger 4))

(defn tick [game]
  (if @*refresh?
    (try (println "calling (init game)")
         (swap! *refresh? not)
         (init game)
         #?(:clj (catch Exception err (throw err))
            :cljs (catch js/Error err (log-once game "init-error" err))))
    (try
      (let [{:keys [delta-time total-time]} game
            world (swap! (::world/atom* game)
                         #(-> %
                              (time/insert total-time delta-time)
                              o/fire-rules))
            {game-width :width game-height :height} (first (o/query-all world ::window/window))
            {cam-fn :cam-fn} (first (o/query-all world ::camera/camera-matrix))
            camera (cam-fn camera)]
        (when (and (pos? game-width) (pos? game-height))
          (c/render game (-> screen-entity
                             (update :viewport assoc :width game-width :height game-height)))
          (shader/render-shader-esses game world game-width game-height)
          (doseq [render-fn @(::render-fns* game)]
            (render-fn game world camera game-width game-height))
          (sprites/render game world camera game-width game-height)))
      #?(:clj (catch Exception err (throw err))
         :cljs (catch js/Error err (log-once game "tick-error" err)))))
  game)
