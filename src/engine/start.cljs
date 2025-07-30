(ns engine.start
  (:require
   [clojure.data :as data]
   [clojure.set :as set]
   [engine.engine :as engine]
   [engine.session :as session]
   [goog.events :as events]
   [leva.core :as leva]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as pc]
   [reagent.core :as r]
   [reagent.dom.client :as rdomc]))

(defonce !panel-atom
  (r/atom
   {:pressed-key {:value "no key" :render (constantly false)}
    :crop?       {:value true}
    :frame       {:value 0 :step 1 :min 0 :max 47}
    :position    {:x 0 :y 0}}))

(defmulti on-leva-change (fn [k _old _new] k))

(defmethod on-leva-change :crop? [_ _ new']
  (swap! session/session* o/insert ::session/leva-spritesheet ::session/crop? new'))

(defmethod on-leva-change :frame [_ _ new']
  (swap! session/session* o/insert ::session/leva-spritesheet ::session/frame new'))

(defmethod on-leva-change :point [_ _ {:keys [x y]}]
  (swap! session/session* o/insert ::session/leva-point {::session/x x ::session/y y}))

(defmethod on-leva-change :default [k old' new']
  #_(println k old' new'))

(defn panel-watcher [_ _ old' new']
  (let [[removed added _common] (data/diff old' new')]
    (doseq [k (set/union (set (keys removed)) (set (keys added)))]
      (on-leva-change k (get old' k) (get new' k)))))

(add-watch !panel-atom :panel-watcher #'panel-watcher)

(defn game-loop [game]
  (let [game (engine/tick game)]
    (js/requestAnimationFrame
     (fn [ts]
       (let [ts ts]
         (try
           (let [{:keys [pos-x pos-y]} (first (o/query-all @session/session* ::session/sprite-esse))] 
             (swap! !panel-atom assoc :position {:x (or pos-x 0) :y (or pos-y 0)}))
           (catch js/Error e (println e)))
         (game-loop (assoc game
                           :delta-time (- ts (:total-time game))
                           :total-time ts)))))))

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
                     (swap! session/session* o/insert ::session/mouse {::session/x x ::session/y y}))))
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
                     (swap! !panel-atom assoc :pressed-key (str keyname))
                     (swap! session/session* o/insert keyname ::session/pressed-key ::session/keydown))))
  (events/listen js/window "keyup"
                 (fn [event]
                   (when-let [keyname (keycode->keyname (.-keyCode event))]
                     (swap! session/session* o/insert keyname ::session/pressed-key ::session/keyup)))))


(defn resize [context]
  (let [display-width context.canvas.clientWidth
        display-height context.canvas.clientHeight]
    (set! context.canvas.width display-width)
    (set! context.canvas.height display-height)
    (swap! session/session* o/insert ::session/window
           {::session/width display-width ::session/height display-height})))

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

;; leva
(defn main-panel []
  [leva/Controls
   {:atom !panel-atom
    :schema {"last key" (leva/monitor (fn [] (:pressed-key @!panel-atom)) {})}}])

(defonce root (delay (rdomc/create-root (.getElementById js/document "app"))))

(defn ^:export run-reagent [] (rdomc/render @root [main-panel]))

;; start the game
(defonce context
  (let [canvas (js/document.querySelector "canvas")
        context (.getContext canvas "webgl2")
        initial-game (assoc (pc/->game context)
                            :delta-time 0
                            :total-time (js/performance.now))]
    (engine/init initial-game)
    (listen-for-mouse canvas)
    (listen-for-keys)
    (resize context)
    (listen-for-resize context)
    (run-reagent)
    (game-loop initial-game)
    context))
