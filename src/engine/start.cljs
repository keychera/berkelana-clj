(ns engine.start
  (:require
   [engine.engine :as engine]
   [engine.world :as world]
   [goog.events :as events]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as pc]
   [rules.input :as input]))

(defn game-loop
  ([game] (game-loop game nil))
  ([game callback-fn]
   (let [game (engine/tick game)]
     (js/requestAnimationFrame
      (fn [ts]
        (let [ts ts]
          (when callback-fn (callback-fn))
          (game-loop (assoc game
                            :delta-time (- ts (:total-time game))
                            :total-time ts)
                     callback-fn)))))))

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
                     (swap! world/world* o/insert ::input/mouse {::input/x x ::input/y y}))))
  #_(events/listen js/window "mousedown"
                   (fn [event]
                     (swap! engine/*state assoc :mouse-button (mousecode->keyword (.-button event)))))
  #_(events/listen js/window "mouseup"
                   (fn [event]
                     (swap! engine/*state assoc :mouse-button nil))))
(defn keycode->keyname [keycode]
  (condp = keycode
    37 :left
    38 :up
    39 :right
    40 :down
    nil))

(defn listen-for-keys []
  (events/listen js/window "keydown"
                 (fn [event]
                   (when-let [keyname (keycode->keyname (.-keyCode event))]
                     ;;  (swap! !panel-atom assoc :pressed-key (str keyname))
                     (swap! world/world* o/insert keyname ::input/pressed-key ::input/keydown))))
  (events/listen js/window "keyup"
                 (fn [event]
                   (when-let [keyname (keycode->keyname (.-keyCode event))]
                     (swap! world/world* o/insert keyname ::input/pressed-key ::input/keyup)))))


(defn resize [context]
  (let [display-width context.canvas.clientWidth
        display-height context.canvas.clientHeight]
    (set! context.canvas.width display-width)
    (set! context.canvas.height display-height)
    (swap! world/world* o/insert ::world/window
           {::world/width display-width ::world/height display-height})))

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
  ([] (-main nil))
  ([loop-callback-fn]
   (let [canvas (js/document.querySelector "canvas")
         context (.getContext canvas "webgl2" (clj->js {:alpha false}))
         initial-game (assoc (pc/->game context)
                             :delta-time 0
                             :total-time (js/performance.now))]
     (engine/init initial-game)
     (listen-for-mouse canvas)
     (listen-for-keys)
     (resize context)
     (listen-for-resize context)
     (game-loop initial-game loop-callback-fn)
     context)))
