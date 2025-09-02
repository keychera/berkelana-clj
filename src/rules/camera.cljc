(ns rules.camera
  (:require
   #?(:clj [engine.macros :refer [s->]]
      :cljs [engine.macros :refer-macros [s->]])
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]
   [play-cljc.transforms :as t]
   [rules.window :as window]))

(s/def ::cam-fn fn?)

(def rules
  (o/ruleset
   {::camera-matrix
    [:what
     [::camera ::cam-fn cam-fn]]
    
    ::update-camera
    [:what
     [::window/window ::window/width width]
     [::window/window ::window/height height]
     :then
     (let [map-size 72 ;; hardcoded for now
           scale 0.15 ;; value from trial and error
           cam-fn
           #(-> %
                (t/translate (+ (* width 0.5 scale) (- map-size) 8)
                             (+ (* height 0.5 scale) (- map-size) 8))
                (t/rotate Math/PI)
                (t/scale scale scale))]
       (s-> session
            (o/insert ::camera ::cam-fn cam-fn)))]}))