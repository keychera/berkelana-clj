(ns engine.shader
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [clojure.spec.alpha :as s]
   [engine.esse :as esse]
   [engine.utils :as utils]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as entities-2d]
   [play-cljc.math :as m]
   [play-cljc.primitives-2d :as primitives-2d]
   [play-cljc.transforms :as t]))

(def vertex-shader
  '{:version "300 es"
    :precision "mediump float"
    :uniforms {u_matrix mat3}
    :inputs {a_position vec2}
    :outputs {}
    :signatures {main ([] void)}
    :functions
    {main ([] (= gl_Position (vec4 (.xy (* u_matrix (vec3 a_position 1))) 0 1)))}})


(def fragment-shader
  {:version   "300 es",
   :precision "mediump float"
   :uniforms  '{u_resolution vec2
                u_root vec2
                u_radius float}
   :inputs    '{}
   :outputs   '{o_color vec4}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (=vec2 st (/ gl_FragCoord.xy u_resolution.xy))
           (= o_color (vec4 st.x st.y "1.0" "0.4")))}})

(defn ->hati [game]
  (let [[game-width game-height] (utils/get-size game)]
    (-> {:vertex     vertex-shader
         :fragment   fragment-shader
         :attributes {'a_position {:data primitives-2d/rect
                                   :type (gl game FLOAT)
                                   :size 2}}
         :uniforms   {'u_matrix     (m/identity-matrix 3)
                      'u_resolution [game-width game-height]
                      'u_root       [0.0 0.0]
                      'u_radius     1.0}}
        (entities-2d/map->TwoDEntity))))

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
     (o/retract! esse-id ::shader-to-load)]

    ::shader-esse
    [:what
     [esse-id ::esse/x x]
     [esse-id ::esse/y y]
     [esse-id ::compiled-shader compiled-shader]]}))

(s/def ::shader-to-load fn?)
(s/def ::loading? boolean?)
(s/def ::compiled-shader map?)

(defn load-shader [game session*]
  (doseq [{:keys [esse-id shader-fn]} (o/query-all @session* ::load-shader)]
    (println "loading shader for" esse-id)
    (swap! session* #(o/insert % esse-id ::loading? true))
    (let [compiled-shader (c/compile game (shader-fn game))]
      (swap! session* #(-> %
                           (o/retract esse-id ::loading?)
                           (o/insert esse-id ::compiled-shader compiled-shader)
                           (o/fire-rules))))))

(defn render-shader-esses [game session game-width game-height]
  (let [shader-esses (o/query-all session ::shader-esse)]
    (doseq [esse shader-esses]
      (let [{:keys [x y compiled-shader]} esse]
        (c/render game
                  (-> compiled-shader
                      (t/project game-width game-height)
                      (t/translate (+ x 32) (+ y 32))
                      (t/scale 64 64)))))))