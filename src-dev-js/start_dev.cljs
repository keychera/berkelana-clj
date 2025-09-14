(ns start-dev
  (:require
   [clojure.data :as data]
   [clojure.set :as set]
   [clojure.spec.test.alpha :as st]
   [engine.start :as start]
   [engine.world :as world]
   [leva.core :as leva]
   [odoyle.rules :as o]
   [reagent.core :as r]
   [reagent.dom.client :as rdomc]
   [rules.dev.dev-only :as dev-only]
   [rules.dev.leva-rules :as leva-rules]
   [shadow.cljs.devtools.client.hud :as hud]
   [shadow.dom :as dom]))

(st/instrument)

(defonce fps-counter*
  (r/atom {:last-time (js/performance.now) :frames 0 :fps 0}))

(defn ^:vibe update-fps! []
  (let [now (js/performance.now)
        {:keys [last-time frames]} @fps-counter*
        delta (- now last-time)]
    (if (> delta 1000) ;; 1 second has passed
      (swap! fps-counter* assoc :last-time now :frames 0 :fps frames)
      (swap! fps-counter* update :frames inc))))

(defonce dev-atom*
  (r/atom {:dev-value  "raw value"
           :dev-slider {:value 0 :min 0 :max 64}}))

(defmulti on-dev-change (fn [k _old _new] k))

(defmethod on-dev-change :dev-slider [_ _ new']
  (swap! start/world-queue conj (fn dev-slider [w] (o/insert w ::leva-rules/dev ::leva-rules/dev-slider new'))))

(defmethod on-dev-change :default [_k _old' _new']
  #_(println k old' new'))

(defn panel-watcher [_ _ old' new']
  (let [[removed added _common] (data/diff old' new')]
    (doseq [k (set/union (set (keys removed)) (set (keys added)))]
      (on-dev-change k (get old' k) (get new' k)))))

(add-watch dev-atom* :panel-watcher #'panel-watcher)

(defn main-panel []
  [:<>
   [leva/Controls
    {:folder {:name "FPS"}
     :atom   fps-counter*
     :schema {"fps graph" (leva/monitor (fn [] (:fps @fps-counter*)) {:graph true :interval 200})
              :fps        {:order 1}
              :last-time  {:render (constantly false)}}}]
   [leva/Controls
    {:folder {:name "Dev"}
     :atom   dev-atom*
     :schema {:dev-value  {:order 0}
              :dev-slider {:order 1}}}]])

(defonce root (delay (rdomc/create-root (.getElementById js/document "app"))))

(defn ^:export run-reagent [] (rdomc/render @root [main-panel]))

(def !hud-visible (atom false))

(defn listen-to-dev-events! [game]
  (if-let [warning (first (o/query-all @(::world/atom* game) ::dev-only/warning))]
    (when (not @!hud-visible)
      (hud/hud-warnings {:info {:sources [{:warnings [{:resource-name "code"
                                                       :msg (:message warning)
                                                       :source-excerpt "what to pass here?"}]}]}})
      (reset! !hud-visible true))
    (when @!hud-visible
      (dom/remove (dom/by-id hud/hud-id))
      (reset! !hud-visible false)))
  (when-let [dev-value (first (o/query-all @(::world/atom* game) ::dev-only/dev-value))]
    (swap! dev-atom* assoc :dev-value (:value dev-value))))

(defn dev-loop [game]
  (update-fps!)
  (listen-to-dev-events! game))

(defonce dev-only
  (do (run-reagent)
      (start/-main {:callback-fn dev-loop})))

(comment
  (dom/remove (dom/by-id hud/hud-id))
  (hud/hud-warnings {:info {:sources [{:warnings [{:resource-name "engine.cljc"
                                                   :msg "hello shadow-hud"
                                                   :file "src/engine/engine.cljc"
                                                   :line 10 :column 10
                                                   :source-excerpt "what to pass here?"}]}]}}))