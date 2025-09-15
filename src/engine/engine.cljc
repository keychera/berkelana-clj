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
   [rules.interface.input :as input]
   [rules.room :as room]
   [rules.shader :as shader]
   [rules.sprites :as sprites]
   [rules.time :as time]
   [rules.ubim :as ubim]
   [rules.window :as window]))

(defn compile-all [game]
  (shader/load-shader game)
  (assets/load-asset game))

(def all-rules-legacy-abstraction
  [window/rules
   time/rules
   input/rules
   tiled/rules
   ubim/rules
   dev-only/rules])

(def all-systems
  ;; gonna refactor everything to this
  (concat [#?(:cljs leva-rules/sysyem)
           assets/system
           camera/system
           grid-move/system
           room/system
           sprites/system
           dialogues/system
           texts/system
           shader/system
           chapter1/system]
          (into [] (map (fn [r] {::world/rules r})) all-rules-legacy-abstraction)))

(defn ->game [context]
  (merge
   (c/->game context)
   {::render-fns*                   (atom nil)
    ::world/atom*                   (atom nil)
    ::world/init-cnt*               (atom 0)  ;; this is dev-only
    ::world/prev-rules*             (atom nil)   ;; this is dev-only
    ::assets/db*                    (atom {})
    ::texts/font-instances*         (atom {})
    ::dialogues/dialogue-instances* (atom nil)}))

(defn init [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
  (let [[game-width game-height] (utils/get-size game)
        all-rules   (apply concat (sp/select [sp/ALL ::world/rules] all-systems))
        all-init    (sp/select [sp/ALL ::world/init-fn some?] all-systems)
        reload-fns  (sp/select [sp/ALL ::world/reload-fn some?] all-systems)
        render-fns  (sp/select [sp/ALL ::world/render-fn some?] all-systems)]
    (swap! (::world/init-cnt* game) inc)
    (reset! (::render-fns* game) render-fns)
    (swap! (::world/atom* game)
           (fn [world]
             (-> (world/init-world world game all-rules reload-fns)
                 (as-> w (reduce (fn [w' init-fn] (init-fn w' game)) w all-init))
                 (window/set-window game-width game-height)
                 (o/fire-rules))))
    (def hmm-game game)
    (compile-all game)))

(def screen-entity
  {:viewport {:x 0 :y 0 :width 0 :height 0}
   :clear    {:color [(/ 0 255) (/ 0 255) (/ 0 255) 1.0] :depth 1}})

(def camera (e/->camera true))

#?(:cljs
   (defn make-limited-logger [limit]
     (let [counter (atom 0)]
       (fn [{world* ::world/atom*} err & args]
      ;; evaling the ::world/atom* will blow up js stack 
         (let [messages (apply str args)]
           (when (< @counter limit)
             (js/console.error (.-stack err))
             (some-> world* (swap!  #(-> % (dev-only/warn (str messages (.-message err))))))
             (swap! counter inc))
           (when (= @counter limit)
             (println "[SUPRESSED]" messages)
             (swap! counter inc)))))))

#?(:cljs (def log-once (make-limited-logger 4)))

(defn tick [game]
  (if @*refresh?
    (try (println "calling (init game)")
         (swap! *refresh? not)
         (init game)
         #?(:clj  (catch Exception err (throw err))
            :cljs (catch js/Error err (log-once game err "[init-error] "))))
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
          (doseq [render-fn @(::render-fns* game)]
            (render-fn world game camera game-width game-height))))
      #?(:clj  (catch Exception err (throw err))
         :cljs (catch js/Error err (log-once game err "[tick-error] ")))))
  game)

(comment
  (+ 1 1)
  ;; if you query the atom instead of deref atom, it will blow up js 
  (o/query-all @(::world/atom* hmm-game) ::texts/texts-to-render)
  (filter #(= (first %) :chara/ubim) (o/query-all @(::world/atom* hmm-game))))