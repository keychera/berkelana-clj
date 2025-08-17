(ns engine.start-dev
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

(defonce !panel-atom
  (r/atom
   {:rotation {:value 0 :min 0 :max (* 2 (.-PI js/Math))}
    :scale    {:value 1 :min 0 :max (* 2 (.-PI js/Math))}}))

(defmulti on-leva-change (fn [k _old _new] k))

(defmethod on-leva-change :rotation [_ _ new']
  (swap! world/world* o/insert ::leva-rules/leva-spritesheet ::leva-rules/rotation new'))

(defmethod on-leva-change :scale [_ _ new']
  (swap! world/world* o/insert ::leva-rules/leva-spritesheet ::leva-rules/scale new'))

(defmethod on-leva-change :default [_k _old' _new']
  #_(println k old' new'))

(defn panel-watcher [_ _ old' new']
  (let [[removed added _common] (data/diff old' new')]
    (doseq [k (set/union (set (keys removed)) (set (keys added)))]
      (on-leva-change k (get old' k) (get new' k)))))

(add-watch !panel-atom :panel-watcher #'panel-watcher)

(defonce !fps-counter
  (r/atom {:last-time (js/performance.now) :frames 0 :fps 0}))

(defn ^:vibe update-fps! []
  (let [now (js/performance.now)
        {:keys [last-time frames]} @!fps-counter
        delta (- now last-time)]
    (if (> delta 1000) ;; 1 second has passed
      (swap! !fps-counter assoc :last-time now :frames 0 :fps frames)
      (swap! !fps-counter update :frames inc))))

(defonce dev-atom*
  (r/atom {:dev-value 0
           :upper  {:a 1 :b 2 :c 3}
           :center {:a 1 :b 2 :c 3}
           :lower  {:a 1 :b 2 :c 3}}))

(defn main-panel []
  [:<>
   [leva/Controls
    {:folder {:name "FPS"}
     :atom   !fps-counter
     :schema {"fps graph" (leva/monitor (fn [] (:fps @!fps-counter)) {:graph true :interval 200})
              :fps        {:order 1}
              :last-time  {:render (constantly false)}}}]
   [leva/Controls
    {:folder {:name "Dev"}
     :atom   dev-atom*
     :schema {:dev-value {:order 0} 
              :upper     {:order 1}
              :center    {:order 2}
              :lower     {:order 3}}}]
   [leva/Controls
    {:folder {:name "State"}
     :atom   !panel-atom}]])

(defonce root (delay (rdomc/create-root (.getElementById js/document "app"))))

(defn ^:export run-reagent [] (rdomc/render @root [main-panel]))

(def !hud-visible (atom false))

(defn listen-to-warning! []
  (let [warning (first (o/query-all @world/world* ::dev-only/warning))]
    (if (some? warning)
      (when (not @!hud-visible)
        (hud/hud-warnings {:info {:sources [{:warnings [{:resource-name "code"
                                                         :msg (:message warning)
                                                         :source-excerpt "what to pass here?"}]}]}})
        (reset! !hud-visible true))
      (when @!hud-visible
        (dom/remove (dom/by-id hud/hud-id))
        (reset! !hud-visible false)))
    (if-let [dev-value (first (o/query-all @world/world* ::dev-only/dev-value))]
      (swap! dev-atom* assoc :dev-value (:value dev-value))
      (swap! dev-atom* assoc :dev-value 0))))

(defn dev-loop []
  (update-fps!)
  (listen-to-warning!))

(defonce dev-only
  (do (run-reagent)
      (start/-main dev-loop)))

(comment
  (dom/remove (dom/by-id hud/hud-id))
  (hud/hud-warnings {:info {:sources [{:warnings [{:resource-name "engine.cljc"
                                                   :msg "hello shadow-hud"
                                                   :file "src/engine/engine.cljc"
                                                   :line 10 :column 10
                                                   :source-excerpt "what to pass here?"}]}]}}))