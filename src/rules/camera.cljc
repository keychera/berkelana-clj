(ns rules.camera
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [engine.world :as world]
   [odoyle.rules :as o]
   [play-cljc.transforms :as t]
   [rules.pos2d :as pos2d]
   [rules.window :as window]))

(s/def ::cam-fn fn?)

(world/system system
  {::world/init-fn
   (fn [world _game]
     (o/insert world ::camera ::pos2d/pos2d {:x -8 :y 0}))
   
   ::world/rules
   (o/ruleset
    {::camera-matrix
     [:what
      [::camera ::cam-fn cam-fn]]

     ::update-camera
     [:what
      [::window/window ::window/width width]
      [::window/window ::window/height height]
      [::camera ::pos2d/pos2d pos] ;; idk which one is better: combined like this or separated like rules.shader or rules.sprite 
      :then
      (let [map-size 72 ;; hardcoded for now
            scale 0.2 ;; value from trial and error
            cam-pos (update-vals pos #(* % 16)) ;; 16 from tile-size
            cam-fn
            #(-> %
                 (t/translate (+ (* width 0.5 scale)  (- map-size) (:x cam-pos))
                              (+ (* height 0.5 scale) (- map-size) (:y cam-pos)))
                 (t/rotate Math/PI)
                 (t/scale scale scale))]
        (s-> session
             (o/insert ::camera ::cam-fn cam-fn)))]})})