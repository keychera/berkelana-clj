(ns rules.shader
  (:require
   #?(:clj [engine.macros :refer [insert! s->]]
      :cljs [engine.macros :refer-macros [s-> insert!]])
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [clojure.spec.alpha :as s]
   [com.rpl.specter :as specter]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as entities-2d]
   [play-cljc.math :as m]
   [play-cljc.transforms :as t]
   [rules.esse :as esse]
   [rules.pos2d :as pos2d]
   [rules.time :as time]))

(def rules
  (o/ruleset
   {::load-shader
    [:what
     [esse-id ::shader-to-load shader-fn]]

    ::loading-shader
    [:what
     [esse-id ::shader-to-load shader-fn]
     [esse-id ::loading? true]
     :then
     (s-> session (o/retract esse-id ::shader-to-load))]

    ::shader-esse
    [:what
     [esse-id ::pos2d/x x]
     [esse-id ::pos2d/y y]
     [esse-id ::compiled-shader compiled-shader]]

    ::shader-update
    [:what
     [::time/now ::time/total total-time]
     [esse-id ::compiled-shader compiled-shader {:then false}]
     :then
     (insert! esse-id ::compiled-shader
              (->> compiled-shader
                   (specter/setval [:uniforms 'u_time] total-time)))]}))

(s/def ::shader-to-load fn?)
(s/def ::loading? boolean?)
(s/def ::compiled-shader map?)

(def vertex-shader
  '{:version "300 es"
    :precision "mediump float"
    :uniforms {u_matrix mat3
               u_time   float}
    :inputs {a_position vec2}
    :outputs {}
    :signatures {main ([] void)}
    :functions
    {main ([]
           (=vec2 pos a_position)
           (=float bounce (* "0.2" (sin (/ u_time "60.0"))))
           (= pos.y (+ pos.y bounce))
           (= pos.y (+ pos.y (* "0.1" (sin (+ (* pos.x "2.0") u_time)))))
           (= gl_Position (vec4 (.xy (* u_matrix (vec3 pos 1))) 0 1)))}})


(def fragment-shader
  {:version   "300 es",
   :precision "mediump float"
   :uniforms  '{u_time float}
   :inputs    '{}
   :outputs   '{o_color vec4}
   :signatures '{main ([] void)}
   :functions
   '{main ([] (= o_color (vec4 "0.8" (sin (/ u_time "500.0")) (cos (/ u_time "200.0")) "0.8")))}})

(defn make-circle [num-of-polygon]
  (let [two-pi (* 2 Math/PI)
        angle-shift (/ two-pi num-of-polygon)]
    (->> (take num-of-polygon (iterate inc 0))
         (map (fn [i] [(float (+ (/ (Math/cos (* i angle-shift)) 2) 1))
                       (float (+ (/ (Math/sin (* i angle-shift)) 2) 1))
                       (float (+ (/ (Math/cos (* (inc i) angle-shift)) 2) 1))
                       (float (+ (/ (Math/sin (* (inc i) angle-shift)) 2) 1))
                       1.0 1.0]))
         flatten (into []))))

(defn ->hati [game]
  (-> {:vertex     vertex-shader
       :fragment   fragment-shader
       :attributes {'a_position {:data (make-circle 16)
                                 :type (gl game FLOAT)
                                 :size 2}}
       :uniforms   {'u_matrix (m/identity-matrix 3)
                    'u_time   0}}
      (entities-2d/map->TwoDEntity)))



(defn load-shader [game world*]
  (doseq [{:keys [esse-id shader-fn]} (o/query-all @world* ::load-shader)]
    (println "loading shader for" esse-id)
    (swap! world* #(o/insert % esse-id ::loading? true))
    (try (let [compiled-shader (c/compile game (shader-fn game))]
           (swap! world* #(-> %
                                (o/retract esse-id ::loading?)
                                (o/insert esse-id ::compiled-shader compiled-shader))))
         (catch #?(:clj Exception :cljs js/Error) err ;; maybe devonly
           (swap! world* #(-> % (o/retract esse-id ::loading?)))
           (throw err)))))

(defn render-shader-esses [game world game-width game-height]
  (let [shader-esses (o/query-all world ::shader-esse)]
    (doseq [esse shader-esses]
      (let [{:keys [x y compiled-shader]} esse]
        (c/render game
                  (-> compiled-shader
                      (t/project game-width game-height)
                      (t/translate x y)
                      (t/scale 64 64)))))))
