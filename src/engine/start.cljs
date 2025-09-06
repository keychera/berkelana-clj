(ns engine.start
  (:require
   [engine.engine :as engine]
   [engine.world :as world]
   [goog.events :as events]
   [odoyle.rules :as o]
   [rules.input :as input]
   [rules.window :as window]))

(def world-queue (atom #queue []))

(defn update-world [game]
  (when (seq @world-queue)
    (let [world-fn (peek @world-queue)]
      (swap! world-queue pop)
      (swap! (::world/atom* game) world-fn))))

(defn game-loop
  ([game] (game-loop game nil))
  ([game {:keys [callback-fn] :as config}]
   (let [game (engine/tick game)]
     (js/requestAnimationFrame
      (fn [ts]
        (let [delta (- ts (:total-time game))]
          (when callback-fn (callback-fn game))
          (game-loop (assoc game :delta-time delta :total-time ts) config)))))))

(defn mousecode->keyword [mousecode]
  (condp = mousecode
    0 :left
    2 :right
    nil))

(defn listen-for-mouse [canvas]
  (events/listen js/window "mousemove"
                 (fn [event]
                   (let [bounds (.getBoundingClientRect canvas)
                         x (- (.-clientX event) (.-left bounds))
                         y (- (.-clientY event) (.-top bounds))]
                     (swap! world-queue conj (fn mouse-move [w] (o/insert w ::input/mouse {::input/x x ::input/y y}))))))
  #_(events/listen js/window "mousedown"
                   (fn [event]
                     (swap! engine/*state assoc :mouse-button (mousecode->keyword (.-button event)))))
  #_(events/listen js/window "mouseup"
                   (fn [event]
                     (swap! engine/*state assoc :mouse-button nil))))

(defn listen-for-keys []
  (events/listen js/window "keydown"
                 (fn [event]
                   (when-let [keyname (input/js-keyCode->keyname (.-keyCode event))]
                     ;;  (swap! !panel-atom assoc :pressed-key (str keyname))
                     (swap! world-queue conj (fn keydown [w] (o/insert w keyname ::input/pressed-key ::input/keydown))))))
  (events/listen js/window "keyup"
                 (fn [event]
                   (when-let [keyname (input/js-keyCode->keyname (.-keyCode event))]
                     (swap! world-queue conj (fn keyup [w] (o/insert w keyname ::input/pressed-key ::input/keyup)))))))

(defn resize [context]
  (let [display-width context.canvas.clientWidth
        display-height context.canvas.clientHeight]
    (set! context.canvas.width display-width)
    (set! context.canvas.height display-height)
    (swap! world-queue conj (fn set-window [w] (window/set-window w display-width display-height)))))

(defn ^:vibe listen-for-resize [context]
  (let [canvas context.canvas
        debounce-timer (atom nil)
        observer (js/ResizeObserver.
                  (fn [_entries]
                    (when @debounce-timer
                      (js/clearTimeout @debounce-timer))
                    (reset! debounce-timer
                            (js/setTimeout #(resize context) 200))))] ; 200ms after last resize event
    (.observe observer canvas)))

;; start the game
(defn -main
  ([] (-main {}))
  ([config]
   (println "running the game...")
   (let [canvas (js/document.querySelector "canvas")
         context (.getContext canvas "webgl2" (clj->js {:alpha false}))
         initial-game (assoc (engine/->game context)
                             :delta-time 0
                             :total-time (js/performance.now))]
     (engine/init initial-game)
     (listen-for-mouse canvas)
     (listen-for-keys)
     (resize context)
     (listen-for-resize context)
     (game-loop initial-game
                (update config
                        :callback-fn
                        (fn callback-fn [afn]
                          (fn [game]
                            (when afn (afn game))
                            (update-world game)))))
     context)))